package cat.narezany.margyt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The badges, and where they come from.
 *
 * They used to be one account and one picture, written into the code, which
 * meant a new badge was a new build of the mod for everybody. Now they are a
 * file in the repository -- `badges.json` -- read when the app starts and
 * again every five minutes. Adding a badge is editing that file.
 *
 * What arrives is kept on disk as well, so the badge is there on the next
 * start before the network has answered, and stays there if GitHub is not
 * reachable at all. Nothing waits on the network: the first draw uses whatever
 * is already known, and a refresh that finds something new simply applies from
 * then on.
 *
 * Pictures named by a badge are fetched the same way and cached beside the
 * file, by a name made from the path, so the same picture is never fetched
 * twice.
 */
public final class Badges {

    private Badges() {}

    /** Where the file lives. Raw, so no page has to be parsed to find it. */
    private static final String SOURCE =
            "https://raw.githubusercontent.com/narezany/MargyT/main/badges.json";
    private static final String FILES =
            "https://raw.githubusercontent.com/narezany/MargyT/main/";

    private static final long EVERY = 5 * 60 * 1000L;

    /** One badge, as the file describes it. */
    public static final class Badge {
        public final String id;
        public final String image;
        public final int colour;
        public final String title;
        public final String text;
        public final String button;

        Badge(String id, String image, int colour, String title, String text, String button) {
            this.id = id;
            this.image = image;
            this.colour = colour;
            this.title = title;
            this.text = text;
            this.button = button;
        }
    }

public static final String KEY = "badges_on";

    private static volatile Boolean cached;

    public static boolean isEnabled() {
        Boolean known = cached;
        if (known != null) return known;
        try {
            android.content.Context context = Margy.context();
            if (context == null) return true;  // on until there is somewhere to read from
            boolean on = context.getSharedPreferences(Margy.PREFS,
                    android.content.Context.MODE_PRIVATE).getBoolean(KEY, true);
            cached = on;
            return on;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setEnabled(boolean enabled) {
        cached = enabled;
        try {
            android.content.Context context = Margy.context();
            if (context == null) return;
            context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY, enabled).apply();
        } catch (Throwable ignored) {
        }
    }

    /**
     * uid -> every badge that account has, in the order the file lists them.
     * Replaced wholesale on a refresh, never edited in place.
     */
    private static volatile Map<String, Badge[]> known = new HashMap<String, Badge[]>();

    /**
     * The same badges by number, because a name has to carry which one it has.
     *
     * The mark left on a name is a single character, and there is nothing else
     * about it that says whose badge it is -- the view that draws it never sees
     * the account. So the character *is* the number: the first badge is
     * U+E000, the second U+E001, and the list here says which is which. They
     * are private-use codepoints, so nothing else can collide with them, and
     * the numbering only has to hold for as long as the app is running.
     */
    private static volatile Badge[] numbered = new Badge[0];

    private static final char FIRST = '\uE000';
    private static final int MOST = 64;

    private static volatile boolean started;

    public static Badge[] of(String uid) {
        if (uid == null) return null;
        return known.get(uid);
    }

    /**
     * The characters that stand for this account's badges, in order.
     *
     * An account can hold several -- one for supporting the mod, one for
     * drawing an icon it ships with -- and each is its own character, so each
     * becomes its own picture and answers its own tap.
     */
    public static String marksFor(String uid) {
        if (!isEnabled()) return "";
        Badge[] held = of(uid);
        if (held == null || held.length == 0) return "";
        Badge[] list = numbered;
        StringBuilder out = new StringBuilder(held.length);
        for (Badge badge : held) {
            for (int i = 0; i < list.length; i++) {
                if (list[i] == badge) {
                    out.append((char) (FIRST + i));
                    break;
                }
            }
        }
        return out.toString();
    }

    /** Whether a character is one of ours, without looking anything up. */
    public static boolean isMark(char c) {
        return c >= FIRST && c < FIRST + MOST;
    }

    public static Badge byMark(char c) {
        Badge[] list = numbered;
        int at = c - FIRST;
        return at >= 0 && at < list.length ? list[at] : null;
    }

    // ------------------------------------------------------------- keeping up

    /**
     * Read what is on disk, then ask GitHub -- now and every five minutes.
     *
     * Called from the mod's start-up hook, so the first read happens before
     * TikTok has drawn anything.
     */
    public static synchronized void start(Context context) {
        if (started) return;
        started = true;

        byte[] cached = Net.read(file(context));
        if (cached != null) {
            apply(cached);
            prefetch(context);
        }

        final Handler handler = new Handler(Looper.getMainLooper());
        final Context application = context.getApplicationContext();
        handler.post(new Runnable() {
            @Override
            public void run() {
                refresh(application);
                handler.postDelayed(this, EVERY);
            }
        });
    }

    private static void refresh(final Context context) {
        Net.away("badges", new Runnable() {
            @Override
            public void run() {
                byte[] fresh = Net.bytes(SOURCE);
                if (fresh == null) return;
                byte[] old = Net.read(file(context));
                if (old != null && java.util.Arrays.equals(old, fresh)) return;
                if (apply(fresh)) {
                    Net.save(file(context), fresh);
                    prefetch(context);
                    Diary.note("badges: " + known.size() + " accounts, "
                            + numbered.length + " badges");
                }
            }
        });
    }

    /**
     * Fetch every badge's picture now rather than when a name needs it.
     *
     * A picture asked for while a name is being drawn cannot be waited on --
     * the badge falls back to the mod's own note and only becomes itself the
     * next time that view is drawn, which is why badges used to appear a beat
     * late or not at all on a profile. There are a handful of pictures and
     * they are cached on disk, so fetching them all at the start costs one
     * round trip on the first run and nothing afterwards.
     */
    private static void prefetch(Context context) {
        try {
            for (Badge badge : numbered) {
                if (badge != null && badge.image.length() > 0) {
                    picture(context, badge.image);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean apply(byte[] json) {
        try {
            JSONObject root = new JSONObject(new String(json, "UTF-8"));
            JSONArray list = root.optJSONArray("badges");
            if (list == null) return false;

            Map<String, java.util.List<Badge>> built =
                    new HashMap<String, java.util.List<Badge>>();
            java.util.LinkedHashMap<Badge, Boolean> distinct =
                    new java.util.LinkedHashMap<Badge, Boolean>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject one = list.optJSONObject(i);
                if (one == null) continue;
                Badge badge = new Badge(
                        one.optString("id", "badge" + i),
                        one.optString("image", ""),
                        colour(one.optString("colour", "")),
                        localised(one, "title"),
                        localised(one, "text"),
                        localised(one, "button"));
                JSONArray users = one.optJSONArray("users");
                if (users == null) continue;
                distinct.put(badge, Boolean.TRUE);
                for (int u = 0; u < users.length(); u++) {
                    String uid = users.optString(u, "");
                    if (uid.length() == 0) continue;
                    java.util.List<Badge> theirs = built.get(uid);
                    if (theirs == null) {
                        theirs = new java.util.ArrayList<Badge>(2);
                        built.put(uid, theirs);
                    }
                    if (!theirs.contains(badge)) theirs.add(badge);
                }
            }
            Badge[] order = new Badge[Math.min(distinct.size(), MOST)];
            int at = 0;
            for (Badge badge : distinct.keySet()) {
                if (at == order.length) break;
                order[at++] = badge;
            }

            Map<String, Badge[]> settled = new HashMap<String, Badge[]>(built.size());
            for (Map.Entry<String, java.util.List<Badge>> entry : built.entrySet()) {
                settled.put(entry.getKey(), entry.getValue().toArray(new Badge[0]));
            }

            numbered = order;
            known = settled;
            return true;
        } catch (Throwable error) {
            Diary.note("badges: unreadable, keeping the last ones -- " + error);
            return false;
        }
    }

    /** `text_ru` before `text`, so a badge can speak the phone's language. */
    private static String localised(JSONObject one, String field) {
        String language = Locale.getDefault().getLanguage();
        String translated = one.optString(field + "_" + language, "");
        if (translated.length() > 0) return translated;
        return one.optString(field, "");
    }

    private static int colour(String hex) {
        try {
            if (hex.length() == 0) return 0;
            return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), "margyt/badges.json");
    }

    // -------------------------------------------------------- their pictures

    private static final Map<String, Bitmap> pictures = new HashMap<String, Bitmap>();

    /**
     * The picture a badge names, or null for the mod's own note.
     *
     * Answers from memory or from the cache on disk, and never waits: a
     * picture that is not here yet is fetched on a thread, and the badge draws
     * the note until the next time it is drawn.
     */
    public static Bitmap picture(final Context context, final String path) {
        if (path == null || path.length() == 0) return null;
        synchronized (pictures) {
            if (pictures.containsKey(path)) return pictures.get(path);
            pictures.put(path, null);  // asked for; do not ask again
        }

        final File cache = new File(context.getFilesDir(), "margyt/badges/" + name(path));
        byte[] have = Net.read(cache);
        if (have != null) return remember(path, have);

        Net.away("badge picture", new Runnable() {
            @Override
            public void run() {
                byte[] raw = Net.bytes(FILES + path);
                if (raw == null) return;
                Net.save(cache, raw);
                remember(path, raw);
            }
        });
        return null;
    }

    private static Bitmap remember(String path, byte[] raw) {
        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length);
            if (bitmap == null) return null;
            synchronized (pictures) {
                pictures.put(path, bitmap);
            }
            return bitmap;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** A file name that is a path's, without being a path. */
    private static String name(String path) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            out.append(Character.isLetterOrDigit(c) || c == '.' ? c : '_');
        }
        return out.toString();
    }
}

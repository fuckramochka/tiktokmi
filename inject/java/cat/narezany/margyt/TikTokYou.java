package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * A word in front of the names of everyone who backed TikTok You.
 *
 * They publish the list themselves, openly, at their own API -- this reads it
 * and nothing else, adds nothing to it, and sends nothing anywhere. What it
 * does with it is write a word in front of those names inside this mod, on
 * this phone. It is a joke, it is off unless switched on, and the word is
 * whatever you set it to.
 *
 * The list is kept on disk the same way the badges are, so it is there before
 * the network answers and stays there when GitHub and everything else is
 * unreachable.
 */
public final class TikTokYou {

    private TikTokYou() {}

    public static final String KEY_ON = "ttyou_on";
    public static final String KEY_PREFIX = "ttyou_prefix";

    /** Where they publish it. */
    private static final String SOURCE = "https://tiktokyou.yzewe.ru/api/v1/badges";

    private static final long EVERY = 60 * 60 * 1000L;

    public static final String DEFAULT_PREFIX = "[тикток хрю]";

    private static volatile Set<String> backers = new HashSet<String>();
    private static volatile boolean started;

    // ---------------------------------------------------------- the switches

    private static volatile Boolean on;
    private static volatile String word;

    public static boolean isEnabled() {
        Boolean known = on;
        if (known != null) return known.booleanValue();
        boolean value = flag(KEY_ON, false);
        on = Boolean.valueOf(value);
        return value;
    }

    public static void setEnabled(boolean enabled) {
        on = Boolean.valueOf(enabled);
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_ON, enabled).apply();
    }

    public static String prefix() {
        String known = word;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        String value = prefs == null ? DEFAULT_PREFIX
                : prefs.getString(KEY_PREFIX, DEFAULT_PREFIX);
        word = value;
        return value;
    }

    public static void setPrefix(String value) {
        if (value == null) value = "";
        word = value;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(KEY_PREFIX, value).apply();
    }

    /** The word to put in front of this account's name, or nothing. */
    public static String prefixFor(String uid) {
        if (uid == null || !isEnabled()) return "";
        return backers.contains(uid) ? prefix() : "";
    }

    public static int count() {
        return backers.size();
    }

    // ------------------------------------------------------------- keeping up

    public static synchronized void start(Context context) {
        if (started) return;
        started = true;

        byte[] cached = Net.read(file(context));
        if (cached != null) apply(cached);

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
        // nothing is fetched unless somebody has asked for the joke
        if (!isEnabled()) return;
        Net.away("tiktok you", new Runnable() {
            @Override
            public void run() {
                byte[] fresh = Net.bytes(SOURCE);
                if (fresh == null) return;
                if (apply(fresh)) {
                    Net.save(file(context), fresh);
                    Diary.note("tiktok you: " + backers.size() + " backers");
                }
            }
        });
    }

    /**
     * Their list is an array of objects, each with a `uid`.
     *
     * Read defensively: this is somebody else's API and it can change shape or
     * answer with a page of html at any time. Anything unreadable leaves the
     * list exactly as it was.
     */
    private static boolean apply(byte[] json) {
        try {
            JSONArray list = new JSONArray(new String(json, "UTF-8"));
            Set<String> built = new HashSet<String>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject one = list.optJSONObject(i);
                if (one == null) continue;
                String uid = one.optString("uid", "");
                if (uid.length() > 0) built.add(uid);
            }
            if (built.isEmpty()) return false;
            backers = built;
            return true;
        } catch (Throwable error) {
            Diary.note("tiktok you: unreadable -- " + error);
            return false;
        }
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), "margyt/tiktokyou.json");
    }

    // ------------------------------------------------------------- the store

    private static boolean flag(String key, boolean fallback) {
        try {
            SharedPreferences prefs = prefs();
            return prefs == null ? fallback : prefs.getBoolean(key, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static SharedPreferences prefs() {
        Context context = Margy.context();
        if (context == null) return null;
        return context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }
}

package mi.tiktokmi;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.ss.android.ugc.aweme.im.common.model.StickerBase;
import com.ss.android.ugc.aweme.im.common.model.StickerImage;
import com.ss.android.ugc.aweme.im.common.model.StickerItem;
import com.ss.android.ugc.aweme.im.streak.api.IStreakService;
import com.ss.android.ugc.aweme.im.streak.api.StreakData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeping a streak alive by sending the sticker you picked.
 *
 * This is the one thing in the mod that acts on its own rather than answering
 * a question, and it is the only one that sends anything. It is off unless
 * switched on, it needs a sticker to have been chosen, and it will send at
 * most one sticker per conversation per day.
 *
 * When it sends is TikTok's own judgement rather than a guess about clocks:
 * the app puts a status on every streak and the mod reads it. A grey flame is
 * a streak still waiting for today, and that is the whole condition.
 *
 * Two halves, and both are found the same way -- by real names, at runtime.
 *
 * Which streaks are fading: `StreakData` is TikTok's own class and its fields
 * say everything -- `convId`, `activeBefore`, `endAt`. Reading them is not the
 * problem; getting hold of one is. Nothing in the app ever reads those fields,
 * so there is nothing there to listen to.
 *
 * What the app does do, all the time, is ask its own `IStreakService` about a
 * conversation -- and that name and its signatures are real. So the mod
 * listens to the questions rather than the answers: every conversation the app
 * asks about is remembered, along with the service that was asked, and from
 * then on the mod can ask about it itself.
 *
 * How to send: `IMStickerApi` and `getImStickerMessageService()` are real
 * names, and the service has exactly one method taking thirteen arguments.
 * Everything that method needs is read out of its own signature -- the source
 * it wants is an enum with a real constant on it, and the options object is a
 * class with a one-argument constructor. So there is not one obfuscated name
 * written down here, and a release that renames them all changes nothing.
 */
public final class Streaks {

    private Streaks() {}

    public static final String KEY_ON = "streak_auto";
    public static final String KEY_STICKER = "streak_sticker";
    public static final String KEY_MODE = "streak_mode";
    public static final String KEY_TEXT = "streak_text";
    public static final String KEY_CONVERSATIONS = "streak_saved_conversations";

    /** What to send: a sticker, or a message. */
    public static final String BY_STICKER = "sticker";
    public static final String BY_TEXT = "text";

    public static final String DEFAULT_TEXT = "🔥 Авто-стрік!";

    /**
     * The status TikTok gives a streak whose flame has gone grey.
     *
     * Its own enum, with its own names: ACTIVE is a streak already kept today,
     * SECONDARY_ACTIVE is one still waiting, EXPIRED is one that is gone. The
     * grey flame is the middle one, and that is the only one worth sending to.
     */
    private static final String GREY = "SECONDARY_ACTIVE";

    private static final long EVERY = 15 * 60 * 1000L;
    private static final long DAY = 24 * 60 * 60 * 1000L;

    /** The constant on TikTok's own enum that says who sent this and why. */
    private static final String SOURCE = "AUTO_CONSECUTIVE_SA_STICKERS";

    /** Streaks the app has looked at, newest last. */
    private static final Map<String, StreakData> known =
            new LinkedHashMap<String, StreakData>();

    /** Stickers the app has drawn, so there is something to choose from. */
    private static final Map<String, StickerItem> seenStickers =
            new LinkedHashMap<String, StickerItem>();

    private static volatile boolean started;

    // -------------------------------------------------------- the switches

    public static boolean isEnabled() {
        return flag(KEY_ON, false);
    }

    public static void setEnabled(boolean on) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_ON, on).apply();
    }

    public static String mode() {
        SharedPreferences prefs = prefs();
        return prefs == null ? BY_STICKER : prefs.getString(KEY_MODE, BY_STICKER);
    }

    public static void setMode(String mode) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(KEY_MODE, mode).apply();
    }

    public static String text() {
        SharedPreferences prefs = prefs();
        return prefs == null ? DEFAULT_TEXT : prefs.getString(KEY_TEXT, DEFAULT_TEXT);
    }

    public static void setText(String value) {
        SharedPreferences prefs = prefs();
        if (prefs != null) {
            prefs.edit().putString(KEY_TEXT,
                    value == null || value.trim().length() == 0 ? DEFAULT_TEXT : value.trim())
                    .apply();
        }
    }

    public static String chosen() {
        SharedPreferences prefs = prefs();
        String id = prefs == null ? "" : prefs.getString(KEY_STICKER, "");
        if (id.isEmpty()) {
            List<String> offered = offered();
            if (!offered.isEmpty()) {
                id = offered.get(0);
                choose(id);
            }
        }
        return id;
    }

    public static void choose(String id) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(KEY_STICKER, id).apply();
    }

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

    // ------------------------------------------------ where the reads land

    /** The service itself, kept from whichever call went past most recently. */
    private static volatile IStreakService service;

    /** Conversations the app has asked about, which is the list to work from. */
    private static final Map<String, Long> asked = new LinkedHashMap<String, Long>();

    /** The app looking a streak up: both halves of the answer are worth having. */
    public static StreakData streakOf(IStreakService from, String conversation, boolean fresh) {
        StreakData data = getStreakData(from, conversation, fresh);
        note(from, conversation);
        if (data != null) {
            synchronized (known) {
                known.put(conversation, data);
            }
        }
        return data;
    }

    /** The app asking whether a conversation has a streak. */
    public static boolean hasStreak(IStreakService from, String conversation) {
        note(from, conversation);
        if (from == null) return false;
        try {
            return from.LJJIJIIJI(conversation);
        } catch (NoSuchMethodError e1) {
            try {
                return from.a0(conversation);
            } catch (Throwable ignored) {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** The app asking whether to show one. */
    public static boolean showsStreak(IStreakService from, String conversation, boolean flag) {
        note(from, conversation);
        if (from == null) return false;
        try {
            return from.LJJJJI(conversation, flag);
        } catch (NoSuchMethodError e1) {
            try {
                return from.h0(conversation, flag);
            } catch (Throwable ignored) {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    // The rest are the same thing: questions the app asks about one
    // conversation. Nothing is done with the answers -- they are here so that
    // the conversation is known about at all.

    public static int streakCount(IStreakService from, String conversation) {
        note(from, conversation);
        if (from == null) return 0;
        try {
            return from.LJII(conversation);
        } catch (NoSuchMethodError e1) {
            try {
                return from.w(conversation);
            } catch (Throwable ignored) {
                return 0;
            }
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static boolean asksAbout(IStreakService from, String conversation) {
        note(from, conversation);
        if (from == null) return false;
        try {
            return from.X(conversation);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean asksAboutToo(IStreakService from, String conversation) {
        note(from, conversation);
        if (from == null) return false;
        try {
            return from.Y(conversation);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Integer streakState(IStreakService from, String conversation) {
        note(from, conversation);
        if (from == null) return null;
        try {
            return from.LJJJJJL(conversation);
        } catch (NoSuchMethodError e1) {
            try {
                return from.l0(conversation);
            } catch (Throwable ignored) {
                return null;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String streakText(IStreakService from, String conversation) {
        note(from, conversation);
        if (from == null) return null;
        try {
            return from.LJIJJ(conversation);
        } catch (NoSuchMethodError e1) {
            try {
                return from.O(conversation);
            } catch (Throwable ignored) {
                return null;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static StreakData getStreakData(IStreakService from, String conversation, boolean fresh) {
        if (from == null) return null;
        try {
            return from.LJIIZILJ(conversation, fresh);
        } catch (NoSuchMethodError e1) {
            try {
                return from.J(conversation, fresh);
            } catch (NoSuchMethodError e2) {
                return reflectStreakData(from, conversation, fresh);
            } catch (Throwable ignored) {
                return null;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static volatile Method reflectiveStreakDataMethod;

    private static StreakData reflectStreakData(IStreakService from, String conversation, boolean fresh) {
        try {
            Method m = reflectiveStreakDataMethod;
            if (m == null) {
                for (Method candidate : from.getClass().getMethods()) {
                    Class<?>[] p = candidate.getParameterTypes();
                    if (p.length == 2 && p[0] == String.class
                            && (p[1] == boolean.class || p[1] == Boolean.class)
                            && StreakData.class.isAssignableFrom(candidate.getReturnType())) {
                        candidate.setAccessible(true);
                        m = candidate;
                        reflectiveStreakDataMethod = m;
                        break;
                    }
                }
            }
            if (m != null) {
                return (StreakData) m.invoke(from, conversation, Boolean.valueOf(fresh));
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static IStreakService getService() {
        if (service != null) return service;
        try {
            Class<?> smClass = Class.forName("com.ss.android.ugc.aweme.framework.services.ServiceManager");
            Method getMethod = smClass.getMethod("get");
            Object sm = getMethod.invoke(null);
            if (sm != null) {
                Method getServiceMethod = sm.getClass().getMethod("getService", Class.class);
                Object svc = getServiceMethod.invoke(sm, IStreakService.class);
                if (svc instanceof IStreakService) {
                    service = (IStreakService) svc;
                }
            }
        } catch (Throwable ignored) {
        }
        return service;
    }

    private static void note(IStreakService from, String conversation) {
        try {
            if (from != null) service = from;
            if (conversation == null || conversation.length() == 0) return;
            synchronized (asked) {
                if (asked.size() > 200) {
                    asked.remove(asked.keySet().iterator().next());
                }
                asked.put(conversation, Long.valueOf(System.currentTimeMillis()));
            }
            saveConversation(conversation);
        } catch (Throwable ignored) {
        }
    }

    private static void saveConversation(String conversation) {
        if (conversation == null || conversation.isEmpty()) return;
        try {
            SharedPreferences prefs = prefs();
            if (prefs == null) return;
            java.util.Set<String> set = prefs.getStringSet(KEY_CONVERSATIONS, null);
            java.util.Set<String> updated = new java.util.HashSet<String>();
            if (set != null) updated.addAll(set);
            if (updated.add(conversation)) {
                prefs.edit().putStringSet(KEY_CONVERSATIONS, updated).apply();
            }
        } catch (Throwable ignored) {}
    }

    /** Every sticker the app touches, so the settings have something to offer. */
    public static StickerBase stickerBase(StickerItem sticker) {
        StickerBase base = sticker == null ? null : sticker.stickerBase;
        try {
            String id = idOf(base);
            if (id != null) {
                synchronized (seenStickers) {
                    if (seenStickers.size() > 60) {
                        seenStickers.remove(seenStickers.keySet().iterator().next());
                    }
                    seenStickers.put(id, sticker);
                }
            }
        } catch (Throwable ignored) {
        }
        return base;
    }

    /** What to call a sticker: its own id, or failing that its picture's. */
    private static String idOf(StickerBase base) {
        if (base == null) return null;
        if (base.id != null) return String.valueOf(base.id);
        StickerImage image = base.image != null ? base.image : base.thumbnail;
        return image == null || image.uri == null ? null : image.uri;
    }

    /** What the settings screen offers: whatever has been seen, newest first. */
    public static List<String> offered() {
        synchronized (seenStickers) {
            List<String> out = new ArrayList<String>(seenStickers.keySet());
            java.util.Collections.reverse(out);
            return out;
        }
    }

    public static StickerItem sticker(String id) {
        synchronized (seenStickers) {
            return seenStickers.get(id);
        }
    }

    /**
     * A small picture of a sticker, for the settings to show.
     *
     * Fetched once and kept in memory: this is a grid of a couple of dozen
     * thumbnails on one screen, not something worth a cache on disk.
     */
    public static android.graphics.Bitmap thumbnail(Context context, String id) {
        synchronized (thumbnails) {
            if (thumbnails.containsKey(id)) return thumbnails.get(id);
            thumbnails.put(id, null);  // asked for; do not ask twice
        }
        final String url = urlOf(id);
        if (url == null) return null;
        final String key = id;
        Net.away("sticker thumbnail", new Runnable() {
            @Override
            public void run() {
                byte[] raw = Net.bytes(url);
                if (raw == null) return;
                try {
                    android.graphics.Bitmap bitmap =
                            android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
                    if (bitmap == null) return;
                    synchronized (thumbnails) {
                        thumbnails.put(key, bitmap);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
        return null;
    }

    private static final Map<String, android.graphics.Bitmap> thumbnails =
            new LinkedHashMap<String, android.graphics.Bitmap>();

    private static String urlOf(String id) {
        try {
            StickerItem sticker = sticker(id);
            StickerBase base = sticker == null ? null : sticker.stickerBase;
            if (base == null) return null;
            // the thumbnail first: this is a grid of them, and the full-size
            // picture is a video on some stickers and a still on others
            StickerImage image = base.thumbnail != null ? base.thumbnail : base.image;
            if (image == null || image.urlList == null || image.urlList.isEmpty()) return null;
            return String.valueOf(image.urlList.get(0));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static volatile long sLastRoundAt = 0;

    public static synchronized void start(Context context) {
        if (started) return;
        started = true;
        final Handler handler = new Handler(Looper.getMainLooper());
        final Context application = context.getApplicationContext();

        // 1. Initial quick scan 8 seconds after start once TikTok services have initialized
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                round(application);
            }
        }, 8000L);

        // 2. Periodic background scan every 10 minutes
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                round(application);
                handler.postDelayed(this, 10 * 60 * 1000L);
            }
        }, 10 * 60 * 1000L);
    }

    /**
     * Called on activity resume to check streaks automatically if enough time has elapsed.
     */
    public static void onResumed(android.app.Activity activity) {
        if (activity == null || !isEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - sLastRoundAt > 2 * 60 * 1000L) {
            final Context app = activity.getApplicationContext();
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    round(app);
                }
            }, 1500L);
        }
    }

    /** One pass over what is known, automatically scanning and sending where grey. */
    public static void round(final Context context) {
        if (!isEnabled() || context == null) return;
        sLastRoundAt = System.currentTimeMillis();
        final boolean byText = BY_TEXT.equals(mode());
        StickerItem sticker = byText ? null : sticker(chosen());
        if (!byText && sticker == null && !seenStickers.isEmpty()) {
            sticker = seenStickers.values().iterator().next();
        }
        if (!byText && sticker == null) {
            sticker = makeFallbackSticker();
        }

        List<String> all = conversations();
        if (all.isEmpty()) {
            Diary.note("streaks: no conversation has been looked at yet");
            return;
        }

        final List<String> due = new ArrayList<String>();
        int grey = 0, lit = 0, gone = 0, unknown = 0, already = 0;
        long now = System.currentTimeMillis();
        for (String conversation : all) {
            StreakData data = ask(conversation);
            if (data == null) {
                unknown++;
                continue;
            }
            String state = statusOf(data);
            if (GREY.equals(state)) {
                grey++;
                if (sentToday(conversation, now)) already++;
                else due.add(conversation);
            } else if ("ACTIVE".equals(state)) {
                lit++;
            } else if (state == null) {
                unknown++;
            } else {
                gone++;
            }
        }

        Diary.note("streaks: " + all.size() + " looked at -- " + grey + " grey, "
                + lit + " lit, " + gone + " over, " + unknown + " unreadable; "
                + already + " already sent today, " + due.size() + " to send");
        if (due.isEmpty()) return;

        final StickerItem toSend = sticker;
        Net.away("streaks", new Runnable() {
            @Override
            public void run() {
                int kept = 0;
                for (String conversation : due) {
                    if (deliver(context, toSend, conversation)) {
                        remember(conversation);
                        kept++;
                        Diary.note("streak kept: " + conversation);
                    } else {
                        Diary.note("streak NOT kept: " + conversation);
                    }
                }
                if (kept > 0) {
                    final int finalKept = kept;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "🔥 Авто-стрік: продовжено " + finalKept + " серій!", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    /**
     * What TikTok makes of a streak: its own status enum (SECONDARY_ACTIVE = grey flame).
     */
    private static String statusOf(StreakData data) {
        IStreakService from = getService();
        if (from == null || data == null) return null;
        try {
            Method reader = status;
            if (reader == null) {
                for (Method method : from.getClass().getMethods()) {
                    Class<?>[] takes = method.getParameterTypes();
                    if (takes.length != 1) continue;
                    if (!takes[0].isInstance(data)) continue;
                    if (!method.getReturnType().isEnum()) continue;
                    method.setAccessible(true);
                    reader = method;
                    status = method;
                    break;
                }
            }
            if (reader != null) {
                Object value = reader.invoke(from, data);
                if (value instanceof Enum) {
                    return ((Enum<?>) value).name();
                }
            }
        } catch (Throwable error) {
            Diary.note("streaks: status reader error: " + error);
        }

        // Fallback: inspect StreakData directly
        try {
            for (Field f : data.getClass().getDeclaredFields()) {
                if (f.getType().isEnum()) {
                    f.setAccessible(true);
                    Object val = f.get(data);
                    if (val instanceof Enum) return ((Enum<?>) val).name();
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static volatile Method status;

    /**
     * Test sending to every known conversation.
     */
    public static void test(final Context context) {
        final boolean byText = BY_TEXT.equals(mode());
        StickerItem sticker = byText ? null : sticker(chosen());
        if (!byText && sticker == null && !seenStickers.isEmpty()) {
            sticker = seenStickers.values().iterator().next();
        }
        if (!byText && sticker == null) {
            sticker = makeFallbackSticker();
        }
        final List<String> all = conversations();
        Diary.note("streak test: " + all.size() + " conversation(s) known, sending "
                + (byText ? "text [" + text() + "]"
                          : "a sticker " + (sticker == null ? "NOT chosen/seen" : "chosen"))
                + ", service " + (getService() == null ? "not seen yet" : "seen"));
        describe();
        if (all.isEmpty()) {
            Diary.note("streak test: no conversations known (open messages in TikTok first)");
            Screen.say(Text.STREAK_TEST_NONE);
            return;
        }

        final StickerItem toSend = sticker;
        Net.away("streak test", new Runnable() {
            @Override
            public void run() {
                int sent = 0, failed = 0;
                for (String conversation : all) {
                    String state = statusOf(ask(conversation));
                    boolean ok = deliver(context, toSend, conversation);
                    if (ok) sent++;
                    else failed++;
                    Diary.note("streak test: " + conversation + " (" + state + ") -> "
                            + (ok ? "sent" : "not sent"));
                }
                Screen.say(Text.STREAK_TEST_DONE_A + sent
                        + Text.STREAK_TEST_DONE_B + failed
                        + Text.STREAK_TEST_DONE_C);
            }
        });
    }

    /** Whichever way was chosen, with automatic fallback so streaks never fail. */
    private static boolean deliver(Context context, StickerItem sticker,
                                   String conversation) {
        if (BY_TEXT.equals(mode())) {
            boolean ok = sendText(context, conversation, text());
            if (!ok && sticker != null) {
                ok = send(context, sticker, conversation);
            }
            return ok;
        } else {
            boolean ok = send(context, sticker, conversation);
            if (!ok) {
                ok = sendText(context, conversation, text());
            }
            return ok;
        }
    }

    /**
     * Send streak text/nudge into conversation.
     */
    private static boolean sendText(Context context, String conversation, String words) {
        if (context == null || conversation == null) return false;
        String msg = (words != null && !words.trim().isEmpty()) ? words.trim() : DEFAULT_TEXT;

        // Try IIMNudgeAndStreakService first
        try {
            Class<?> smClass = Class.forName("com.ss.android.ugc.aweme.framework.services.ServiceManager");
            Method getMethod = smClass.getMethod("get");
            Object sm = getMethod.invoke(null);
            if (sm != null) {
                Class<?> nudgeClass = Class.forName("com.ss.android.ugc.aweme.im.service.service.IIMNudgeAndStreakService");
                Method getServiceMethod = sm.getClass().getMethod("getService", Class.class);
                Object nudgeService = getServiceMethod.invoke(sm, nudgeClass);
                if (nudgeService != null) {
                    for (Method m : nudgeService.getClass().getMethods()) {
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length == 3 && Context.class.isAssignableFrom(params[0])
                                && params[1] == String.class && params[2] == String.class) {
                            m.setAccessible(true);
                            m.invoke(nudgeService, context.getApplicationContext(), conversation, msg);
                            Diary.note("streak text sent via IIMNudgeAndStreakService: " + conversation);
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable error) {
            Diary.note("streak sendText via nudge: " + error);
        }

        // Try IStreakService methods taking (String, String, ...)
        IStreakService svc = getService();
        if (svc != null) {
            try {
                for (Method m : svc.getClass().getMethods()) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length >= 2 && params[0] == String.class && params[1] == String.class) {
                        m.setAccessible(true);
                        Object[] args = new Object[params.length];
                        args[0] = conversation;
                        args[1] = msg;
                        for (int i = 2; i < params.length; i++) {
                            args[i] = maybe(params[i]);
                        }
                        m.invoke(svc, args);
                        Diary.note("streak text sent via IStreakService." + m.getName() + " for " + conversation);
                        return true;
                    }
                }
            } catch (Throwable error) {
                Diary.note("streak sendText via IStreakService: " + error);
            }
        }

        return false;
    }

    /** Every conversation worth asking about, newest first. */
    private static List<String> conversations() {
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<String>();

        // 1. Query IStreakService if available
        IStreakService svc = getService();
        if (svc != null) {
            try {
                for (Method m : svc.getClass().getMethods()) {
                    if (m.getParameterTypes().length == 0 && Map.class.isAssignableFrom(m.getReturnType())) {
                        Map<?, ?> map = (Map<?, ?>) m.invoke(svc);
                        if (map != null) {
                            for (Object k : map.keySet()) {
                                if (k instanceof String) {
                                    all.add((String) k);
                                    saveConversation((String) k);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2. Query persistent storage
        try {
            SharedPreferences prefs = prefs();
            if (prefs != null) {
                java.util.Set<String> saved = prefs.getStringSet(KEY_CONVERSATIONS, null);
                if (saved != null) all.addAll(saved);
            }
        } catch (Throwable ignored) {}

        // 3. Query memory
        synchronized (asked) {
            all.addAll(asked.keySet());
        }

        List<String> out = new ArrayList<String>(all);
        java.util.Collections.reverse(out);
        return out;
    }

    /**
     * Ask the service about one conversation.
     */
    private static StreakData ask(String conversation) {
        IStreakService from = getService();
        if (from != null) {
            try {
                StreakData fresh = getStreakData(from, conversation, false);
                if (fresh != null) {
                    synchronized (known) {
                        known.put(conversation, fresh);
                    }
                    return fresh;
                }
            } catch (Throwable ignored) {
            }
        }
        synchronized (known) {
            return known.get(conversation);
        }
    }

    private static boolean sentToday(String conversation, long now) {
        SharedPreferences prefs = prefs();
        if (prefs == null || conversation == null) return true;
        return now - prefs.getLong("streak_at_" + conversation, 0) < DAY;
    }

    private static void remember(String conversation) {
        SharedPreferences prefs = prefs();
        if (prefs != null) {
            prefs.edit().putLong("streak_at_" + conversation,
                    System.currentTimeMillis()).apply();
        }
    }

    // ------------------------------------------------------- the sending

    /**
     * Send one sticker into one conversation.
     */
    public static boolean send(Context context, StickerItem sticker, String conversation) {
        try {
            Object service = messageService();
            if (service == null) {
                Diary.note("streak send: no sticker service on this release");
                return false;
            }

            if (sticker == null) {
                sticker = makeFallbackSticker();
            }

            Method sender = null;
            for (Method method : service.getClass().getMethods()) {
                if (method.getReturnType() == void.class) {
                    Class<?>[] params = method.getParameterTypes();
                    boolean hasContext = false;
                    boolean hasSticker = false;
                    boolean hasString = false;
                    for (Class<?> p : params) {
                        if (Context.class.isAssignableFrom(p)) hasContext = true;
                        if (p == StickerItem.class || "com.ss.android.ugc.aweme.im.common.model.StickerItem".equals(p.getName())) hasSticker = true;
                        if (p == String.class) hasString = true;
                    }
                    if (hasContext && hasSticker && hasString) {
                        sender = method;
                        break;
                    }
                }
            }
            if (sender == null) {
                for (Method method : service.getClass().getMethods()) {
                    if (method.getParameterTypes().length == 13
                            && method.getReturnType() == void.class) {
                        sender = method;
                        break;
                    }
                }
            }
            if (sender == null) {
                Diary.note("streak send: no sticker sender method found on "
                        + service.getClass().getName());
                return false;
            }

            Class<?>[] types = sender.getParameterTypes();
            Object[] args = new Object[types.length];
            int stickerAt = -1;
            for (int i = 0; i < types.length; i++) {
                if (types[i] == StickerItem.class || (sticker != null && types[i].isInstance(sticker))) {
                    stickerAt = i;
                }
            }

            for (int i = 0; i < types.length; i++) {
                if (types[i].isPrimitive()) {
                    args[i] = blank(types[i]);
                } else if (Context.class.isAssignableFrom(types[i])) {
                    args[i] = context.getApplicationContext();
                } else if (i == stickerAt) {
                    args[i] = sticker;
                } else if (types[i] == String.class) {
                    args[i] = conversation;
                } else {
                    Object source = findSource(types[i]);
                    if (source != null) {
                        args[i] = source;
                    } else if (i == stickerAt + 1) {
                        args[i] = maybe(types[i]);
                    } else {
                        args[i] = maybe(types[i]);
                    }
                }
            }

            sender.setAccessible(true);
            sender.invoke(service, args);
            Diary.note("streak send: " + sender.getName() + " called for " + conversation);
            return true;
        } catch (Throwable error) {
            Throwable why = error instanceof java.lang.reflect.InvocationTargetException
                    && error.getCause() != null ? error.getCause() : error;
            Diary.note("streak send failed: " + why);
            StackTraceElement[] where = why.getStackTrace();
            if (where != null && where.length > 0) Diary.note("   at " + where[0]);
            return false;
        }
    }

    private static StickerItem makeFallbackSticker() {
        if (!seenStickers.isEmpty()) {
            return seenStickers.values().iterator().next();
        }
        try {
            StickerItem item = new StickerItem();
            item.stickerBase = new StickerBase();
            item.stickerBase.id = Long.valueOf(0);
            return item;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Write down what the sticker service can do. Temporary.
     *
     * The sender is picked by shape -- thirteen arguments, returns nothing --
     * and if that is the wrong method, or the right one wants something the
     * mod is not giving it, the only way to tell is to look at what is there.
     */
    public static void describe() {
        Object service = messageService();
        if (service == null) {
            Diary.note("streak service: not found");
            return;
        }
        Diary.note("streak service: " + service.getClass().getName());
        int shown = 0;
        for (Method method : service.getClass().getMethods()) {
            Class<?> owner = method.getDeclaringClass();
            if (owner == Object.class) continue;
            StringBuilder line = new StringBuilder(method.getName()).append('(');
            Class<?>[] takes = method.getParameterTypes();
            for (int i = 0; i < takes.length; i++) {
                if (i > 0) line.append(", ");
                line.append(takes[i].getSimpleName());
            }
            line.append(") -> ").append(method.getReturnType().getSimpleName());
            Diary.note("   " + line);
            if (++shown > 40) break;
        }
    }

    /** IMStickerApi's own singleton, then the service it names. */
    private static Object messageService() {
        try {
            Class<?> api = Class.forName("com.ss.android.ugc.aweme.im.sticker.api.IMStickerApi");
            Object instance = null;
            for (Field field : api.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object holder = field.get(null);
                if (holder == null) continue;
                for (Method method : holder.getClass().getMethods()) {
                    if (method.getParameterTypes().length == 0
                            && api.isAssignableFrom(method.getReturnType())) {
                        method.setAccessible(true);
                        instance = method.invoke(holder);
                        break;
                    }
                }
                if (instance != null) break;
            }
            if (instance == null) return null;

            Method service = instance.getClass().getMethod("getImStickerMessageService");
            service.setAccessible(true);
            return service.invoke(instance);
        } catch (Throwable error) {
            Diary.note("streak service: " + error);
            return null;
        }
    }

    private static Object findSource(Class<?> type) {
        if (type == null) return null;
        if (type.isEnum()) return constant(type, SOURCE);
        try {
            Class<?> cls = Class.forName("X.173Q", false, type.getClassLoader());
            if (type.isAssignableFrom(cls)) {
                return constant(cls, SOURCE);
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Class<?> inner : type.getDeclaredClasses()) {
                if (inner.isEnum() && type.isAssignableFrom(inner)) {
                    Object c = constant(inner, SOURCE);
                    if (c != null) return c;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** A named constant on an enum, or its first value if that name has gone. */
    private static Object constant(Class<?> type, String name) {
        try {
            return Enum.valueOf((Class<Enum>) type.asSubclass(Enum.class), name);
        } catch (Throwable ignored) {
            Object[] all = type.getEnumConstants();
            return all == null || all.length == 0 ? null : all[0];
        }
    }

    /** Something harmless of the right shape, for the arguments not being set. */
    private static Object blank(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return Boolean.FALSE;
        if (type == int.class) return Integer.valueOf(0);
        if (type == long.class) return Long.valueOf(0);
        return Integer.valueOf(0);
    }

    /** An options object, when the argument is a class that takes one thing. */
    private static Object maybe(Class<?> type) {
        try {
            for (Constructor<?> made : type.getDeclaredConstructors()) {
                if (made.getParameterTypes().length != 1) continue;
                if (made.getParameterTypes()[0].isPrimitive()) continue;
                made.setAccessible(true);
                return made.newInstance(new Object[]{null});
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}

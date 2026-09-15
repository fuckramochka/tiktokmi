package mi.tiktokmi;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

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

    /** What to send: a sticker, or a message. */
    public static final String BY_STICKER = "sticker";
    public static final String BY_TEXT = "text";

    public static final String DEFAULT_TEXT = "Это авто серия!";

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
        return prefs == null ? "" : prefs.getString(KEY_STICKER, "");
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
        StreakData data = from == null ? null : from.J(conversation, fresh);
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
        return from != null && from.a0(conversation);
    }

    /** The app asking whether to show one. */
    public static boolean showsStreak(IStreakService from, String conversation, boolean flag) {
        note(from, conversation);
        return from != null && from.h0(conversation, flag);
    }

    // The rest are the same thing: questions the app asks about one
    // conversation. Nothing is done with the answers -- they are here so that
    // the conversation is known about at all.

    public static int streakCount(IStreakService from, String conversation) {
        note(from, conversation);
        return from == null ? 0 : from.w(conversation);
    }

    public static boolean asksAbout(IStreakService from, String conversation) {
        note(from, conversation);
        return from != null && from.X(conversation);
    }

    public static boolean asksAboutToo(IStreakService from, String conversation) {
        note(from, conversation);
        return from != null && from.Y(conversation);
    }

    public static Integer streakState(IStreakService from, String conversation) {
        note(from, conversation);
        return from == null ? null : from.l0(conversation);
    }

    public static String streakText(IStreakService from, String conversation) {
        note(from, conversation);
        return from == null ? null : from.O(conversation);
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
        } catch (Throwable ignored) {
        }
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

    // ------------------------------------------------------------ the round

    public static synchronized void start(Context context) {
        if (started) return;
        started = true;
        final Handler handler = new Handler(Looper.getMainLooper());
        final Context application = context.getApplicationContext();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                round(application);
                handler.postDelayed(this, EVERY);
            }
        }, EVERY);
    }

    /** One pass over what is known, sending where it is needed. */
    public static void round(final Context context) {
        if (!isEnabled()) return;
        final boolean byText = BY_TEXT.equals(mode());
        final StickerItem sticker = byText ? null : sticker(chosen());
        if (!byText && sticker == null) {
            Diary.note("streaks: nothing to send -- no sticker chosen yet");
            return;
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

        Net.away("streaks", new Runnable() {
            @Override
            public void run() {
                for (String conversation : due) {
                    if (deliver(context, sticker, conversation)) {
                        remember(conversation);
                        Diary.note("streak kept: " + conversation);
                    } else {
                        Diary.note("streak NOT kept: " + conversation);
                    }
                }
            }
        });
    }

    /**
     * What TikTok makes of a streak: its own word for it.
     *
     * The service has a method that turns a streak into a status, and the
     * status is an enum whose constants TikTok named itself. Neither the
     * method nor the enum keeps its name between releases, but the shape does:
     * it is the one method taking a streak and answering with an enum. So it
     * is found by that, and the answer is read as the name it carries.
     */
    private static String statusOf(StreakData data) {
        IStreakService from = service;
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
            if (reader == null) {
                Diary.note("streaks: no way to read a status on this release");
                return null;
            }
            Object value = reader.invoke(from, data);
            return value == null ? null : ((Enum<?>) value).name();
        } catch (Throwable error) {
            Diary.note("streaks: status -- " + error);
            return null;
        }
    }

    private static volatile Method status;

    /**
     * Send to every conversation the mod knows about, whatever its state.
     *
     * A button rather than a schedule: the point is to find out whether
     * sending works at all, so it skips every condition -- not grey, already
     * sent today, none of it -- and writes down what happened to each one.
     */
    public static void test(final Context context) {
        final boolean byText = BY_TEXT.equals(mode());
        final StickerItem sticker = byText ? null : sticker(chosen());
        final List<String> all = conversations();
        Diary.note("streak test: " + all.size() + " conversation(s) known, sending "
                + (byText ? "text [" + text() + "]"
                          : "a sticker " + (sticker == null ? "NOT chosen" : "chosen"))
                + ", service " + (service == null ? "not seen yet" : "seen"));
        describe();
        if (all.isEmpty()) return;
        if (!byText && sticker == null) return;

        Net.away("streak test", new Runnable() {
            @Override
            public void run() {
                for (String conversation : all) {
                    String state = statusOf(ask(conversation));
                    boolean sent = deliver(context, sticker, conversation);
                    Diary.note("streak test: " + conversation + " (" + state + ") -> "
                            + (sent ? "sent" : "not sent"));
                }
            }
        });
    }

    /** Whichever way was chosen. */
    private static boolean deliver(Context context, StickerItem sticker,
                                   String conversation) {
        if (BY_TEXT.equals(mode())) return sendText(context, conversation, text());
        return send(context, sticker, conversation);
    }

    /**
     * Sending words rather than a sticker. Not done, and not guessed at.
     *
     * This used to look for a method by its shape -- something taking the
     * conversation and the words -- and call whatever matched. That is a
     * dangerous way to find anything: the methods on a messenger service are
     * not all senders, plenty of them take two strings, and one of the ones it
     * reached reported an account. A blind call can do anything the app can
     * do, and "it had the right shape" is not a reason to let one run.
     *
     * So nothing is called until the right method is known the way the sticker
     * sender is known: TikTok's own call to that one is in the apk and could be
     * read. Nothing in the apk sends plain text under any name, and the service
     * that would arrives in a module downloaded at runtime -- so this waits
     * rather than experiments on somebody's account.
     */
    private static boolean sendText(Context context, String conversation, String words) {
        Diary.note("streak text: not available in this build");
        return false;
    }

    /** Every conversation worth asking about, newest first. */
    private static List<String> conversations() {
        synchronized (asked) {
            List<String> out = new ArrayList<String>(asked.keySet());
            java.util.Collections.reverse(out);
            return out;
        }
    }

    /**
     * Ask the service about one conversation.
     *
     * The cached answer is the fallback: the service is only there once the
     * app has used it at least once, and until then the mod knows only what
     * went past.
     */
    private static StreakData ask(String conversation) {
        IStreakService from = service;
        if (from != null) {
            try {
                StreakData fresh = from.J(conversation, false);
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
     *
     * Every part of the call is worked out from the method it is calling: the
     * service comes from a real name, the method is the only one on it taking
     * thirteen arguments, and each argument's type is read from that method.
     * The enum it wants is recognised by a constant TikTok named itself, and
     * the options object by having a constructor that takes one thing.
     */
    public static boolean send(Context context, StickerItem sticker, String conversation) {
        try {
            Object service = messageService();
            if (service == null) {
                Diary.note("streak send: no sticker service on this release");
                return false;
            }

            Method sender = null;
            for (Method method : service.getClass().getMethods()) {
                if (method.getParameterTypes().length == 13
                        && method.getReturnType() == void.class) {
                    sender = method;
                    break;
                }
            }
            if (sender == null) {
                Diary.note("streak send: no method taking thirteen arguments on "
                        + service.getClass().getName());
                return false;
            }

            // TikTok's own call, which is in the apk even though the service
            // that answers it is not:
            //
            //   LIZJ(context.getApplicationContext(), null, null,
            //        AUTO_CONSECUTIVE_SA_STICKERS, sticker, new X(null),
            //        null, null, conversationId, null, null, null, false)
            //
            // Null for everything not named. The first version of this built
            // an object for every argument it had not been told about, which
            // meant handing made-up protocol messages to a sender that wanted
            // none -- and that is what the exception was.
            Class<?>[] types = sender.getParameterTypes();
            Object[] args = new Object[types.length];
            int stickerAt = -1;
            for (int i = 0; i < types.length; i++) {
                if (types[i].isInstance(sticker)) stickerAt = i;
            }

            for (int i = 0; i < types.length; i++) {
                if (types[i].isPrimitive()) {
                    args[i] = blank(types[i]);
                } else if (types[i] == Context.class) {
                    args[i] = context.getApplicationContext();
                } else if (i == stickerAt) {
                    args[i] = sticker;
                } else if (types[i] == String.class) {
                    args[i] = conversation;
                } else if (types[i].isEnum()) {
                    args[i] = constant(types[i], SOURCE);
                } else if (i == stickerAt + 1) {
                    // the one argument TikTok does build, and it builds it
                    // with nothing in it
                    args[i] = maybe(types[i]);
                } else {
                    args[i] = null;
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

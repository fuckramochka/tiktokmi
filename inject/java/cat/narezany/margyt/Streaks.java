package cat.narezany.margyt;

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

    /** How close to the end is close enough to act. */
    private static final long SOON = 6 * 60 * 60 * 1000L;
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
        final StickerItem sticker = sticker(chosen());
        if (sticker == null) return;

        final List<StreakData> fading = new ArrayList<StreakData>();
        long now = System.currentTimeMillis();
        for (String conversation : conversations()) {
            StreakData data = ask(conversation);
            if (data == null) continue;
            long ends = data.activeBefore > 0 ? data.activeBefore : data.endAt;
            if (ends <= 0) continue;
            if (ends < 1_000_000_000_000L) ends *= 1000;  // seconds, not millis
            if (ends - now > SOON || ends < now) continue;
            if (sentToday(conversation, now)) continue;
            fading.add(data);
            ending.put(data, conversation);
        }
        if (fading.isEmpty()) return;

        Net.away("streaks", new Runnable() {
            @Override
            public void run() {
                for (StreakData data : fading) {
                    String conversation = ending.get(data);
                    if (conversation == null) conversation = data.convId;
                    if (send(context, sticker, conversation)) {
                        remember(conversation);
                        Diary.note("streak kept: " + conversation);
                    }
                }
            }
        });
    }

    /** Which conversation this streak belongs to, remembered as it was found. */
    private static final Map<StreakData, String> ending =
            new java.util.WeakHashMap<StreakData, String>();

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
            if (service == null) return false;

            Method sender = null;
            for (Method method : service.getClass().getMethods()) {
                if (method.getParameterTypes().length == 13
                        && method.getReturnType() == void.class) {
                    sender = method;
                    break;
                }
            }
            if (sender == null) {
                Diary.note("streak: the sender has moved");
                return false;
            }

            Class<?>[] types = sender.getParameterTypes();
            Object[] args = new Object[13];
            for (int i = 0; i < 13; i++) args[i] = blank(types[i]);
            for (int i = 0; i < 13; i++) {
                if (types[i] == Context.class) args[i] = context;
                else if (types[i].isInstance(sticker)) args[i] = sticker;
                else if (types[i] == String.class) args[i] = conversation;
                else if (types[i].isEnum()) args[i] = constant(types[i], SOURCE);
                else if (!types[i].isPrimitive() && args[i] == null) {
                    args[i] = maybe(types[i]);
                }
            }

            sender.setAccessible(true);
            sender.invoke(service, args);
            return true;
        } catch (Throwable error) {
            Diary.note("streak send: " + error);
            return false;
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

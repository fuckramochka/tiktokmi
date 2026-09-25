package mi.tiktokmi;

import android.content.SharedPreferences;

import com.bytedance.ies.abmock.SettingsManager;

import java.util.HashMap;
import java.util.Map;

/**
 * The switches TikTok ships turned off for you.
 *
 * Every feature in the app is behind a named flag, and the value of each is
 * decided per account on the server. Two people on the same version get
 * different apps: voice comments, the splash advertisement, features already
 * written and shipped and simply not switched on for every account.
 *
 * The flag is read through one class whose name is real,
 * `com.bytedance.ies.abmock.SettingsManager`, with one static per type -- no
 * receiver, so a rewritten read keeps the very shape it had. Those reads come
 * through here instead: a flag on the list below is
 * answered by the mod, and every other flag in the app -- there are tens of
 * thousands -- is passed straight back to TikTok untouched.
 *
 * Each override belongs to a group with a switch of its own, so this is a
 * menu rather than a patch: nothing here is forced on anybody.
 *
 * Every flag name below was checked against 46.9.42 rather than copied from a
 * list. One that a later release drops is simply never asked for.
 */
public final class Flags {

    private Flags() {}

    // the groups, each with a switch on the mod's screen
    public static final int ADS = 0;
    public static final int VOICE = 1;

    public static final String KEY_VOICE = "flag_voice";

    private static final class Override {
        final int group;
        final Object value;

        Override(int group, Object value) {
            this.group = group;
            this.value = value;
        }
    }

    private static final Map<String, Override> OVERRIDES = new HashMap<String, Override>();

    private static void put(int group, Object value, String... keys) {
        for (String key : keys) OVERRIDES.put(key, new Override(group, value));
    }

    static {
        // A splash advertisement never becomes a post, so the feed filter never
        // sees it. This is the only place it can be turned off.
        put(ADS, Boolean.FALSE,
                "enable_normal_splash_ad", "enable_normal_splash_ad_ab", "splash_ad_enable",
                "enable_live_splash", "search_ad_refactor_enable", "search_enable_mix_adgap",
                "commerce_enable");

        put(VOICE, Integer.valueOf(1), "audio_comment_publish");
    }

    // ------------------------------------------------------------ the switches

    public static boolean isOn(int group) {
        if (group == ADS) return Feed.isEnabled();
        return bool(keyOf(group));
    }

    private static String keyOf(int group) {
        switch (group) {
            default: return KEY_VOICE;
        }
    }

    public static boolean isOn(String key) {
        return bool(key);
    }

    public static void set(String key, boolean on) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(key, on).apply();
    }

    private static boolean bool(String key) {
        try {
            SharedPreferences prefs = prefs();
            return prefs != null && prefs.getBoolean(key, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static SharedPreferences prefs() {
        android.content.Context context = Margy.context();
        if (context == null) return null;
        return context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE);
    }

    /** The override for a flag, or null when the mod has nothing to say. */
    private static Object of(String key) {
        Boolean ghost = Ghost.overrideFlag(key);
        if (ghost != null) return ghost;
        Boolean plugin = Plugins.flag(key);
        if (plugin != null) return plugin;
        Override override = OVERRIDES.get(key);
        if (override == null) return null;
        if (!isOn(override.group)) return null;
        prove(key, override.value);
        return override.value;
    }

    /**
     * Proof, in the diary, that an override fired on this build: the flag's
     * name and the answer it got, once each. A switch whose flag never fires
     * leaves no line, which is how its owner finds out it does nothing here.
     */
    private static final java.util.Set<String> proven =
            new java.util.HashSet<String>();

    private static void prove(String key, Object value) {
        try {
            synchronized (proven) {
                if (!proven.add(key)) return;
            }
            Diary.note("flag: " + key + " answered " + value);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------- where the reads land

    public static boolean flag(String key, boolean fallback) {
        Object value = of(key);
        if (value instanceof Boolean) return ((Boolean) value).booleanValue();
        if (value instanceof Integer) return ((Integer) value).intValue() != 0;
        return SettingsManager.LIZ(key, fallback);
    }

    public static int flag(String key, int fallback) {
        Object value = of(key);
        if (value instanceof Integer) return ((Integer) value).intValue();
        if (value instanceof Boolean) return ((Boolean) value).booleanValue() ? 1 : 0;
        return SettingsManager.LJ(key, fallback);
    }

    public static long flag(String key, long fallback) {
        Object value = of(key);
        if (value instanceof Integer) return ((Integer) value).intValue();
        return SettingsManager.LJFF(key, fallback);
    }

    public static String flag(String key, String fallback) {
        Object value = of(key);
        if (value instanceof String) return (String) value;
        return SettingsManager.LJI(key, fallback);
    }

    public static float flag(String key, float fallback) {
        Object value = of(key);
        if (value instanceof Integer) return ((Integer) value).intValue();
        return SettingsManager.LIZJ(key, fallback);
    }

    public static double flag(String key, double fallback) {
        Object value = of(key);
        if (value instanceof Integer) return ((Integer) value).intValue();
        return SettingsManager.LIZIZ(key, fallback);
    }
}

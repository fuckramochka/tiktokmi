package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

/**
 * A theme of your own, over the one TikTok is wearing.
 *
 * The rule this follows is the one the app already follows: a colour is
 * repainted here only where TikTok would repaint it when you switch between
 * light and dark. `margyt/nightly.py` works out what that set is by reading
 * the style table -- a theme colour is an attribute that two styles give two
 * different values -- and the build writes it into `Nightly`. Anything not on
 * that list is left exactly as it was: the brand pink, a photo, the white of
 * an icon over a video.
 *
 * What a colour becomes keeps its place rather than its value. A colour is
 * measured by how far it sits from its own theme's background -- black is 0 in
 * the dark theme, white is 1, and the greys of cards and dividers sit between
 * -- and it is put the same distance from the chosen background towards the
 * chosen text. A card a shade above black stays a shade above whatever the
 * background becomes, and a divider stays as faint as it was.
 *
 * Off unless switched on, and the accent is not part of it: a colour the
 * accent has already claimed never reaches here.
 */
public final class Themes {

    private Themes() {}

    public static final String KEY_ON = "theme_on";
    public static final String KEY_MATERIAL = "theme_material";
    public static final String KEY_TEXT = "theme_text";
    public static final String KEY_BACKGROUND = "theme_background";

    /** What the mod falls back to: TikTok's own dark, near enough. */
    private static final int TEXT = 0xFFFFFFFF;
    private static final int BACKGROUND = 0xFF121212;

    // ---------------------------------------------------------- the switches

    public static boolean isEnabled() {
        Settled now = settled;
        return (now == null ? read() : now).on;
    }

    public static void setEnabled(boolean on) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_ON, on).apply();
        forget();
    }

    public static boolean isMaterial() {
        Settled now = settled;
        return (now == null ? read() : now).material;
    }

    public static void setMaterial(boolean on) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_MATERIAL, on).apply();
        forget();
    }

    public static int text() {
        Settled now = settled;
        return (now == null ? read() : now).text;
    }

    public static int background() {
        Settled now = settled;
        return (now == null ? read() : now).background;
    }

    /**
     * What the settings say, read once and kept.
     *
     * This is asked on every colour the app draws -- `Paint.setColor` alone is
     * nine thousand call sites -- and the answer changes only when somebody
     * opens the mod's own screen and changes it. Reading the preferences that
     * often would put a lock on the drawing path for no reason at all.
     *
     * One object holds the whole answer, so a reader either sees the old
     * settings or the new ones and never half of each.
     */
    private static final class Settled {
        final boolean on;
        final boolean material;
        final int text;
        final int background;

        Settled(boolean on, boolean material, int text, int background) {
            this.on = on;
            this.material = material;
            this.text = text;
            this.background = background;
        }
    }

    private static volatile Settled settled;

    private static Settled read() {
        boolean on = flag(KEY_ON, false);
        boolean material = flag(KEY_MATERIAL, false);
        int chosenText = 0;
        int chosenBackground = 0;
        if (material) {
            chosenText = fromSystem(true);
            chosenBackground = fromSystem(false);
        }
        if (chosenText == 0) chosenText = number(KEY_TEXT, TEXT);
        if (chosenBackground == 0) chosenBackground = number(KEY_BACKGROUND, BACKGROUND);

        Settled fresh = new Settled(on, material, chosenText, chosenBackground);
        settled = fresh;
        return fresh;
    }

    public static void setText(int colour) {
        put(KEY_TEXT, colour);
    }

    public static void setBackground(int colour) {
        put(KEY_BACKGROUND, colour);
    }

    /**
     * The wallpaper's own colours, when the phone offers them.
     *
     * Android 12 puts the palette it took from the wallpaper into the
     * framework's own resources, so this is a read rather than a calculation.
     * Which end of the neutral ramp to take depends on which theme is on.
     */
    private static int fromSystem(boolean forText) {
        Context context = Margy.context();
        if (context == null || Build.VERSION.SDK_INT < 31) return 0;
        try {
            boolean dark = isDark();
            int id = forText == dark
                    ? android.R.color.system_neutral1_50
                    : android.R.color.system_neutral1_900;
            return context.getColor(id);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    // ------------------------------------------------------------ the paint

    /**
     * A colour, if this is one of the ones the theme owns.
     *
     * Returns what it was given when theming is off, when the colour is not on
     * the list, or when there is nothing sensible to say -- so a caller can
     * hand anything to this and use the answer without asking.
     */
    public static int recolour(int colour) {
        Settled now = settled;
        if (now == null) now = read();
        if (!now.on) return colour;
        if (!Nightly.owns(colour)) return colour;

        // how far this colour is from its own theme's background: in the dark
        // theme the background is the black end, in the light theme the white
        float level = brightness(colour);
        if (!isDark()) level = 1.0f - level;

        int mixed = mix(now.background, now.text, level);
        return (colour & 0xFF000000) | (mixed & 0xFFFFFF);
    }

    /** How bright a colour reads, 0 for black and 1 for white. */
    private static float brightness(int colour) {
        int red = (colour >> 16) & 0xFF;
        int green = (colour >> 8) & 0xFF;
        int blue = colour & 0xFF;
        return (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255.0f;
    }

    /** A step of the way from one colour to another, kept inside the ends. */
    private static int mix(int from, int to, float how) {
        if (how < 0f) how = 0f;
        if (how > 1f) how = 1f;
        int red = round(((from >> 16) & 0xFF), ((to >> 16) & 0xFF), how);
        int green = round(((from >> 8) & 0xFF), ((to >> 8) & 0xFF), how);
        int blue = round((from & 0xFF), (to & 0xFF), how);
        return (red << 16) | (green << 8) | blue;
    }

    private static int round(int from, int to, float how) {
        return (int) (from + (to - from) * how + 0.5f);
    }

    // ------------------------------------------------- which theme is on

    private static volatile int mode;  // 0 unknown, 1 dark, 2 light
    private static volatile long asked;

    /** Whether the app is wearing its dark theme, asked at most once a second. */
    public static boolean isDark() {
        long now = android.os.SystemClock.uptimeMillis();
        if (mode != 0 && now - asked < 1000) return mode == 1;
        boolean dark = true;
        try {
            Context context = Margy.context();
            if (context != null) {
                Configuration config = context.getResources().getConfiguration();
                dark = (config.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
            }
        } catch (Throwable ignored) {
        }
        mode = dark ? 1 : 2;
        asked = now;
        return dark;
    }

    /** Something the answers depend on has changed; nothing remembered stands. */
    static void forget() {
        mode = 0;
        settled = null;
        Accent.forget();
    }

    // ------------------------------------------------------------ the store

    private static boolean flag(String key, boolean fallback) {
        try {
            SharedPreferences prefs = prefs();
            return prefs == null ? fallback : prefs.getBoolean(key, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int number(String key, int fallback) {
        try {
            SharedPreferences prefs = prefs();
            return prefs == null ? fallback : prefs.getInt(key, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void put(String key, int value) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(key, value).apply();
        forget();
    }

    private static SharedPreferences prefs() {
        Context context = Margy.context();
        if (context == null) return null;
        return context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }
}

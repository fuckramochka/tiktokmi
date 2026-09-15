package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;

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
    public static final String KEY_STRENGTH = "theme_strength";

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
     * How much of the chosen background colour to actually use, 0 to 100.
     *
     * A background is mostly not a colour, it is a darkness -- and a screen
     * painted in full green is a toy rather than a theme. So the colour picked
     * is one end of a line whose other end is the theme's own extreme, black
     * in the dark theme and white in the light one, and this says how far
     * along that line to sit. At 10 it is a black that is faintly green; at
     * 100 it is green.
     */
    public static int strength() {
        Settled now = settled;
        return (now == null ? read() : now).strength;
    }

    public static void setStrength(int percent) {
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;
        put(KEY_STRENGTH, percent);
    }

    /** The chosen colour as it will actually be used. */
    public static int backgroundInUse() {
        Settled now = settled;
        if (now == null) now = read();
        return toned(now.background, now.strength);
    }

    /**
     * The chosen colour at the depth asked for.
     *
     * Not a mix with black, which is what this used to be: mixing a colour
     * with black by channel takes the colour away with it, so at a tenth of
     * the way the answer was black and nothing else. What is wanted is the
     * same colour, darker -- so the hue and the saturation are kept exactly
     * and only the brightness is moved. A tenth of the way is now a very dark
     * green rather than a black that used to be green.
     */
    private static int toned(int chosen, int percent) {
        try {
            float[] hsv = new float[3];
            android.graphics.Color.colorToHSV(chosen, hsv);
            float how = percent / 100.0f;
            if (isDark()) {
                // from nearly black up to the colour's own brightness
                hsv[2] = 0.04f + (hsv[2] - 0.04f) * how;
            } else {
                // and from nearly white down to it
                hsv[2] = 1.0f - (1.0f - hsv[2]) * how;
                hsv[1] = hsv[1] * how;
            }
            return 0xFF000000 | (android.graphics.Color.HSVToColor(hsv) & 0xFFFFFF);
        } catch (Throwable ignored) {
            return chosen;
        }
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
        final int strength;

        Settled(boolean on, boolean material, int text, int background, int strength) {
            this.on = on;
            this.material = material;
            this.text = text;
            this.background = background;
            this.strength = strength;
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

        int strength = number(KEY_STRENGTH, 100);
        if (strength < 0) strength = 0;
        if (strength > 100) strength = 100;

        Settled fresh = new Settled(on, material, chosenText, chosenBackground, strength);
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
    /**
     * A colour, if this is one of the ones the theme owns.
     *
     * `fromTheme` says how the colour was come by. True when it was read as a
     * theme colour -- out of a theme attribute or a colour resource -- and any
     * of the theme's colours is fair game. False when it is a number handed
     * straight to a Paint, where only the colours that mean nothing else are
     * safe to touch: TikTok's dark theme writes in white, and so does the
     * caption over a video, and those two must not share a fate.
     */
    public static int recolour(int colour, boolean fromTheme) {
        Settled now = settled;
        if (now == null) now = read();
        if (!now.on) return colour;
        if (!(fromTheme ? Nightly.owns(colour) : Nightly.unmistakable(colour))) {
            return colour;
        }

        // how far this colour is from its own theme's background: in the dark
        // theme the background is the black end, in the light theme the white
        float level = brightness(colour);
        if (!isDark()) level = 1.0f - level;

        int mixed = mix(toned(now.background, now.strength), now.text, level);
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

    // ------------------------------------------------- the ones already drawn

    /**
     * Repaint what is already on screen.
     *
     * Every rule in the patcher rewrites TikTok's own bytecode, and a
     * background written in a layout is never touched by TikTok's bytecode at
     * all: the framework reads the attribute and builds the drawable inside
     * its own code, where nothing can be rewritten. That is most of the
     * comments panel, the inbox and a conversation -- which is exactly what
     * stayed TikTok's own colour while everything else moved.
     *
     * So those are dealt with from the other end, after they exist. The tree
     * is walked and anything wearing a colour the theme owns is repainted. It
     * is bounded: only flat colours, only colours on the list, and a cap on
     * how much of a tree is walked at once.
     */
    public static void repaint(View root) {
        Settled now = settled;
        if (now == null) now = read();
        if (!now.on || root == null) return;
        try {
            seen = 0;
            walk(root, 0);
        } catch (Throwable ignored) {
        }
    }

    /**
     * How deep to go, and how much to do at once.
     *
     * Fourteen was far too shallow and it showed: a comment sheet is a few
     * levels down and was reached, while a profile or the inbox -- a fragment
     * inside a pager inside a list inside a coordinator -- is twenty and more,
     * and the walk simply stopped before it got there. The budget is what
     * keeps this bounded now, rather than the depth: a screen is a few hundred
     * views, and anything claiming to be tens of thousands is not a screen.
     */
    private static final int DEEP = 40;
    private static final int BUDGET = 4000;

    private static int seen;

    /**
     * Repaint a drawable, whatever kind it turns out to be.
     *
     * A flat colour is the easy case and not the common one: a panel is
     * usually a shape with rounded corners, and a row is often a stack of
     * drawables with the colour somewhere inside it. Both are followed. A
     * shape is repainted where it stands, which is why the view is handed in
     * as well -- the same shape can be shared between views, and a new one is
     * made rather than the shared one changed.
     */
    private static void paint(android.graphics.drawable.Drawable drawable,
                              View view, int depth) {
        if (drawable == null || depth > 3) return;
        try {
            if (drawable instanceof android.graphics.drawable.ColorDrawable) {
                int was = ((android.graphics.drawable.ColorDrawable) drawable).getColor();
                int now = recolour(was, true);
                if (now != was && view != null) {
                    view.setBackground(new android.graphics.drawable.ColorDrawable(now));
                }
                return;
            }
            if (drawable instanceof android.graphics.drawable.GradientDrawable) {
                android.graphics.drawable.GradientDrawable shape =
                        (android.graphics.drawable.GradientDrawable) drawable;
                android.content.res.ColorStateList held =
                        Build.VERSION.SDK_INT >= 24 ? shape.getColor() : null;
                if (held == null) return;
                int was = held.getDefaultColor();
                int now = recolour(was, true);
                if (now != was) {
                    android.graphics.drawable.Drawable copy = shape.mutate();
                    ((android.graphics.drawable.GradientDrawable) copy).setColor(now);
                    if (view != null) view.setBackground(copy);
                }
                return;
            }
            if (drawable instanceof android.graphics.drawable.LayerDrawable) {
                android.graphics.drawable.LayerDrawable layers =
                        (android.graphics.drawable.LayerDrawable) drawable;
                for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                    paint(layers.getDrawable(i), null, depth + 1);
                }
                return;
            }
            if (drawable instanceof android.graphics.drawable.InsetDrawable) {
                paint(((android.graphics.drawable.InsetDrawable) drawable).getDrawable(),
                        null, depth + 1);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void walk(View view, int depth) {
        if (view == null || depth > DEEP || ++seen > BUDGET) return;

        paint(view.getBackground(), view, 0);

        // Backgrounds only. Text colours are already answered where the app
        // sets them, in their thousands, and repainting them here as well
        // collapsed two colours that were close into one: the labels under the
        // last row of the share sheet came out the colour of the sheet.

        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            int many = group.getChildCount();
            for (int i = 0; i < many; i++) walk(group.getChildAt(i), depth + 1);
        }
    }

    /**
     * Watch a screen, so that panels opened inside it are repainted too.
     *
     * A comment sheet or a conversation is not a new screen as far as Android
     * is concerned -- it is more views inside the one that is already up -- so
     * waiting for the next screen would never repaint them. A layout listener
     * hears about them, and the work is held off until the layouts stop
     * arriving, because one of these fires many times a second while anything
     * is moving.
     */
    public static void watch(final android.app.Activity activity) {
        if (activity == null) return;
        try {
            final View root = activity.getWindow().getDecorView();
            if (Boolean.TRUE.equals(root.getTag(WATCHING))) {
                repaint(root);
                Badge.rewrite(root);
                return;
            }
            root.setTag(WATCHING, Boolean.TRUE);
            root.getViewTreeObserver().addOnGlobalLayoutListener(
                    new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            long now = android.os.SystemClock.uptimeMillis();
                            if (now - last < QUIET) return;
                            last = now;
                            repaint(root);
                            Badge.rewrite(root);
                        }
                    });
            repaint(root);
            Badge.rewrite(root);
        } catch (Throwable error) {
            Diary.note("theme: " + error);
        }
    }

    private static volatile long last;
    private static final long QUIET = 400;


    // setTag(int, ...) wants a key that looks like a resource id, and every key
    // the mod uses has to differ from every other one. This was the same
    // number SettingsRow marks a window with, so whichever ran first told the
    // other its work was already done -- and the MargyT row stopped appearing
    // in TikTok's settings at all.
    private static final int WATCHING = 0x4D61726A;  // "Marj"

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

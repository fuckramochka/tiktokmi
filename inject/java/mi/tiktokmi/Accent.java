package mi.tiktokmi;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bytedance.tux.icon.TuxIconView;

/**
 * TikTok's accent colour, made changeable.
 *
 * The app is built around one pink, `#FE2C55`: the like, the follow button, the
 * tab underline, the badges. Most of the places it is drawn hold it as a plain
 * constant in the bytecode -- `const v1, -0x1d3ab` before a `Paint.setColor` --
 * so the build rewrites each of those into a call here, and the colour becomes
 * whatever this returns.
 *
 * This sits in the drawing path of half the app, so it answers from a cached
 * int and never throws: the worst it can do when something is wrong is hand
 * back the pink the app came with.
 */
public final class Accent {

    private Accent() {}

    /**
     * TikTok's own pink, and always the colour a swap is measured from.
     *
     * It stays the reference even when the build baked something else into the
     * resources. Making the baked colour the reference instead is a mistake
     * that was made once and is worth writing down: the app then stops
     * recognising its own pink wherever the build did not reach -- which is
     * most of the bytecode -- and starts recognising whatever sits near the
     * baked colour by hue, which for a mint is the green of somebody being
     * online. Shades this build wrote are recognised by the list in Baked
     * instead, exactly, and sent wherever their original would go.
     */
    public static final int TIKTOK = 0xFFFE2C55;

    /** What this apk was built with: the colour before anyone chooses another. */
    public static final int BUILT_WITH = Baked.ACCENT;

    public static final String KEY = "accent";

    /** iso-style names are not needed here; the label is the colour itself. */
    public static final int[] PALETTE = {
            BUILT_WITH,
            TIKTOK,      // TikTok's own pink, in case the build baked another
            0xFFFFB7C5,  // Miogram Strawberry Milk
            0xFFE6E6FA,  // Miogram Lavender Dream
            0xFFBFFCC6,  // Miogram Minty Angel
            0xFFA0E7E5,  // Miogram Cyber Fairy
            0xFFFF1493,  // Miogram Neon Pink
            0xFF00F0FF,  // Miogram Cyan Cyber
            0xFFFFD700,  // Miogram Royal Gold
            0xFF8DD1B0,  // Mint
            0xFF25F4EE,  // TikTok cyan
            0xFF4C8DFF,
            0xFF9B6BFF,
            0xFFFF8A3D,
            0xFF35C759,
            0xFFFF4D6D,
            0xFF1A1423,  // Dark Velvet
            0xFFE8E8E8,
    };

    /** The palette without repeats: the built-in colour may be in it twice. */
    public static int[] palette() {
        int[] out = new int[PALETTE.length];
        int count = 0;
        for (int colour : PALETTE) {
            boolean seen = false;
            for (int i = 0; i < count; i++) seen |= out[i] == colour;
            if (!seen) out[count++] = colour;
        }
        int[] trimmed = new int[count];
        System.arraycopy(out, 0, trimmed, 0, count);
        return trimmed;
    }

    /**
     * The accent the phone took from the wallpaper, or zero.
     *
     * Android 12 puts that palette into the framework's own resources, so this
     * is a read rather than a guess. Read straight from the resources and not
     * through the mod's own interception, which would hand back whatever the
     * accent already is.
     */
    public static int fromWallpaper() {
        Context context = Margy.context();
        if (context == null || android.os.Build.VERSION.SDK_INT < 31) return 0;
        try {
            return context.getResources().getColor(
                    android.R.color.system_accent1_400, context.getTheme());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static volatile int cached;

    public static int colour() {
        int known = cached;
        if (known != 0) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return BUILT_WITH;  // too early to know; do not cache it
        int chosen = 0;
        try {
            chosen = prefs.getInt(KEY, 0);
        } catch (Throwable ignored) {
        }
        // Auto-detect Material You from wallpaper on first run if available
        if (chosen == 0) {
            int wallpaper = fromWallpaper();
            chosen = (wallpaper != 0) ? wallpaper : BUILT_WITH;
        }
        cached = chosen;
        return chosen;
    }

    /**
     * Where a rewritten constant lands.
     *
     * `const v1, -0x1d3ab` in TikTok's bytecode becomes a call to this, so it
     * is the accent as chosen -- and then whatever the plugins make of it.
     * `colour()` itself stays plain: it is what the mod paints its own screen
     * with, and a plugin recolouring the settings it is being configured from
     * would be a poor joke.
     */
    public static int accent() {
        return Plugins.colour(colour());
    }

    public static void set(int colour) {
        cached = colour == 0 ? BUILT_WITH : colour;
        forget();
        Themes.forget();
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(KEY, cached).apply();
        if (!MiogramBridge.isSyncingFromAmegram() && MiogramBridge.isThemeSyncEnabled()) {
            try {
                MiogramBridge.syncThemeToAmegram(Margy.context(), cached, Themes.isDark(), Themes.backgroundInUse() == 0xFF000000);
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean isDefault() {
        return colour() == BUILT_WITH;
    }

    private static SharedPreferences prefs() {
        try {
            Context context = Margy.context();
            if (context == null) return null;
            return context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------- where colours arrive

    /**
     * Any colour of TikTok's red family comes back on the chosen accent.
     *
     * Not the one value it used to be: the app draws its pink at a dozen
     * opacities and next to a family of neighbours, and swapping only the exact
     * brand colour left nine tenths of the red on screen. Palette says what
     * belongs to the family and where it moves to.
     */
    public static int swap(int colour) {
        // Paint.setColor is called on every frame of everything, so the answer
        // to the question just asked is kept: one long holds both halves, so a
        // reader either sees a whole pair or none of it, with no lock either way
        long known = memo;
        if ((int) (known >>> 32) == colour) return (int) known;

        int out = Plugins.colour(translate(colour));
        memo = ((long) colour << 32) | (out & 0xFFFFFFFFL);
        return out;
    }

    /**
     * Two ways in, and the second only when the first says nothing.
     *
     * TikTok's own family is recognised by hue, which reaches every shade of
     * it including the ones no build ever saw. What the build baked is not a
     * family at all -- it is a list -- and each entry knows the shade it was
     * made from, so it is sent wherever that shade would go now.
     */
    private static int translate(int colour) {
        int chosen = colour();
        int moved = Palette.map(colour, TIKTOK, chosen);
        if (moved != colour) return moved;

        int origin = originOf(colour);
        if (origin != 0) return Palette.map(origin, TIKTOK, chosen);

        // last, and only for the colours TikTok repaints itself when its own
        // theme changes: the accent has had its say and did not want this one
        return Themes.recolour(colour, true);
    }

    /** The shade a baked colour was made from, or zero. Binary search. */
    private static int originOf(int colour) {
        int[] baked = Baked.BAKED;
        int low = 0, high = baked.length - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int here = baked[middle];
            if (here == colour) return Baked.FROM[middle];
            if (here < colour) low = middle + 1; else high = middle - 1;
        }
        return 0;
    }

    private static volatile long memo;

    /** Forget the one remembered answer: something it depended on has changed. */
    static void forget() {
        memo = 0;
    }

    // ---------------------------------------------- where a colour is used

    /**
     * The other half of the accent.
     *
     * Reading a colour is not the only way to have one: it can be computed,
     * blended, or carried in from somewhere the mod never sees. But it has to
     * be applied to something before it is drawn, and there are only so many
     * ways to apply one. Every call site of these in TikTok's bytecode is
     * rewritten to come through here, which is how the accent reaches what the
     * resource table and the constants never could.
     */
    /**
     * A background written in a layout, which arrives already wrapped.
     *
     * `android:background="?attr/..."` is resolved by the framework before any
     * of the app's code sees it, and what comes back is a ColorDrawable rather
     * than a number -- so every rule that watches for colours looks straight
     * past the thing most screens are painted with. Unwrapped here, moved, and
     * handed back as a new drawable: the one that came out of the resources is
     * shared with everything else that asked for it.
     */
    public static Drawable getDrawable(TypedArray array, int index) {
        return moved(array.getDrawable(index));
    }

    public static void setBackgroundResource(View view, int id) {
        try {
            Drawable drawable = view.getContext().getDrawable(id);
            Drawable out = moved(drawable);
            if (out != drawable) {
                view.setBackground(out);
                return;
            }
        } catch (Throwable ignored) {
        }
        view.setBackgroundResource(id);
    }

    /** The same drawable in the chosen colours, or the one that came in. */
    private static Drawable moved(Drawable drawable) {
        if (!(drawable instanceof ColorDrawable)) return drawable;
        int was = ((ColorDrawable) drawable).getColor();
        int now = sourced(was);
        return now == was ? drawable : new ColorDrawable(now);
    }

    public static ColorStateList getColorStateList(Resources resources, int id) {
        return moved(resources.getColorStateList(id));
    }

    public static ColorStateList getColorStateList(TypedArray array, int index) {
        return moved(array.getColorStateList(index));
    }

    /**
     * A colour per state, moved state by state.
     *
     * There is no way to read the states back before Android 10, and below
     * that the list is left exactly as it was -- one colour on an older phone
     * is a small thing next to a list rebuilt out of guesses.
     */
    private static ColorStateList moved(ColorStateList list) {
        if (list == null) return null;
        try {
            // getColors and getStates exist from Android 10 but are not in the
            // jar this is compiled against, so they are asked for by name; a
            // phone that does not have them keeps its one colour
            int[] colours = (int[]) call(list, "getColors");
            int[][] states = (int[][]) call(list, "getStates");
            if (colours == null || states == null || colours.length == 0
                    || states.length < colours.length) {
                int was = list.getDefaultColor();
                int now = sourced(was);
                return now == was ? list : ColorStateList.valueOf(now);
            }

            int[] out = new int[colours.length];
            int[][] kept = new int[colours.length][];
            boolean any = false;
            for (int i = 0; i < colours.length; i++) {
                kept[i] = states[i];
                out[i] = sourced(colours[i]);
                any |= out[i] != colours[i];
            }
            return any ? new ColorStateList(kept, out) : list;
        } catch (Throwable ignored) {
            return list;
        }
    }

    private static Object call(Object on, String name) {
        try {
            java.lang.reflect.Method method = on.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(on);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void setStatusBarColor(android.view.Window window, int colour) {
        window.setStatusBarColor(sourced(colour));
    }

    public static void setNavigationBarColor(android.view.Window window, int colour) {
        window.setNavigationBarColor(sourced(colour));
    }

    public static void setHintTextColor(TextView view, int colour) {
        view.setHintTextColor(swap(colour));
    }

    public static void setTint(Drawable drawable, int colour) {
        drawable.setTint(swap(colour));
    }

    public static void setColors(GradientDrawable shape, int[] colours) {
        if (colours == null) {
            shape.setColors(null);
            return;
        }
        int[] out = new int[colours.length];
        for (int i = 0; i < colours.length; i++) out[i] = swap(colours[i]);
        shape.setColors(out);
    }

    public static void setColor(Paint paint, int colour) {
        paint.setColor(swap(colour));
    }

    public static void setColor(GradientDrawable shape, int colour) {
        shape.setColor(swap(colour));
    }

    public static void setColor(TuxIconView icon, int colour) {
        icon.setColor(swap(colour));
    }

    public static void setColorFilter(ImageView view, int colour) {
        view.setColorFilter(swap(colour));
    }

    public static void setColorFilter(ImageView view, int colour, PorterDuff.Mode mode) {
        view.setColorFilter(swap(colour), mode);
    }

    public static void setTextColor(TextView view, int colour) {
        view.setTextColor(swap(colour));
    }

    public static void setBackgroundColor(View view, int colour) {
        view.setBackgroundColor(swap(colour));
    }

    public static ColorStateList valueOf(int colour) {
        return ColorStateList.valueOf(swap(colour));
    }

    public static int getColor(Context context, int id) {
        return sourced(context.getColor(id));
    }

    public static int getColor(Resources resources, int id) {
        return sourced(resources.getColor(id));
    }

    public static int getColor(Resources resources, int id, Resources.Theme theme) {
        return sourced(resources.getColor(id, theme));
    }

    public static int getColor(TypedArray array, int index, int fallback) {
        return sourced(array.getColor(index, fallback));
    }

    /**
     * A colour that came out of the resources rather than out of arithmetic.
     *
     * This is where TikTok's theme colours actually enter the app -- a theme
     * attribute or a colour resource -- so a colour arriving here is known to
     * be the theme's and the whole list applies to it. The accent goes first;
     * it only ever claims its own family, and what it leaves is offered to the
     * theme.
     */
    private static int sourced(int colour) {
        int moved = swap(colour);
        if (moved != colour) return moved;
        return Themes.recolour(colour, true);
    }
}

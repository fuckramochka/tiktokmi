package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;

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
     * The colour this apk was built with, and the one every swap looks for.
     *
     * TikTok ships #FE2C55. A build given --accent bakes another one into the
     * vectors and the resources, and then that is the colour to look for.
     */
    public static final int TIKTOK = Baked.ACCENT;

    public static final String KEY = "accent";

    /** iso-style names are not needed here; the label is the colour itself. */
    public static final int[] PALETTE = {
            TIKTOK,
            0xFFFE2C55,  // TikTok's own pink, in case the build baked another
            0xFF8DD1B0,  // Margy mint
            0xFF25F4EE,  // TikTok's own cyan
            0xFF4C8DFF,
            0xFF9B6BFF,
            0xFFFF8A3D,
            0xFF35C759,
            0xFFFFD23F,
            0xFFFF4D6D,
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

    private static volatile int cached;

    public static int colour() {
        int known = cached;
        if (known != 0) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return TIKTOK;  // too early to know; do not cache it
        int chosen = TIKTOK;
        try {
            chosen = prefs.getInt(KEY, TIKTOK);
        } catch (Throwable ignored) {
        }
        if (chosen == 0) chosen = TIKTOK;
        cached = chosen;
        return chosen;
    }

    public static void set(int colour) {
        cached = colour == 0 ? TIKTOK : colour;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(KEY, cached).apply();
    }

    public static boolean isDefault() {
        return colour() == TIKTOK;
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
        return Palette.map(colour, TIKTOK, colour());
    }

    public static int getColor(Context context, int id) {
        return swap(context.getColor(id));
    }

    public static int getColor(Resources resources, int id) {
        return swap(resources.getColor(id));
    }

    public static int getColor(Resources resources, int id, Resources.Theme theme) {
        return swap(resources.getColor(id, theme));
    }

    public static int getColor(TypedArray array, int index, int fallback) {
        return swap(array.getColor(index, fallback));
    }
}

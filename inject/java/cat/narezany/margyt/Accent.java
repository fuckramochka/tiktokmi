package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.PorterDuff;
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
        if (prefs == null) return BUILT_WITH;  // too early to know; do not cache it
        int chosen = BUILT_WITH;
        try {
            chosen = prefs.getInt(KEY, BUILT_WITH);
        } catch (Throwable ignored) {
        }
        if (chosen == 0) chosen = BUILT_WITH;
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
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(KEY, cached).apply();
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
        return Themes.recolour(colour);
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

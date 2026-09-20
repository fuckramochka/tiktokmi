package mi.tiktokmi;

import android.graphics.Color;

/**
 * Which colours the accent takes over, and what it turns them into.
 *
 * The same arithmetic as `tiktokmi/palette.py`, which does this to the resource
 * table and the compiled XML while the apk is built. This one is for the
 * colours that are only known while the app is running -- whatever comes back
 * from `Resources.getColor`, from a `TypedArray`, or out of a constant the
 * bytecode patch redirected here.
 *
 * The two have to agree: a build bakes the resources with the python and the
 * app moves the rest with this, and a colour that came out mint in one and
 * something else in the other would show as a seam down the middle of a screen.
 *
 * TikTok is not built around a single pink. `#FE2C55` is the one in the brand
 * guide, but the app draws it at a dozen opacities and surrounds it with a
 * family -- the magentas of the gradients, the reds of Shop and LIVE. So the
 * rule is a zone around the reference hue, and the move is relative: whatever
 * step takes the reference to the accent is applied to every member, so the
 * reference lands exactly on the accent and a lighter member stays lighter.
 * Alpha is never touched.
 */
public final class Palette {

    private Palette() {}

    /** How far from the reference hue still counts as the family, in degrees. */
    public static final float HUE = 30f;

    /** Below these there is no hue worth moving: greys, near-black, near-white. */
    public static final float MIN_SATURATION = 0.15f;
    public static final float MIN_VALUE = 0.2f;

    public static boolean captures(int colour, int reference) {
        // the reference itself is always in the family, at any opacity and
        // whatever it is: the thresholds are there to keep unrelated greys from
        // being dragged along with the neighbours
        if ((colour & 0xFFFFFF) == (reference & 0xFFFFFF)) return true;

        float[] here = hsv(colour);
        if (here[1] < MIN_SATURATION || here[2] < MIN_VALUE) return false;
        float apart = Math.abs(here[0] - hsv(reference)[0]);
        if (apart > 180f) apart = 360f - apart;
        return apart <= HUE;
    }

    /**
     * Move `colour` by the step that takes `reference` to `accent`.
     *
     * Sits in the drawing path of half the app, so the common case -- the
     * accent still being the colour the apk was built with -- is one integer
     * comparison and out.
     */
    public static int map(int colour, int reference, int accent) {
        if (reference == accent) return colour;
        try {
            if (!captures(colour, reference)) return colour;

            float[] here = hsv(colour);
            float[] from = hsv(reference);
            float[] to = hsv(accent);

            float hue = to[0] + (here[0] - from[0]);
            hue = ((hue % 360f) + 360f) % 360f;
            float satRatio = from[1] <= 0.01f ? 1f : here[1] / from[1];
            float valRatio = from[2] <= 0.01f ? 1f : here[2] / from[2];
            float saturation = clamp(to[1] * satRatio);
            if (to[1] > 0.05f && saturation < 0.08f && here[1] >= 0.2f) {
                saturation = 0.08f;
            }
            float value = clamp(to[2] * valRatio);

            return Color.HSVToColor(Color.alpha(colour),
                    new float[]{hue, saturation, value});
        } catch (Throwable ignored) {
            // nothing about a colour is worth taking an app down for
            return colour;
        }
    }

    private static float[] hsv(int colour) {
        float[] out = new float[3];
        Color.colorToHSV(colour, out);
        return out;
    }

    private static float clamp(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }
}

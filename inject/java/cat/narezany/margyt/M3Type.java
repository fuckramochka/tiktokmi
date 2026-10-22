package cat.narezany.margyt;

import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * The Material 3 type scale, as the Compose tokens give it.
 *
 * Size, tracking and line height go together: a 14sp line set on the
 * platform's default spacing comes out at 16sp tall instead of 20, which is
 * why text drawn by hand never quite looks like Google's.
 */
final class M3Type {
    private M3Type() {}

    static void displaySmall(TextView view) { set(view, 36, 0f, 44, false); }
    static void headlineMedium(TextView view) { set(view, 28, 0f, 36, false); }
    static void headlineSmall(TextView view) { set(view, 24, 0f, 32, false); }
    static void titleLarge(TextView view) { set(view, 22, 0f, 28, false); }
    static void titleSmall(TextView view) { set(view, 14, 0.1f, 20, true); }
    static void bodyLarge(TextView view) { set(view, 16, 0.5f, 24, false); }
    static void bodyMedium(TextView view) { set(view, 14, 0.2f, 20, false); }
    static void bodySmall(TextView view) { set(view, 12, 0.4f, 16, false); }
    static void labelLarge(TextView view) { set(view, 14, 0.1f, 20, true); }

    /** Size and tracking only, for a single line whose height the row decides. */
    static void line(TextView view, float sp, float trackingSp) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setLetterSpacing(trackingSp / sp);
    }

    private static void set(TextView view, float sp, float trackingSp, float lineSp, boolean medium) {
        line(view, sp, trackingSp);
        view.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        int height = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, lineSp,
                view.getResources().getDisplayMetrics()));
        if (Build.VERSION.SDK_INT >= 28) {
            view.setLineHeight(height);
        } else {
            int natural = view.getPaint().getFontMetricsInt(null);
            view.setLineSpacing(Math.max(0, height - natural), 1f);
        }
    }
}

package cat.narezany.margyt;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * Whatever is in front of the person right now.
 *
 * The mod often knows something worth offering -- a sticker was touched, and
 * it could be saved -- at a moment when it has no screen of its own to say it
 * on. TikTok's own screens are not ours to add buttons to at will, so instead
 * the offer appears for a few seconds over whatever is open and then leaves.
 *
 * Nothing here holds an activity: a weak reference, cleared when it goes away.
 */
public final class Screen {

    private Screen() {}

    private static volatile WeakReference<Activity> current = new WeakReference<Activity>(null);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int TAG = 0x4D61726A;  // "Marj"
    private static final long LINGER = 5000;

    public static void at(Activity activity) {
        current = new WeakReference<Activity>(activity);
    }

    public static void gone(Activity activity) {
        if (current.get() == activity) current = new WeakReference<Activity>(null);
    }

    public static Activity now() {
        return current.get();
    }

    /** A word on the current screen, from any thread. */
    public static void say(final String message) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                Activity activity = current.get();
                if (activity == null || activity.isFinishing()) return;
                Popup.show(activity, null, message, null);
            }
        });
    }

    /**
     * A button over the current screen, for a few seconds.
     *
     * It sits above everything the screen has drawn, takes only its own taps,
     * and removes itself whether or not it was used.
     */
    public static void offer(final String label, final Runnable action) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    final Activity activity = current.get();
                    if (activity == null || activity.isFinishing()) return;
                    final ViewGroup content = (ViewGroup) activity.getWindow()
                            .getDecorView().findViewById(android.R.id.content);
                    if (content == null) return;

                    View already = content.findViewWithTag(TAG);
                    if (already != null) content.removeView(already);

                    final TextView pill = new TextView(activity);
                    pill.setTag(TAG);
                    pill.setText(label);
                    pill.setTextColor(onAccent());
                    pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    pill.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    pill.setGravity(Gravity.CENTER);
                    pill.setPadding(dp(activity, 20), dp(activity, 10),
                            dp(activity, 20), dp(activity, 10));

                    GradientDrawable shape = new GradientDrawable();
                    shape.setColor(Accent.colour());
                    shape.setCornerRadius(dp(activity, 22));
                    pill.setBackground(shape);
                    pill.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            remove(content, pill);
                            try {
                                action.run();
                            } catch (Throwable error) {
                                Diary.note("offer: " + error);
                            }
                        }
                    });

                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                    params.bottomMargin = dp(activity, 96);
                    content.addView(pill, params);

                    MAIN.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            remove(content, pill);
                        }
                    }, LINGER);
                } catch (Throwable error) {
                    Diary.note("offer: " + error);
                }
            }
        });
    }

    private static void remove(ViewGroup parent, View view) {
        try {
            if (view.getParent() == parent) parent.removeView(view);
        } catch (Throwable ignored) {
        }
    }

    private static int onAccent() {
        int colour = Accent.colour();
        int red = (colour >> 16) & 0xFF, green = (colour >> 8) & 0xFF, blue = colour & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000 > 150 ? 0xFF1C2C24 : 0xFFFFFFFF;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}

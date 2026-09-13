package cat.narezany.margyt;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
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
    /**
     * How long an offer may stay if nothing at all happens.
     *
     * It is a safety net rather than a timer: the offer goes when the screen
     * that prompted it goes -- when something outside it is touched, or when
     * the activity stops being in front. Counting seconds was the first way
     * and the wrong one; it meant catching the button before it fled.
     */
    private static final long LINGER = 120000;

    /** Long enough for whatever was touched to have opened its own window. */
    private static final long AFTER = 550;

    public static void at(Activity activity) {
        current = new WeakReference<Activity>(activity);
    }

    public static void gone(Activity activity) {
        if (current.get() == activity) current = new WeakReference<Activity>(null);
        // an offer belongs to the screen it was made on
        Dialog open = showing;
        if (open != null) {
            showing = null;
            close(open);
        }
    }

    /** At most one offer at a time: a second replaces the first. */
    private static volatile Dialog showing;

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
     * A button over whatever is in front, for a few seconds.
     *
     * A window of its own rather than a view inside the screen's, and that is
     * the whole point: a sticker opens in a window of its own too, and a view
     * added to the activity underneath it is drawn underneath it. A dialog is
     * a new window, so it lands on top of whatever is already there.
     *
     * It does not dim, it does not take the touches that miss it, and it lets
     * itself out after a few seconds whether or not it was used.
     */
    public static void offer(String label, Runnable action) {
        offer(label, 0.52f, action);
    }

    /**
     * `where` is how far down the screen it sits, as a fraction of its height.
     *
     * The delay is not politeness. The offer is made at the moment something
     * is touched, and what was touched usually opens a window of its own a
     * beat later -- which then covers anything already showing. Waiting lets
     * that window open first, so this one lands on top of it.
     */
    public static void offer(final String label, final float where, final Runnable action) {
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    final Activity activity = current.get();
                    if (activity == null || activity.isFinishing()) return;

                    Dialog previous = showing;
                    if (previous != null) close(previous);

                    final Dialog dialog = new Dialog(activity);
                    Window window = dialog.getWindow();
                    if (window == null) return;
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.setDimAmount(0f);
                    window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            // told about touches that land anywhere else, which
                            // is how it knows the screen under it has been left
                            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);

                    dialog.setContentView(pill(activity, label, new Runnable() {
                        @Override
                        public void run() {
                            close(dialog);
                            try {
                                action.run();
                            } catch (Throwable error) {
                                Diary.note("offer: " + error);
                            }
                        }
                    }));
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.getWindow().getDecorView().setOnTouchListener(
                            new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, android.view.MotionEvent event) {
                            if (event.getAction() == android.view.MotionEvent.ACTION_OUTSIDE) {
                                close(dialog);
                            }
                            return false;
                        }
                    });
                    dialog.show();
                    showing = dialog;

                    WindowManager.LayoutParams params = window.getAttributes();
                    params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    // a little below the middle: clear of the picture itself
                    // and well clear of everything the screen keeps at its foot
                    params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                    params.y = (int) (activity.getResources().getDisplayMetrics()
                            .heightPixels * where);
                    window.setAttributes(params);

                    MAIN.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            close(dialog);
                        }
                    }, LINGER);
                } catch (Throwable error) {
                    Diary.note("offer: " + error);
                }
            }
        }, AFTER);
    }

    /** The button itself, which anything may borrow. */
    public static TextView pill(Activity activity, String label, final Runnable action) {
        TextView pill = new TextView(activity);
        pill.setText(label);
        pill.setTextColor(onAccent());
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        pill.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(activity, 22), dp(activity, 11), dp(activity, 22), dp(activity, 11));

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Accent.colour());
        shape.setCornerRadius(dp(activity, 22));
        pill.setBackground(shape);
        pill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        return pill;
    }

    private static void close(Dialog dialog) {
        try {
            if (dialog.isShowing()) dialog.dismiss();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------ how far along it is

    private static volatile Dialog bar;
    private static volatile TextView barText;

    /**
     * A line at the top of the screen saying how a download is going.
     *
     * At the top because the bottom is where every app keeps its own things,
     * and because a download is not something to interrupt what is being
     * watched. Its own window, so it survives whatever screen changes under it.
     */
    public static void progress(final String what, final int percent) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    String line = percent < 0 ? what : what + "  " + percent + "%";
                    TextView already = barText;
                    if (already != null && bar != null) {
                        already.setText(line);
                        return;
                    }
                    Activity activity = current.get();
                    if (activity == null || activity.isFinishing()) return;

                    Dialog dialog = new Dialog(activity);
                    Window window = dialog.getWindow();
                    if (window == null) return;
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.setDimAmount(0f);
                    window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

                    TextView text = pill(activity, line, new Runnable() {
                        @Override
                        public void run() {
                        }
                    });
                    dialog.setContentView(text);
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();

                    WindowManager.LayoutParams params = window.getAttributes();
                    params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                    params.y = dp(activity, 56);
                    window.setAttributes(params);

                    bar = dialog;
                    barText = text;
                } catch (Throwable error) {
                    Diary.note("progress: " + error);
                }
            }
        });
    }

    public static void progressGone() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                Dialog open = bar;
                bar = null;
                barText = null;
                if (open != null) close(open);
            }
        });
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

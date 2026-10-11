package mi.tiktokmi;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 2x Playback Speed Lock Overlay Pill ("Вниз · закрепить 2×").
 *
 * Appears during fast-forward (2x playback) in the TikTok feed.
 * Allows users to swipe down or tap to lock playback at 2x without having
 * to keep holding their finger on the screen.
 * Matching photo_2026-10-10_11-56-07.jpg.
 */
public final class SpeedOverlay {

    private SpeedOverlay() {}

    public static final String KEY = "speed_lock_enabled";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Dialog sActiveDialog;
    private static volatile boolean sIsLocked = false;
    private static volatile Boolean sEnabled;

    public static boolean isEnabled() {
        Boolean known = sEnabled;
        if (known != null) return known.booleanValue();
        boolean on = true;
        try {
            Context context = Margy.context();
            if (context != null) {
                SharedPreferences prefs = context.getSharedPreferences(
                        Margy.PREFS, Context.MODE_PRIVATE);
                on = prefs.getBoolean(KEY, true);
            }
        } catch (Throwable ignored) {
        }
        sEnabled = Boolean.valueOf(on);
        return on;
    }

    public static void setEnabled(boolean enabled) {
        sEnabled = Boolean.valueOf(enabled);
        try {
            Context context = Margy.context();
            if (context != null) {
                context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY, enabled).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isLocked() {
        return sIsLocked;
    }

    public static void unlock() {
        sIsLocked = false;
        dismiss();
    }

    public static void show(final Activity activity) {
        show(activity, 2.0f);
    }

    public static void show(final Activity activity, final float speed) {
        if (!isEnabled() || activity == null || activity.isFinishing()) return;
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (sActiveDialog != null && sActiveDialog.isShowing()) {
                        return; // already showing
                    }

                    final Dialog dialog = new Dialog(activity);
                    Window window = dialog.getWindow();
                    if (window == null) return;

                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.setDimAmount(0f);
                    window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

                    final LinearLayout root = new LinearLayout(activity);
                    root.setOrientation(LinearLayout.VERTICAL);
                    root.setGravity(Gravity.CENTER_HORIZONTAL);

                    // Pill Container
                    final LinearLayout pill = new LinearLayout(activity);
                    pill.setOrientation(LinearLayout.HORIZONTAL);
                    pill.setGravity(Gravity.CENTER_VERTICAL);
                    pill.setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), dp(activity, 8));

                    final GradientDrawable pillBg = new GradientDrawable();
                    pillBg.setColor(Accent.colour());
                    pillBg.setCornerRadius(dp(activity, 24));
                    pill.setBackground(pillBg);

                    // Lock Icon
                    final LockIconView lockIcon = new LockIconView(activity, onColor(Accent.colour()), sIsLocked);
                    pill.addView(lockIcon, new LinearLayout.LayoutParams(dp(activity, 18), dp(activity, 18)));

                    // Pill Text
                    final TextView pillText = new TextView(activity);
                    pillText.setText(sIsLocked ? Text.SPEED_LOCKED : Text.SPEED_DOWN_LOCK);
                    pillText.setTextColor(onColor(Accent.colour()));
                    pillText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    pillText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                    pillText.setPadding(dp(activity, 8), 0, 0, 0);
                    pill.addView(pillText);

                    root.addView(pill, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    // Subtitle: "Скорость: 2х ▶"
                    final TextView speedText = new TextView(activity);
                    int speedInt = Math.round(speed);
                    speedText.setText(Text.SPEED_INDICATOR + " ▶");
                    speedText.setTextColor(0xFFFFFFFF);
                    speedText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                    speedText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                    speedText.setGravity(Gravity.CENTER);

                    LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    subParams.topMargin = dp(activity, 10);
                    root.addView(speedText, subParams);

                    // Touch listener for swipe down or click to lock
                    pill.setOnTouchListener(new View.OnTouchListener() {
                        private float startY = 0f;

                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            switch (event.getAction()) {
                                case MotionEvent.ACTION_DOWN:
                                    startY = event.getRawY();
                                    return true;
                                case MotionEvent.ACTION_MOVE:
                                    float dy = event.getRawY() - startY;
                                    if (dy > dp(activity, 24)) { // Drag down gesture
                                        toggleLock(activity, lockIcon, pillText);
                                        startY = Float.MAX_VALUE; // trigger once
                                        return true;
                                    }
                                    break;
                                case MotionEvent.ACTION_UP:
                                    float diff = event.getRawY() - startY;
                                    if (diff < dp(activity, 20)) { // Tap
                                        toggleLock(activity, lockIcon, pillText);
                                        return true;
                                    }
                                    break;
                            }
                            return false;
                        }
                    });

                    dialog.setContentView(root);
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();

                    WindowManager.LayoutParams params = window.getAttributes();
                    params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                    params.y = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.08f);
                    window.setAttributes(params);

                    sActiveDialog = dialog;

                    // Auto dismiss after 6 seconds if not locked
                    MAIN.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!sIsLocked && sActiveDialog == dialog) {
                                dismiss();
                            }
                        }
                    }, 6000);

                } catch (Throwable error) {
                    Diary.note("SpeedOverlay.show: " + error);
                }
            }
        });
    }

    private static void toggleLock(Activity activity, LockIconView lockIcon, TextView pillText) {
        sIsLocked = !sIsLocked;
        lockIcon.setLocked(sIsLocked);
        pillText.setText(sIsLocked ? Text.SPEED_LOCKED : Text.SPEED_DOWN_LOCK);
        if (!sIsLocked) {
            MAIN.postDelayed(new Runnable() {
                @Override
                public void run() {
                    dismiss();
                }
            }, 600);
        }
    }

    public static void dismiss() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Dialog d = sActiveDialog;
                    sActiveDialog = null;
                    if (d != null && d.isShowing()) {
                        d.dismiss();
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    // ------------------------------------------------ Padlock Vector View

    static class LockIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean isLocked;

        public LockIconView(Context context, int color, boolean locked) {
            super(context);
            this.paint.setColor(color);
            this.isLocked = locked;
        }

        public void setLocked(boolean locked) {
            this.isLocked = locked;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();

            // Lock Body (bottom rounded rectangle)
            paint.setStyle(Paint.Style.FILL);
            RectF body = new RectF(w * 0.18f, h * 0.42f, w * 0.82f, h * 0.90f);
            canvas.drawRoundRect(body, dp(getContext(), 2.5f), dp(getContext(), 2.5f), paint);

            // Shackle (curved arch)
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 2.0f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            RectF arch = new RectF(w * 0.30f, h * 0.12f, w * 0.70f, h * 0.55f);
            if (isLocked) {
                // Closed arch
                canvas.drawArc(arch, 180, 180, false, paint);
                canvas.drawLine(w * 0.30f, h * 0.34f, w * 0.30f, h * 0.44f, paint);
                canvas.drawLine(w * 0.70f, h * 0.34f, w * 0.70f, h * 0.44f, paint);
            } else {
                // Slightly open arch
                canvas.drawArc(arch, 190, 170, false, paint);
                canvas.drawLine(w * 0.30f, h * 0.34f, w * 0.30f, h * 0.44f, paint);
                canvas.drawLine(w * 0.70f, h * 0.28f, w * 0.70f, h * 0.38f, paint);
            }

            // Keyhole
            paint.setStyle(Paint.Style.FILL);
            int origColor = paint.getColor();
            paint.setColor(Accent.colour()); // cutout tint
            canvas.drawCircle(w * 0.50f, h * 0.62f, dp(getContext(), 1.5f), paint);
            canvas.drawRect(w * 0.46f, h * 0.62f, w * 0.54f, h * 0.74f, paint);
            paint.setColor(origColor);
        }
    }

    private static int onColor(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 150 ? 0xFF1C1C1E : 0xFFFFFFFF;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}

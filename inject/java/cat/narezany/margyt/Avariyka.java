package cat.narezany.margyt;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Random;

/**
 * A silly thing: the "emergency update" window old TikTok mods put over everything
 * to drive people into their Telegram channels, now and then, in the middle of the
 * screen, with a cross that does nothing for five seconds. A joke at their expense;
 * nothing in it opens anything.
 */
final class Avariyka {

    private Avariyka() {}

    static final String KEY = "fun_avariyka";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Random RANDOM = new Random();
    private static WeakReference<Activity> where = new WeakReference<>(null);
    private static WeakReference<Dialog> open = new WeakReference<>(null);
    private static final Runnable SHOW = Avariyka::show;

    static boolean isOn(Context c) {
        return c.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false);
    }

    static void set(Context c, boolean on) {
        c.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply();
        if (!on) MAIN.removeCallbacks(SHOW);
    }

    /** Each TikTok screen that comes up: the next window is set for a minute or two from now. */
    static void resumed(Activity activity) {
        if (activity == null || activity.getClass().getName().startsWith("cat.narezany.margyt.")) return;
        if (!isOn(activity)) return;
        where = new WeakReference<>(activity);
        MAIN.removeCallbacks(SHOW);
        MAIN.postDelayed(SHOW, 60_000L + RANDOM.nextInt(90_000));
    }

    private static void show() {
        Activity activity = where.get();
        if (activity == null || activity.isFinishing() || !isOn(activity)) return;
        if (!activity.hasWindowFocus() || Watch.here() != activity) { MAIN.postDelayed(SHOW, 20_000L); return; }
        Dialog shown = open.get();
        if (shown != null && shown.isShowing()) return;
        try {
            Dialog dialog = make(activity);
            open = new WeakReference<>(dialog);
            dialog.show();
        } catch (Throwable error) {
            Diary.note("avariyka: " + error);
        }
        MAIN.postDelayed(SHOW, 75_000L + RANDOM.nextInt(105_000));
    }

    private static Dialog make(Activity c) {
        final Dialog dialog = new Dialog(c);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.6f);
        }
        float unit = c.getResources().getDisplayMetrics().density;

        FrameLayout frame = new FrameLayout(c);
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(px(unit, 18), px(unit, 16), px(unit, 18), px(unit, 18));
        GradientDrawable back = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFFFF35C, 0xFFFFC300});
        back.setCornerRadius(px(unit, 14));
        back.setStroke(px(unit, 4), 0xFFE00000);
        card.setBackground(back);

        TextView alarm = new TextView(c);
        alarm.setText("⚠️ СРОЧНО ⚠️ АВАРИЙНОЕ ОБНОВЛЕНИЕ ⚠️");
        alarm.setTextColor(Color.WHITE);
        alarm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        alarm.setTypeface(Typeface.DEFAULT_BOLD);
        alarm.setGravity(Gravity.CENTER);
        alarm.setPadding(px(unit, 6), px(unit, 4), px(unit, 6), px(unit, 4));
        alarm.setBackgroundColor(0xFFE00000);
        card.addView(alarm, new LinearLayout.LayoutParams(-1, -2));
        android.animation.ObjectAnimator blink = android.animation.ObjectAnimator.ofFloat(alarm, "alpha", 1f, 0.25f);
        blink.setDuration(380); blink.setRepeatCount(android.animation.ValueAnimator.INFINITE); blink.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        blink.start();

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView words = new TextView(c);
        words.setText("МЫ ЖРЁМ ГОВНО!!! ВЫШЛО ОБНОВЛЕНИЕ MARGYT 6.7.6.7!!! БЫСТРЕЕ СКАЧИВАЙ В НАШЕМ ТЕЛЕГРАМ КАНАЛЕ @margytiktok ИНАЧЕ ТВОЙ АККАУНТ ЗАБАНЯТ НАВСЕГДА!!!");
        words.setTextColor(0xFFC40000);
        words.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        words.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        words.setLineSpacing(0, 1.05f);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1f);
        left.rightMargin = px(unit, 12);
        row.addView(words, left);
        ImageView logo = new ImageView(c);
        try {
            android.graphics.drawable.Drawable icon = c.getPackageManager().getApplicationIcon(c.getPackageName());
            logo.setImageDrawable(icon);
        } catch (Throwable ignored) {
            logo.setImageBitmap(Badges.note());
        }
        row.addView(logo, new LinearLayout.LayoutParams(px(unit, 72), px(unit, 72)));
        LinearLayout.LayoutParams rowWhere = new LinearLayout.LayoutParams(-1, -2);
        rowWhere.topMargin = px(unit, 14);
        card.addView(row, rowWhere);

        TextView button = new TextView(c);
        button.setText("📥 СКАЧАТЬ 6.7.6.7 (ОБЯЗАТЕЛЬНО)");
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, px(unit, 12), 0, px(unit, 12));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xFF00A82D);
        pill.setCornerRadius(px(unit, 10));
        button.setBackground(pill);
        // the button is the joke too: it goes nowhere
        button.setOnClickListener(v -> button.setText("😐 ЭТО ШУТКА, ЭТО АВАРИЙКА ИЗ СМЕШНЯВОК"));
        LinearLayout.LayoutParams buttonWhere = new LinearLayout.LayoutParams(-1, -2);
        buttonWhere.topMargin = px(unit, 14);
        card.addView(button, buttonWhere);

        FrameLayout.LayoutParams cardWhere = new FrameLayout.LayoutParams(-1, -2);
        cardWhere.topMargin = px(unit, 14);
        cardWhere.rightMargin = px(unit, 10);
        frame.addView(card, cardWhere);

        // the cross: grey and counting down for five seconds, and only then of any use
        final TextView cross = new TextView(c);
        cross.setGravity(Gravity.CENTER);
        cross.setTextColor(Color.WHITE);
        cross.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        cross.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(0xFF8A8A8A);
        cross.setBackground(dot);
        FrameLayout.LayoutParams crossWhere = new FrameLayout.LayoutParams(px(unit, 34), px(unit, 34), Gravity.TOP | Gravity.END);
        frame.addView(cross, crossWhere);
        final int[] left5 = {5};
        cross.setText("5");
        Runnable tick = new Runnable() {
            @Override public void run() {
                if (!dialog.isShowing()) return;
                left5[0]--;
                if (left5[0] > 0) { cross.setText(String.valueOf(left5[0])); MAIN.postDelayed(this, 1000); return; }
                cross.setText("✕");
                dot.setColor(0xFF222222);
                cross.setOnClickListener(v -> { blink.cancel(); dialog.dismiss(); });
            }
        };
        MAIN.postDelayed(tick, 1000);

        dialog.setContentView(frame);
        if (window != null) {
            android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.width = Math.min(px(unit, 360), c.getResources().getDisplayMetrics().widthPixels - px(unit, 32));
            params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }
        return dialog;
    }

    private static int px(float unit, int dp) { return Math.round(dp * unit); }
}

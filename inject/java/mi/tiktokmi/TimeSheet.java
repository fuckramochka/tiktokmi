package mi.tiktokmi;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.ss.android.ugc.aweme.feed.model.Aweme;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Publication Date & Time Bottom Sheet ("Точное время публикации").
 *
 * Displays exact date, second-accurate time, relative age, timezone, UTC time,
 * and technical Aweme identifiers.
 * Follows the MD3 Material You design language matching photo_2026-10-10_11-56-01.jpg.
 */
public final class TimeSheet {

    private TimeSheet() {}

    public static void show(Context context, Aweme aweme) {
        if (context == null) return;
        long timeSec = 0;
        String aid = "0";
        if (aweme != null) {
            try {
                timeSec = aweme.getCreateTime();
                aid = aweme.getAid();
            } catch (Throwable ignored) {
            }
        }
        if (timeSec <= 0) {
            timeSec = System.currentTimeMillis() / 1000L;
        }
        show(context, timeSec, aid);
    }

    public static void show(final Context context, final long timeSec, final String awemeId) {
        if (context == null) return;
        try {
            final Skin skin = Skin.remembered(context);
            final Dialog dialog = new Dialog(context);
            Window window = dialog.getWindow();
            if (window != null) {
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.6f);
            }

            final long timeMs = timeSec * 1000L;
            final Date date = new Date(timeMs);
            final Locale locale = Locale.getDefault();

            // Date formats
            // E.g. "8 октября 2026 г."
            SimpleDateFormat titleFormat = new SimpleDateFormat("d MMMM yyyy 'г.'", locale);
            final String dateTitle = titleFormat.format(date);

            // E.g. "Четверг, 14:44:52"
            SimpleDateFormat subFormat = new SimpleDateFormat("EEEE, HH:mm:ss", locale);
            String dayAndTime = capitalize(subFormat.format(date));

            // Relative age: e.g. "1 день 23 часа назад"
            final String relativeAge = formatRelativeAge(timeMs);

            // Row 1: "08.10.2026 14:44:52"
            SimpleDateFormat fullFmt = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", locale);
            final String fullDateTime = fullFmt.format(date);

            // Row 2: "14:44:52"
            SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", locale);
            final String exactTime = timeFmt.format(date);

            // Row 3: "Четверг"
            SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE", locale);
            final String dayOfWeek = capitalize(dayFmt.format(date));

            // Row 4: "UTC+03:00 · Europe/Moscow"
            TimeZone tz = TimeZone.getDefault();
            final String timezoneStr = formatTimezone(tz, timeMs);

            // Row 5: "08.10.2026 11:44:52 UTC"
            SimpleDateFormat utcFmt = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss 'UTC'", Locale.US);
            utcFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            final String utcTime = utcFmt.format(date);

            final String safeAid = awemeId != null ? awemeId : "0";

            ScrollView scroll = new ScrollView(context);
            scroll.setVerticalScrollBarEnabled(false);

            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20));

            GradientDrawable rootBg = new GradientDrawable();
            rootBg.setColor(skin.card);
            rootBg.setCornerRadii(new float[]{
                    dp(context, 28), dp(context, 28),
                    dp(context, 28), dp(context, 28),
                    0, 0,
                    0, 0
            });
            root.setBackground(rootBg);
            scroll.addView(root);

            // ------------------------------------------------ Header
            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            // Clock badge
            FrameLayout badge = new FrameLayout(context);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(Accent.colour());
            badgeBg.setCornerRadius(dp(context, 16));
            badge.setBackground(badgeBg);

            ClockIconView clockIcon = new ClockIconView(context, onColor(Accent.colour()));
            FrameLayout.LayoutParams badgeIconLp = new FrameLayout.LayoutParams(
                    dp(context, 28), dp(context, 28), Gravity.CENTER);
            badge.addView(clockIcon, badgeIconLp);
            header.addView(badge, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));

            LinearLayout headerText = new LinearLayout(context);
            headerText.setOrientation(LinearLayout.VERTICAL);
            headerText.setPadding(dp(context, 14), 0, 0, 0);

            TextView titleView = new TextView(context);
            titleView.setText(dateTitle);
            titleView.setTextColor(skin.text);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            headerText.addView(titleView);

            TextView subView = new TextView(context);
            subView.setText(dayAndTime);
            subView.setTextColor(blend(Accent.colour(), skin.text, 0.4f));
            subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            headerText.addView(subView);
            header.addView(headerText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            root.addView(header);

            // Relative age pill tag
            TextView relPill = new TextView(context);
            relPill.setText(relativeAge);
            relPill.setTextColor(blend(Accent.colour(), 0xFF88FF88, 0.2f));
            relPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            relPill.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));

            GradientDrawable relBg = new GradientDrawable();
            relBg.setColor(blend(skin.card, Accent.colour(), 0.20f));
            relBg.setCornerRadius(dp(context, 12));
            relPill.setBackground(relBg);

            LinearLayout.LayoutParams relParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            relParams.topMargin = dp(context, 14);
            root.addView(relPill, relParams);

            // ------------------------------------------------ Section 1: Точное время
            TextView sec1 = sectionHeader(context, skin, Text.EXACT_TIME_HEADER);
            LinearLayout.LayoutParams sec1Params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sec1Params.topMargin = dp(context, 18);
            sec1Params.bottomMargin = dp(context, 8);
            root.addView(sec1, sec1Params);

            LinearLayout cardTime = createGroupCard(context, skin);
            cardTime.addView(createRow(context, skin, new ArticleIconView(context, skin.text),
                    fullDateTime, Text.DATE_TIME_PUB, false));
            cardTime.addView(createRow(context, skin, new ClockIconView(context, skin.text),
                    exactTime, Text.TIME_TO_SECOND, false));
            cardTime.addView(createRow(context, skin, new WaveIconView(context, skin.text),
                    dayOfWeek, Text.DAY_OF_WEEK, false));
            cardTime.addView(createRow(context, skin, new GlobeIconView(context, skin.text),
                    timezoneStr, Text.TIMEZONE, false));
            cardTime.addView(createRow(context, skin, new WorldIconView(context, skin.text),
                    utcTime, Text.GMT_TIME, true));
            root.addView(cardTime);

            // ------------------------------------------------ Section 2: Технические данные
            TextView sec2 = sectionHeader(context, skin, Text.TECH_DATA);
            LinearLayout.LayoutParams sec2Params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sec2Params.topMargin = dp(context, 16);
            sec2Params.bottomMargin = dp(context, 8);
            root.addView(sec2, sec2Params);

            LinearLayout cardTech = createGroupCard(context, skin);
            cardTech.addView(createRow(context, skin, new HashIconView(context, skin.text),
                    safeAid, Text.PUB_ID, false));
            cardTech.addView(createRow(context, skin, new TimerIconView(context, skin.text),
                    String.valueOf(timeSec), Text.UNIX_TIMESTAMP, true));
            root.addView(cardTech);

            // ------------------------------------------------ Actions
            // "Скопировать всё"
            TextView copyBtn = new TextView(context);
            copyBtn.setText(Text.COPY_ALL);
            copyBtn.setTextColor(onColor(Accent.colour()));
            copyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            copyBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            copyBtn.setGravity(Gravity.CENTER);
            copyBtn.setPadding(0, dp(context, 14), 0, dp(context, 14));

            GradientDrawable copyBg = new GradientDrawable();
            copyBg.setColor(Accent.colour());
            copyBg.setCornerRadius(dp(context, 26));
            copyBtn.setBackground(copyBg);
            copyBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(Text.DATE_TIME_PUB).append(": ").append(fullDateTime).append("\n");
                    sb.append(Text.TIME_TO_SECOND).append(": ").append(exactTime).append("\n");
                    sb.append(Text.DAY_OF_WEEK).append(": ").append(dayOfWeek).append("\n");
                    sb.append(Text.TIMEZONE).append(": ").append(timezoneStr).append("\n");
                    sb.append(Text.GMT_TIME).append(": ").append(utcTime).append("\n");
                    sb.append(Text.PUB_ID).append(": ").append(safeAid).append("\n");
                    sb.append(Text.UNIX_TIMESTAMP).append(": ").append(timeSec);

                    try {
                        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("TikTok MI Time", sb.toString()));
                            Toast.makeText(context, Text.COPIED_TO_CLIPBOARD, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Throwable error) {
                        Diary.note("copy error: " + error);
                    }
                }
            });

            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            copyParams.topMargin = dp(context, 20);
            root.addView(copyBtn, copyParams);

            // "Закрыть"
            TextView closeBtn = new TextView(context);
            closeBtn.setText(Text.CLOSE);
            closeBtn.setTextColor(skin.text);
            closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            closeBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            closeBtn.setGravity(Gravity.CENTER);
            closeBtn.setPadding(0, dp(context, 14), 0, dp(context, 14));

            GradientDrawable closeBg = new GradientDrawable();
            closeBg.setColor(blend(skin.card, 0xFFFFFFFF, 0.08f));
            closeBg.setCornerRadius(dp(context, 26));
            closeBtn.setBackground(closeBg);
            closeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });

            LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            closeParams.topMargin = dp(context, 10);
            root.addView(closeBtn, closeParams);

            dialog.setContentView(scroll);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.gravity = Gravity.BOTTOM;
                window.setAttributes(params);
            }
        } catch (Throwable error) {
            Diary.note("TimeSheet.show: " + error);
        }
    }

    private static TextView sectionHeader(Context context, Skin skin, String title) {
        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextColor(skin.muted());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(context, 4), 0, dp(context, 4), 0);
        return tv;
    }

    private static LinearLayout createGroupCard(Context context, Skin skin) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(blend(skin.card, 0xFFFFFFFF, 0.05f));
        bg.setCornerRadius(dp(context, 18));
        group.setBackground(bg);
        group.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        return group;
    }

    private static View createRow(Context context, Skin skin, View iconView,
                                  String mainText, String subText, boolean isLast) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 6), dp(context, 10), dp(context, 6), dp(context, 10));

        FrameLayout badge = new FrameLayout(context);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(blend(skin.card, 0xFFFFFFFF, 0.12f));
        badgeBg.setCornerRadius(dp(context, 12));
        badge.setBackground(badgeBg);

        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                dp(context, 20), dp(context, 20), Gravity.CENTER);
        badge.addView(iconView, iconLp);
        row.addView(badge, new LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(context, 12), 0, 0, 0);

        TextView title = new TextView(context);
        title.setText(mainText);
        title.setTextColor(skin.text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(title);

        TextView sub = new TextView(context);
        sub.setText(subText);
        sub.setTextColor(skin.muted());
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textCol.addView(sub);

        row.addView(textCol, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    // ------------------------------------------------ Formatter Helpers

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String formatTimezone(TimeZone tz, long timeMs) {
        int offset = tz.getOffset(timeMs);
        int hours = Math.abs(offset) / (1000 * 60 * 60);
        int mins = (Math.abs(offset) / (1000 * 60)) % 60;
        String sign = offset >= 0 ? "+" : "-";
        String utcStr = String.format(Locale.US, "UTC%s%02d:%02d", sign, hours, mins);
        String id = tz.getID();
        return utcStr + " · " + id;
    }

    private static String formatRelativeAge(long timeMs) {
        long diff = Math.max(0, System.currentTimeMillis() - timeMs);
        long sec = diff / 1000L;
        if (sec < 60) return "Только что";
        long mins = sec / 60L;
        if (mins < 60) {
            return mins + " " + pluralRu(mins, "минуту", "минуты", "минут") + " назад";
        }
        long hours = mins / 60L;
        if (hours < 24) {
            long remMins = mins % 60L;
            if (remMins == 0) {
                return hours + " " + pluralRu(hours, "час", "часа", "часов") + " назад";
            }
            return hours + " " + pluralRu(hours, "час", "часа", "часов") + " "
                    + remMins + " " + pluralRu(remMins, "минуту", "минуты", "минут") + " назад";
        }
        long days = hours / 24L;
        long remHours = hours % 24L;
        if (remHours == 0) {
            return days + " " + pluralRu(days, "день", "дня", "дней") + " назад";
        }
        return days + " " + pluralRu(days, "день", "дня", "дней") + " "
                + remHours + " " + pluralRu(remHours, "час", "часа", "часов") + " назад";
    }

    private static String pluralRu(long n, String form1, String form2, String form5) {
        n = Math.abs(n) % 100;
        long n1 = n % 10;
        if (n > 10 && n < 20) return form5;
        if (n1 > 1 && n1 < 5) return form2;
        if (n1 == 1) return form1;
        return form5;
    }

    // ------------------------------------------------ Vector Icons

    static class ClockIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public ClockIconView(Context context, int color) {
            super(context);
            paint.setColor(color);
        }
        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(cx, cy) * 0.85f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.8f));
            canvas.drawCircle(cx, cy, r, paint);
            // Hour and minute hands
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(cx, cy, cx - r * 0.45f, cy - r * 0.3f, paint);
            canvas.drawLine(cx, cy, cx + r * 0.35f, cy - r * 0.5f, paint);
            // Center pin
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, dp(getContext(), 1.6f), paint);
        }
    }

    static class ArticleIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public ArticleIconView(Context context, int color) {
            super(context);
            paint.setColor(color);
        }
        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.5f));
            RectF r = new RectF(w * 0.22f, h * 0.15f, w * 0.78f, h * 0.85f);
            canvas.drawRoundRect(r, dp(getContext(), 3), dp(getContext(), 3), paint);
            // Lines
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(w * 0.32f, h * 0.35f, w * 0.68f, h * 0.35f, paint);
            canvas.drawLine(w * 0.32f, h * 0.50f, w * 0.68f, h * 0.50f, paint);
            canvas.drawLine(w * 0.32f, h * 0.65f, w * 0.55f, h * 0.65f, paint);
        }
    }

    static class WaveIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public WaveIconView(Context context, int color) {
            super(context);
            paint.setColor(color);
        }
        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.8f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            Path p = new Path();
            p.moveTo(w * 0.20f, h * 0.50f);
            p.lineTo(w * 0.38f, h * 0.32f);
            p.lineTo(w * 0.58f, h * 0.68f);
            p.lineTo(w * 0.80f, h * 0.45f);
            canvas.drawPath(p, paint);
        }
    }

    static class GlobeIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public GlobeIconView(Context context, int color) {
            super(context);
            paint.setColor(color);
        }
        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(cx, cy) * 0.85f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.5f));
            canvas.drawCircle(cx, cy, r, paint);
            canvas.drawLine(cx - r, cy, cx + r, cy, paint);
            // Ellipse longitude
            RectF el = new RectF(cx - r * 0.48f, cy - r, cx + r * 0.48f, cy + r);
            canvas.drawOval(el, paint);
        }
    }

    static class WorldIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public WorldIconView(Context context, int color) {
            super(context);
            paint.setColor(color);
        }
        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(cx, cy) * 0.85f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.5f));
            canvas.drawCircle(cx, cy, r, paint);
            // Continents outline or meridians
            canvas.drawLine(cx, cy - r, cx, cy + r, paint);
            canvas.drawLine(cx - r, cy, cx + r, cy, paint);
        }
    }

    static class HashIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public HashIconView(Context context, int color) {
            super(context);
            paint.setColor(color);
        }
        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.8f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            // Two verticals
            canvas.drawLine(w * 0.40f, h * 0.20f, w * 0.35f, h * 0.80f, paint);
            canvas.drawLine(w * 0.65f, h * 0.20f, w * 0.60f, h * 0.80f, paint);
            // Two horizontals
            canvas.drawLine(w * 0.22f, h * 0.40f, w * 0.80f, h * 0.40f, paint);
            canvas.drawLine(w * 0.20f, h * 0.60f, w * 0.78f, h * 0.60f, paint);
        }
    }

    static class TimerIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public TimerIconView(Context context, int color) {
            super(context);
            paint.setColor(color);
        }
        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f + dp(getContext(), 1);
            float r = Math.min(cx, cy) * 0.78f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.5f));
            canvas.drawCircle(cx, cy, r, paint);
            // Top button
            canvas.drawLine(cx - dp(getContext(), 3), cy - r - dp(getContext(), 2.5f),
                    cx + dp(getContext(), 3), cy - r - dp(getContext(), 2.5f), paint);
            // Hand
            canvas.drawLine(cx, cy, cx + r * 0.5f, cy - r * 0.3f, paint);
        }
    }

    // ------------------------------------------------ Color Helpers

    private static int blend(int color1, int color2, float ratio) {
        float inv = 1f - ratio;
        int a = Math.round(Color.alpha(color1) * inv + Color.alpha(color2) * ratio);
        int r = Math.round(Color.red(color1) * inv + Color.red(color2) * ratio);
        int g = Math.round(Color.green(color1) * inv + Color.green(color2) * ratio);
        int b = Math.round(Color.blue(color1) * inv + Color.blue(color2) * ratio);
        return Color.argb(a, r, g, b);
    }

    private static int onColor(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 150 ? 0xFF1C2C24 : 0xFFFFFFFF;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}

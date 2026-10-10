package mi.tiktokmi;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import android.widget.TextView;

import com.ss.android.ugc.aweme.base.model.UrlModel;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.Video;
import com.ss.android.ugc.aweme.music.model.Music;

import java.util.List;

/**
 * Video Quality Selection Bottom Sheet ("Выберите качество").
 *
 * Displays available resolution streams (e.g. 576p / 1080p, 480p HEVC) and audio-only extraction.
 * Follows the MD3 Material You design language with rounded cards, squircle badges,
 * and accent highlights.
 */
public final class QualitySheet {

    private QualitySheet() {}

    public interface OnQualitySelected {
        void onSelected(int index, String label);
    }

    public static void show(Context context, final Aweme aweme) {
        show(context, aweme, null);
    }

    public static void show(final Context context, final Aweme aweme, final OnQualitySelected listener) {
        if (context == null) return;
        try {
            Skin skin = Skin.remembered(context);
            final Dialog dialog = new Dialog(context);
            Window window = dialog.getWindow();
            if (window != null) {
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.6f);
            }

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

            // Title
            TextView titleView = new TextView(context);
            titleView.setText(Text.QUALITY_CHOOSE);
            titleView.setTextColor(skin.text);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 16));
            root.addView(titleView);

            // Best quality resolution estimation
            int videoHeight = 576;
            if (aweme != null && aweme.getVideo() != null) {
                int h = aweme.getVideo().getHeight();
                if (h > 0) videoHeight = h;
            }
            String bestLabel = videoHeight + "p";

            // Card 1: Best Quality (Highlighted with Accent)
            View cardBest = createQualityCard(context, skin, true, "SD", bestLabel,
                    Text.QUALITY_BEST, false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (listener != null) listener.onSelected(0, bestLabel);
                    downloadOption(context, aweme, 0);
                }
            });
            root.addView(cardBest);

            // Card 2: Lower / HEVC Quality
            View cardHevc = createQualityCard(context, skin, false, "SD", "480p · HEVC",
                    Text.QUALITY_WORST, false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (listener != null) listener.onSelected(1, "480p · HEVC");
                    downloadOption(context, aweme, 1);
                }
            });
            LinearLayout.LayoutParams hevcParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hevcParams.topMargin = dp(context, 10);
            root.addView(cardHevc, hevcParams);

            // Card 3: Audio Only ("Только звук")
            View cardAudio = createQualityCard(context, skin, false, null, Text.QUALITY_AUDIO_ONLY,
                    null, true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (listener != null) listener.onSelected(2, Text.QUALITY_AUDIO_ONLY);
                    downloadOption(context, aweme, 2);
                }
            });
            LinearLayout.LayoutParams audioParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            audioParams.topMargin = dp(context, 10);
            root.addView(cardAudio, audioParams);

            // Bottom pill button: "Понятно"
            TextView gotItBtn = new TextView(context);
            gotItBtn.setText(Text.GOT_IT);
            gotItBtn.setTextColor(skin.text);
            gotItBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            gotItBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            gotItBtn.setGravity(Gravity.CENTER);
            gotItBtn.setPadding(0, dp(context, 14), 0, dp(context, 14));

            GradientDrawable pillBg = new GradientDrawable();
            // Dark card pill background with subtle green/accent tint
            int tintBg = blend(skin.card, Accent.colour(), 0.15f);
            pillBg.setColor(tintBg);
            pillBg.setCornerRadius(dp(context, 26));
            gotItBtn.setBackground(pillBg);
            gotItBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnParams.topMargin = dp(context, 18);
            root.addView(gotItBtn, btnParams);

            dialog.setContentView(root);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.gravity = Gravity.BOTTOM;
                window.setAttributes(params);
            }
        } catch (Throwable error) {
            Diary.note("QualitySheet.show: " + error);
        }
    }

    private static View createQualityCard(Context context, Skin skin, boolean highlighted,
                                          String badgeText, String title, String subtitle,
                                          boolean isAudio, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));

        GradientDrawable bg = new GradientDrawable();
        int accent = Accent.colour();
        if (highlighted) {
            bg.setColor(accent);
        } else {
            bg.setColor(blend(skin.card, 0xFFFFFFFF, 0.05f));
        }
        bg.setCornerRadius(dp(context, 18));
        row.setBackground(bg);
        row.setOnClickListener(onClick);

        // Squircle badge on left
        FrameLayout badgeContainer = new FrameLayout(context);
        GradientDrawable badgeBg = new GradientDrawable();
        if (highlighted) {
            badgeBg.setColor(blend(accent, 0xFFFFFFFF, 0.22f));
        } else {
            badgeBg.setColor(blend(skin.card, 0xFFFFFFFF, 0.12f));
        }
        badgeBg.setCornerRadius(dp(context, 14));
        badgeContainer.setBackground(badgeBg);

        int iconColor = highlighted ? onColor(accent) : skin.text;

        if (isAudio) {
            MusicNoteView noteView = new MusicNoteView(context, iconColor);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(context, 22), dp(context, 22), Gravity.CENTER);
            badgeContainer.addView(noteView, lp);
        } else {
            SdBadgeView sdView = new SdBadgeView(context, badgeText != null ? badgeText : "SD", iconColor);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(context, 26), dp(context, 18), Gravity.CENTER);
            badgeContainer.addView(sdView, lp);
        }
        row.addView(badgeContainer, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));

        // Text column
        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(context, 14), 0, dp(context, 8), 0);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(highlighted ? onColor(accent) : skin.text);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(titleView);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subView = new TextView(context);
            subView.setText(subtitle);
            subView.setTextColor(highlighted ? blend(onColor(accent), accent, 0.25f) : skin.muted());
            subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            textCol.addView(subView);
        }
        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Download arrow icon on right
        DownloadArrowView arrowView = new DownloadArrowView(context, iconColor);
        row.addView(arrowView, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));

        return row;
    }

    private static void downloadOption(final Context context, final Aweme aweme, final int optionIndex) {
        if (aweme == null) return;
        Video video = aweme.getVideo();

        UrlModel targetUrl = null;
        String mime = "video/mp4";
        String ext = "mp4";
        String prefix = "video";

        if (optionIndex == 0) {
            // Best quality video
            if (video != null) {
                targetUrl = video.getDownloadNoWatermarkAddr();
                if (!usable(targetUrl)) targetUrl = video.getPlayAddr();
                if (!usable(targetUrl)) targetUrl = video.getDownloadAddr();
            }
        } else if (optionIndex == 1) {
            // HEVC / Standard
            if (video != null) {
                targetUrl = video.getPlayAddrBytevc1();
                if (!usable(targetUrl)) targetUrl = video.getDownloadAddr();
                if (!usable(targetUrl)) targetUrl = video.getPlayAddr();
            }
        } else if (optionIndex == 2) {
            // Audio only
            mime = "audio/mp3";
            ext = "mp3";
            prefix = "audio";
            Music music = aweme.getMusic();
            if (music != null) targetUrl = music.getPlayUrl();
            if (!usable(targetUrl) && video != null) targetUrl = video.getPlayAddr();
        }

        if (targetUrl == null || !usable(targetUrl)) {
            Screen.say(Text.DOWNLOAD_FAILED);
            return;
        }

        final String downloadUrl = (String) targetUrl.getUrlList().get(0);
        final String aid = aweme.getAid();
        final String fileName = Gallery.name(prefix + "_" + aid, downloadUrl, ext);
        final String finalMime = mime;
        final boolean isAudio = optionIndex == 2;

        Screen.progress(isAudio ? "Скачивание звука..." : "Скачивание...", -1);
        Net.away("quality-download", new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] data = Net.bytes(downloadUrl);
                    Screen.progressGone();
                    if (data != null && data.length > 0) {
                        String saved = Gallery.save(context, data, fileName, finalMime);
                        if (saved != null) {
                            Screen.say(isAudio ? Text.AUDIO_SAVED : Text.DOWNLOAD_SAVED);
                        } else {
                            Screen.say(Text.DOWNLOAD_FAILED);
                        }
                    } else {
                        Screen.say(Text.DOWNLOAD_FAILED);
                    }
                } catch (Throwable error) {
                    Screen.progressGone();
                    Diary.note("quality download error: " + error);
                    Screen.say(Text.DOWNLOAD_FAILED);
                }
            }
        });
    }

    private static boolean usable(UrlModel url) {
        if (url == null) return false;
        try {
            List urls = url.getUrlList();
            return urls != null && !urls.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ------------------------------------------------ Custom Vector Views

    static class SdBadgeView extends View {
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String text;
        private final RectF rect = new RectF();

        public SdBadgeView(Context context, String text, int color) {
            super(context);
            this.text = text != null ? text : "SD";
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(context, 1.5f));
            borderPaint.setColor(color);

            textPaint.setColor(color);
            textPaint.setTextSize(sp(context, 9.5f));
            textPaint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float pad = dp(getContext(), 1.5f);
            rect.set(pad, pad, getWidth() - pad, getHeight() - pad);
            canvas.drawRoundRect(rect, dp(getContext(), 3.5f), dp(getContext(), 3.5f), borderPaint);

            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float y = (getHeight() - fm.ascent - fm.descent) / 2f;
            canvas.drawText(text, getWidth() / 2f, y, textPaint);
        }
    }

    static class MusicNoteView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public MusicNoteView(Context context, int color) {
            super(context);
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();

            // Head (tilted ellipse)
            canvas.save();
            canvas.rotate(-20f, w * 0.42f, h * 0.72f);
            RectF head = new RectF(w * 0.22f, h * 0.62f, w * 0.58f, h * 0.85f);
            canvas.drawOval(head, paint);
            canvas.restore();

            // Stem
            paint.setStrokeWidth(dp(getContext(), 2.2f));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(w * 0.52f, h * 0.72f, w * 0.52f, h * 0.22f, paint);

            // Flag/hook at top right
            canvas.drawLine(w * 0.52f, h * 0.22f, w * 0.72f, h * 0.35f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    static class DownloadArrowView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public DownloadArrowView(Context context, int color) {
            super(context);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(context, 2f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float s = dp(getContext(), 6.5f);

            // Downward arrow stem
            canvas.drawLine(cx, cy - s, cx, cy + s * 0.5f, paint);
            // Arrow heads
            canvas.drawLine(cx - s * 0.6f, cy, cx, cy + s * 0.5f, paint);
            canvas.drawLine(cx + s * 0.6f, cy, cx, cy + s * 0.5f, paint);
            // Bottom tray line
            canvas.drawLine(cx - s * 0.9f, cy + s * 1.15f, cx + s * 0.9f, cy + s * 1.15f, paint);
        }
    }

    // ------------------------------------------------ Utilities

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

    private static float sp(Context context, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                context.getResources().getDisplayMetrics());
    }
}

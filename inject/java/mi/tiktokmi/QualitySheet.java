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
import java.util.Locale;

/**
 * Video Quality Selection Bottom Sheet ("Качество загрузки").
 *
 * Displays available resolution streams (e.g. 720p HEVC with [Лучшее] badge,
 * 576p variants, web source fallback, and audio extraction).
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
            root.setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 20));

            GradientDrawable rootBg = new GradientDrawable();
            rootBg.setColor(skin.card);
            rootBg.setCornerRadii(new float[]{
                    dp(context, 28), dp(context, 28),
                    dp(context, 28), dp(context, 28),
                    0, 0,
                    0, 0
            });
            root.setBackground(rootBg);

            // 1. Drag handle bar (MD3 pill)
            View handle = new View(context);
            GradientDrawable handleBg = new GradientDrawable();
            handleBg.setColor((skin.text & 0x00FFFFFF) | 0x33000000);
            handleBg.setCornerRadius(dp(context, 3));
            handle.setBackground(handleBg);
            LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                    dp(context, 36), dp(context, 4));
            handleParams.gravity = Gravity.CENTER_HORIZONTAL;
            handleParams.bottomMargin = dp(context, 16);
            root.addView(handle, handleParams);

            // 2. Centered Title ("Качество загрузки")
            TextView titleView = new TextView(context);
            titleView.setText(Text.QUALITY_DOWNLOAD_TITLE);
            titleView.setTextColor(skin.muted());
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
            titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            titleView.setGravity(Gravity.CENTER);
            titleView.setPadding(0, 0, 0, dp(context, 16));
            root.addView(titleView);

            // Resolution info estimation
            int videoHeight = 720;
            int videoWidth = 1280;
            int durationSec = 15;
            if (aweme != null && aweme.getVideo() != null) {
                int h = aweme.getVideo().getHeight();
                int w = aweme.getVideo().getWidth();
                int d = aweme.getVideo().getDuration();
                if (h > 0) videoHeight = h;
                if (w > 0) videoWidth = w;
                if (d > 0) durationSec = Math.max(1, d / 1000);
            }

            String bestResLabel = videoHeight + "p  HEVC";
            double estMb = Math.round((durationSec * 0.45f) * 10.0) / 10.0;
            String bestMeta = (estMb > 1.0 ? String.format(Locale.US, "%.1f MB", estMb) : "348.5 KB") + ", 458 Kbps";

            // Card 1: Best Quality (Tinted with TikTok Red, "Лучшее" badge)
            View cardBest = createQualityCard(context, skin, true, bestResLabel,
                    bestMeta, Text.QUALITY_BEST_BADGE, false, false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (listener != null) listener.onSelected(0, bestResLabel);
                    downloadOption(context, aweme, 0);
                }
            });
            root.addView(cardBest);

            // Card 2: Lower / HEVC variants (e.g. "576p  H264/HEVC", "4 варианта >")
            String varLabel = Math.min(576, videoHeight) + "p  H264/HEVC";
            String varSub = "4 " + Text.QUALITY_VARIANTS_LABEL;
            View cardVariants = createQualityCard(context, skin, false, varLabel,
                    varSub, null, true, false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (listener != null) listener.onSelected(1, varLabel);
                    downloadOption(context, aweme, 1);
                }
            });
            LinearLayout.LayoutParams varParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            varParams.topMargin = dp(context, 10);
            root.addView(cardVariants, varParams);

            // Card 3: Web-version source ("Веб-версия  MP4")
            String webLabel = Text.QUALITY_WEB_SOURCE + "  MP4";
            View cardWeb = createQualityCard(context, skin, false, webLabel,
                    bestMeta, null, false, false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (listener != null) listener.onSelected(2, webLabel);
                    downloadOption(context, aweme, 2);
                }
            });
            LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            webParams.topMargin = dp(context, 10);
            root.addView(cardWeb, webParams);

            // Card 4: Audio Only ("Только звук  MP3")
            String audioLabel = Text.QUALITY_AUDIO_ONLY + "  MP3";
            double audioMb = Math.round((durationSec * 0.16f) * 10.0) / 10.0;
            String audioMeta = (audioMb > 1.0 ? String.format(Locale.US, "%.1f MB", audioMb) : "348.5 KB") + ", 458 Kbps";
            View cardAudio = createQualityCard(context, skin, false, audioLabel,
                    audioMeta, null, false, true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (listener != null) listener.onSelected(3, audioLabel);
                    downloadOption(context, aweme, 3);
                }
            });
            LinearLayout.LayoutParams audioParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            audioParams.topMargin = dp(context, 10);
            root.addView(cardAudio, audioParams);

            // Bottom button: "Отмена" (Cancel)
            TextView cancelBtn = new TextView(context);
            cancelBtn.setText(Text.QUALITY_CANCEL);
            cancelBtn.setTextColor(skin.text);
            cancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            cancelBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            cancelBtn.setGravity(Gravity.CENTER);
            cancelBtn.setPadding(0, dp(context, 14), 0, dp(context, 14));
            cancelBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnParams.topMargin = dp(context, 14);
            root.addView(cancelBtn, btnParams);

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

    private static View createQualityCard(Context context, Skin skin, boolean isHighlighted,
                                          String title, String subtitle, String badgeText,
                                          boolean hasChevron, boolean isAudio,
                                          View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 14));

        GradientDrawable bg = new GradientDrawable();
        int accent = Accent.colour();
        if (isHighlighted) {
            // Soft red container highlight
            int tintBg = blend(skin.card, accent, skin.dark() ? 0.22f : 0.12f);
            bg.setColor(tintBg);
            bg.setStroke(dp(context, 1), blend(accent, skin.card, 0.40f));
        } else {
            bg.setColor(blend(skin.card, 0xFFFFFFFF, skin.dark() ? 0.05f : -0.04f));
            bg.setStroke(dp(context, 1), (skin.text & 0x00FFFFFF) | 0x12000000);
        }
        bg.setCornerRadius(dp(context, 18));
        row.setBackground(bg);
        row.setOnClickListener(onClick);

        // Text column (Left)
        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(0, 0, dp(context, 8), 0);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(isHighlighted ? accent : skin.text);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        textCol.addView(titleView);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subView = new TextView(context);
            subView.setText(subtitle);
            subView.setTextColor(skin.muted());
            subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            textCol.addView(subView);
        }
        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Right side: Badge, Chevron, or Icon
        if (badgeText != null && !badgeText.isEmpty()) {
            TextView badgeView = new TextView(context);
            badgeView.setText(badgeText);
            badgeView.setTextColor(0xFFFFFFFF);
            badgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            badgeView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            badgeView.setPadding(dp(context, 12), dp(context, 5), dp(context, 12), dp(context, 5));

            GradientDrawable pillBadge = new GradientDrawable();
            pillBadge.setColor(accent);
            pillBadge.setCornerRadius(dp(context, 12));
            badgeView.setBackground(pillBadge);
            row.addView(badgeView);
        } else if (hasChevron) {
            TextView chevron = new TextView(context);
            chevron.setText("›");
            chevron.setTextColor(skin.muted());
            chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            chevron.setPadding(dp(context, 4), 0, 0, 0);
            row.addView(chevron);
        } else if (isAudio) {
            MusicNoteView noteView = new MusicNoteView(context, skin.muted());
            row.addView(noteView, new LinearLayout.LayoutParams(dp(context, 22), dp(context, 22)));
        } else {
            DownloadArrowView arrowView = new DownloadArrowView(context, skin.muted());
            row.addView(arrowView, new LinearLayout.LayoutParams(dp(context, 22), dp(context, 22)));
        }

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
            // Best quality video (unstamped / play stream)
            if (video != null) {
                targetUrl = video.getDownloadNoWatermarkAddr();
                if (!usable(targetUrl)) targetUrl = video.getPlayAddr();
                if (!usable(targetUrl)) targetUrl = video.getDownloadAddr();
            }
        } else if (optionIndex == 1) {
            // Standard / HEVC
            if (video != null) {
                targetUrl = video.getPlayAddrBytevc1();
                if (!usable(targetUrl)) targetUrl = video.getDownloadAddr();
                if (!usable(targetUrl)) targetUrl = video.getPlayAddr();
            }
        } else if (optionIndex == 2) {
            // Web-version source fallback
            if (video != null) {
                targetUrl = video.getPlayAddrH264();
                if (!usable(targetUrl)) targetUrl = video.getPlayAddr();
                if (!usable(targetUrl)) targetUrl = video.getDownloadNoWatermarkAddr();
            }
            prefix = "web_video";
        } else if (optionIndex == 3) {
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
        final boolean isAudio = optionIndex == 3;

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
        return (r * 299 + g * 587 + b * 114) / 1000 > 150 ? 0xFF1C1C1E : 0xFFFFFFFF;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}

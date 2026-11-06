package mi.tiktokmi;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

/**
 * Pictures at the top of a setting that show what it does, the way a phone's
 * own settings do: a phone with TikTok's feed on it for the screen saver and
 * the simple feed, a conversation with fuckramochka for the streak sticker, a
 * profile for the avatar shape. Drawn here, so they follow the setting live
 * and need nothing from the network.
 *
 * Each remembers how it was last drawn, so a picture made again after the
 * settings were rebuilt moves from there to the new state instead of jumping.
 */
final class SettingsArt {
    private SettingsArt() {}

    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);

    static float dp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    /** One number moved over a few hundred milliseconds, redrawing as it goes. */
    private static final class Eased {
        float value;
        private ValueAnimator animator;

        Eased(float value) { this.value = value; }

        void to(final View view, float target, long duration, android.animation.TimeInterpolator curve) {
            if (animator != null) animator.cancel();
            if (!Motion.enabled(view.getContext()) || Math.abs(target - value) < 0.001f) {
                value = target;
                view.invalidate();
                return;
            }
            animator = ValueAnimator.ofFloat(value, target);
            animator.setDuration(duration);
            animator.setInterpolator(curve);
            animator.addUpdateListener(a -> {
                value = (Float) a.getAnimatedValue();
                view.invalidate();
            });
            animator.start();
        }
    }

    // ================================================================ phone

    /**
     * TikTok's feed on a phone: a video, the bar across the top with LIVE on
     * the left, the buttons down the right, the caption and the bottom bar.
     */
    static final class Phone extends View {
        /**
         * How each card's phone was last drawn, by the card: the screen saver's
         * phone is faint, and that was spilling onto every other phone in the
         * settings, which then showed nothing but a video.
         * In order: ui, eye, bar, simple, liked, capsule, seek, date, rail x4.
         */
        private static final java.util.HashMap<String, float[]> MEMORY = new java.util.HashMap<String, float[]>();
        private final float[] memory;

        private final Skin skin;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final RectF box = new RectF();
        private final Path path = new Path();
        private final Eased ui, eye, bar, simple, liked, capsule, seek, date;
        private final Eased[] rails;
        private float wantUi, wantEye, wantBar, wantSimple, wantLiked, wantCapsule, wantSeek, wantDate;
        private final float[] wantRail;
        private Bitmap avatar;

        /** Told which of the buttons down the right was tapped on the picture itself. */
        interface RailTapped { void tapped(int kind); }
        private RailTapped railTapped;
        private final float[] railCentres = new float[8];

        /** The heart filled with the accent: what a liked video looks like in it. */
        Phone liked(boolean on) { wantLiked = on ? 1f : 0f; return this; }

        /** The bottom bar as a floating capsule. */
        Phone capsule(boolean on) { wantCapsule = on ? 1f : 0f; return this; }

        /** A seek bar along the bottom of the video. */
        Phone seekbar(boolean on) { wantSeek = on ? 1f : 0f; return this; }

        /** When the video was posted, beside the author's name. */
        Phone date(boolean on) { wantDate = on ? 1f : 0f; return this; }

        /** One of the four buttons down the right: like, comments, favourites, share. */
        Phone rail(int kind, boolean shown) { wantRail[kind] = shown ? 1f : 0f; return this; }

        /** Lets the buttons on the picture be tapped to hide and show them. */
        Phone onRail(RailTapped listener) {
            railTapped = listener;
            setClickable(listener != null);
            return this;
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent event) {
            if (railTapped == null) return super.onTouchEvent(event);
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) return true;
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                float reach = 12 * u;
                for (int i = 0; i < 4; i++) {
                    float dx = event.getX() - railCentres[i * 2], dy = event.getY() - railCentres[i * 2 + 1];
                    if (dx * dx + dy * dy < reach * reach) {
                        railTapped.tapped(i);
                        return true;
                    }
                }
            }
            return true;
        }

        Phone(Context context, Skin skin) {
            this(context, skin, "phone");
        }

        /** `key` names the card: each remembers its own phone. */
        Phone(Context context, Skin skin, String key) {
            super(context);
            this.skin = skin;
            float[] m = MEMORY.get(key);
            if (m == null) {
                m = new float[] {1, 0, 1, 0, 0, 0, 0, 0, 1, 1, 1, 1};
                MEMORY.put(key, m);
            }
            memory = m;
            ui = new Eased(m[0]); eye = new Eased(m[1]); bar = new Eased(m[2]); simple = new Eased(m[3]);
            liked = new Eased(m[4]); capsule = new Eased(m[5]); seek = new Eased(m[6]); date = new Eased(m[7]);
            rails = new Eased[] {new Eased(m[8]), new Eased(m[9]), new Eased(m[10]), new Eased(m[11])};
            wantUi = m[0]; wantEye = m[1]; wantBar = m[2]; wantSimple = m[3];
            wantLiked = m[4]; wantCapsule = m[5]; wantSeek = m[6]; wantDate = m[7];
            wantRail = new float[] {m[8], m[9], m[10], m[11]};
            avatar = Launcher.preview("tilcayo");
            setMinimumHeight(Math.round(dp(context, 250)));
        }

        /** The screen saver: on or off, how faint it makes things, and whether the bottom bar stays. */
        Phone burn(boolean on, int strength, boolean keepBar) {
            float faint = on ? Math.max(0.1f, 1f - strength / 100f) : 1f;
            wantUi = faint;
            wantEye = on ? 1f : 0f;
            wantBar = on ? (keepBar ? faint : 0f) : 1f;
            return this;
        }

        /** The simple feed: the tabs, the avatar, the sound and the record go, and the rest gets smaller. */
        Phone simple(boolean on) {
            wantSimple = on ? 1f : 0f;
            return this;
        }

        /** Move to what was last asked for. */
        void settle() {
            ui.to(this, wantUi, 320, EMPHASIZED);
            bar.to(this, wantBar, 320, EMPHASIZED);
            simple.to(this, wantSimple, 360, EMPHASIZED);
            eye.to(this, wantEye, 420, new OvershootInterpolator(2.2f));
            liked.to(this, wantLiked, 360, new OvershootInterpolator(2f));
            capsule.to(this, wantCapsule, 380, EMPHASIZED);
            seek.to(this, wantSeek, 320, EMPHASIZED);
            date.to(this, wantDate, 320, EMPHASIZED);
            for (int i = 0; i < 4; i++) {
                rails[i].to(this, wantRail[i], 300, EMPHASIZED);
                memory[8 + i] = wantRail[i];
            }
            memory[0] = wantUi; memory[1] = wantEye; memory[2] = wantBar; memory[3] = wantSimple;
            memory[4] = wantLiked; memory[5] = wantCapsule; memory[6] = wantSeek; memory[7] = wantDate;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            settle();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(dp(getContext(), 250)));
        }

        private float u;   // a hundredth of the screen's width
        private float sx, sy;   // where the screen starts, for taps on what is drawn in it

        @Override
        protected void onDraw(Canvas canvas) {
            float d = dp(getContext(), 1);
            float height = getHeight() - 28 * d;
            float width = height * 0.49f;
            float left = (getWidth() - width) / 2f, top = 14 * d;
            // the phone: side buttons, a dark body with a thin bezel
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF2C2C30);
            box.set(left + width - d, top + height * 0.2f, left + width + 2.2f * d, top + height * 0.3f);
            canvas.drawRoundRect(box, d, d, paint);
            box.set(left + width - d, top + height * 0.36f, left + width + 2.2f * d, top + height * 0.52f);
            canvas.drawRoundRect(box, d, d, paint);
            box.set(left, top, left + width, top + height);
            paint.setColor(0xFF1B1B1F);
            canvas.drawRoundRect(box, width * 0.16f, width * 0.16f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d);
            paint.setColor((skin.text & 0xFFFFFF) | 0x26000000);
            canvas.drawRoundRect(box, width * 0.16f, width * 0.16f, paint);
            paint.setStyle(Paint.Style.FILL);
            box.inset(2.6f * d, 2.6f * d);
            float screenLeft = box.left, screenTop = box.top, w = box.width(), h = box.height();
            u = w / 100f;
            sx = screenLeft;
            sy = screenTop;
            canvas.save();
            path.reset();
            path.addRoundRect(box, width * 0.13f, width * 0.13f, Path.Direction.CW);
            canvas.clipPath(path);
            video(canvas, box);
            canvas.translate(screenLeft, screenTop);
            float s = simple.value;
            int faint = Math.round(255 * ui.value);
            top(canvas, w, faint, s);
            rail(canvas, w, h, faint, s);
            caption(canvas, w, h, faint, s);
            nav(canvas, w, h, Math.round(255 * bar.value));
            status(canvas, w, h);
            canvas.restore();
        }

        /** The phone's own: the time, signal and battery, the camera hole and the gesture handle. */
        private void status(Canvas canvas, float w, float h) {
            words(canvas, "12:00", 7 * u, 6.6f * u, 4.2f * u, 255, true, Paint.Align.LEFT);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF000000);
            canvas.drawCircle(w / 2f, 4.6f * u, 2.1f * u, paint);
            paint.setColor(0xFF1E2A3A);
            canvas.drawCircle(w / 2f, 4.6f * u, 0.9f * u, paint);
            white(255);
            for (int i = 0; i < 4; i++) {
                float bx = w - 22 * u + i * 1.6f * u, bh = (1.2f + i * 0.9f) * u;
                canvas.drawRect(bx, 6.2f * u - bh, bx + 1f * u, 6.2f * u, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.5f * u);
            box.set(w - 14 * u, 3.4f * u, w - 7.5f * u, 6.4f * u);
            canvas.drawRoundRect(box, 0.8f * u, 0.8f * u, paint);
            paint.setStyle(Paint.Style.FILL);
            box.set(w - 13.3f * u, 4.1f * u, w - 9.5f * u, 5.7f * u);
            canvas.drawRect(box, paint);
            canvas.drawRect(w - 7.3f * u, 4.4f * u, w - 6.7f * u, 5.4f * u, paint);
            paint.setColor(0xCCFFFFFF);
            box.set(w / 2f - 15 * u, h - 2.6f * u, w / 2f + 15 * u, h - 1.6f * u);
            canvas.drawRoundRect(box, 0.5f * u, 0.5f * u, paint);
        }

        /** The video: a dark picture with two soft lights in it, never dimmed. */
        private void video(Canvas canvas, RectF screen) {
            int accent = Accent.colour();
            paint.setShader(new LinearGradient(0, screen.top, 0, screen.bottom,
                    Expressive.mix(0xFF1B1530, accent, 0.25f), 0xFF0B1418, Shader.TileMode.CLAMP));
            canvas.drawRect(screen, paint);
            paint.setShader(new RadialGradient(screen.left + screen.width() * 0.3f, screen.top + screen.height() * 0.38f,
                    screen.width() * 0.55f, (Expressive.mix(accent, 0xFFFFFFFF, 0.2f) & 0xFFFFFF) | 0x66000000, 0, Shader.TileMode.CLAMP));
            canvas.drawRect(screen, paint);
            paint.setShader(new RadialGradient(screen.left + screen.width() * 0.75f, screen.top + screen.height() * 0.62f,
                    screen.width() * 0.45f, 0x5525C9D6, 0, Shader.TileMode.CLAMP));
            canvas.drawRect(screen, paint);
            paint.setShader(null);
        }

        private void white(int alpha) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(alpha, 255, 255, 255));
        }

        private void words(Canvas canvas, String said, float x, float y, float size, int alpha, boolean bold, Paint.Align align) {
            text.setColor(Color.argb(alpha, 255, 255, 255));
            text.setTextSize(size);
            text.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            text.setTextAlign(align);
            canvas.drawText(said, x, y, text);
        }

        private void top(Canvas canvas, float w, int alpha, float s) {
            float y = 17 * u;
            // LIVE, or the eye that takes its place
            float e = eye.value;
            if (e < 0.999f) {
                int liveAlpha = Math.round(alpha * (1f - Math.min(1f, e)));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.2f * u);
                paint.setColor(Color.argb(liveAlpha, 255, 255, 255));
                box.set(5 * u, y - 4 * u, 17 * u, y + 4 * u);
                canvas.drawRoundRect(box, 2 * u, 2 * u, paint);
                words(canvas, "LIVE", 11 * u, y + 1.8f * u, 4.6f * u, liveAlpha, true, Paint.Align.CENTER);
            }
            if (e > 0.001f) eyeAt(canvas, 11 * u, y, 6.5f * u * Math.max(0f, e));
            // the two tabs, gone in the simple feed
            int tabs = Math.round(alpha * (1f - s));
            if (tabs > 0) {
                words(canvas, Local.of("Подписки"), 40 * u, y + 2 * u, 5.6f * u, tabs * 160 / 255, false, Paint.Align.CENTER);
                words(canvas, Local.of("Для вас"), 64 * u, y + 2 * u, 5.6f * u, tabs, true, Paint.Align.CENTER);
                white(tabs);
                box.set(60 * u, y + 4.5f * u, 68 * u, y + 5.5f * u);
                canvas.drawRoundRect(box, u, u, paint);
            }
            // search stays
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.4f * u);
            paint.setColor(Color.argb(alpha, 255, 255, 255));
            canvas.drawCircle(89 * u, y - 0.5f * u, 3.2f * u, paint);
            canvas.drawLine(91.4f * u, y + 1.9f * u, 93.5f * u, y + 4f * u, paint);
        }

        /** The eye the screen saver puts where LIVE was: always clear, it is what turns it off. */
        private void eyeAt(Canvas canvas, float cx, float cy, float r) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            path.reset();
            path.moveTo(cx - r, cy);
            path.quadTo(cx, cy - r * 1.05f, cx + r, cy);
            path.quadTo(cx, cy + r * 1.05f, cx - r, cy);
            path.close();
            canvas.drawPath(path, paint);
            paint.setColor(Accent.colour());
            canvas.drawCircle(cx, cy, r * 0.42f, paint);
            paint.setColor(0xFF111111);
            canvas.drawCircle(cx, cy, r * 0.2f, paint);
        }

        private void rail(Canvas canvas, float w, float h, int alpha, float s) {
            float x = 91 * u;
            float scale = 1f - 0.15f * s;
            float y = h * 0.5f;
            float step = 14.5f * u;
            // the author's picture and the follow button, gone in the simple feed
            int author = Math.round(alpha * (1f - s));
            if (author > 0) {
                float r = 5.6f * u;
                if (avatar != null) {
                    paint.setShader(new BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                    Matrix m = new Matrix();
                    m.setRectToRect(new RectF(0, 0, avatar.getWidth(), avatar.getHeight()),
                            new RectF(x - r, y - step - r, x + r, y - step + r), Matrix.ScaleToFit.FILL);
                    paint.getShader().setLocalMatrix(m);
                    paint.setAlpha(author);
                    canvas.drawCircle(x, y - step, r, paint);
                    paint.setShader(null);
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(0.8f * u);
                paint.setColor(Color.argb(author, 255, 255, 255));
                canvas.drawCircle(x, y - step, r, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor((Accent.colour() & 0xFFFFFF) | (author << 24));
                canvas.drawCircle(x, y - step + r, 2.2f * u, paint);
            }
            String[] counts = {"12K", "345", "1K", "89"};
            for (int i = 0; i < 4; i++) {
                float cy = y + i * step;
                railCentres[i * 2] = sx + x;
                railCentres[i * 2 + 1] = sy + cy;
                float there = rails[i].value;
                int a = Math.round(alpha * there);
                if (railTapped != null && there < 0.99f) {
                    // a tappable place keeps an outline, so a hidden button can be brought back
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(0.6f * u);
                    paint.setColor(Color.argb(Math.round(alpha * 0.45f * (1f - there)), 255, 255, 255));
                    canvas.drawCircle(x, cy, 4.6f * u, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                if (a <= 0) continue;
                float r = 3.6f * u * scale * (0.6f + 0.4f * there);
                white(a);
                if (i == 0 && liked.value > 0.01f) {
                    int like = Accent.colour();
                    paint.setColor((Expressive.mix(0xFFFFFFFF, like, Math.min(1f, liked.value)) & 0xFFFFFF) | (a << 24));
                    r *= 1f + 0.15f * (float) Math.sin(Math.min(1f, liked.value) * Math.PI);
                }
                switch (i) {
                    case 0: heart(canvas, x, cy, r); break;
                    case 1: bubble(canvas, x, cy, r); break;
                    case 2: bookmark(canvas, x, cy, r); break;
                    default: share(canvas, x, cy, r); break;
                }
                words(canvas, counts[i], x, cy + r + 4.6f * u * scale, 3.8f * u * scale, a, true, Paint.Align.CENTER);
            }
            // the record that turns, gone in the simple feed
            int record = Math.round(alpha * (1f - s));
            if (record > 0) {
                float cy = h * 0.83f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(record, 40, 40, 40));
                canvas.drawCircle(x, cy, 4.8f * u, paint);
                paint.setColor(Color.argb(record, 200, 200, 200));
                canvas.drawCircle(x, cy, 1.8f * u, paint);
            }
        }

        private void heart(Canvas canvas, float cx, float cy, float r) {
            path.reset();
            path.moveTo(cx, cy + r);
            path.cubicTo(cx - r * 1.9f, cy - r * 0.2f, cx - r * 0.7f, cy - r * 1.5f, cx, cy - r * 0.45f);
            path.cubicTo(cx + r * 0.7f, cy - r * 1.5f, cx + r * 1.9f, cy - r * 0.2f, cx, cy + r);
            path.close();
            canvas.drawPath(path, paint);
        }

        private void bubble(Canvas canvas, float cx, float cy, float r) {
            box.set(cx - r * 1.2f, cy - r, cx + r * 1.2f, cy + r * 0.8f);
            canvas.drawOval(box, paint);
            path.reset();
            path.moveTo(cx - r * 0.5f, cy + r * 0.5f);
            path.lineTo(cx - r * 0.9f, cy + r * 1.25f);
            path.lineTo(cx + r * 0.1f, cy + r * 0.7f);
            path.close();
            canvas.drawPath(path, paint);
        }

        private void bookmark(Canvas canvas, float cx, float cy, float r) {
            path.reset();
            path.moveTo(cx - r * 0.85f, cy - r);
            path.lineTo(cx + r * 0.85f, cy - r);
            path.lineTo(cx + r * 0.85f, cy + r);
            path.lineTo(cx, cy + r * 0.45f);
            path.lineTo(cx - r * 0.85f, cy + r);
            path.close();
            canvas.drawPath(path, paint);
        }

        private void share(Canvas canvas, float cx, float cy, float r) {
            path.reset();
            path.moveTo(cx + r, cy - r * 0.1f);
            path.lineTo(cx + r * 0.05f, cy - r);
            path.lineTo(cx + r * 0.05f, cy - r * 0.5f);
            path.cubicTo(cx - r * 0.9f, cy - r * 0.4f, cx - r * 1.1f, cy + r * 0.4f, cx - r, cy + r);
            path.cubicTo(cx - r * 0.6f, cy + r * 0.2f, cx - r * 0.2f, cy + r * 0.25f, cx + r * 0.05f, cy + r * 0.25f);
            path.lineTo(cx + r * 0.05f, cy + r * 0.8f);
            path.close();
            canvas.drawPath(path, paint);
        }

        private void caption(Canvas canvas, float w, float h, int alpha, float s) {
            float y = h * (0.74f + 0.03f * s);
            words(canvas, "@fuckramochka", 5 * u, y, 5f * u, alpha, true, Paint.Align.LEFT);
            if (date.value > 0.01f) {
                text.setTextSize(5f * u);
                float after = 5 * u + text.measureText("@fuckramochka");
                words(canvas, " · " + Local.of("3 дня назад"), after, y, 4.2f * u, Math.round(alpha * date.value * 0.8f), false, Paint.Align.LEFT);
            }
            if (seek.value > 0.01f) {
                float line = h - 18.5f * u;
                int sa = Math.round(alpha * seek.value);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(sa * 80 / 255, 255, 255, 255));
                canvas.drawRect(0, line - 0.5f * u, w, line + 0.5f * u, paint);
                paint.setColor(Color.argb(sa, 255, 255, 255));
                canvas.drawRect(0, line - 0.5f * u, w * 0.38f, line + 0.5f * u, paint);
                canvas.drawCircle(w * 0.38f, line, 1.6f * u, paint);
            }
            white(alpha * 210 / 255);
            float line = 2.2f * u * (1f - 0.15f * s);
            box.set(5 * u, y + 3 * u, 62 * u, y + 3 * u + line);
            canvas.drawRoundRect(box, line, line, paint);
            box.set(5 * u, y + 7 * u, 46 * u, y + 7 * u + line);
            canvas.drawRoundRect(box, line, line, paint);
            if (s < 0.999f) {
                // a third line and the sound, which the simple feed leaves out
                int gone = Math.round(alpha * (1f - s));
                white(gone * 210 / 255);
                box.set(5 * u, y + 11 * u, 54 * u, y + 11 * u + line);
                canvas.drawRoundRect(box, line, line, paint);
                words(canvas, "♫  TikTok Mi — original sound", 5 * u, y + 18.5f * u, 3.8f * u, gone, false, Paint.Align.LEFT);
            }
        }

        private void nav(Canvas canvas, float w, float h, int alpha) {
            if (alpha <= 0) return;
            float c = capsule.value;
            float top = h - 16 * u - 2.5f * u * c;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(Math.round(alpha * (1f - 0.25f * c)), Math.round(28 * c), Math.round(28 * c), Math.round(32 * c)));
            box.set(7 * u * c, top, w - 7 * u * c, h - 3 * u * c);
            canvas.drawRoundRect(box, 6.5f * u * c, 6.5f * u * c, paint);
            float y = top + 6 * u;
            for (int i = 0; i < 5; i++) {
                float x = w * (0.1f + 0.2f * i);
                if (i == 2) {
                    paint.setColor(Color.argb(alpha, 0x25, 0xF4, 0xEE));
                    box.set(x - 6.3f * u, y - 3.6f * u, x + 4.7f * u, y + 3.6f * u);
                    canvas.drawRoundRect(box, 1.6f * u, 1.6f * u, paint);
                    paint.setColor((Accent.colour() & 0xFFFFFF) | (alpha << 24));
                    box.set(x - 4.7f * u, y - 3.6f * u, x + 6.3f * u, y + 3.6f * u);
                    canvas.drawRoundRect(box, 1.6f * u, 1.6f * u, paint);
                    white(alpha);
                    box.set(x - 5.5f * u, y - 3.6f * u, x + 5.5f * u, y + 3.6f * u);
                    canvas.drawRoundRect(box, 1.6f * u, 1.6f * u, paint);
                    paint.setColor(Color.argb(alpha, 0, 0, 0));
                    canvas.drawRect(x - 2.4f * u, y - 0.45f * u, x + 2.4f * u, y + 0.45f * u, paint);
                    canvas.drawRect(x - 0.45f * u, y - 2.4f * u, x + 0.45f * u, y + 2.4f * u, paint);
                } else {
                    white(i == 0 ? alpha : alpha * 170 / 255);
                    canvas.drawCircle(x, y, 2.3f * u, paint);
                }
            }
        }
    }

    // ================================================================= chat

    /** A conversation with fuckramochka: the sticker the streak sends, sent, when the streak is on. */
    static final class Chat extends View {
        private static float lastShown = 0f;

        private final Skin skin;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final RectF box = new RectF();
        private final Path path = new Path();
        private final Eased shown = new Eased(lastShown);
        private float want;
        private Bitmap sticker, avatar;

        Chat(Context context, Skin skin) {
            super(context);
            this.skin = skin;
            avatar = Launcher.preview("tilcayo");
        }

        Chat state(boolean on, Bitmap sticker) {
            want = on ? 1f : 0f;
            this.sticker = sticker;
            invalidate();
            return this;
        }

        void settle() {
            shown.to(this, want, 460, want > 0 ? new OvershootInterpolator(1.6f) : EMPHASIZED);
            lastShown = want;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            settle();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(dp(getContext(), 200)));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float d = dp(getContext(), 1);
            float left = 16 * d, right = getWidth() - 16 * d, top = 14 * d, bottom = getHeight() - 14 * d;
            box.set(left, top, right, bottom);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(skin.page);
            canvas.drawRoundRect(box, 20 * d, 20 * d, paint);
            // the head of the conversation
            float hy = top + 26 * d;
            if (avatar != null) round(canvas, avatar, left + 30 * d, hy, 14 * d, 255);
            words(canvas, "fuckramochka", left + 52 * d, hy - 1 * d, 15 * d, skin.text, true);
            words(canvas, "🔥 128", left + 52 * d, hy + 15 * d, 12 * d, skin.muted(), false);
            paint.setColor(Expressive.mix(skin.page, skin.text, 0.1f));
            canvas.drawRect(left, top + 50 * d, right, top + 50 * d + Math.max(1, d), paint);
            // what fuckramochka wrote
            float by = top + 76 * d;
            String said = Local.of("продлеваем? 🔥");
            text.setTextSize(14 * d);
            text.setTypeface(Typeface.DEFAULT);
            float tw = text.measureText(said);
            box.set(left + 14 * d, by - 16 * d, left + 14 * d + tw + 24 * d, by + 12 * d);
            paint.setColor(Expressive.mix(skin.page, skin.text, 0.09f));
            canvas.drawRoundRect(box, 16 * d, 16 * d, paint);
            words(canvas, said, left + 26 * d, by + 3 * d, 14 * d, skin.text, false);
            // the sticker, sent by the mod
            float size = 64 * d, cx = right - 18 * d - size / 2f, cy = bottom - 16 * d - size / 2f;
            float t = shown.value;
            if (t < 0.98f) {
                // where it will go: a dashed outline, while the streak is off
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f * d);
                paint.setPathEffect(new DashPathEffect(new float[] {6 * d, 5 * d}, 0));
                paint.setColor((skin.muted() & 0xFFFFFF) | (Math.round(140 * (1f - Math.min(1f, t))) << 24));
                box.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
                canvas.drawRoundRect(box, 14 * d, 14 * d, paint);
                paint.setPathEffect(null);
                paint.setStyle(Paint.Style.FILL);
            }
            if (t > 0.01f) {
                canvas.save();
                float scale = 0.5f + 0.5f * t;
                canvas.scale(scale, scale, cx, cy);
                int alpha = Math.round(255 * Math.min(1f, t));
                if (sticker != null) {
                    paint.setAlpha(alpha);
                    box.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
                    canvas.drawBitmap(sticker, null, box, paint);
                    paint.setAlpha(255);
                } else {
                    text.setTextAlign(Paint.Align.CENTER);
                    text.setTextSize(44 * d);
                    text.setColor(Color.argb(alpha, 0, 0, 0));
                    canvas.drawText("🔥", cx, cy + 15 * d, text);
                    text.setTextAlign(Paint.Align.LEFT);
                }
                canvas.restore();
                text.setTextAlign(Paint.Align.RIGHT);
                words(canvas, "TikTok Mi", cx + size / 2f, cy + size / 2f + 13 * d, 10 * d,
                        (skin.muted() & 0xFFFFFF) | (alpha << 24), false);
                text.setTextAlign(Paint.Align.LEFT);
            }
        }

        private void words(Canvas canvas, String said, float x, float y, float size, int colour, boolean bold) {
            text.setColor(colour);
            text.setTextSize(size);
            text.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            canvas.drawText(said, x, y, text);
        }

        private void round(Canvas canvas, Bitmap picture, float cx, float cy, float r, int alpha) {
            BitmapShader shader = new BitmapShader(picture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setRectToRect(new RectF(0, 0, picture.getWidth(), picture.getHeight()),
                    new RectF(cx - r, cy - r, cx + r, cy + r), Matrix.ScaleToFit.FILL);
            shader.setLocalMatrix(m);
            paint.setShader(shader);
            paint.setAlpha(alpha);
            canvas.drawCircle(cx, cy, r, paint);
            paint.setShader(null);
            paint.setAlpha(255);
        }
    }

    // ============================================================== profile

    /** fuckramochka's profile, with the avatar in the shape that is chosen. */
    static final class Profile extends View {
        private static String lastShape;

        private final Skin skin;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Matrix matrix = new Matrix();
        private final Path clip = new Path();
        private final Eased morph = new Eased(1f);
        private String from, to;
        private final Bitmap avatar;

        Profile(Context context, Skin skin) {
            super(context);
            this.skin = skin;
            avatar = Launcher.preview("tilcayo");
            to = AvatarShape.chosen();
            from = lastShape == null ? to : lastShape;
        }

        /** Another shape: the old one gives way to it. */
        void shape(String shape) {
            if (shape.equals(to)) return;
            from = to;
            to = shape;
            morph.value = 0f;
            morph.to(this, 1f, 420, new OvershootInterpolator(1.4f));
            lastShape = to;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!from.equals(to)) {
                morph.value = 0f;
                morph.to(this, 1f, 420, new OvershootInterpolator(1.4f));
            }
            lastShape = to;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(dp(getContext(), 196)));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float d = dp(getContext(), 1);
            float cx = getWidth() / 2f, cy = 58 * d, r = 40 * d;
            float t = morph.value;
            float settled = Math.max(0f, Math.min(1f, t));
            if (!from.equals(to) && settled < 1f) avatarIn(canvas, from, cx, cy, r, Math.round(255 * (1f - settled)), 1f);
            avatarIn(canvas, to, cx, cy, r, Math.round(255 * settled), 0.85f + 0.15f * t);
            text.setTextAlign(Paint.Align.CENTER);
            words(canvas, "fuckramochka", cx, cy + r + 24 * d, 17 * d, skin.text, true);
            words(canvas, "@fuckramochka", cx, cy + r + 42 * d, 13 * d, skin.muted(), false);
            float y = cy + r + 72 * d, gap = getWidth() / 4f;
            String[][] stats = {{"17", Local.of("Подписки")}, {"4,2K", Local.of("Подписчики")}, {"52K", Local.of("Лайки")}};
            for (int i = 0; i < 3; i++) {
                float x = cx + (i - 1) * gap;
                words(canvas, stats[i][0], x, y, 16 * d, skin.text, true);
                words(canvas, stats[i][1], x, y + 17 * d, 12 * d, skin.muted(), false);
            }
            text.setTextAlign(Paint.Align.LEFT);
        }

        private void avatarIn(Canvas canvas, String shape, float cx, float cy, float r, int alpha, float scale) {
            if (alpha <= 0 || avatar == null) return;
            int index = 0;
            for (int i = 0; i < AvatarShape.ALL.length; i++) if (AvatarShape.ALL[i].equals(shape)) index = i;
            matrix.reset();
            matrix.setScale(r * scale, r * scale);
            matrix.postTranslate(cx, cy);
            clip.reset();
            AvatarShape.unit(index).transform(matrix, clip);
            BitmapShader shader = new BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setRectToRect(new RectF(0, 0, avatar.getWidth(), avatar.getHeight()),
                    new RectF(cx - r, cy - r, cx + r, cy + r), Matrix.ScaleToFit.FILL);
            shader.setLocalMatrix(m);
            paint.setShader(shader);
            paint.setAlpha(alpha);
            canvas.drawPath(clip, paint);
            paint.setShader(null);
            paint.setAlpha(255);
        }

        private void words(Canvas canvas, String said, float x, float y, float size, int colour, boolean bold) {
            text.setColor(colour);
            text.setTextSize(size);
            text.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            canvas.drawText(said, x, y, text);
        }
    }

    // ================================================================ style

    /** A settings card in one of the three styles, small: what choosing it would look like. */
    static final class Style extends View {
        private final Skin skin;
        private final String mode;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        boolean chosen;

        Style(Context context, Skin skin, String mode, boolean chosen) {
            super(context);
            this.skin = skin;
            this.mode = mode;
            this.chosen = chosen;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(dp(getContext(), 104)));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float d = dp(getContext(), 1), w = getWidth(), h = getHeight();
            int accent = Accent.colour();
            box.set(3 * d, 3 * d, w - 3 * d, h - 3 * d);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            boolean glass = "glass".equals(mode), fluent = "fluent".equals(mode);
            // the page behind
            if (glass) {
                paint.setShader(new LinearGradient(0, 0, w, h, Expressive.mix(accent, 0xFF2B2250, 0.4f), 0xFF0E3A44, Shader.TileMode.CLAMP));
            } else {
                paint.setColor(fluent ? Expressive.mix(skin.page, skin.text, 0.04f) : skin.page);
            }
            canvas.drawRoundRect(box, 16 * d, 16 * d, paint);
            paint.setShader(null);
            if (glass) {
                paint.setShader(new RadialGradient(w * 0.75f, h * 0.3f, w * 0.4f, (accent & 0xFFFFFF) | 0xAA000000, 0, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(box, 16 * d, 16 * d, paint);
                paint.setShader(null);
            }
            if (chosen) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f * d);
                paint.setColor(accent);
                canvas.drawRoundRect(box, 16 * d, 16 * d, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            float left = 11 * d, right = w - 11 * d;
            for (int row = 0; row < 2; row++) {
                float top = 16 * d + row * 38 * d, bottom = top + (fluent ? 33 : 36) * d;
                box.set(left, top, right, bottom);
                if (glass) {
                    paint.setColor(0x40FFFFFF);
                    canvas.drawRoundRect(box, 18 * d, 18 * d, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(d);
                    paint.setColor(0x66FFFFFF);
                    canvas.drawRoundRect(box, 18 * d, 18 * d, paint);
                    paint.setStyle(Paint.Style.FILL);
                } else if (fluent) {
                    paint.setColor(skin.card);
                    canvas.drawRoundRect(box, 4 * d, 4 * d, paint);
                } else {
                    // a segmented group: large outer corners, small inner ones
                    float outer = 12 * d, inner = 3 * d;
                    float[] corners = row == 0 ? new float[] {outer, outer, outer, outer, inner, inner, inner, inner}
                            : new float[] {inner, inner, inner, inner, outer, outer, outer, outer};
                    Path p = new Path();
                    p.addRoundRect(box, corners, Path.Direction.CW);
                    paint.setColor(skin.card);
                    canvas.drawPath(p, paint);
                }
                float cy = (top + bottom) / 2f;
                int ink = glass ? 0xFFFFFFFF : skin.text;
                // the icon
                paint.setColor(glass ? 0x88FFFFFF : fluent ? accent : Expressive.mix(skin.card, accent, 0.3f));
                if (fluent) {
                    box.set(left + 7 * d, cy - 5 * d, left + 17 * d, cy + 5 * d);
                    canvas.drawRoundRect(box, 2 * d, 2 * d, paint);
                } else {
                    canvas.drawCircle(left + 13 * d, cy, 7 * d, paint);
                }
                // two lines of words
                paint.setColor((ink & 0xFFFFFF) | 0xCC000000);
                box.set(left + 25 * d, cy - 5 * d, right - 34 * d, cy - 2 * d);
                canvas.drawRoundRect(box, 2 * d, 2 * d, paint);
                paint.setColor((ink & 0xFFFFFF) | 0x66000000);
                box.set(left + 25 * d, cy + 2 * d, right - 44 * d, cy + 5 * d);
                canvas.drawRoundRect(box, 2 * d, 2 * d, paint);
                // the switch, on in the first row
                boolean on = row == 0;
                float sw = fluent ? 18 * d : 22 * d, sh = fluent ? 9 * d : 13 * d;
                box.set(right - 6 * d - sw, cy - sh / 2f, right - 6 * d, cy + sh / 2f);
                if (glass) {
                    paint.setColor(on ? (accent & 0xFFFFFF) | 0xE6000000 : 0x55FFFFFF);
                    canvas.drawRoundRect(box, sh / 2f, sh / 2f, paint);
                    paint.setColor(0xFFFFFFFF);
                    float tx = on ? box.right - sh * 0.62f : box.left + sh * 0.62f;
                    box.set(tx - sh * 0.62f, box.top + d, tx + sh * 0.62f, box.bottom - d);
                    canvas.drawRoundRect(box, sh / 2f, sh / 2f, paint);
                } else {
                    if (on) {
                        paint.setColor(accent);
                        canvas.drawRoundRect(box, sh / 2f, sh / 2f, paint);
                    } else {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(fluent ? d : 1.5f * d);
                        paint.setColor(skin.muted());
                        canvas.drawRoundRect(box, sh / 2f, sh / 2f, paint);
                        paint.setStyle(Paint.Style.FILL);
                    }
                    float r = fluent ? 2.6f * d : (on ? 4.6f * d : 3f * d);
                    paint.setColor(on ? 0xFFFFFFFF : skin.muted());
                    canvas.drawCircle(on ? box.right - sh / 2f : box.left + sh / 2f, cy, r, paint);
                }
            }
        }
    }

    // ============================================================== loading

    /** TikTok's two dots beside the mod's shape: the one in use bright, the other faint. */
    static final class Loader extends View {
        private static float lastOn = 0f;
        private final Skin skin;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Eased on = new Eased(lastOn);
        private float want;

        Loader(Context context, Skin skin, boolean enabled) {
            super(context);
            this.skin = skin;
            want = enabled ? 1f : 0f;
        }

        void set(boolean enabled) {
            want = enabled ? 1f : 0f;
            on.to(this, want, 320, EMPHASIZED);
            lastOn = want;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            set(want > 0.5f);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(dp(getContext(), 128)));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float d = dp(getContext(), 1), w = getWidth(), cy = 56 * d;
            float t = on.value;
            // TikTok's: a pink dot and a cyan one passing each other
            float phase = (android.os.SystemClock.uptimeMillis() % 900) / 900f;
            float swing = (float) Math.sin(phase * Math.PI * 2) * 9 * d;
            float lx = w * 0.3f;
            int faintTikTok = Math.round(255 * (1f - 0.75f * t));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((0x25F4EE) | (faintTikTok << 24));
            canvas.drawCircle(lx - swing, cy, 9 * d, paint);
            paint.setColor((0xFE2C55) | (faintTikTok << 24));
            canvas.drawCircle(lx + swing, cy, 9 * d, paint);
            // the mod's, drawn by the very code that draws it over TikTok
            int faintOurs = Math.round(255 * (0.25f + 0.75f * t));
            float size = 46 * d, rx = w * 0.7f;
            canvas.saveLayerAlpha(rx - size, cy - size, rx + size, cy + size, faintOurs);
            canvas.translate(rx - size / 2f, cy - size / 2f);
            Loading.preview(canvas, Math.round(size), Math.round(size));
            canvas.restore();
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(13 * d);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            text.setColor(Expressive.mix(skin.muted(), skin.text, 1f - t));
            canvas.drawText("TikTok", lx, cy + 46 * d, text);
            text.setColor(Expressive.mix(skin.muted(), skin.text, t));
            canvas.drawText("TikTok Mi", rx, cy + 46 * d, text);
            // only while it can be seen: scrolled back into sight, it is drawn and starts again
            if (isShown() && getGlobalVisibleRect(seen)) postInvalidateOnAnimation();
        }

        private final android.graphics.Rect seen = new android.graphics.Rect();
    }
}

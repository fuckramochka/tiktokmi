package cat.narezany.margyt;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * More pictures for the settings: the frame rate, the speed held and locked,
 * the feed with what is hidden taken out of it, the profile grid, the new
 * profile, the black theme, the badges on a name, a comment being copied,
 * the notifications let through, and the three toys. Each follows its
 * setting as it is changed; the ones that move only move while in sight.
 */
final class SettingsPictures {
    private SettingsPictures() {}

    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);

    /** What every picture here has: the skin, a height, eased numbers and a clock that runs while it is seen. */
    abstract static class Art extends View {
        final Skin skin;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        final RectF box = new RectF();
        final Path path = new Path();
        private final Rect seen = new Rect();
        private final int tall;
        final float d;

        Art(Context context, Skin skin, int heightDp) {
            super(context);
            this.skin = skin;
            this.tall = heightDp;
            this.d = context.getResources().getDisplayMetrics().density;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(tall * d));
        }

        /** Asks for the next frame, but only while some of it is on the screen. */
        void again() {
            if (isShown() && getGlobalVisibleRect(seen)) postInvalidateOnAnimation();
        }

        /** Seconds since the phone started, for pictures that loop. */
        float clock() {
            return (SystemClock.uptimeMillis() % 1000000L) / 1000f;
        }

        void words(Canvas canvas, String said, float x, float y, float sizeDp, int colour, boolean bold, Paint.Align align) {
            text.setColor(colour);
            text.setTextSize(sizeDp * d);
            text.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            text.setTextAlign(align);
            canvas.drawText(said, x, y, text);
        }

        void round(Canvas canvas, Bitmap picture, float cx, float cy, float r, int alpha) {
            if (picture == null) return;
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

        int faint(int colour, float amount) {
            return (colour & 0xFFFFFF) | (Math.round(Color.alpha(colour) * Math.max(0f, Math.min(1f, amount))) << 24);
        }

        int surface(float amount) {
            return Expressive.mix(skin.page, skin.text, amount);
        }
    }

    /** One number moved over a few hundred milliseconds. */
    static final class Eased {
        float value;
        private ValueAnimator animator;

        Eased(float value) { this.value = value; }

        void to(final View view, float target, long duration) {
            if (animator != null) animator.cancel();
            if (!Motion.enabled(view.getContext()) || Math.abs(target - value) < 0.001f) {
                value = target;
                view.invalidate();
                return;
            }
            animator = ValueAnimator.ofFloat(value, target);
            animator.setDuration(duration);
            animator.setInterpolator(EMPHASIZED);
            animator.addUpdateListener(a -> {
                value = (Float) a.getAnimatedValue();
                view.invalidate();
            });
            animator.start();
        }
    }

    // ============================================================ frame rate

    /** A ball going to and fro, drawn as often as the chosen rate allows, beside TikTok's sixty. */
    static final class Fps extends Art {
        private int rate;

        Fps(Context context, Skin skin, String chosen) {
            super(context, skin, 150);
            set(chosen);
        }

        void set(String chosen) {
            int n = 0;
            try { n = Integer.parseInt(chosen); } catch (Throwable ignored) { }
            rate = n <= 0 ? 0 : n;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), left = 28 * d, right = w - 28 * d;
            float t = clock();
            lane(canvas, 40 * d, left, right, t, 60, "TikTok · 60", skin.muted());
            int mine = rate == 0 ? 120 : rate;
            lane(canvas, 104 * d, left, right, t, mine,
                    (rate == 0 ? Text.FPS_AUTO : rate + " FPS"), Accent.colour());
            again();
        }

        private void lane(Canvas canvas, float y, float left, float right, float t, int fps, String name, int colour) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(surface(0.1f));
            box.set(left - 14 * d, y - 14 * d, right + 14 * d, y + 14 * d);
            canvas.drawRoundRect(box, 14 * d, 14 * d, paint);
            // where the ball is, worked out only at the moments a screen at this rate would draw it
            float step = 1f / fps;
            for (int ghost = 5; ghost >= 0; ghost--) {
                float at = (float) Math.floor((t - ghost * step) / step) * step;
                float phase = (at % 1.6f) / 1.6f;
                float along = 0.5f - 0.5f * (float) Math.cos(phase * Math.PI * 2);
                float x = left + (right - left) * along;
                paint.setColor(faint(colour, ghost == 0 ? 1f : 0.32f - ghost * 0.05f));
                canvas.drawCircle(x, y, (ghost == 0 ? 9 : 8) * d, paint);
            }
            words(canvas, name, left - 14 * d, y - 20 * d, 12, skin.muted(), true, Paint.Align.LEFT);
        }
    }

    // ============================================================ speed

    /** A finger on the edge of a video: the speed while it is held, and swiped down to keep it. */
    static final class Speed extends Art {
        private boolean lock;
        private float speed;

        Speed(Context context, Skin skin, float speed, boolean lock) {
            super(context, skin, 220);
            set(speed, lock);
        }

        void set(float speed, boolean lock) {
            this.speed = speed;
            this.lock = lock;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float h = getHeight() - 24 * d, w = h * 0.49f;
            float left = (getWidth() - w) / 2f, top = 12 * d;
            box.set(left, top, left + w, top + h);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, top, 0, top + h, Expressive.mix(0xFF1B1530, Accent.colour(), 0.25f),
                    0xFF0B1418, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(box, w * 0.14f, w * 0.14f, paint);
            paint.setShader(null);
            float cycle = 3.6f, t = (clock() % cycle) / cycle;
            // touch at 10%, the speed shows at 20%, then either let go at 60% or swipe down and keep it
            boolean down = t > 0.1f && (lock ? t < 0.78f : t < 0.6f);
            boolean fast = t > 0.2f && (lock ? t < 0.97f : t < 0.62f);
            boolean kept = lock && t > 0.72f && t < 0.97f;
            float swipe = lock ? Math.max(0f, Math.min(1f, (t - 0.45f) / 0.25f)) : 0f;
            String shown = (speed == (int) speed ? String.valueOf((int) speed) : String.valueOf(speed)) + "×";
            if (fast) {
                float px = left + w / 2f, py = top + 30 * d;
                text.setTextSize(13 * d);
                float tw = text.measureText(shown + (kept ? "  🔒" : "")) + 22 * d;
                paint.setColor(0xCC000000);
                box.set(px - tw / 2f, py - 13 * d, px + tw / 2f, py + 13 * d);
                canvas.drawRoundRect(box, 13 * d, 13 * d, paint);
                words(canvas, shown + (kept ? "  🔒" : ""), px, py + 5 * d, 13, 0xFFFFFFFF, true, Paint.Align.CENTER);
            }
            if (down) {
                float fx = left + w * 0.82f, fy = top + h * (0.45f + 0.3f * swipe);
                paint.setColor(0x66FFFFFF);
                canvas.drawCircle(fx, fy, 16 * d, paint);
                paint.setColor(0xDDFFFFFF);
                canvas.drawCircle(fx, fy, 9 * d, paint);
                if (lock && swipe > 0f) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(3 * d);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    paint.setColor(0x55FFFFFF);
                    canvas.drawLine(fx, top + h * 0.45f, fx, fy, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
            words(canvas, lock ? Text.SPEED_LOCK : Text.PLAYER_SPEED, getWidth() / 2f, getHeight() - 2 * d, 12,
                    skin.muted(), false, Paint.Align.CENTER);
            again();
        }
    }

    // ============================================================ feed

    /** The feed as a strip of cards; what is hidden folds away. */
    static final class Feed extends Art {
        // what each card is: 0 a video, then the kinds the switches hide
        static final int VIDEO = 0, AD = 1, LIVE = 2, PHOTO = 3, STORY = 4, SUGGEST = 5;
        private static final int[] STRIP = {VIDEO, AD, LIVE, VIDEO, PHOTO, STORY, SUGGEST, VIDEO};
        private final Eased[] shown = new Eased[6];
        private final boolean[] want = new boolean[6];
        private static final float[] last = {1, 1, 1, 1, 1, 1};

        Feed(Context context, Skin skin) {
            super(context, skin, 150);
            for (int i = 0; i < 6; i++) { shown[i] = new Eased(last[i]); want[i] = true; }
        }

        Feed hide(int kind, boolean hidden) {
            want[kind] = !hidden;
            return this;
        }

        void settle() {
            for (int i = 1; i < 6; i++) {
                shown[i].to(this, want[i] ? 1f : 0f, 380);
                last[i] = want[i] ? 1f : 0f;
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            settle();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cardW = 38 * d, gap = 6 * d, cardH = 92 * d, top = 18 * d;
            float total = 0;
            for (int kind : STRIP) total += (cardW + gap) * shown[kind].value;
            float x = (getWidth() - total + gap) / 2f;
            String[] names = {"", Local.of("Реклама"), "LIVE", Local.of("Фото"), Local.of("Истории"), Local.of("Люди")};
            int[] tones = {0xFF3A2E5C, 0xFFE8A33D, 0xFFFE2C55, 0xFF2F9E8F, 0xFF7C5CD6, 0xFF4E7FD8};
            for (int i = 0; i < STRIP.length; i++) {
                int kind = STRIP[i];
                float v = shown[kind].value;
                if (v <= 0.01f) continue;
                float wide = cardW * v;
                box.set(x, top + cardH * (1 - v) * 0.15f, x + wide, top + cardH - cardH * (1 - v) * 0.15f);
                paint.setShader(new LinearGradient(0, box.top, 0, box.bottom, Expressive.mix(tones[kind], 0xFFFFFFFF, 0.15f),
                        Expressive.mix(tones[kind], 0xFF000000, 0.35f), Shader.TileMode.CLAMP));
                paint.setAlpha(Math.round(255 * v));
                canvas.drawRoundRect(box, 9 * d, 9 * d, paint);
                paint.setShader(null);
                paint.setAlpha(255);
                if (kind == VIDEO) {
                    paint.setColor(faint(0xFFFFFFFF, 0.85f * v));
                    path.reset();
                    float cx = box.centerX(), cy = box.centerY();
                    path.moveTo(cx - 5 * d, cy - 7 * d);
                    path.lineTo(cx + 7 * d, cy);
                    path.lineTo(cx - 5 * d, cy + 7 * d);
                    path.close();
                    canvas.drawPath(path, paint);
                } else if (wide > 20 * d) {
                    canvas.save();
                    canvas.rotate(-90, box.centerX(), box.centerY());
                    words(canvas, names[kind], box.centerX(), box.centerY() + 4 * d, 10, faint(0xFFFFFFFF, v), true, Paint.Align.CENTER);
                    canvas.restore();
                }
                x += (cardW + gap) * v;
            }
            int hidden = 0;
            for (int i = 1; i < 6; i++) if (!want[i]) hidden++;
            words(canvas, hidden == 0 ? Local.of("Лента как у TikTok") : Local.of("Скрыто: ") + hidden,
                    getWidth() / 2f, top + cardH + 26 * d, 12, skin.muted(), false, Paint.Align.CENTER);
        }
    }

    // ============================================================ grid

    /** A profile's grid of videos, with square corners or round ones. */
    static final class Grid extends Art {
        private static float last = 0f;
        private final Eased round = new Eased(last);
        private float want;

        Grid(Context context, Skin skin, boolean on) {
            super(context, skin, 150);
            want = on ? 1f : 0f;
        }

        void set(boolean on) {
            want = on ? 1f : 0f;
            round.to(this, want, 360);
            last = want;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            set(want > 0.5f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float gap = (2 + 4 * round.value) * d, cell = 40 * d, tall = 56 * d;
            float width = cell * 4 + gap * 3, left = (getWidth() - width) / 2f, top = 16 * d;
            int[] tones = {0xFF5C4B8A, 0xFF2F7A6E, 0xFF9A4D6B, 0xFF3D5C9A, 0xFF8A6B3A, 0xFF4B7A3D, 0xFF7A3D5C, 0xFF3A6B8A};
            for (int i = 0; i < 8; i++) {
                float x = left + (i % 4) * (cell + gap), y = top + (i / 4) * (tall + gap);
                box.set(x, y, x + cell, y + tall);
                paint.setShader(new LinearGradient(x, y, x + cell, y + tall, Expressive.mix(tones[i], 0xFFFFFFFF, 0.2f),
                        tones[i], Shader.TileMode.CLAMP));
                float r = 10 * d * round.value;
                canvas.drawRoundRect(box, r, r, paint);
                paint.setShader(null);
                words(canvas, "▶ " + (1 + i * 3) + "K", x + 4 * d, y + tall - 5 * d, 8, 0xFFFFFFFF, true, Paint.Align.LEFT);
            }
        }
    }

    // ============================================================ profile layout

    /** The head of a profile, the old way (in the middle) and the new (to the left, with a banner on top). */
    static final class Layout extends Art {
        private static float last = 1f;
        private final Eased fresh = new Eased(last);
        private float want;
        private final Bitmap avatar;

        Layout(Context context, Skin skin, boolean newOne) {
            super(context, skin, 200);
            want = newOne ? 1f : 0f;
            avatar = Launcher.preview("tilcayo");
        }

        void set(boolean newOne) {
            want = newOne ? 1f : 0f;
            fresh.to(this, want, 480);
            last = want;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            set(want > 0.5f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float t = fresh.value, w = getWidth();
            float left = 20 * d, right = w - 20 * d, top = 10 * d;
            // the banner grows in from nothing
            float banner = 64 * d * t;
            if (banner > 1) {
                box.set(left, top, right, top + banner);
                paint.setShader(new LinearGradient(left, top, right, top + banner, Accent.colour(),
                        Expressive.mix(Accent.colour(), 0xFF25C9D6, 0.6f), Shader.TileMode.CLAMP));
                canvas.drawRoundRect(box, 16 * d, 16 * d, paint);
                paint.setShader(null);
            }
            float r = (34 - 6 * t) * d;
            float cx = w / 2f + (left + 16 * d + r - w / 2f) * t;
            float cy = top + r + 6 * d + (banner - r * 0.9f) * t + 2 * d * (1 - t);
            paint.setColor(skin.page);
            canvas.drawCircle(cx, cy, r + 3 * d, paint);
            round(canvas, avatar, cx, cy, r, 255);
            // the name and the counts: under the picture, or beside it
            float nameX = w / 2f + (cx + r + 14 * d - w / 2f) * t;
            float nameY = (cy + r + 22 * d) * (1 - t) + (cy + 6 * d) * t;
            Paint.Align align = t < 0.5f ? Paint.Align.CENTER : Paint.Align.LEFT;
            int fade = Math.round(255 * Math.abs(t - 0.5f) * 2);
            words(canvas, "narezany", nameX, nameY, 16, faint(skin.text, fade / 255f), true, align);
            words(canvas, "@narezany", nameX, nameY + 17 * d, 12, faint(skin.muted(), fade / 255f), false, align);
            float statsY = Math.max(nameY + 46 * d, cy + r + 34 * d);
            String[] stats = {"17 " + Local.of("подписки"), "4,2K " + Local.of("подписчики"), "52K " + Local.of("лайки")};
            if (t < 0.5f) {
                float gap = (right - left) / 3f;
                for (int i = 0; i < 3; i++)
                    words(canvas, stats[i], left + gap * (i + 0.5f), statsY, 11, faint(skin.text, fade / 255f), false, Paint.Align.CENTER);
            } else {
                float x = left + 4 * d;
                for (int i = 0; i < 3; i++) {
                    words(canvas, stats[i], x, statsY, 11, faint(skin.text, fade / 255f), false, Paint.Align.LEFT);
                    x += text.measureText(stats[i]) + 14 * d;
                }
            }
        }
    }

    // ============================================================ black

    /** A screen of the app, its dark grey going to true black. */
    static final class Black extends Art {
        private static float last = 0f;
        private final Eased black = new Eased(last);
        private float want;

        Black(Context context, Skin skin, boolean on) {
            super(context, skin, 150);
            want = on ? 1f : 0f;
        }

        void set(boolean on) {
            want = on ? 1f : 0f;
            black.to(this, want, 420);
            last = want;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            set(want > 0.5f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float t = black.value, w = getWidth();
            int page = Expressive.mix(0xFF161823, 0xFF000000, t), card = Expressive.mix(0xFF252632, 0xFF101010, t);
            box.set(24 * d, 12 * d, w - 24 * d, getHeight() - 12 * d);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(page);
            canvas.drawRoundRect(box, 20 * d, 20 * d, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d);
            paint.setColor(surface(0.18f));
            canvas.drawRoundRect(box, 20 * d, 20 * d, paint);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 3; i++) {
                float y = box.top + 16 * d + i * 38 * d;
                paint.setColor(card);
                canvas.drawRoundRect(new RectF(box.left + 14 * d, y, box.right - 14 * d, y + 30 * d), 10 * d, 10 * d, paint);
                paint.setColor(Accent.colour());
                canvas.drawCircle(box.left + 30 * d, y + 15 * d, 8 * d, paint);
                paint.setColor(0xCCFFFFFF);
                canvas.drawRoundRect(new RectF(box.left + 46 * d, y + 9 * d, box.left + 46 * d + (90 - i * 18) * d, y + 13 * d), 2 * d, 2 * d, paint);
                paint.setColor(0x66FFFFFF);
                canvas.drawRoundRect(new RectF(box.left + 46 * d, y + 18 * d, box.left + 46 * d + (60 + i * 10) * d, y + 21 * d), 2 * d, 2 * d, paint);
            }
            words(canvas, t > 0.5f ? "#000000" : "#161823", box.right - 14 * d, box.bottom - 10 * d, 10, 0x88FFFFFF, false, Paint.Align.RIGHT);
        }
    }

    // ============================================================ badges

    /** A name with the badges after it: the person's own, or a few to show what they look like. */
    static final class Badged extends Art {
        private static float last = 1f;
        private final Eased on = new Eased(last);
        private float want;
        private final Badges.Badge[] worn;
        private final Bitmap[] pictures;

        Badged(Context context, Skin skin, boolean enabled) {
            super(context, skin, 96);
            want = enabled ? 1f : 0f;
            Badges.Badge[] mine = Badges.of(Account.id());
            if (mine == null || mine.length == 0) {
                Badges.Badge[] all = Badges.all();
                mine = new Badges.Badge[Math.min(3, all.length)];
                System.arraycopy(all, 0, mine, 0, mine.length);
            }
            worn = mine;
            pictures = new Bitmap[worn.length];
            for (int i = 0; i < worn.length; i++) {
                if (worn[i] != null && worn[i].image != null && worn[i].image.length() > 0)
                    pictures[i] = Badges.picture(context, worn[i].image);
            }
        }

        void set(boolean enabled) {
            want = enabled ? 1f : 0f;
            on.to(this, want, 360);
            last = want;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            set(want > 0.5f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), cy = getHeight() / 2f;
            String name = "narezany";
            text.setTextSize(22 * d);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            float nameWidth = text.measureText(name), size = 24 * d, gap = 6 * d;
            float badges = worn.length * (size + gap) * on.value;
            float x = (w - nameWidth - badges) / 2f;
            words(canvas, name, x, cy + 8 * d, 22, skin.text, true, Paint.Align.LEFT);
            x += nameWidth + gap;
            for (int i = 0; i < worn.length; i++) {
                if (worn[i] == null) continue;
                float s = size * on.value;
                // as wide as the picture is for its height, the way a badge sits after a name
                float wide = pictures[i] != null && pictures[i].getHeight() > 0
                        ? s * pictures[i].getWidth() / pictures[i].getHeight() : s;
                box.set(x, cy - s / 2f, x + wide, cy + s / 2f);
                if (pictures[i] != null) {
                    paint.setColorFilter(new android.graphics.PorterDuffColorFilter(worn[i].colour == 0 ? 0xFF8DD1B0 : worn[i].colour,
                            android.graphics.PorterDuff.Mode.SRC_IN));
                    paint.setAlpha(Math.round(255 * on.value));
                    canvas.drawBitmap(pictures[i], null, box, paint);
                    paint.setColorFilter(null);
                    paint.setAlpha(255);
                } else {
                    paint.setColor(faint(worn[i].colour == 0 ? 0xFF8DD1B0 : worn[i].colour, on.value));
                    canvas.drawCircle(box.centerX(), box.centerY(), s / 2.6f, paint);
                }
                x += (box.width() + gap * on.value);
            }
        }
    }

    // ============================================================ comment

    /** A comment, a few of its words picked out, and the menu with "select part" in it. */
    static final class Copying extends Art {
        private boolean on;
        private final Bitmap avatar;

        Copying(Context context, Skin skin, boolean on) {
            super(context, skin, 156);
            this.on = on;
            avatar = Launcher.preview("tilcayo");
        }

        void set(boolean on) {
            this.on = on;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float left = 22 * d, top = 18 * d;
            round(canvas, avatar, left + 16 * d, top + 16 * d, 16 * d, 255);
            words(canvas, "narezany", left + 42 * d, top + 10 * d, 12, skin.muted(), true, Paint.Align.LEFT);
            String first = Local.of("это "), picked = Local.of("лучший мод"), rest = Local.of(" на тикток 🔥");
            text.setTextSize(15 * d);
            text.setTypeface(Typeface.DEFAULT);
            float x = left + 42 * d, y = top + 32 * d;
            float a = text.measureText(first), b = text.measureText(picked);
            float t = (clock() % 3.2f) / 3.2f;
            float sweep = on ? Math.max(0f, Math.min(1f, (t - 0.15f) / 0.3f)) : 0f;
            if (on && sweep > 0f) {
                paint.setColor(faint(Accent.colour(), 0.35f));
                box.set(x + a, y - 15 * d, x + a + b * sweep, y + 5 * d);
                canvas.drawRect(box, paint);
                paint.setColor(Accent.colour());
                canvas.drawCircle(x + a, y + 9 * d, 5 * d, paint);
                canvas.drawCircle(x + a + b * sweep, y + 9 * d, 5 * d, paint);
            }
            words(canvas, first, x, y, 15, skin.text, false, Paint.Align.LEFT);
            words(canvas, picked, x + a, y, 15, skin.text, false, Paint.Align.LEFT);
            words(canvas, rest, x + a + b, y, 15, skin.text, false, Paint.Align.LEFT);
            // the menu a long press opens
            float my = y + 26 * d;
            String[] items = on ? new String[] {Text.COMMENT_SELECT, Local.of("Копировать")} : new String[] {Local.of("Копировать"), Local.of("Ответить")};
            float mx = x;
            for (int i = 0; i < items.length; i++) {
                text.setTextSize(13 * d);
                float iw = text.measureText(items[i]) + 24 * d;
                box.set(mx, my, mx + iw, my + 34 * d);
                paint.setColor(i == 0 && on ? faint(Accent.colour(), 0.22f) : surface(0.1f));
                canvas.drawRoundRect(box, 17 * d, 17 * d, paint);
                words(canvas, items[i], mx + iw / 2f, my + 22 * d, 13, skin.text, i == 0 && on, Paint.Align.CENTER);
                mx += iw + 8 * d;
            }
            words(canvas, on ? Local.of("копируется без ника автора") : Local.of("TikTok копирует только всё целиком"),
                    x, my + 56 * d, 11, skin.muted(), false, Paint.Align.LEFT);
            if (on) again();
        }
    }

    // ============================================================ notifications

    /** Three notifications as the phone would show them; the kinds turned off grey out and are struck. */
    static final class Notices extends Art {
        private final Eased[] shown = new Eased[3];
        private final boolean[] want = new boolean[3];
        private static final float[] last = {1, 1, 1};

        Notices(Context context, Skin skin) {
            super(context, skin, 196);
            for (int i = 0; i < 3; i++) { shown[i] = new Eased(last[i]); want[i] = true; }
        }

        Notices set(int kind, boolean on) {
            want[kind] = on;
            return this;
        }

        void settle() {
            for (int i = 0; i < 3; i++) {
                shown[i].to(this, want[i] ? 1f : 0f, 340);
                last[i] = want[i] ? 1f : 0f;
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            settle();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            String[][] said = {
                    {"narezany", Local.of("прислал(а) тебе видео")},
                    {"TikTok Shop", Local.of("скидки до 90% только сегодня")},
                    {"TikTok", Local.of("новое видео от тех, на кого ты подписан")}};
            float left = 20 * d, right = getWidth() - 20 * d, top = 14 * d;
            for (int i = 0; i < 3; i++) {
                float v = shown[i].value, y = top + i * 58 * d;
                box.set(left, y, right, y + 52 * d);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(surface(0.08f + 0.04f * v));
                canvas.drawRoundRect(box, 18 * d, 18 * d, paint);
                paint.setColor(faint(i == 0 ? Accent.colour() : 0xFF25C9D6, 0.35f + 0.65f * v));
                canvas.drawCircle(left + 24 * d, y + 26 * d, 12 * d, paint);
                words(canvas, said[i][0], left + 46 * d, y + 22 * d, 13, faint(skin.text, 0.35f + 0.65f * v), true, Paint.Align.LEFT);
                words(canvas, said[i][1], left + 46 * d, y + 40 * d, 12, faint(skin.muted(), 0.4f + 0.6f * v), false, Paint.Align.LEFT);
                if (v < 0.99f) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(2 * d);
                    paint.setColor(faint(0xFFE5484D, 1f - v));
                    canvas.drawLine(left + 40 * d, y + 26 * d, left + 40 * d + (right - left - 60 * d) * (1f - v), y + 26 * d, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
        }
    }

    // ============================================================ toys

    /** The fake "emergency update" window, small, its cross counting down. */
    static final class Emergency extends Art {
        Emergency(Context context, Skin skin) { super(context, skin, 176); }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), left = 40 * d, right = w - 40 * d, top = 16 * d, bottom = getHeight() - 16 * d;
            box.set(left, top, right, bottom);
            paint.setColor(0xFFFFFFFF);
            canvas.drawRoundRect(box, 18 * d, 18 * d, paint);
            words(canvas, "⚠️", w / 2f, top + 40 * d, 26, 0xFF000000, false, Paint.Align.CENTER);
            words(canvas, Local.of("СРОЧНОЕ ОБНОВЛЕНИЕ"), w / 2f, top + 66 * d, 14, 0xFFD32F2F, true, Paint.Align.CENTER);
            words(canvas, Local.of("ваша версия устарела"), w / 2f, top + 84 * d, 11, 0xFF555555, false, Paint.Align.CENTER);
            box.set(left + 18 * d, bottom - 50 * d, right - 18 * d, bottom - 16 * d);
            paint.setColor(0xFFD32F2F);
            canvas.drawRoundRect(box, 17 * d, 17 * d, paint);
            words(canvas, Local.of("ОБНОВИТЬ В ТЕЛЕГРАМЕ"), w / 2f, bottom - 28 * d, 12, 0xFFFFFFFF, true, Paint.Align.CENTER);
            int left5 = 5 - (int) ((clock() % 6f));
            String cross = left5 > 0 ? String.valueOf(left5) : "✕";
            words(canvas, cross, right - 16 * d, top + 22 * d, 13, 0xFF888888, true, Paint.Align.CENTER);
            again();
        }
    }

    /** TikTok's buttons tumbling down and piling up, over and over. */
    static final class Fall extends Art {
        Fall(Context context, Skin skin) { super(context, skin, 170); }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), floor = getHeight() - 16 * d;
            float t = (clock() % 4f) / 4f;
            String[] pieces = {"♥", "💬", "★", "➦", "+", "@", "♫", "🔍"};
            for (int i = 0; i < pieces.length; i++) {
                float start = i * 0.06f, local = Math.max(0f, Math.min(1f, (t - start) / 0.45f));
                float fall = local * local;
                float x = w * (0.15f + 0.1f * i) + (float) Math.sin(i * 1.7f) * 10 * d;
                float y = 20 * d + (floor - 20 * d - (i % 3) * 18 * d) * Math.min(1f, fall);
                if (local >= 1f) y += (float) Math.abs(Math.sin((t - start - 0.45f) * 18)) * -6 * d * Math.max(0f, 1f - (t - start - 0.45f) * 4);
                canvas.save();
                canvas.rotate(local * (i % 2 == 0 ? 160 : -120), x, y);
                words(canvas, pieces[i], x, y, 22, skin.text, true, Paint.Align.CENTER);
                canvas.restore();
            }
            paint.setColor(surface(0.15f));
            canvas.drawRect(20 * d, floor + 2 * d, w - 20 * d, floor + 4 * d, paint);
            again();
        }
    }

    /** The screamer, rehearsed: the fat cat swelling to fill the frame, now and then. */
    static final class Scream extends Art {
        private final Bitmap face;

        Scream(Context context, Skin skin) {
            super(context, skin, 170);
            face = Launcher.preview("tilcayo");
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float t = (clock() % 3f) / 3f;
            float pop = t < 0.7f ? 0f : (float) Math.sin((t - 0.7f) / 0.3f * Math.PI);
            float r = (26 + 54 * pop) * d;
            float shake = pop > 0.2f ? (float) Math.sin(clock() * 90) * 4 * d * pop : 0f;
            round(canvas, face, getWidth() / 2f + shake, getHeight() / 2f, r, 255);
            if (pop > 0.3f) words(canvas, "AAAAA!", getWidth() / 2f, getHeight() / 2f + r + 4 * d, 14 + 6 * pop,
                    0xFFE5484D, true, Paint.Align.CENTER);
            again();
        }
    }

    // ============================================================ ages

    /** Videos of different ages along a week; the ones older than the chosen limit go grey and are crossed. */
    static final class Ages extends Art {
        private static float last = 168f;
        private final Eased cut = new Eased(last);
        private float want;
        private static final float[] AGE = {1, 3, 7, 14, 22, 33, 50, 70, 96, 130, 160};

        Ages(Context context, Skin skin, int hours) {
            super(context, skin, 120);
            want = hours <= 0 ? 168f : hours;
        }

        void set(int hours) {
            want = hours <= 0 ? 168f : hours;
            cut.to(this, want, 420);
            last = want;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            set(want >= 168f ? 0 : Math.round(want));
        }

        /** Hours on a scale that gives the first day as much room as the rest of the week. */
        private float at(float hours, float left, float right) {
            float along = (float) (Math.log1p(hours) / Math.log1p(168));
            return left + (right - left) * along;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float left = 28 * d, right = getWidth() - 28 * d, y = 54 * d;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(surface(0.14f));
            canvas.drawRoundRect(new RectF(left, y - 2 * d, right, y + 2 * d), 2 * d, 2 * d, paint);
            float limit = cut.value, edge = at(limit, left, right);
            paint.setColor(faint(Accent.colour(), 0.35f));
            canvas.drawRoundRect(new RectF(left, y - 2 * d, edge, y + 2 * d), 2 * d, 2 * d, paint);
            for (float age : AGE) {
                float x = at(age, left, right);
                boolean kept = age <= limit + 0.01f;
                paint.setColor(kept ? Accent.colour() : surface(0.3f));
                box.set(x - 7 * d, y - 26 * d, x + 7 * d, y - 8 * d);
                canvas.drawRoundRect(box, 3 * d, 3 * d, paint);
                if (!kept) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1.6f * d);
                    paint.setColor(0xFFE5484D);
                    canvas.drawLine(box.left, box.top, box.right, box.bottom, paint);
                    canvas.drawLine(box.right, box.top, box.left, box.bottom, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
            if (limit < 167.5f) {
                paint.setColor(Accent.colour());
                canvas.drawRoundRect(new RectF(edge - 1.5f * d, y - 34 * d, edge + 1.5f * d, y + 10 * d), 1.5f * d, 1.5f * d, paint);
            }
            words(canvas, Local.of("сейчас"), left, y + 26 * d, 11, skin.muted(), false, Paint.Align.LEFT);
            words(canvas, Local.of("день"), at(24, left, right), y + 26 * d, 11, skin.muted(), false, Paint.Align.CENTER);
            words(canvas, Local.of("неделя"), right, y + 26 * d, 11, skin.muted(), false, Paint.Align.RIGHT);
        }
    }

    // ============================================================ downloads

    /** A saved video: TikTok's mark and the author's name bouncing in its corner, or a clean frame. */
    static final class Watermark extends Art {
        private static float last = 0f;
        private final Eased clean = new Eased(last);
        private float want;

        Watermark(Context context, Skin skin, boolean on) {
            super(context, skin, 170);
            want = on ? 1f : 0f;
        }

        void set(boolean on) {
            want = on ? 1f : 0f;
            clean.to(this, want, 380);
            last = want;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            set(want > 0.5f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float h = getHeight() - 20 * d, w = h * 0.62f, left = (getWidth() - w) / 2f, top = 10 * d;
            box.set(left, top, left + w, top + h);
            paint.setShader(new LinearGradient(left, top, left + w, top + h, Expressive.mix(0xFF2B2250, Accent.colour(), 0.3f),
                    0xFF0F3A44, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(box, 14 * d, 14 * d, paint);
            paint.setShader(null);
            float mark = 1f - clean.value;
            if (mark > 0.01f) {
                // the mark moves from corner to corner, as TikTok's does
                boolean low = (clock() % 4f) > 2f;
                float x = low ? left + w - 10 * d : left + 10 * d, y = low ? top + h - 28 * d : top + 22 * d;
                Paint.Align align = low ? Paint.Align.RIGHT : Paint.Align.LEFT;
                words(canvas, "\u266A TikTok", x, y, 13, faint(0xFFFFFFFF, 0.9f * mark), true, align);
                words(canvas, "@narezany", x, y + 15 * d, 10, faint(0xFFFFFFFF, 0.8f * mark), false, align);
                again();
            }
            if (clean.value > 0.01f) words(canvas, "\u2713", left + w / 2f, top + h / 2f + 10 * d, 28,
                    faint(0xFFFFFFFF, clean.value), true, Paint.Align.CENTER);
        }
    }

    // ============================================================ brightness

    /** The brightness slider: TikTok throws it to the top for the camera and HDR, or it stays where it was put. */
    static final class Bright extends Art {
        private boolean kept;

        Bright(Context context, Skin skin, boolean kept) {
            super(context, skin, 120);
            this.kept = kept;
        }

        void set(boolean kept) {
            this.kept = kept;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float left = 60 * d, right = getWidth() - 34 * d, y = 54 * d;
            float t = (clock() % 3f) / 3f;
            boolean hdr = t > 0.35f && t < 0.85f;
            float level = kept || !hdr ? 0.4f : 1f;
            // a sun that grows with the level
            float r = (7 + 6 * level) * d;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFC83D);
            canvas.drawCircle(32 * d, y, r * 0.6f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2 * d);
            paint.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                canvas.drawLine(32 * d + (float) Math.cos(a) * r * 0.85f, y + (float) Math.sin(a) * r * 0.85f,
                        32 * d + (float) Math.cos(a) * r * 1.2f, y + (float) Math.sin(a) * r * 1.2f, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(surface(0.15f));
            canvas.drawRoundRect(new RectF(left, y - 12 * d, right, y + 12 * d), 12 * d, 12 * d, paint);
            paint.setColor(0xFFFFC83D);
            canvas.drawRoundRect(new RectF(left, y - 12 * d, left + (right - left) * level, y + 12 * d), 12 * d, 12 * d, paint);
            words(canvas, hdr ? "HDR" : Local.of("видео"), getWidth() / 2f, y + 36 * d, 12,
                    hdr ? Accent.colour() : skin.muted(), true, Paint.Align.CENTER);
            again();
        }
    }

    // ============================================================ sound

    /** A speaker: the sound TikTok took away for copyright, gone, or there again. */
    static final class Sound extends Art {
        private boolean on;

        Sound(Context context, Skin skin, boolean on) {
            super(context, skin, 110);
            this.on = on;
        }

        void set(boolean on) {
            this.on = on;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f - 20 * d, cy = 46 * d;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(skin.text);
            path.reset();
            path.moveTo(cx - 16 * d, cy - 8 * d);
            path.lineTo(cx - 6 * d, cy - 8 * d);
            path.lineTo(cx + 6 * d, cy - 18 * d);
            path.lineTo(cx + 6 * d, cy + 18 * d);
            path.lineTo(cx - 6 * d, cy + 8 * d);
            path.lineTo(cx - 16 * d, cy + 8 * d);
            path.close();
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3 * d);
            paint.setStrokeCap(Paint.Cap.ROUND);
            if (on) {
                float t = clock();
                for (int i = 0; i < 3; i++) {
                    float pulse = (float) ((Math.sin(t * 4 - i) + 1) / 2);
                    paint.setColor(faint(Accent.colour(), 0.4f + 0.6f * pulse));
                    float r = (14 + i * 9) * d;
                    canvas.drawArc(new RectF(cx + 6 * d - r, cy - r, cx + 6 * d + r, cy + r), -40, 80, false, paint);
                }
                again();
            } else {
                paint.setColor(0xFFE5484D);
                canvas.drawLine(cx + 16 * d, cy - 10 * d, cx + 36 * d, cy + 10 * d, paint);
                canvas.drawLine(cx + 36 * d, cy - 10 * d, cx + 16 * d, cy + 10 * d, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            words(canvas, on ? Local.of("звук на месте") : Local.of("звук удалён по копирайту"), getWidth() / 2f, cy + 44 * d, 12,
                    skin.muted(), false, Paint.Align.CENTER);
        }
    }

    // ============================================================ dep

    /** The dep window floating over TikTok, drifting a little. */
    static final class DepWindow extends Art {
        private static float last = 0f;
        private final Eased shown = new Eased(last);
        private float want;

        DepWindow(Context context, Skin skin, boolean on) {
            super(context, skin, 150);
            want = on ? 1f : 0f;
        }

        void set(boolean on) {
            want = on ? 1f : 0f;
            shown.to(this, want, 360);
            last = want;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            set(want > 0.5f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float h = getHeight() - 20 * d, w = h * 0.49f, left = (getWidth() - w) / 2f, top = 10 * d;
            box.set(left, top, left + w, top + h);
            paint.setShader(new LinearGradient(0, top, 0, top + h, 0xFF2B2250, 0xFF0F2A30, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(box, 12 * d, 12 * d, paint);
            paint.setShader(null);
            float v = shown.value;
            if (v > 0.01f) {
                float drift = (float) Math.sin(clock() * 1.4f) * 3 * d;
                float bw = 52 * d, bh = 26 * d, bx = left + w - bw - 6 * d, by = top + 28 * d + drift;
                box.set(bx, by, bx + bw * v, by + bh);
                paint.setColor(faint(0xFF1E1E1E, 0.92f));
                canvas.drawRoundRect(box, 13 * d, 13 * d, paint);
                if (v > 0.6f) {
                    words(canvas, "\uD83C\uDFB0 2000", bx + bw / 2f, by + 17 * d, 11, 0xFFFFC83D, true, Paint.Align.CENTER);
                }
                again();
            }
        }
    }
}

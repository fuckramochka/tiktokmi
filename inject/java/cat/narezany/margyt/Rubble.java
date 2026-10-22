package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Random;

/**
 * A silly thing: TikTok's buttons, words and pictures come off the screen and
 * fall, roll with the phone and jump when it is shaken.
 *
 * Every piece is a picture of a real view, taken once; the view itself is made
 * see-through and kept that way. A tap on a piece, wherever it has rolled, is
 * handed to the view it came from, at the view's real place; a tap on the empty
 * place a piece left does nothing. The video underneath is left alone. Pieces
 * that turn up later (the next video's buttons) are thrown in as well, up to a
 * limit, and a piece whose view is no longer on the screen (the profile, once
 * the feed is open) is put away until it is back. Nothing is saved: it lasts
 * until TikTok is closed for good and opened again.
 */
final class Rubble {

    private Rubble() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int MOST = 90;
    private static final int TAG = 0x7e0c0338;

    /** Asked for from the settings: starts on the next TikTok screen that comes up. */
    private static volatile boolean armed;
    private static volatile boolean running;

    static boolean running() { return running; }

    static void arm(Activity settings) {
        armed = true;
        try { settings.finish(); } catch (Throwable ignored) {}
    }

    static void resumed(Activity activity) {
        if (!(armed || running) || activity == null || activity.getClass().getName().startsWith("cat.narezany.margyt.")) return;
        MAIN.postDelayed(() -> start(activity), 450L);
    }

    private static void start(Activity activity) {
        try {
            if (activity.isFinishing() || !activity.hasWindowFocus() && !armed) return;
            View decor = activity.getWindow().getDecorView();
            if (!(decor instanceof FrameLayout) || !decor.isAttachedToWindow()) return;
            FrameLayout screen = (FrameLayout) decor;
            for (int i = 0; i < screen.getChildCount(); i++) if (screen.getChildAt(i) instanceof World) return;
            armed = false;
            running = true;
            World world = new World(activity, screen);
            screen.addView(world, new FrameLayout.LayoutParams(-1, -1));
            world.setElevation(1000f);
            world.collect();
        } catch (Throwable error) {
            Diary.note("rubble: " + error);
        }
    }

    private static final class Piece {
        View source;
        Bitmap picture;
        float x, y, w, h, vx, vy, angle, spin, mass, alpha;
        /** Still lying where it came from, over its neighbours: it touches nothing until it is clear of them. */
        boolean ghost = true;
        /** Its view is not on the screen just now. */
        boolean away;
    }

    private static final class World extends View implements Choreographer.FrameCallback, SensorEventListener {
        private final FrameLayout screen;
        private final ArrayList<Piece> pieces = new ArrayList<>();
        private final IdentityHashMap<View, Boolean> taken = new IdentityHashMap<>();
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();
        private final SensorManager sensors;
        private final float unit;
        private float gx = 0, gy = 9.8f, lastShake;
        private long last, collected;
        private boolean ticking;

        World(Context context, FrameLayout screen) {
            super(context);
            this.screen = screen;
            unit = context.getResources().getDisplayMetrics().density;
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            sensors = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            try {
                Sensor sensor = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (sensor != null) sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
            } catch (Throwable ignored) {}
            tick();
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            try { if (sensors != null) sensors.unregisterListener(this); } catch (Throwable ignored) {}
            Choreographer.getInstance().removeFrameCallback(this);
            ticking = false;
        }


        private void tick() {
            if (ticking) return;
            ticking = true;
            Choreographer.getInstance().postFrameCallback(this);
        }

        // ------------------------------------------------------------ pieces

        void collect() {
            collected = android.os.SystemClock.uptimeMillis();
            View content = screen.findViewById(android.R.id.content);
            int[] budget = {6000};
            walk(content == null ? screen : content, 0, budget);
        }

        private void walk(View view, int depth, int[] budget) {
            if (pieces.size() >= MOST || view == null || view == this || depth > 40 || budget[0]-- <= 0) return;
            if (view.getVisibility() != VISIBLE || view.getAlpha() < 0.05f) return;
            if (view instanceof TextureView || view instanceof SurfaceView) return;
            if (taken.containsKey(view)) return;
            int w = view.getWidth(), h = view.getHeight();
            if (w <= 0 || h <= 0) return;
            int across = screen.getWidth(), down = screen.getHeight();
            boolean huge = w * (float) h > across * (float) down * 0.3f || w > across * 0.97f && h > unit * 140;
            boolean group = view instanceof ViewGroup && ((ViewGroup) view).getChildCount() > 0;
            boolean piece = !huge && (!group || view.isClickable() && w < across * 0.6f && h < unit * 120)
                    && w >= unit * 6 && h >= unit * 6
                    && (view instanceof TextView || view instanceof ImageView || view.isClickable() || view.getBackground() != null || !group);
            if (piece && !medium(view, 0)) {
                take(view);
                return;
            }
            if (group) {
                ViewGroup parent = (ViewGroup) view;
                for (int i = 0; i < parent.getChildCount(); i++) walk(parent.getChildAt(i), depth + 1, budget);
            }
        }

        /** Whether a video is somewhere inside: that stays where it is. */
        private boolean medium(View view, int depth) {
            if (view instanceof TextureView || view instanceof SurfaceView) return true;
            if (!(view instanceof ViewGroup) || depth > 12) return false;
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (medium(group.getChildAt(i), depth + 1)) return true;
            return false;
        }

        private void take(View view) {
            Rect seen = new Rect();
            if (!view.getGlobalVisibleRect(seen) || seen.width() < 4 || seen.height() < 4) return;
            int[] at = new int[2], origin = new int[2];
            view.getLocationOnScreen(at);
            getLocationOnScreen(origin);
            Bitmap picture;
            try {
                picture = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
                view.draw(new Canvas(picture));
            } catch (Throwable error) {
                return;
            }
            if (blank(picture)) { picture.recycle(); return; }
            Piece piece = new Piece();
            piece.source = view;
            piece.picture = picture;
            piece.w = view.getWidth();
            piece.h = view.getHeight();
            piece.x = at[0] - origin[0];
            piece.y = at[1] - origin[1];
            // it starts exactly where it was, at rest, and falls from there
            piece.spin = (random.nextFloat() - .5f) * 30;
            piece.alpha = view.getAlpha();
            piece.mass = Math.max(1f, piece.w * piece.h / (unit * unit * 100));
            pieces.add(piece);
            taken.put(view, Boolean.TRUE);
            view.setTag(TAG, Boolean.TRUE);
            view.setAlpha(0f);
        }

        private static boolean blank(Bitmap picture) {
            int w = picture.getWidth(), h = picture.getHeight();
            for (int i = 0; i < 25; i++) {
                int x = (i % 5) * (w - 1) / 4, y = (i / 5) * (h - 1) / 4;
                if ((picture.getPixel(x, y) >>> 24) > 16) return false;
            }
            // the samples missed it: look once along the middle
            for (int x = 0; x < w; x += Math.max(1, w / 40)) if ((picture.getPixel(x, h / 2) >>> 24) > 16) return false;
            return true;
        }

        // ------------------------------------------------------------ motion

        @Override public void onSensorChanged(SensorEvent event) {
            float ax = event.values[0], ay = event.values[1], az = event.values[2];
            // the screen's down is the phone's -y; tilting right sends things right
            gx = gx * .8f + (-ax) * .2f;
            gy = gy * .8f + ay * .2f;
            float force = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            long now = android.os.SystemClock.uptimeMillis();
            if (Math.abs(force - 9.8f) > 7f && now - lastShake > 120) {
                lastShake = now;
                for (Piece piece : pieces) {
                    piece.vx += (random.nextFloat() - .5f) * unit * 900 + ax * -unit * 40;
                    piece.vy += (random.nextFloat() - .7f) * unit * 900 + ay * unit * 40;
                    piece.spin += (random.nextFloat() - .5f) * 600;
                }
            }
        }

        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        @Override public void doFrame(long time) {
            ticking = false;
            if (!isAttachedToWindow()) return;
            float dt = last == 0 ? 1f / 60 : Math.min(1f / 30, (time - last) / 1e9f);
            last = time;
            try {
                long now = android.os.SystemClock.uptimeMillis();
                // what TikTok shows later comes apart too
                if (now - collected > 1500 && pieces.size() < MOST) collect();
                watch();
                for (int i = 0; i < 4; i++) step(dt / 4);
                for (Piece piece : pieces) if (!piece.away && piece.source.getAlpha() != 0f) piece.source.setAlpha(0f);
            } catch (Throwable error) {
                Diary.note("rubble frame: " + error);
            }
            invalidate();
            tick();
        }

        private final Rect seen = new Rect();

        /** Puts away the pieces whose views have left the screen, and gives up those that are gone for good. */
        private void watch() {
            for (int i = pieces.size() - 1; i >= 0; i--) {
                Piece p = pieces.get(i);
                if (!p.source.isAttachedToWindow()) {
                    // a recycled view may come back as something else: it gets its look back
                    p.source.setAlpha(p.alpha);
                    p.source.setTag(TAG, null);
                    taken.remove(p.source);
                    pieces.remove(i);
                    continue;
                }
                p.away = !p.source.isShown() || !p.source.getGlobalVisibleRect(seen);
            }
        }

        // ------------------------------------------------------------ taps

        private Piece held;
        private boolean swallowing, forwarding;
        private float downX, downY;

        @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
            if (forwarding) return false;
            int action = event.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                held = hit(event.getX(), event.getY());
                downX = event.getRawX(); downY = event.getRawY();
                if (held != null) return true;
                // the empty place a piece came from does nothing any more
                swallowing = leftBehind((int) event.getRawX(), (int) event.getRawY());
                return swallowing;
            }
            if (held != null) {
                if (action == android.view.MotionEvent.ACTION_UP) {
                    Piece tapped = held;
                    held = null;
                    if (Math.hypot(event.getRawX() - downX, event.getRawY() - downY) < unit * 12) tap(tapped);
                    else { tapped.vx += (event.getRawX() - downX) * 4; tapped.vy += (event.getRawY() - downY) * 4; }
                } else if (action == android.view.MotionEvent.ACTION_CANCEL) held = null;
                return true;
            }
            if (swallowing) {
                if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) swallowing = false;
                return true;
            }
            return false;
        }

        private Piece hit(float x, float y) {
            for (int i = pieces.size() - 1; i >= 0; i--) {
                Piece p = pieces.get(i);
                if (!p.away && x >= p.x && x <= p.x + p.w && y >= p.y && y <= p.y + p.h) return p;
            }
            return null;
        }

        private boolean leftBehind(int x, int y) {
            for (Piece p : pieces) if (!p.away && p.source.getGlobalVisibleRect(seen) && seen.contains(x, y)) return true;
            return false;
        }

        /** A tap at the middle of the piece's own view, where it really is, past this overlay. */
        private void tap(Piece piece) {
            int[] at = new int[2], origin = new int[2];
            piece.source.getLocationOnScreen(at);
            screen.getLocationOnScreen(origin);
            float x = at[0] - origin[0] + piece.source.getWidth() / 2f, y = at[1] - origin[1] + piece.source.getHeight() / 2f;
            long now = android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0);
            android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 40, android.view.MotionEvent.ACTION_UP, x, y, 0);
            forwarding = true;
            try { screen.dispatchTouchEvent(down); screen.dispatchTouchEvent(up); }
            catch (Throwable error) { Diary.note("rubble tap: " + error); }
            finally { forwarding = false; down.recycle(); up.recycle(); }
        }

        private void step(float dt) {
            float width = getWidth(), height = getHeight();
            float pull = unit * 160; // pixels per second squared for each m/s²
            for (Piece p : pieces) {
                if (p.away) continue;
                p.vx += gx * pull * dt;
                p.vy += gy * pull * dt;
                p.vx *= 0.999f;
                p.vy *= 0.999f;
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.angle += p.spin * dt;
                p.spin *= 0.985f;
                // the edges of the screen
                if (p.x < 0) { p.x = 0; p.vx = -p.vx * .35f; p.vy *= .92f; p.spin += p.vy * .02f; }
                if (p.x + p.w > width) { p.x = width - p.w; p.vx = -p.vx * .35f; p.vy *= .92f; p.spin -= p.vy * .02f; }
                if (p.y < 0) { p.y = 0; p.vy = -p.vy * .35f; p.vx *= .92f; }
                if (p.y + p.h > height) { p.y = height - p.h; p.vy = -p.vy * .35f; p.vx *= .92f; p.spin -= p.vx * .02f; }
            }
            // pieces push each other apart, the light ones more
            int n = pieces.size();
            // a piece that came off lying over others falls through them until it is clear;
            // pushing them apart at once is what flung everything across the screen
            for (int i = 0; i < n; i++) {
                Piece a = pieces.get(i);
                if (!a.ghost || a.away) continue;
                boolean clear = true;
                for (int j = 0; j < n && clear; j++) {
                    Piece b = pieces.get(j);
                    if (b == a || b.away) continue;
                    if (Math.min(a.x + a.w, b.x + b.w) > Math.max(a.x, b.x) && Math.min(a.y + a.h, b.y + b.h) > Math.max(a.y, b.y)) clear = false;
                }
                if (clear) a.ghost = false;
            }
            for (int i = 0; i < n; i++) {
                Piece a = pieces.get(i);
                if (a.ghost || a.away) continue;
                for (int j = i + 1; j < n; j++) {
                    Piece b = pieces.get(j);
                    if (b.ghost || b.away) continue;
                    float ox = Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x);
                    if (ox <= 0) continue;
                    float oy = Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y);
                    if (oy <= 0) continue;
                    float total = a.mass + b.mass, ka = b.mass / total, kb = a.mass / total;
                    if (ox < oy) {
                        float sign = a.x + a.w / 2 < b.x + b.w / 2 ? -1 : 1;
                        a.x += sign * ox * ka; b.x -= sign * ox * kb;
                        float relative = a.vx - b.vx;
                        if (relative * sign < 0) {
                            float impulse = relative * 1.3f;
                            a.vx -= impulse * ka; b.vx += impulse * kb;
                            float slide = (a.vy - b.vy) * .1f; a.vy -= slide * ka; b.vy += slide * kb;
                        }
                    } else {
                        float sign = a.y + a.h / 2 < b.y + b.h / 2 ? -1 : 1;
                        a.y += sign * oy * ka; b.y -= sign * oy * kb;
                        float relative = a.vy - b.vy;
                        if (relative * sign < 0) {
                            float impulse = relative * 1.3f;
                            a.vy -= impulse * ka; b.vy += impulse * kb;
                            float slide = (a.vx - b.vx) * .1f; a.vx -= slide * ka; b.vx += slide * kb;
                            a.spin += slide * .05f; b.spin -= slide * .05f;
                        }
                    }
                }
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            for (Piece p : pieces) {
                if (p.away || p.picture == null || p.picture.isRecycled()) continue;
                int save = canvas.save();
                // the boxes knock about square; the tilt is for the eye and stays small
                float tilt = (float) Math.toDegrees(Math.sin(Math.toRadians(p.angle))) * .35f;
                canvas.rotate(tilt, p.x + p.w / 2, p.y + p.h / 2);
                canvas.drawBitmap(p.picture, p.x, p.y, paint);
                canvas.restoreToCount(save);
            }
        }
    }
}

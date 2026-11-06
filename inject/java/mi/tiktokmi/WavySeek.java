package mi.tiktokmi;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import java.lang.ref.WeakReference;

/**
 * The feed's progress bar as Material 3 draws one now: the part already watched is a
 * wave in the accent colour that keeps moving while the video plays and lies flat when
 * it stops, then a gap, a small upright handle, and the rest as a plain faint line.
 *
 * Only with the Material 3 feed switched on. TikTok's own bar keeps working: only the
 * picture of it is swapped, and its own picture is put back when the switch goes off.
 */
final class WavySeek {

    private WavySeek() {}

    private static final int WATCHED = 0x7e0c0341;
    private static WeakReference<SeekBar> bar = new WeakReference<>(null);
    private static Drawable original;
    private static long looked;

    static void attach(Activity activity) {
        if (activity == null || !activity.getClass().getName().equals("com.ss.android.ugc.aweme.main.MainActivity")) return;
        final View root = activity.getWindow().getDecorView();
        if (Boolean.TRUE.equals(root.getTag(WATCHED))) return;
        root.setTag(WATCHED, Boolean.TRUE);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> check(root));
        root.post(() -> check(root));
    }

    private static void check(View root) {
        try {
            boolean wanted = Md3.isOn(Md3.KEY_FEED);
            SeekBar known = bar.get();
            if (known != null && (!known.isAttachedToWindow() || known.getRootView() != root)) known = null;
            if (!wanted) {
                if (known != null && known.getProgressDrawable() instanceof Wave && original != null) known.setProgressDrawable(original);
                return;
            }
            if (known == null) {
                long now = SystemClock.uptimeMillis();
                if (now - looked < 700) return;
                looked = now;
                View found = find(root, 0, new int[]{3000});
                if (!(found instanceof SeekBar)) return;
                known = (SeekBar) found;
                bar = new WeakReference<>(known);
            }
            if (!(known.getProgressDrawable() instanceof Wave)) {
                original = known.getProgressDrawable();
                Wave wave = new Wave(known);
                known.setProgressDrawable(wave);
            }
        } catch (Throwable error) {
            Diary.note("wavy seek: " + error);
        }
    }

    /** TikTok 47.2.41 names the feed's bar "vpz", inside "video_seek_bar". */
    private static View find(View view, int depth, int[] budget) {
        if (view == null || depth > 40 || budget[0]-- <= 0) return null;
        if (view instanceof SeekBar && "vpz".equals(Names.of(view))) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), depth + 1, budget);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class Wave extends Drawable {
        private final WeakReference<SeekBar> owner;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float unit;
        private float phase, amplitude;
        private long last;

        Wave(SeekBar seek) {
            owner = new WeakReference<>(seek);
            unit = seek.getResources().getDisplayMetrics().density;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
        }

        private java.lang.reflect.Method pausedAsk;
        private boolean asked;

        private boolean playing(SeekBar seek) {
            try {
                if (!asked) { asked = true; try { pausedAsk = seek.getClass().getMethod("getMPauseStatus"); } catch (Throwable ignored) {} }
                if (pausedAsk == null) return !seek.isPressed();
                Object paused = pausedAsk.invoke(seek);
                return !Boolean.TRUE.equals(paused) && !seek.isPressed();
            } catch (Throwable ignored) {
                return !seek.isPressed();
            }
        }

        @Override public void draw(Canvas canvas) {
            SeekBar seek = owner.get();
            if (seek == null) return;
            android.graphics.Rect box = getBounds();
            float left = box.left, right = box.right, cy = box.exactCenterY();
            int max = Math.max(1, seek.getMax());
            float at = left + (right - left) * Math.max(0, Math.min(max, seek.getProgress())) / max;
            boolean dragging = seek.isPressed();
            float thick = unit * (dragging ? 6f : 4f), gap = unit * 5f;
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1000f);
            last = now;
            boolean moving = playing(seek);
            // the wave rises while the video plays and settles flat when it stops
            float target = moving ? unit * 3f : 0f;
            amplitude += (target - amplitude) * Math.min(1f, dt * 8f);
            float length = unit * 28f;
            if (moving) phase = (phase + dt * length * 1.2f) % length;
            int accent = Accent.colour();

            // the rest of the video: a faint straight line to the end, and a dot that marks it
            line.setStrokeWidth(thick);
            line.setColor((accent & 0xFFFFFF) | 0x4D000000);
            float restFrom = Math.min(right, at + gap + unit * 2f);
            if (restFrom < right - thick / 2) canvas.drawLine(restFrom, cy, right - thick / 2, cy, line);
            line.setStyle(Paint.Style.FILL);
            line.setColor(accent);
            canvas.drawCircle(right - thick / 2, cy, thick / 2.4f, line);
            line.setStyle(Paint.Style.STROKE);

            // what has been watched: the wave
            float waveTo = at - gap;
            if (waveTo > left + thick / 2) {
                path.reset();
                float step = unit * 2f;
                boolean first = true;
                for (float x = left + thick / 2; x <= waveTo; x += step) {
                    float y = cy + (float) Math.sin((x + phase) / length * Math.PI * 2) * amplitude;
                    if (first) { path.moveTo(x, y); first = false; } else path.lineTo(x, y);
                }
                float y = cy + (float) Math.sin((waveTo + phase) / length * Math.PI * 2) * amplitude;
                path.lineTo(waveTo, y);
                line.setColor(accent);
                line.setStrokeWidth(thick);
                canvas.drawPath(path, line);
            }

            // the handle: a short upright bar
            line.setStyle(Paint.Style.FILL);
            line.setColor(accent);
            float half = unit * (dragging ? 2.5f : 2f), tall = unit * (dragging ? 12f : 9f);
            canvas.drawRoundRect(at - half, cy - tall, at + half, cy + tall, half, half, line);
            line.setStyle(Paint.Style.STROKE);

            // keep moving while there is something to move, a little under thirty frames a second
            if (seek.isShown() && (moving || Math.abs(amplitude - target) > 0.1f)) scheduleSelf(this::invalidateSelf, now + 33);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override protected boolean onLevelChange(int level) { invalidateSelf(); return true; }
    }
}

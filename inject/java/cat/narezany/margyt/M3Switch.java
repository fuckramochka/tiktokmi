package cat.narezany.margyt;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * A Material 3 switch, drawn here.
 *
 * The platform's own Switch is the one from 2014 and looks it. Material's is a
 * library this build cannot add -- adding one means adding resources, and
 * adding resources means rewriting a 25 MB resource table -- so the shape is
 * drawn instead: a 52 by 32 track, a small thumb that grows as it slides, and a
 * check inside it when it is on.
 */
public final class M3Switch extends View {

    public interface OnChanged {
        void onChanged(boolean checked);
    }

    private static final int TRACK_WIDTH = 52;
    private static final int TRACK_HEIGHT = 32;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    private int accent = Accent.TIKTOK;
    private int off = 0x66888888;
    private int surface = 0xFF000000;

    private boolean checked;
    private float position;  // 0 off, 1 on
    private OnChanged listener;

    public M3Switch(Context context) {
        super(context);
        setClickable(true);
    }

    public void colours(int accent, int off, int surface) {
        this.accent = accent;
        this.off = off;
        this.surface = surface;
        invalidate();
    }

    public void setChecked(boolean value) {
        setChecked(value, false);
    }

    public void setChecked(boolean value, boolean animate) {
        if (checked == value && (position == (value ? 1f : 0f))) return;
        checked = value;
        if (!animate) {
            position = value ? 1f : 0f;
            invalidate();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(position, value ? 1f : 0f);
        animator.setDuration(160);
        animator.addUpdateListener(a -> {
            position = (Float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public boolean isChecked() {
        return checked;
    }

    public void setOnChanged(OnChanged listener) {
        this.listener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && isEnabled()) {
            setChecked(!checked, true);
            if (listener != null) listener.onChanged(checked);
            performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(dp(TRACK_WIDTH), dp(TRACK_HEIGHT));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float height = dp(TRACK_HEIGHT);
        float width = dp(TRACK_WIDTH);
        float radius = height / 2f;
        float top = (getHeight() - height) / 2f;

        // the track: outlined when off, filled with the accent when on
        box.set(0, top, width, top + height);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(withAlpha(surface, 255), accent, position));
        canvas.drawRoundRect(box, radius, radius, paint);
        if (position < 1f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(withAlpha(off, Math.round(255 * (1f - position))));
            box.inset(dp(1), dp(1));
            canvas.drawRoundRect(box, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        // the thumb: small and grey, growing into a white circle as it slides
        float small = dp(16) / 2f, large = dp(24) / 2f;
        float thumb = small + (large - small) * position;
        float travel = width - dp(32);
        float centre = dp(16) + travel * position;
        paint.setColor(position < 0.5f
                ? blend(off, 0xFFFFFFFF, position * 2f)
                : 0xFFFFFFFF);
        canvas.drawCircle(centre, top + height / 2f, thumb, paint);

        if (position > 0.5f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(withAlpha(accent, Math.round(255 * (position - 0.5f) * 2f)));
            float y = top + height / 2f;
            float unit = dp(3);
            canvas.drawLine(centre - unit, y, centre - unit / 3f, y + unit, paint);
            canvas.drawLine(centre - unit / 3f, y + unit, centre + unit * 1.2f, y - unit, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private static int withAlpha(int colour, int alpha) {
        return (colour & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static int blend(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.argb(
                Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t),
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

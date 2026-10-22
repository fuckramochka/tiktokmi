package cat.narezany.margyt;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * A Material 3 switch, drawn here.
 *
 * The platform's own Switch is the one from 2014 and looks it. Material's is a
 * library this build cannot add -- adding one means adding resources, and
 * adding resources means rewriting a 25 MB resource table -- so the shape is
 * drawn instead, to the Compose tokens: a 52 by 32 track with a 2dp outline
 * while off, a 16dp handle that grows to 24 when on and to 28 under the finger,
 * and a check on the handle when on, as Android's own settings draw it.
 */
public final class M3Switch extends View {

    /** Kept public so a screen that has to change its structure can wait for the motion. */
    public static final long ANIMATION_DURATION_MS = 300L;

    public interface OnChanged {
        void onChanged(boolean checked);
    }

    private static final int TRACK_WIDTH = 52;
    private static final int TRACK_HEIGHT = 32;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    // The off colours follow the card the switch sits on, not the wallpaper or
    // the system theme: only how light the card is matters.
    private boolean lightSurface;
    private int surface = 0xFF1D1D1D;

    private boolean checked;
    private float position;  // 0 off, 1 on
    private OnChanged listener;
    private ValueAnimator animator;
    private boolean interruptingAnimation;
    private boolean dragging;
    private float downX,downY,downPosition;
    private final GlassOptics glassTrack=new GlassOptics(),glassThumb=new GlassOptics();
    private final RectF glassBox=new RectF();

    public M3Switch(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
    }

    public void colours(int accent, int off, int surface) {
        // The "on" colour is read from Accent when drawn, so a change of accent
        // shows without this being called again.
        lightSurface = luma(surface) > 0.55f;
        this.surface = surface;
        invalidate();
    }

    public void setChecked(boolean value) {
        setChecked(value, false);
    }

    public void setChecked(boolean value, boolean animate) {
        if (checked == value && (position == (value ? 1f : 0f))) return;
        if (animator != null) {
            // Keep the interpolated position. Snapping to the old or new end
            // point here is visible when someone taps the row twice quickly.
            interruptingAnimation = true;
            animator.cancel();
            animator = null;
            interruptingAnimation = false;
        }
        checked = value;
        if (!animate || !Motion.enabled(getContext())) {
            position = value ? 1f : 0f;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(position, value ? 1f : 0f);
        animator.setDuration(Design.fluent()?180L:ANIMATION_DURATION_MS);
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f));
        animator.addUpdateListener(a -> {
            position = (Float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!interruptingAnimation && animation == animator) {
                    position = checked ? 1f : 0f;
                    animator = null;
                    invalidate();
                }
            }
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
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX=event.getX();downY=event.getY();downPosition=position;dragging=false;
                setPressed(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                {
                    float delta=event.getX()-downX;
                    if(!dragging && Math.abs(delta)>dp(4) && Math.abs(delta)>Math.abs(event.getY()-downY)){
                        dragging=true;if(animator!=null){interruptingAnimation=true;animator.cancel();animator=null;interruptingAnimation=false;}
                        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if(dragging){position=Math.max(0,Math.min(1,downPosition+delta/dp(20)*(getLayoutDirection()==LAYOUT_DIRECTION_RTL?-1:1)));invalidate();}
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                if(dragging){dragging=false;setChecked(checked,true);}
                return true;
            case MotionEvent.ACTION_UP:
                boolean inside = event.getX() >= 0 && event.getX() < getWidth()
                        && event.getY() >= 0 && event.getY() < getHeight();
                setPressed(false);
                if(dragging){
                    dragging=false;boolean next=position>=.5f;boolean changed=next!=checked;
                    setChecked(next,true);if(changed && listener!=null)listener.onChanged(next);super.performClick();return true;
                }
                if (inside) performClick();
                return true;
            default:
                return true;
        }
    }

    /**
     * The one place the switch changes, so everything that can press it works.
     *
     * TalkBack and a keyboard call this and never touch the screen; the toggle
     * used to live in the touch handler, where neither of them could reach it.
     */
    @Override
    public boolean performClick() {
        if (!isEnabled()) return false;
        setChecked(!checked, true);
        if (listener != null) listener.onChanged(checked);
        super.performClick();
        return true;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(checked);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(dp(Design.fluent()?42:TRACK_WIDTH), dp(Design.fluent()?22:TRACK_HEIGHT));
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        // the handle grows while held, and nothing else redraws it then
        invalidate();
    }

    private final android.graphics.Path tick = new android.graphics.Path();

    /** The Material 3 switch. */
    @Override
    protected void onDraw(Canvas canvas) {
        if(Design.fluent()){drawFluent(canvas);return;}
        if(Design.glass()){drawGlass(canvas);return;}
        float w = getWidth(), h = getHeight();
        float eased = position * position * (3f - 2f * position);
        int accent = on(Accent.colour());
        int onAccent = luma(accent) > 0.59f ? 0xFF111111 : 0xFFFFFFFF;
        // surface container highest and outline, taken from the card it sits on
        int track = Expressive.mix(surface, lightSurface ? 0xFF000000 : 0xFFFFFFFF, lightSurface ? 0.10f : 0.13f);
        int outline = lightSurface ? 0xFF79797F : 0xFF939399;

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(track, accent, eased));
        box.set(0, 0, w, h);
        canvas.drawRoundRect(box, h / 2f, h / 2f, paint);
        if (eased < 1f) {
            float half = dp(1f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2f));
            paint.setColor(withAlpha(outline, Math.round(255 * (1f - eased))));
            box.set(half, half, w - half, h - half);
            canvas.drawRoundRect(box, h / 2f - half, h / 2f - half, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        float along = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? 1f - position : position;
        float cx = h / 2f + (w - h) * along, cy = h / 2f;
        float size = isPressed() ? 28f : 16f + 8f * eased;
        paint.setColor(blend(outline, onAccent, eased));
        canvas.drawCircle(cx, cy, dp(size) / 2f, paint);

        if (eased > 0.5f) {
            // a 16dp check in the selected handle
            float k = dp(16f) / 24f;
            tick.reset();
            tick.moveTo(cx - 6.5f * k, cy + 0.5f * k);
            tick.lineTo(cx - 2f * k, cy + 5f * k);
            tick.lineTo(cx + 7f * k, cy - 4.5f * k);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(withAlpha(accent, Math.round(255 * Math.min(1f, (eased - 0.5f) * 2f))));
            canvas.drawPath(tick, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    /**
     * The accent, unless it would vanish into the card: some devices feed a
     * white accent back in, and a white track on a white card cannot be told
     * from nothing. Then "on" is black on a light card and white on a dark one.
     */
    private int on(int accent) {
        if (Math.abs(luma(accent) - luma(surface)) >= 0.2f) return accent;
        return lightSurface ? 0xFF111111 : 0xFFFFFFFF;
    }

    private static float luma(int colour) {
        return (0.299f * Color.red(colour) + 0.587f * Color.green(colour) + 0.114f * Color.blue(colour)) / 255f;
    }

    private static int withAlpha(int colour, int alpha) {
        return (colour & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private void drawFluent(Canvas canvas){
        if(GlassBackdrop.capturing)return;
        box.set(dp(1),dp(1),getWidth()-dp(1),getHeight()-dp(1));
        paint.setStyle(Paint.Style.FILL);paint.setShader(null);
        paint.setColor(blend(lightSurface?0xffe5e5e5:0xff383838,Accent.colour(),position));
        canvas.drawRoundRect(box,dp(11),dp(11),paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));
        paint.setColor(lightSurface?0x70404040:0x70ffffff);canvas.drawRoundRect(box,dp(11),dp(11),paint);
        paint.setStyle(Paint.Style.FILL);
        float p=getLayoutDirection()==LAYOUT_DIRECTION_RTL?1-position:position;
        float x=dp(11)+(getWidth()-dp(22))*p;
        paint.setColor(blend(lightSurface?0xff555555:0xffdedede,0xffffffff,position));
        canvas.drawCircle(x,getHeight()/2f,dp(isPressed()?7.4f:6.5f),paint);
    }

    private void drawGlass(Canvas canvas){
        if(GlassBackdrop.capturing)return;
        glassBox.set(dp(1),dp(1),getWidth()-dp(1),getHeight()-dp(1));
        int off=lightSurface?0xffd4d8df:0xff343a43;
        glassTrack.draw(canvas,this,glassBox,glassBox.height()/2f,glassBox.height()/2f,off,isPressed()?.6f:0);
        paint.setShader(null);paint.setColor(withAlpha(Accent.colour(),Math.round(230*position)));
        canvas.drawRoundRect(glassBox,glassBox.height()/2f,glassBox.height()/2f,paint);
        float p=getLayoutDirection()==LAYOUT_DIRECTION_RTL?1-position:position;
        float centre=dp(16)+(getWidth()-dp(32))*p;
        float half=dp(isPressed()?14.2f:13f);
        glassBox.set(centre-half,dp(3),centre+half,getHeight()-dp(3));
        glassThumb.draw(canvas,this,glassBox,dp(13),dp(13),0xffffffff,isPressed()?1:0);
        paint.setColor(0xafffffff);canvas.drawRoundRect(glassBox,dp(13),dp(13),paint);
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

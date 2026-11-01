package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;

import java.util.ArrayList;

/**
 * Letters that spring in as they are typed, letters that fly off as they are deleted, and a cursor
 * that glides after the text. Ported from LotusTok (@saintcursed) and done again.
 *
 * Only the box that has the focus is looked after: it is found when the focus comes to it, not by
 * walking the screen. TikTok's text is left as it is laid out; a letter just typed is made clear for
 * the moment it is animated (a colour span, which takes no room) and drawn moving on an overlay of
 * the box, in its place. Nothing is drawn again once nothing moves.
 */
public final class TypingAnim {

    static final String ON = "typing_anim", BLUR = "typing_anim_blur", SLIDE = "typing_anim_slide",
            SCALE = "typing_anim_scale", ROTATE = "typing_anim_rotate", GHOST = "typing_anim_ghost",
            CURSOR = "typing_anim_cursor", SPEED = "typing_anim_speed", CURVE = "typing_anim_curve";

    private static final long[] DURATIONS = {220L, 380L, 620L};
    private static final float[] TAU = {40f, 75f, 120f};
    private static final int TAG_MINE = 0x7f5a0121, TAG_WATCHED = 0x7f5a0122, TAG_CARET = 0x7f5a0123, TAG_CARET_WAS = 0x7f5a0124;

    private TypingAnim() {}

    private static SharedPreferences prefs() {
        Context context = Margy.context();
        return context == null ? null : context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }

    static boolean on(String key) {
        SharedPreferences prefs = prefs();
        return prefs != null && prefs.getBoolean(key, false);
    }

    static void set(String key, boolean value) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(key, value).apply();
    }

    private static int number(String key, int fallback, int count) {
        SharedPreferences prefs = prefs();
        int at = prefs == null ? fallback : prefs.getInt(key, fallback);
        return Math.max(0, Math.min(count - 1, at));
    }

    static int speed() {
        return number(SPEED, 1, DURATIONS.length);
    }

    static void setSpeed(int at) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(SPEED, Math.max(0, Math.min(DURATIONS.length - 1, at))).apply();
    }

    static void setCurve(int at) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(CURVE, Math.max(0, Math.min(3, at))).apply();
    }

    static void nextSpeed() {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(SPEED, (speed() + 1) % DURATIONS.length).apply();
    }

    static String speedName() {
        return new String[] {Local.of("быстро"), Local.of("плавно"), Local.of("медленно")}[speed()];
    }

    static int curve() {
        return number(CURVE, 3, 4);
    }

    static void nextCurve() {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(CURVE, (curve() + 1) % 4).apply();
    }

    static String curveName() {
        return new String[] {Local.of("мягкая"), Local.of("пружина"), Local.of("резкая"), Local.of("выскок")}[curve()];
    }

    /** How long a letter takes to come in. */
    static long duration() {
        long base = DURATIONS[speed()];
        return curve() == 3 ? base * 3 / 2 : base;
    }

    /** How long a deleted letter takes to go. */
    static long leaving() {
        return DURATIONS[speed()];
    }

    /** How far behind the cursor glides, as a time constant in milliseconds. */
    static float glide() {
        return TAU[speed()];
    }

    static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float left = 1f - t;
        switch (curve()) {
            case 1: {
                float c = 2.2f, u = t - 1f;
                return 1f + (c + 1f) * u * u * u + c * u * u;
            }
            case 2:
                return 1f - left * left * left * left * left;
            case 3:
                return 1f - (float) (Math.exp(-5.2 * t) * Math.cos(2.0 * Math.PI * 1.35 * t));
            default:
                return 1f - left * left * left;
        }
    }

    // ------------------------------------------------------------ drawing one letter

    /** What the switches are now, read once a frame rather than once a letter. */
    static final class Look {
        boolean blur, slide, scale, rotate;

        static Look now() {
            Look look = new Look();
            look.blur = on(BLUR);
            look.slide = on(SLIDE);
            look.scale = on(SCALE);
            look.rotate = on(ROTATE);
            return look;
        }
    }

    private static final BlurMaskFilter[] BLURS = new BlurMaskFilter[64];

    static BlurMaskFilter blur(int radius) {
        if (radius < 1) return null;
        radius = Math.min(BLURS.length - 1, radius);
        BlurMaskFilter known = BLURS[radius];
        if (known == null) BLURS[radius] = known = new BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL);
        return known;
    }

    /**
     * A letter coming in, t from 0 to 1, drawn with its baseline at y and its middle at x.
     * The same for the box and for the picture in the settings.
     */
    static void arrive(Canvas canvas, Paint paint, String letter, float x, float y, float size, float t, float tilt, Look look, int alpha) {
        float e = ease(t);
        float calm = Math.max(0f, Math.min(1f, t * 3.2f));
        float width = paint.measureText(letter);
        paint.setAlpha(Math.round(alpha * calm));
        paint.setMaskFilter(look.blur ? blur(Math.round(size * 0.07f * (1f - Math.min(1f, t * 2f)))) : null);
        canvas.save();
        canvas.translate(x, y);
        if (look.slide) canvas.translate(0f, size * 0.7f * (1f - e));
        if (look.scale) {
            float s = Math.max(0.05f, curve() == 3 ? e : 0.2f + 0.8f * e);
            canvas.scale(s, s);
        }
        if (look.rotate) canvas.rotate(tilt * (1f - e));
        canvas.drawText(letter, -width / 2f, 0f, paint);
        canvas.restore();
        paint.setMaskFilter(null);
        paint.setAlpha(alpha);
    }

    /** A deleted letter going up and away, t from 0 to 1. */
    static void depart(Canvas canvas, Paint paint, String letter, float x, float y, float size, float t, Look look, int alpha) {
        float e = 1f - (1f - t) * (1f - t);
        float width = paint.measureText(letter);
        paint.setAlpha(Math.round(alpha * (1f - t) * (1f - t)));
        paint.setMaskFilter(look.blur ? blur(Math.max(1, Math.round(size * 0.2f * e))) : null);
        canvas.save();
        canvas.translate(x, y - size * 0.4f * e);
        float s = 1f - 0.4f * e;
        canvas.scale(s, s);
        canvas.drawText(letter, -width / 2f, 0f, paint);
        canvas.restore();
        paint.setMaskFilter(null);
        paint.setAlpha(alpha);
    }

    // ------------------------------------------------------------ finding the box

    static void attach(final Activity activity) {
        if (Version.LITE) return;
        try {
            final View decor = activity.getWindow().getDecorView();
            if (decor.getTag(TAG_MINE) == null) {
                decor.setTag(TAG_MINE, Boolean.TRUE);
                decor.getViewTreeObserver().addOnGlobalFocusChangeListener(new ViewTreeObserver.OnGlobalFocusChangeListener() {
                    @Override public void onGlobalFocusChanged(View was, View now) {
                        try {
                            if (now instanceof EditText) watch((EditText) now);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
            View focused = activity.getCurrentFocus();
            if (focused instanceof EditText) watch((EditText) focused);
        } catch (Throwable error) {
            Diary.note("typing animation attach: " + error);
        }
    }

    private static void watch(EditText edit) {
        Object known = edit.getTag(TAG_WATCHED);
        Watcher watcher;
        if (known instanceof Watcher) {
            watcher = (Watcher) known;
        } else {
            if (!on(ON) && !on(CURSOR)) return;
            watcher = new Watcher(edit);
            edit.setTag(TAG_WATCHED, watcher);
            edit.addTextChangedListener(watcher);
            edit.getOverlay().add(watcher.layer);
        }
        boolean glide = on(ON) && on(CURSOR);
        if (glide != (edit.getTag(TAG_CARET) != null)) caret(edit, glide);
        edit.postInvalidateOnAnimation();
    }

    private static void caret(EditText edit, boolean hide) {
        edit.setTag(TAG_CARET, hide ? Boolean.TRUE : null);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            if (hide) {
                if (edit.getTag(TAG_CARET_WAS) == null) edit.setTag(TAG_CARET_WAS, new Object[] {edit.getTextCursorDrawable()});
                android.graphics.drawable.GradientDrawable none = new android.graphics.drawable.GradientDrawable();
                none.setColor(0);
                none.setSize(1, 1);
                edit.setTextCursorDrawable(none);
            } else {
                Object[] was = (Object[]) edit.getTag(TAG_CARET_WAS);
                if (was != null) {
                    edit.setTextCursorDrawable((Drawable) was[0]);
                    edit.setTag(TAG_CARET_WAS, null);
                }
            }
        } else {
            edit.setCursorVisible(!hide);
        }
    }

    // ------------------------------------------------------------ the box

    /** A letter being animated in: where it is in the text, and the span that keeps TikTok's copy clear meanwhile. */
    private static final class Pop {
        final ForegroundColorSpan clear = new ForegroundColorSpan(0);
        final long began;
        final float tilt;

        Pop(long began) {
            this.began = began;
            this.tilt = (began % 2 == 0 ? 1f : -1f) * (6f + (began % 7));
        }
    }

    private static final class Watcher implements TextWatcher {
        private final EditText view;
        final Layer layer;
        private int start, before, count;

        Watcher(EditText view) {
            this.view = view;
            this.layer = new Layer(view);
        }

        @Override
        public void beforeTextChanged(CharSequence s, int st, int c, int after) {
            if (c <= after || c - after > 24 || !on(ON) || !on(GHOST)) return;
            try {
                layer.leave(s.subSequence(st + after, st + c).toString(), st + after);
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void onTextChanged(CharSequence s, int st, int b, int c) {
            start = st;
            before = b;
            count = c;
        }

        @Override
        public void afterTextChanged(Editable text) {
            if (count <= before || count - before > 24 || !on(ON)) return;
            int from = start + before, to = Math.min(text.length(), start + count);
            long now = SystemClock.uptimeMillis();
            try {
                for (int i = from; i < to; ) {
                    int next = Math.min(to, i + Character.charCount(Character.codePointAt(text, i)));
                    Pop pop = new Pop(now);
                    text.setSpan(pop.clear, i, next, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    layer.pops.add(pop);
                    i = next;
                }
                while (layer.pops.size() > 48) layer.finish(layer.pops.get(0));
            } catch (Throwable ignored) {
            }
            view.postInvalidateOnAnimation();
        }
    }

    private static final class Layer extends Drawable {
        private final EditText view;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        final ArrayList<Pop> pops = new ArrayList<Pop>();
        private final ArrayList<Gone> gone = new ArrayList<Gone>();
        private float x, y, h;
        private boolean placed;
        private long last, moved;
        private float tx = -1, ty = -1;

        Layer(EditText view) {
            this.view = view;
            setBounds(0, 0, 20000, 20000);
        }

        private static final class Gone {
            final String text;
            final float x, y, size;
            final long began = SystemClock.uptimeMillis();

            Gone(String text, float x, float y, float size) {
                this.text = text;
                this.x = x;
                this.y = y;
                this.size = size;
            }
        }

        /** The letter's span comes off: TikTok draws it again itself. */
        void finish(Pop pop) {
            pops.remove(pop);
            Editable text = view.getText();
            if (text != null) text.removeSpan(pop.clear);
        }

        void leave(String text, int at) {
            Layout layout = view.getLayout();
            if (layout == null || text.length() == 0 || at < 0 || at > view.length()) return;
            int line = layout.getLineForOffset(at);
            float gx = layout.getPrimaryHorizontal(at) + view.getCompoundPaddingLeft() - view.getScrollX();
            float gy = layout.getLineBaseline(line) + view.getTotalPaddingTop() - view.getScrollY();
            float width = view.getPaint().measureText(text);
            gone.add(new Gone(text, gx + width / 2f, gy, view.getTextSize()));
            if (gone.size() > 40) gone.remove(0);
            view.postInvalidateOnAnimation();
        }

        private void dress() {
            paint.set(view.getPaint());
            paint.setAntiAlias(true);
            paint.setColor(view.getCurrentTextColor());
        }

        @Override
        public void draw(Canvas canvas) {
            long now = SystemClock.uptimeMillis();
            boolean again = false;
            Look look = null;
            Editable text = view.getText();
            Layout layout = view.getLayout();
            if (!pops.isEmpty()) {
                long length = duration();
                look = Look.now();
                dress();
                int alpha = paint.getAlpha();
                for (int i = pops.size() - 1; i >= 0; i--) {
                    Pop pop = pops.get(i);
                    float t = (now - pop.began) / (float) length;
                    int from = text == null ? -1 : text.getSpanStart(pop.clear), to = text == null ? -1 : text.getSpanEnd(pop.clear);
                    if (t >= 1f || from < 0 || to <= from || layout == null) {
                        finish(pop);
                        continue;
                    }
                    again = true;
                    int line = layout.getLineForOffset(from);
                    String letter = text.subSequence(from, to).toString();
                    float width = paint.measureText(letter);
                    float lx = layout.getPrimaryHorizontal(from) + view.getCompoundPaddingLeft() - view.getScrollX();
                    float ly = layout.getLineBaseline(line) + view.getTotalPaddingTop() - view.getScrollY();
                    arrive(canvas, paint, letter, lx + width / 2f, ly, view.getTextSize(), t, pop.tilt, look, alpha);
                }
            }
            if (!gone.isEmpty()) {
                if (look == null) look = Look.now();
                dress();
                int alpha = paint.getAlpha();
                long length = leaving();
                for (int i = gone.size() - 1; i >= 0; i--) {
                    Gone g = gone.get(i);
                    float t = (now - g.began) / (float) length;
                    if (t >= 1f) {
                        gone.remove(i);
                        continue;
                    }
                    again = true;
                    depart(canvas, paint, g.text, g.x, g.y, g.size, t, look, alpha);
                }
            }
            if (view.getTag(TAG_CARET) != null && view.hasFocus() && layout != null) {
                if (cursor(canvas, now, layout)) again = true;
                else view.postInvalidateDelayed(120);
            } else {
                placed = false;
            }
            if (again) view.postInvalidateOnAnimation();
        }

        /** Draws the gliding cursor; true while it is still on its way. */
        private boolean cursor(Canvas canvas, long now, Layout layout) {
            int from = view.getSelectionStart(), to = view.getSelectionEnd();
            if (to < 0 || from != to) return false;
            int line = layout.getLineForOffset(to);
            float goalX = layout.getPrimaryHorizontal(to) + view.getCompoundPaddingLeft() - view.getScrollX();
            float goalY = layout.getLineTop(line) + view.getTotalPaddingTop() - view.getScrollY();
            float goalH = layout.getLineBottom(line) - layout.getLineTop(line);
            long dt = Math.max(1L, Math.min(50L, now - last));
            last = now;
            if (!placed) {
                x = goalX;
                y = goalY;
                h = goalH;
                placed = true;
                moved = now;
            }
            float k = 1f - (float) Math.exp(-dt / glide());
            x += (goalX - x) * k;
            y += (goalY - y) * k;
            h += (goalH - h) * k;
            if (Math.abs(goalX - tx) > 0.5f || Math.abs(goalY - ty) > 0.5f) {
                tx = goalX;
                ty = goalY;
                moved = now;
            }
            boolean going = Math.abs(goalX - x) > 0.3f || Math.abs(goalY - y) > 0.3f;
            // a cursor at rest blinks, half a second on and half off
            float idle = (now - moved) / 1000f;
            boolean shown = going || idle < 0.5f || ((int) ((idle - 0.5f) / 0.5f)) % 2 == 0;
            if (shown) {
                paint.reset();
                paint.setAntiAlias(true);
                paint.setColor(Accent.colour() | 0xFF000000);
                float w = view.getResources().getDisplayMetrics().density * 2.2f;
                rect.set(x - w / 2f, y + h * 0.08f, x + w / 2f, y + h * 0.92f);
                canvas.drawRoundRect(rect, w, w, paint);
            }
            return going;
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}

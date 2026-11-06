package mi.tiktokmi;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

/** One appearance choice shared by every mod-owned surface. */
final class Design {
    static final String KEY = "ui_design";
    private static volatile String chosen;
    private static volatile UiStyle style;
    private static final int OWNED = 0x4d674c31;
    private static final java.util.WeakHashMap<Drawable, GlassOptics> OPTICS = new java.util.WeakHashMap<>();
    private Design() {}

    /**
     * The style in use: one of the three built in, or one somebody made in a .mtui
     * file (UiStyle). Everything that differs between styles reads its numbers here.
     */
    static UiStyle style() {
        UiStyle known=style;
        if(known!=null)return known;
        String key=key();
        UiStyle found=UiStyle.custom(key)?UiStyle.load(Margy.context(),key):builtIn(key);
        if(found==null)found=UiStyle.MATERIAL;
        if(Margy.context()!=null)style=found;
        return found;
    }

    private static UiStyle builtIn(String key) {
        return "glass".equals(key)?UiStyle.LIQUID:"fluent".equals(key)?UiStyle.ACRYLIC:UiStyle.MATERIAL;
    }

    /** What is chosen: "md3", "glass", "fluent", or "custom:" and an installed style's id. */
    static String key() {
        String known=chosen;
        if(known!=null)return known;
        Context context=Margy.context();
        if(context==null)return "md3";
        String saved=context.getSharedPreferences(Margy.PREFS,0).getString(KEY,"md3");
        chosen=valid(saved)?saved:"md3";
        return chosen;
    }

    /** The material the surfaces are made of, which the code that draws them branches on. */
    static String mode() {
        int base=style().base;
        return base==UiStyle.GLASS?"glass":base==UiStyle.FLUENT?"fluent":"md3";
    }

    static boolean valid(String key) {
        if("md3".equals(key) || "glass".equals(key) || "fluent".equals(key))return true;
        return UiStyle.custom(key) && UiStyle.load(Margy.context(),key)!=null;
    }
    static boolean glass() { return style().base==UiStyle.GLASS; }
    static boolean fluent() { return style().base==UiStyle.FLUENT; }
    static boolean frosted() { return glass() || fluent(); }
    /** Switches, sliders, badges and loading, which a style may take from another one. */
    static boolean controlsGlass() { return style().controls==UiStyle.GLASS; }
    static boolean controlsFluent() { return style().controls==UiStyle.FLUENT; }
    static boolean controlsFrosted() { return controlsGlass() || controlsFluent(); }

    static void choose(Context context,String key) {
        if(!valid(key))throw new IllegalArgumentException("Unknown interface style");
        context.getSharedPreferences(Margy.PREFS,0).edit().putString(KEY,key).apply();
        chosen=key;style=null;
        OPTICS.clear();GlassBackdrop.changed();
    }

    static void reload() { chosen=null;style=null;OPTICS.clear();GlassBackdrop.changed(); }
    static String name() { return style().name; }
    static String caption(String value) { return value.replace("Material 3",name()); }

    static int pageColour(Context context){
        int custom=AppearanceColors.get(AppearanceColors.BACKGROUND);
        if(custom!=0)return custom;
        if(Themes.isEnabled())return Themes.backgroundInUse();
        int original=Skin.remembered(context).page;
        return style().neutral?(NavBar.bright(original)<.5f?0xff1c1c1c:0xfff3f3f3):original;
    }
    static int cardColour(Context context){
        int custom=AppearanceColors.get(AppearanceColors.SURFACE);
        if(custom!=0)return custom;
        int page=pageColour(context);
        return Expressive.mix(page,NavBar.bright(page)<.5f?0xffffffff:0xff000000,style().cardLift);
    }
    static View owner(Drawable drawable) {
        Drawable.Callback step = drawable.getCallback();
        for (int i = 0; i < 12; i++) {
            if (step instanceof View) return (View) step;
            if (!(step instanceof Drawable)) return null;
            step = ((Drawable) step).getCallback();
        }
        return null;
    }

    static boolean draw(Canvas canvas, Drawable drawable, RectF rect, float top, float bottom, int colour, float press) {
        return draw(canvas, drawable, owner(drawable), rect, top, bottom, colour, press);
    }

    static boolean draw(Canvas canvas, Drawable drawable, View view, RectF rect, float top, float bottom, int colour, float press) {
        if (!frosted()) return false;
        if (GlassBackdrop.capturing) return false;
        if (view == null || rect.width() <= 0 || rect.height() <= 0) return false;
        GlassOptics optics = OPTICS.get(drawable);
        if (optics == null) { optics = new GlassOptics(); OPTICS.put(drawable, optics); }
        if(fluent()) {
            float density=view.getResources().getDisplayMetrics().density;
            float radius=density*(rect.height()>density*56?style().card:style().small);
            top=Math.min(top,radius);bottom=Math.min(bottom,radius);
        }
        optics.draw(canvas, view, rect, top, bottom, colour, press);
        return true;
    }

    /** Visit only an owned window, on layout changes; never scan TikTok every frame. */
    static void own(View root) {
        if (root.getTag(OWNED) != null) { decorate(root); return; }
        root.setTag(OWNED, Boolean.TRUE);
        decorate(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> { if (frosted()) decorate(root); });
    }

    private static void decorate(View view) {
        if (frosted()) {
            Drawable bg = view.getBackground();
            if (bg instanceof GradientDrawable && !(bg instanceof ColourSample)
                    && !(view instanceof android.widget.ImageView)) {
                Surface surface = new Surface(view, bg);
                view.setBackground(surface);
                surface.bind();
            } else if (bg instanceof android.graphics.drawable.RippleDrawable
                    && !(view instanceof android.widget.ImageView)) {
                android.graphics.drawable.RippleDrawable ripple = (android.graphics.drawable.RippleDrawable) bg;
                for (int i=0;i<ripple.getNumberOfLayers();i++) {
                    Drawable layer = ripple.getDrawable(i);
                    if (ripple.getId(i) != android.R.id.mask && layer instanceof GradientDrawable
                            && !(layer instanceof ColourSample)) {
                        Surface surface = new Surface(view, layer);
                        ripple.setDrawable(i,surface);
                        surface.bind();
                    }
                }
            }
            if (view.getBackground() instanceof Surface || view.getBackground() instanceof android.graphics.drawable.RippleDrawable)
                GlassBackdrop.register(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) decorate(group.getChildAt(i));
        }
    }

    static void page(Activity activity, View root, int colour) {
        colour=pageColour(activity);
        root.setBackground(frosted() ? GlassBackdrop.page(colour) : new ColorDrawable(colour));
        window(activity.getWindow(), colour);
        own(root);
    }

    static void window(Window window, int colour) {
        if (window == null) return;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                window.setBackgroundBlurRadius(frosted() ? Math.round(style().windowBlur * window.getDecorView().getResources().getDisplayMetrics().density) : 0);
            } catch (RuntimeException ignored) { /* Optical surfaces also work without compositor blur. */ }
        }
        if (frosted()) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
    }

    /** A colour editor must show the actual colour, rather than turn its sample into glass. */
    static final class ColourSample extends GradientDrawable {
        ColourSample() { super(); }
        ColourSample(Orientation orientation, int[] colours) { super(orientation, colours); }
    }

    static void dialog(Dialog dialog, int colour) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window(window, colour);
        own(window.getDecorView());
        if (frosted()) window.setDimAmount(.24f);
    }

    /** Wrap custom gradient buttons and inputs without losing their original MD3 appearance. */
    static final class Surface extends Drawable implements Drawable.Callback {
        private final java.lang.ref.WeakReference<View> view;
        private final Drawable original;
        private final RectF rect = new RectF();
        Surface(View host, Drawable original) {
            this.view = new java.lang.ref.WeakReference<>(host);
            this.original = original;
            original.setCallback(this);
        }
        void bind() { original.setCallback(this); }
        @Override public void draw(Canvas canvas) {
            View host = view.get();
            rect.set(getBounds());
            float radius = Math.min(rect.height()/2f, host == null ? 24 : 22*host.getResources().getDisplayMetrics().density);
            int colour = 0xff30383c;
            if (original instanceof GradientDrawable && Build.VERSION.SDK_INT >= 24) {
                GradientDrawable shape = (GradientDrawable) original;
                if (shape.getColor() != null) colour = shape.getColor().getDefaultColor();
                if (shape.getCornerRadius() > 0) radius = Math.min(shape.getCornerRadius(), rect.height()/2f);
            }
            if (!Design.draw(canvas, this, host, rect, radius, radius, colour, isPressed() ? 1 : 0)) original.draw(canvas);
        }
        private boolean isPressed() { for (int state : getState()) if (state == android.R.attr.state_pressed) return true; return false; }
        @Override protected void onBoundsChange(android.graphics.Rect bounds) { original.setBounds(bounds); }
        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) { original.setState(state); invalidateSelf(); return true; }
        @Override public int getIntrinsicWidth() { return original.getIntrinsicWidth(); }
        @Override public int getIntrinsicHeight() { return original.getIntrinsicHeight(); }
        @Override public boolean getPadding(android.graphics.Rect padding) { return original.getPadding(padding); }
        @Override public void getOutline(android.graphics.Outline outline) { original.getOutline(outline); }
        @Override public void setAlpha(int alpha) { original.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { original.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        @Override public void invalidateDrawable(Drawable who) { invalidateSelf(); }
        @Override public void scheduleDrawable(Drawable who,Runnable run,long at) { scheduleSelf(run,at); }
        @Override public void unscheduleDrawable(Drawable who,Runnable run) { unscheduleSelf(run); }
    }
}

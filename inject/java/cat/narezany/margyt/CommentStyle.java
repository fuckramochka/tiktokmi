package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Dresses the native comment tree; adapters, accessibility, gestures and actions stay native. */
final class CommentStyle {
    static final String KEY="design_comments";
    private static final int WATCH=0x4d674c39;
    // Verified from the 47.2.41 comments window, not labels or coordinates on a screenshot.
    private static final int WINDOW=0x7f0a1911,HEADER_TITLE=0x7f0a78fe,PANEL=0x7f0a2e60;
    private static final int ROW=0x7f0a1b88,NAME=0x7f0a87e8,INPUT=0x7f0a45e2;
    private static final WeakHashMap<View,Observer> WINDOWS=new WeakHashMap<>();
    private CommentStyle(){}
    static boolean enabled(){Context context=Margy.context();return context==null || context.getSharedPreferences(Margy.PREFS,0).getBoolean(KEY,true);}
    static void set(boolean value){Context context=Margy.context();if(context!=null)context.getSharedPreferences(Margy.PREFS,0).edit().putBoolean(KEY,value).apply();refresh();}
    static void refresh(){for(Observer observer:new ArrayList<>(WINDOWS.values()))observer.schedule();}
    static boolean open(View root){
        Observer observer=WINDOWS.get(root);View sheet=observer==null?null:observer.sheet.get();
        if(sheet==null || !sheet.isAttachedToWindow() || !sheet.isShown())return false;
        float alpha=1f;for(View view=sheet;view!=null;view=view.getParent() instanceof View?(View)view.getParent():null)alpha*=view.getAlpha();
        if(alpha<.05f)return false;
        Rect visible=new Rect();if(!sheet.getGlobalVisibleRect(visible))return false;
        int[] position=new int[2];root.getLocationOnScreen(position);
        return visible.intersect(position[0],position[1],position[0]+root.getWidth(),position[1]+root.getHeight())
                && visible.height()>root.getHeight()*.08f;
    }
    static void attach(Activity activity){
        String name=activity.getClass().getName();if(!name.endsWith(".SplashActivity") && !name.endsWith(".MainActivity"))return;
        View root=activity.getWindow().getDecorView();
        Observer observer=WINDOWS.get(root);
        if(observer==null){observer=new Observer(root);WINDOWS.put(root,observer);root.setTag(WATCH,observer);}
        observer.schedule();
    }
    private static final class Original {
        final Drawable background;final ViewOutlineProvider outline;final boolean clipped;
        Original(View view){background=view.getBackground();outline=view.getOutlineProvider();clipped=view.getClipToOutline();}
        void restore(View view){view.setBackground(background);view.setOutlineProvider(outline);view.setClipToOutline(clipped);GlassBackdrop.unscene(view);}
    }
    private static final class Observer implements ViewTreeObserver.OnGlobalLayoutListener,ViewTreeObserver.OnScrollChangedListener,View.OnAttachStateChangeListener {
        final WeakReference<View> root;
        final WeakHashMap<View,Original> originals=new WeakHashMap<>();
        WeakReference<View> nativeWindow=new WeakReference<>(null);
        WeakReference<View> sheet=new WeakReference<>(null);
        boolean queued,applying;String appearance="";
        /** TikTok writes the sheet's title in light letters on its dark theme and dark ones on its light theme. */
        boolean lightWords=true;
        private final Runnable applyLater=()->{queued=false;apply();};
        Observer(View view){
            root=new WeakReference<>(view);ViewTreeObserver tree=view.getViewTreeObserver();tree.addOnGlobalLayoutListener(this);tree.addOnScrollChangedListener(this);view.addOnAttachStateChangeListener(this);
        }
        @Override public void onGlobalLayout(){schedule();}
        @Override public void onScrollChanged(){schedule();}
        void schedule(){View view=root.get();if(!queued && !applying && view!=null){queued=true;view.postDelayed(applyLater,32);}}
        Original remember(View view){Original saved=originals.get(view);if(saved==null){saved=new Original(view);originals.put(view,saved);}return saved;}
        void background(View view,Drawable drawable){remember(view);if(view.getBackground()!=drawable)view.setBackground(drawable);}
        void clear(View view){if(view.getBackground()!=null && !(view.getBackground() instanceof Surface) && !(view.getBackground() instanceof ThreadLine))background(view,null);}
        void restore(){for(View view:new ArrayList<>(originals.keySet()))if(view!=null)originals.get(view).restore(view);originals.clear();}
        void apply(){
            View top=root.get();if(top==null || !top.isAttachedToWindow() || applying)return;
            applying=true;
            try{
                boolean enabled=enabled();
                View heading=null;View found=nativeWindow.get();if(found!=null)heading=found.findViewById(HEADER_TITLE);
                if(heading instanceof android.widget.TextView)lightWords=NavBar.bright(((android.widget.TextView)heading).getCurrentTextColor())>.5f;
                String style=Design.mode()+":"+enabled+":"+Design.pageColour(top.getContext())+":"+Accent.colour()+":"+lightWords;
                if(!style.equals(appearance)){restore();appearance=style;}
                if(!enabled)return;
                View window=nativeWindow.get();
                if(window==null || !window.isAttachedToWindow()){window=top.findViewById(WINDOW);nativeWindow=new WeakReference<>(window);}
                if(window==null || !window.isShown())return;
                View title=window.findViewById(HEADER_TITLE);if(title==null)return;
                View panel=title;
                for(int i=0;i<8 && panel!=null && panel.getId()!=PANEL;i++)panel=panel.getParent() instanceof View?(View)panel.getParent():null;
                if(panel==null || panel.getHeight()<1)return;
                sheet=new WeakReference<>(panel);
                if(!(panel.getBackground() instanceof Surface)){
                    background(panel,new Surface(panel,true,this));
                    remember(panel);
                    panel.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View view,Outline outline){float dp=view.getResources().getDisplayMetrics().density;outline.setRoundRect(0,0,view.getWidth(),view.getHeight()+Math.round(32*dp),(Design.fluent()?8:28)*dp);}});
                    panel.setClipToOutline(true);GlassBackdrop.scene(panel);
                }
                dress(panel,panel,0,new int[]{1800});
            }catch(Throwable error){Diary.note("comment surface: "+error.getClass().getSimpleName());}
            finally{applying=false;}
        }
        void dress(View view,View panel,int depth,int[] budget){
            if(depth>35 || budget[0]--<=0 || view.getVisibility()!=View.VISIBLE)return;
            int id=view.getId();float dp=view.getResources().getDisplayMetrics().density;
            if(view!=panel && view instanceof ViewGroup && view.getWidth()>panel.getWidth()*.72f
                    && id!=ROW && !(view.getBackground() instanceof Surface))clear(view);
            if(id==ROW && view instanceof ViewGroup){
                View name=view.findViewById(NAME);
                boolean reply=name!=null && name.getLeft()>view.getWidth()*.20f;
                if(reply && !(view.getBackground() instanceof ThreadLine))background(view,new ThreadLine(view));
                else if(!reply && view.getBackground() instanceof ThreadLine)background(view,null);
            }
            if(id==INPUT && !(view.getBackground() instanceof Surface))background(view,new Surface(view,false,this));
            if(id==0x7f0a943c || id==0x7f0a180d){
                View target=view.getParent() instanceof View?(View)view.getParent():null;
                if(target!=null && !(target.getBackground() instanceof Surface))background(target,new Surface(target,false,this));
            }
            if(Design.fluent() && (id==0x7f0a88b2 || id==0x7f0a09f8) && !(view.getBackground() instanceof Surface))background(view,new Surface(view,false,this));
            // Native theme hooks exclusively own text colours, including reply-expansion links.
            if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)dress(group.getChildAt(i),panel,depth+1,budget);}
        }
        @Override public void onViewDetachedFromWindow(View view){view.removeCallbacks(applyLater);ViewTreeObserver tree=view.getViewTreeObserver();if(tree.isAlive()){tree.removeOnGlobalLayoutListener(this);tree.removeOnScrollChangedListener(this);}view.removeOnAttachStateChangeListener(this);WINDOWS.remove(view);originals.clear();}
        @Override public void onViewAttachedToWindow(View view){}
    }
    private static final class Surface extends Drawable {
        final WeakReference<View> owner;final boolean sheet;final RectF rect=new RectF();final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        final WeakReference<Observer> watcher;
        Surface(View view,boolean sheet,Observer watcher){owner=new WeakReference<>(view);this.sheet=sheet;this.watcher=new WeakReference<>(watcher);}
        /**
         * The page the sheet sits in, light or dark as TikTok's own theme is now. The mod's
         * theme colour is kept when it is of the same kind; a remembered colour from the
         * other theme is not, which is how the sheet stayed dark on a light TikTok.
         */
        private int page(View view){
            Observer seen=watcher.get();boolean dark=seen==null?Themes.isDark():seen.lightWords;
            int page=Design.pageColour(view.getContext());
            if((NavBar.bright(page)<.5f)!=dark)page=dark?0xFF121212:0xFFFFFFFF;
            return page|0xFF000000;
        }
        @Override public void draw(Canvas canvas){
            View view=owner.get();if(view==null)return;rect.set(getBounds());float dp=view.getResources().getDisplayMetrics().density;
            int page=page(view);boolean dark=NavBar.bright(page)<.5f;
            int card=Expressive.mix(page,dark?0xffffffff:0xff000000,Design.fluent()?.075f:.045f);
            int colour=sheet?card:blend(card,Accent.colour(),.12f);
            float radius=(sheet?(Design.fluent()?8:28):(Design.fluent()?4:24))*dp;
            // The footer is square against the display edge; a single sheet owns the material.
            if(!Design.draw(canvas,this,view,rect,radius,sheet?0:radius,colour,view.isPressed()?1:0)){
                paint.setColor(colour);float[] corners={radius,radius,radius,radius,sheet?0:radius,sheet?0:radius,sheet?0:radius,sheet?0:radius};
                Path path=new Path();path.addRoundRect(rect,corners,Path.Direction.CW);canvas.drawPath(path,paint);
            }
            if(sheet){paint.setColor(dark?0x608f9a95:0x60717c76);float x=rect.centerX();canvas.drawRoundRect(x-18*dp,rect.top+4*dp,x+18*dp,rect.top+7*dp,2*dp,2*dp,paint);}
        }
        private static int blend(int base,int accent,float amount){return Color.rgb(Math.round(Color.red(base)*(1-amount)+Color.red(accent)*amount),Math.round(Color.green(base)*(1-amount)+Color.green(accent)*amount),Math.round(Color.blue(base)*(1-amount)+Color.blue(accent)*amount));}
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(ColorFilter filter){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static final class ThreadLine extends Drawable {
        final WeakReference<View> owner;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        ThreadLine(View view){owner=new WeakReference<>(view);}
        @Override public void draw(Canvas canvas){View view=owner.get();if(view==null)return;float dp=view.getResources().getDisplayMetrics().density;paint.setColor(0x335c8c79);paint.setStrokeWidth(dp);float x=42*dp;canvas.drawLine(x,0,x,getBounds().height(),paint);}
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(ColorFilter filter){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}

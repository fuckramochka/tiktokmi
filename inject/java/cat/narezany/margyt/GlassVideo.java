package cat.narezany.margyt;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.graphics.SurfaceTexture;
import java.lang.ref.WeakReference;

/** Binds only the feed controller's current cell, including both native renderer paths. */
public final class GlassVideo {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static WeakReference<Object> controller=new WeakReference<>(null),manager=new WeakReference<>(null);
    private static WeakReference<View> render=new WeakReference<>(null);
    private static WeakReference<Surface> surface=new WeakReference<>(null);
    private static String aid="";
    private static long position=Long.MIN_VALUE,lastEvent,lastCheck,lastDiagnostic;
    private static int generation;
    private static boolean playing;
    private static long textureStamp=Long.MIN_VALUE;
    private static boolean textureReady;
    private GlassVideo(){}

    public static void resumed(Object owner,String id){PlayerTools.heard(owner,id);event(owner,id,Long.MIN_VALUE,true);}
    public static void progress(Object owner,String id,long time){PlayerTools.heard(owner,id);event(owner,id,time,false);}
    public static void paused(Object owner,String id){
        if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(()->paused(owner,id));return;}
        if(controller.get()==owner && aid.equals(id)){playing=false;position=Long.MIN_VALUE;GlassBackdrop.videoChanged(false);}
    }
    /** Observe TikTok's existing listener; never replace it or consume its callbacks. */
    public static void frameUpdated(SurfaceTexture texture){
        if(texture==null || !Design.frosted())return;
        if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(()->frameUpdated(texture));return;}
        GlassBackdrop.textureUpdated(texture);
        View view=render.get();
        if(!(view instanceof TextureView) || ((TextureView)view).getSurfaceTexture()!=texture)return;
        if(!aid.equals(NativeRead.aid(NativeRead.get(controller.get(),"LLJJJIL"))))return;
        long stamp;try{stamp=texture.getTimestamp();}catch(RuntimeException released){return;}
        if(textureReady && stamp==textureStamp)return;
        textureReady=true;textureStamp=stamp;GlassBackdrop.frameAvailable();
    }
    static boolean frameReady(View view){return !(view instanceof TextureView) || textureReady;}
    private static void event(Object owner,String id,long time,boolean force){
        if(!Design.frosted())return;
        if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(()->event(owner,id,time,force));return;}
        long now=SystemClock.uptimeMillis();
        if(!force && controller.get()==owner && aid.equals(id) && position==time)return;
        if(!force && controller.get()==owner && aid.equals(id) && now-lastEvent<90)return;
        Object cell=NativeRead.get(owner,"LLLILZ");
        String selected=NativeRead.aid(NativeRead.get(owner,"LLJJJIL"));
        if(id==null || selected.isEmpty() || !selected.equals(id))return;
        Object renderer=NativeRead.get(cell,"Q3");
        Object view=NativeRead.get(renderer,"getVideoView");
        Object handle=NativeRead.get(renderer,"getSurface");
        // Verified BaseFeedPlayerView + 0qYR API for TikTok's other player implementation.
        Object player=NativeRead.get(cell,"V");
        Object holder=NativeRead.get(player,"getSurfaceHolder");
        Object holderView=NativeRead.get(holder,"getView"),holderSurface=NativeRead.get(holder,"getSurface");
        if(holderView instanceof View && holderSurface instanceof Surface && ((Surface)holderSurface).isValid()){
            view=holderView;handle=holderSurface;
        }else if(!(view instanceof View)){
            view=NativeRead.get(player,"getVideoView");handle=NativeRead.get(player,"getSurface");
        }
        if(!(view instanceof View)){
            if(now-lastDiagnostic>5000){lastDiagnostic=now;android.util.Log.w("MargyTGlass","Current cell has no renderer: cell="+type(cell)+", component="+type(renderer)+", player="+type(player));}
            return;
        }
        View next=surfaceView((View)view,0,new int[]{120});if(next==null)next=(View)view;
        Surface nextSurface=handle instanceof Surface?(Surface)handle:null;
        if(next instanceof SurfaceView)nextSurface=((SurfaceView)next).getHolder().getSurface();
        boolean changed=controller.get()!=owner || !aid.equals(id) || render.get()!=next || surface.get()!=nextSurface;
        if(changed){
            generation++;controller=new WeakReference<>(owner);manager=new WeakReference<>(NativeRead.get(owner,"getPlayerManager"));
            render=new WeakReference<>(next);surface=new WeakReference<>(nextSurface);aid=id;
            textureStamp=Long.MIN_VALUE;textureReady=false;
            android.util.Log.w("MargyTGlass","Current renderer "+type(next)+", surface="+(nextSurface!=null)+", generation="+generation);
        }
        position=time;lastEvent=now;playing=true;lastCheck=0;
        GlassBackdrop.videoChanged(changed);
    }
    private static String type(Object value){return value==null?"none":value.getClass().getSimpleName();}
    private static View surfaceView(View view,int depth,int[] budget){
        if(depth>12 || budget[0]--<=0)return null;
        if(view instanceof TextureView || view instanceof SurfaceView)return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=surfaceView(group.getChildAt(i),depth+1,budget);if(found!=null)return found;}}
        return null;
    }
    static View render(View root){
        View view=render.get();
        if(view==null || view.getRootView()!=root || !view.isAttachedToWindow() || !view.isShown())return null;
        android.graphics.Rect visible=new android.graphics.Rect();
        return view.getGlobalVisibleRect(visible) && visible.width()>0 && visible.height()>0?view:null;
    }
    static boolean playing(){
        if(!playing)return false;
        long now=SystemClock.uptimeMillis();
        if(now-lastCheck>=100){
            Object value=NativeRead.get(manager.get(),"isPlaying");lastCheck=now;
            if(value instanceof Boolean && !(Boolean)value)playing=false;
            else if(!(value instanceof Boolean) && now-lastEvent>1000)playing=false;
        }
        return playing;
    }
    static Surface surface(){Surface value=surface.get();return value!=null && value.isValid()?value:null;}
    static int generation(){return generation;}
}

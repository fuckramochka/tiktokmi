package cat.narezany.margyt;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Shared event-driven backdrop. Never changes live view visibility or polls a paused player. */
final class GlassBackdrop {
    private static final WeakHashMap<View,Session> WINDOWS=new WeakHashMap<>();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final int SCENE=0x4d674c38;
    static boolean capturing;
    /** How wide the copy of the screen behind the glass is: it is blurred, so small is enough. */
    private static final float WIDTH=240f;
    /**
     * The video behind the glass is copied a dozen times a second; the rest of the
     * screen behind it is drawn again in software, which TikTok's constant layouts
     * asked for seven times a second, and that took the main thread from the video
     * copies and the app alike. Under a blur the rest changes little: 400 ms.
     */
    private static final long VIDEO_FRAME_NS=80_000_000L,VIDEO_FRAME_MS=80L,CAPTURE_MS=400L;
    /** `version` changes whenever the picture in `bitmap` does. */
    static final class Frame {Bitmap bitmap;float scale;int x,y;Canvas canvas;boolean ready;int version;}

    static void changed(){for(Session session:new ArrayList<>(WINDOWS.values()))session.dispose();WINDOWS.clear();}
    static void videoChanged(boolean selection){
        for(Session session:new ArrayList<>(WINDOWS.values()))session.request(true,selection);
    }
    static void frameAvailable(){for(Session session:new ArrayList<>(WINDOWS.values()))session.request(true,false);}
    static void scene(View view){view.setTag(SCENE,Boolean.TRUE);}
    static void unscene(View view){view.setTag(SCENE,null);}
    static void register(View view){
        if(!Design.frosted() || capturing)return;
        Session session=session(view);session.owners.put(view,Boolean.TRUE);
    }
    static Frame frame(View view){
        Session session=session(view);session.owners.put(view,Boolean.TRUE);
        session.prepare();
        for(View parent=view;parent!=null;){
            if(parent.getTag(SCENE)!=null)return session.scene();
            parent=parent.getParent() instanceof View?(View)parent.getParent():null;
        }
        if(session.layout)session.request(true,false);
        session.animate();
        return session.frame;
    }
    static void textureUpdated(SurfaceTexture texture){
        for(Session session:new ArrayList<>(WINDOWS.values())){
            View source=session.video.get();
            if(source instanceof TextureView && ((TextureView)source).getSurfaceTexture()==texture){session.textureEvents=true;session.textureFrame(texture);}
        }
    }
    private static Session session(View view){
        // A dialog samples its activity, but owns a separate exclusion set and lifecycle.
        View host=view.getRootView(),source=host;
        android.content.Context context=view.getContext();
        for(int i=0;i<12 && context instanceof android.content.ContextWrapper;i++){
            if(context instanceof android.app.Activity){source=((android.app.Activity)context).getWindow().getDecorView();break;}
            android.content.Context next=((android.content.ContextWrapper)context).getBaseContext();if(next==context)break;context=next;
        }
        Session value=WINDOWS.get(host);
        if(value==null){value=new Session(host,source);WINDOWS.put(host,value);}
        return value;
    }
    static Drawable page(int colour){return new Page(colour,Accent.colour());}
    private static final class Page extends Drawable {
        private final int colour,accent;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Page(int colour,int accent){this.colour=colour|0xff000000;this.accent=accent;}
        @Override protected void onBoundsChange(Rect bounds){
            float amount=Design.fluent()?.015f:.09f;
            int hint=Color.rgb(Math.round(Color.red(colour)*(1-amount)+Color.red(accent)*amount),
                    Math.round(Color.green(colour)*(1-amount)+Color.green(accent)*amount),
                    Math.round(Color.blue(colour)*(1-amount)+Color.blue(accent)*amount));
            paint.setShader(new LinearGradient(bounds.left,bounds.top,bounds.right,bounds.bottom,new int[]{hint,colour},null,Shader.TileMode.CLAMP));
        }
        @Override public void draw(Canvas canvas){paint.setColor(colour);canvas.drawRect(getBounds(),paint);}
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(ColorFilter filter){}
        @Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }
    private static final class Session implements Choreographer.FrameCallback,ViewTreeObserver.OnGlobalLayoutListener,
            ViewTreeObserver.OnScrollChangedListener,View.OnAttachStateChangeListener,ViewTreeObserver.OnWindowFocusChangeListener {
        private final WeakReference<View> host,root;
        private final WeakHashMap<View,Boolean> owners=new WeakHashMap<>();
        private final Frame frame=new Frame(),expanded=new Frame();
        private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Rect visible=new Rect(),videoClip=new Rect();
        private final int[] location=new int[2];
        private final ArrayList<View> candidates=new ArrayList<>();
        private WeakReference<View> video=new WeakReference<>(null);
        private Bitmap background,pixels,spare;
        private boolean queued,copying,disposed,needsVideo,layout=true,sceneDirty=true,animating,textureEvents,mediaPresent,readyLogged;
        private int bound,failures,nativeGeneration=-1;
        private String diagnostic="";
        private long copied,vsync,lastTextureStamp=Long.MIN_VALUE,shownVideo,captured;
        private final Runnable refresh=()->{queued=false;update();};
        Session(View window,View source){
            host=new WeakReference<>(window);root=new WeakReference<>(source);
            observe(source,true);if(window!=source)observe(window,true);
            window.addOnAttachStateChangeListener(this);
        }
        private void observe(View view,boolean add){
            ViewTreeObserver tree=view.getViewTreeObserver();if(!tree.isAlive())return;
            if(add){tree.addOnGlobalLayoutListener(this);tree.addOnScrollChangedListener(this);tree.addOnWindowFocusChangeListener(this);}
            else{tree.removeOnGlobalLayoutListener(this);tree.removeOnScrollChangedListener(this);tree.removeOnWindowFocusChangeListener(this);}
        }
        private boolean active(){
            View view=host.get();if(disposed || !Design.frosted() || view==null || !view.isShown() || view.getWindowVisibility()!=View.VISIBLE)return false;
            for(View owner:new ArrayList<>(owners.keySet()))if(owner!=null && owner.isAttachedToWindow() && owner.isShown()
                    && owner.getAlpha()>.01f && owner.getGlobalVisibleRect(visible))return true;
            return false;
        }
        @Override public void onWindowFocusChanged(boolean focused){if(focused){layout=true;request(true,false);}}
        @Override public void onGlobalLayout(){layout=true;request(false,false);}
        @Override public void onScrollChanged(){layout=true;request(true,false);}
        private boolean videoVisible(){
            View view=root.get(),window=host.get();
            if(!active() || view==null || window==null || !window.hasWindowFocus() || CommentStyle.open(view))return false;
            for(View owner:new ArrayList<>(owners.keySet())){
                if(owner==null || !owner.isShown() || owner.getAlpha()<.01f || !owner.getGlobalVisibleRect(visible))continue;
                boolean scene=false;for(View step=owner;step!=null;step=step.getParent() instanceof View?(View)step.getParent():null){if(step.getTag(SCENE)!=null){scene=true;break;}}
                if(!scene)return true;
            }
            return false;
        }
        void textureFrame(SurfaceTexture texture){
            long stamp;try{stamp=texture.getTimestamp();}catch(RuntimeException released){return;}
            if(stamp==lastTextureStamp)return;
            lastTextureStamp=stamp;if(videoVisible())request(true,false);
        }
        void animate(){
            if(video.get() instanceof TextureView && textureEvents)return;
            if(!animating && video.get()!=null && videoVisible()){
                // Texture timestamp checks cost no bitmap copies; paused frames cause no redraws.
                if(frame.ready && !(video.get() instanceof TextureView) && GlassVideo.render(root.get())==video.get() && !GlassVideo.playing())return;
                animating=true;Choreographer.getInstance().postFrameCallback(this);
            }
        }
        @Override public void doFrame(long time){
            animating=false;
            if(!videoVisible())return;
            // the video behind the glass is blurred past recognising: a dozen frames a second is
            // all it shows, and every frame is a copy off the GPU that phones below the top pay for
            if(time-vsync>=VIDEO_FRAME_NS && !copying){
                vsync=time;View source=video.get();
                if(source instanceof TextureView){
                    SurfaceTexture texture=((TextureView)source).getSurfaceTexture();if(texture!=null)textureFrame(texture);
                }else if(source!=null){needsVideo=true;update();}
            }
            animate();
        }
        void request(boolean newVideo,boolean selection){
            needsVideo|=newVideo;
            if(selection)layout=true;
            if(!queued && active()){
                queued=true;
                MAIN.postDelayed(refresh,newVideo?Math.max(0,VIDEO_FRAME_MS-(SystemClock.uptimeMillis()-copied)):0);
            }
            animate();
        }
        void prepare(){
            View view=root.get();if(view==null || view.getWidth()<1 || view.getHeight()<1)return;
            float scale=Math.min(1f,WIDTH/view.getWidth());
            int width=Math.max(1,Math.round(view.getWidth()*scale)),height=Math.max(1,Math.round(view.getHeight()*scale));
            if(frame.bitmap==null || frame.bitmap.getWidth()!=width || frame.bitmap.getHeight()!=height){
                frame.bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);frame.canvas=new Canvas(frame.bitmap);frame.scale=scale;
                background=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);layout=true;frame.ready=false;
            }
            view.getLocationOnScreen(location);frame.x=location[0];frame.y=location[1];
        }
        private boolean candidate(View view){return BackdropSource.visible(view);}
        private void capture(){
            View source=root.get(),window=host.get();if(source==null || background==null)return;
            background.eraseColor(Design.pageColour(source.getContext()));
            Canvas canvas=new Canvas(background);canvas.scale(frame.scale,frame.scale);candidates.clear();mediaPresent=false;
            boolean previous=capturing;capturing=true;
            try{
                BackdropScene.draw(source,canvas,view->{
                    if(source==window && owners.containsKey(view))return true;
                    if(view instanceof TextureView || view instanceof SurfaceView){
                        if(view.isShown() && view.getGlobalVisibleRect(visible))mediaPresent=true;
                        if(candidate(view))candidates.add(view);return true;
                    }
                    return false;
                });
            }finally{capturing=previous;}
            View selected=null,verified=GlassVideo.render(source);
            if(candidates.contains(verified))selected=verified;
            else if(candidates.size()==1)selected=candidates.get(0);
            String state="sources="+candidates.size()+", selected="+(selected==null?"none":selected.getClass().getSimpleName())+", media="+mediaPresent;
            if(!state.equals(diagnostic)){diagnostic=state;android.util.Log.w("MargyTGlass","Feed capture "+state);}
            // While the feed scrolls, or a cell is swapped for another, there are moments with two
            // players, or none that counts as the one on screen. Those moments used to repaint the
            // glass with the page's own colour, which showed through the blurred video as a flash
            // of the theme. The glass now keeps the last video it had until another one is ready.
            if(selected==null && holding()){recheck();return;}
            bind(selected);
            if(selected==null || pixels==null){
                if(holding()){recheck();return;}
                // Media is a separate compositor layer. A flat software placeholder is not its backdrop.
                frame.ready=!mediaPresent;base(frame);
            }else publish(selected);
        }
        private void base(Frame destination){
            if(destination.canvas==null || background==null)return;
            destination.canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            destination.canvas.drawBitmap(background,0,0,paint);
            destination.version++;
        }
        private void bind(View source){
            int generation=source!=null && GlassVideo.render(root.get())==source?GlassVideo.generation():-1;
            if(video.get()==source && nativeGeneration==generation)return;
            // the previous video's pixels stay on the glass until the new one has a frame of its own
            nativeGeneration=generation;if(!holding())frame.ready=false;readyLogged=false;video=new WeakReference<>(source);bound++;spare=null;if(source==null)pixels=null;failures=0;lastTextureStamp=Long.MIN_VALUE;textureEvents=false;
            needsVideo=source!=null;sceneDirty=true;animate();
        }
        void update(){
            if(!active())return;
            prepare();if(frame.bitmap==null)return;
            if(layout){
                // The screen behind is drawn again in software for every layout, and while a list
                // scrolls that was every frame. Once in a while is enough for something blurred.
                long now=SystemClock.uptimeMillis(),left=CAPTURE_MS-(now-captured);
                if(left>0){MAIN.removeCallbacks(refresh);queued=true;MAIN.postDelayed(refresh,left);}
                else{layout=false;captured=now;capture();sceneDirty=true;invalidateOwners();}
            }
            View source=video.get();
            if(source==null || !videoVisible()){needsVideo=false;return;}
            if(!candidate(source)){layout=true;if(!holding())frame.ready=false;request(true,false);return;}
            if(!needsVideo || copying)return;
            needsVideo=false;
            if(source==GlassVideo.render(root.get()) && !GlassVideo.frameReady(source))return;
            int width=Math.max(1,Math.round(source.getWidth()*frame.scale)),height=Math.max(1,Math.round(source.getHeight()*frame.scale));
            if(width<2 || height<2)return;
            if(spare==null || spare.getWidth()!=width || spare.getHeight()!=height)spare=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            Bitmap destination=spare;final int ticket=bound;
            try{
                if(source instanceof TextureView && ((TextureView)source).isAvailable()){
                    if(((TextureView)source).getBitmap(destination)!=null)accept(source,destination,ticket);
                }else if(Build.VERSION.SDK_INT>=24){
                    Surface surface=source instanceof SurfaceView?((SurfaceView)source).getHolder().getSurface():null;
                    if(surface==null || !surface.isValid()){needsVideo=true;animate();return;}
                    copying=true;
                    PixelCopy.request(surface,destination,result->{
                        copying=false;
                        if(result==PixelCopy.SUCCESS)accept(source,destination,ticket);
                        else if(failures++<2)android.util.Log.w("MargyTGlass","Current surface copy result="+result);
                        if(needsVideo)request(false,false);
                    },MAIN);
                }
            }catch(Throwable error){copying=false;if(failures++<2)android.util.Log.w("MargyTGlass","Current renderer copy failed",error);}
            animate();
        }
        private void accept(View source,Bitmap destination,int ticket){
            copied=SystemClock.uptimeMillis();
            if(disposed || !videoVisible() || ticket!=bound || video.get()!=source || !candidate(source))return;
            if(pixels!=null && pixels.sameAs(destination))return;
            Bitmap previous=pixels;pixels=destination;spare=previous;publish(source);
        }
        private void publish(View source){
            if(pixels==null)return;
            if(!readyLogged){readyLogged=true;android.util.Log.w("MargyTGlass","Backdrop ready: "+source.getClass().getSimpleName());}
            base(frame);source.getLocationOnScreen(location);
            int save=frame.canvas.save();
            if(source.getGlobalVisibleRect(videoClip))frame.canvas.clipRect((videoClip.left-frame.x)*frame.scale,(videoClip.top-frame.y)*frame.scale,(videoClip.right-frame.x)*frame.scale,(videoClip.bottom-frame.y)*frame.scale);
            RectF box=new RectF((location[0]-frame.x)*frame.scale,(location[1]-frame.y)*frame.scale,
                    (location[0]-frame.x+source.getWidth())*frame.scale,(location[1]-frame.y+source.getHeight())*frame.scale);
            frame.canvas.drawBitmap(pixels,null,box,paint);frame.canvas.restoreToCount(save);
            frame.ready=true;frame.version++;sceneDirty=true;shownVideo=SystemClock.uptimeMillis();invalidateOwners();
        }
        /** Whether the glass showed a video a moment ago and should keep it rather than flash the page. */
        /** Look again once the hold is over, so a screen without video does get the page in the end. */
        private void recheck(){MAIN.removeCallbacks(again);MAIN.postDelayed(again,1250L);}
        private final Runnable again=()->{layout=true;request(false,false);};
        private boolean holding(){return frame.ready && shownVideo!=0 && SystemClock.uptimeMillis()-shownVideo<1200L;}
        Frame scene(){
            if(frame.bitmap==null)return frame;
            if(expanded.bitmap==null || expanded.bitmap.getWidth()!=frame.bitmap.getWidth() || expanded.bitmap.getHeight()!=frame.bitmap.getHeight()){
                expanded.bitmap=Bitmap.createBitmap(frame.bitmap.getWidth(),frame.bitmap.getHeight(),Bitmap.Config.ARGB_8888);
                expanded.canvas=new Canvas(expanded.bitmap);expanded.scale=frame.scale;sceneDirty=true;
            }
            expanded.x=frame.x;expanded.y=frame.y;
            if(sceneDirty){
                // Comments own a static theme background; never the underlying video.
                Drawable background=page(Design.pageColour(root.get().getContext()));
                background.setBounds(0,0,expanded.bitmap.getWidth(),expanded.bitmap.getHeight());background.draw(expanded.canvas);expanded.version++;
                expanded.ready=true;sceneDirty=false;
            }
            return expanded;
        }
        private void invalidateOwners(){for(View owner:new ArrayList<>(owners.keySet()))if(owner!=null && owner.isShown())owner.postInvalidateOnAnimation();}
        void dispose(){
            disposed=true;MAIN.removeCallbacks(refresh);MAIN.removeCallbacks(again);View window=host.get(),source=root.get();
            Choreographer.getInstance().removeFrameCallback(this);animating=false;
            if(source!=null)observe(source,false);if(window!=null){if(window!=source)observe(window,false);window.removeOnAttachStateChangeListener(this);}
            owners.clear();pixels=spare=background=null;frame.bitmap=expanded.bitmap=null;frame.canvas=expanded.canvas=null;
        }
        @Override public void onViewDetachedFromWindow(View view){dispose();WINDOWS.remove(view);}
        @Override public void onViewAttachedToWindow(View view){}
    }
}

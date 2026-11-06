"""Exercise real renderer binding and playback event throttling, including recycled cells."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest
from tiktokmi import dexpatch
ROOT=pathlib.Path(__file__).resolve().parents[1]

class GlassVideoTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
    def test_current_cell_events_and_idle(self):
        sources={
            'android/os/Looper.java': 'package android.os;public class Looper{static final Looper MAIN=new Looper();public static Looper getMainLooper(){return MAIN;}public static Looper myLooper(){return MAIN;}}',
            'android/os/Handler.java': 'package android.os;public class Handler{public static int posts;public Handler(Looper l){}public void post(Runnable r){posts++;r.run();}}',
            'android/os/SystemClock.java': 'package android.os;public class SystemClock{public static long now=1000;public static long uptimeMillis(){return now;}}',
            'android/util/Log.java': 'package android.util;public class Log{public static int i(String t,String s){return 0;}public static int w(String t,String s){return 0;}}',
            'android/graphics/Rect.java': 'package android.graphics;public class Rect{public int width(){return 360;}public int height(){return 800;}}',
            'android/view/Surface.java': 'package android.view;public class Surface{public boolean valid=true;public boolean isValid(){return valid;}}',
            'android/view/View.java': 'package android.view;public class View{public View root=this;public boolean shown=true,attached=true,visible=true;public View getRootView(){return root;}public boolean isAttachedToWindow(){return attached;}public boolean isShown(){return shown;}public boolean getGlobalVisibleRect(android.graphics.Rect r){return visible;}}',
            'android/view/ViewGroup.java': 'package android.view;public class ViewGroup extends View{public int getChildCount(){return 0;}public View getChildAt(int i){return null;}}',
            'android/graphics/SurfaceTexture.java': 'package android.graphics;public class SurfaceTexture{public long stamp;public long getTimestamp(){return stamp;}}',
            'android/view/TextureView.java': 'package android.view;public class TextureView extends View{public android.graphics.SurfaceTexture texture=new android.graphics.SurfaceTexture();public android.graphics.SurfaceTexture getSurfaceTexture(){return texture;}}',
            'android/view/SurfaceView.java': 'package android.view;public class SurfaceView extends View{public Holder getHolder(){return new Holder();}public static class Holder{public Surface getSurface(){return new Surface();}}}',
            'mi/tiktokmi/Design.java': 'package mi.tiktokmi;class Design{static boolean enabled=true;static boolean frosted(){return enabled;}}',
            'mi/tiktokmi/PlayerTools.java': 'package mi.tiktokmi;class PlayerTools{static void heard(Object controller,String aid){}}',
            'mi/tiktokmi/GlassBackdrop.java': 'package mi.tiktokmi;class GlassBackdrop{static int events,selections,frames;static void textureUpdated(android.graphics.SurfaceTexture t){}static void frameAvailable(){frames++;}static void videoChanged(boolean selected){events++;if(selected)selections++;}}',
            'mi/tiktokmi/Test.java': HARNESS,
        }
        for name in ['GlassVideo','NativeRead']:
            sources['mi/tiktokmi/'+name+'.java']=(ROOT/'inject/java/mi/tiktokmi'/(name+'.java')).read_text()
        with tempfile.TemporaryDirectory() as directory:
            work=pathlib.Path(directory)
            for name,source in sources.items():
                path=work/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source)
            result=subprocess.run(['javac','-d',str(work)]+[str(p) for p in work.rglob('*.java')],capture_output=True,text=True)
            self.assertEqual(0,result.returncode,result.stderr)
            result=subprocess.run(['java','-cp',str(work),'mi.tiktokmi.Test'],capture_output=True,text=True)
            self.assertEqual(0,result.returncode,result.stderr)

    def test_playback_hooks_scoped_idempotent_and_wide_registers(self):
        signatures=[
            ('public final onResumePlay(Ljava/lang/String;)V','resumed', 'p1'),
            ('public final synthetic onPausePlay(Ljava/lang/String;)V','paused','p1'),
            ('public final onPausePlay(Ljava/lang/String;LX/0qPa;)V','paused','p1'),
            ('public final onPlayPause(Ljava/lang/String;)V','paused','p1'),
            ('public final onPlaying(Ljava/lang/String;)V','resumed','p1'),
            ('public final synthetic onRenderFirstFrameFromResume(Ljava/lang/String;)V','resumed','p1'),
            ('public final onPlayProgressChange(Ljava/lang/String;JJ)V','progress','p3'),
        ]
        with tempfile.TemporaryDirectory() as directory:
            work=pathlib.Path(directory);path=work/'Player.smali'
            text='.class public '+dexpatch.PLAYER_CONTROLLER+'\n.super Ljava/lang/Object;\n'
            for signature,_,_ in signatures:
                cross='    invoke-virtual {p0, p1}, '+dexpatch.PLAYER_CONTROLLER+'->onPausePlay(Ljava/lang/String;)V\n' if 'onResumePlay(' in signature else ''
                text+='\n.method '+signature+'\n    .registers 18\n'+cross+'    return-void\n.end method\n'
            path.write_text(text)
            other=work/'Other.smali';other.write_text(text.replace(dexpatch.PLAYER_CONTROLLER,'Lexample/Other;'))
            before=other.read_text()
            counts=dexpatch.rewrite_anchored(str(work));self.assertEqual(len(signatures),counts['native backdrop playback events'])
            after=path.read_text()
            for signature,target,last in signatures:
                body=after.split('.method '+signature)[1].split('.end method')[0]
                self.assertIn('invoke-static/range {p0 .. '+last+'}, Lmi/tiktokmi/GlassVideo;->'+target,body)
            self.assertEqual(before,other.read_text())
            self.assertEqual({},dexpatch.rewrite_anchored(str(work)))

    def test_texture_callback_preserves_listener_and_ignores_other_signatures(self):
        text='.class public Lexample/NativeTextureListener;\n.super Ljava/lang/Object;\n.method public final onSurfaceTextureUpdated(Landroid/graphics/SurfaceTexture;)V\n    .registers 2\n    invoke-static {p1}, Lexample/Observer;->updated(Landroid/graphics/SurfaceTexture;)V\n    const/4 p1, 0x1\n    return-void\n.end method\n'
        with tempfile.TemporaryDirectory() as directory:
            work=pathlib.Path(directory);path=work/'Listener.smali';path.write_text(text)
            unrelated=work/'Static.smali';unrelated.write_text(text.replace('public final','public static'))
            counts=dexpatch.rewrite_anchored(str(work))
            self.assertEqual(1,counts['native texture frame events'])
            after=path.read_text()
            self.assertIn('Lexample/Observer;->updated',after)
            self.assertLess(after.index('GlassVideo;->frameUpdated'),after.index('const/4 p1'), 'observe SurfaceTexture before native code reuses its parameter register')
            self.assertIn('invoke-static/range {p1 .. p1}, Lmi/tiktokmi/GlassVideo;->frameUpdated',after)
            self.assertNotIn('GlassVideo',unrelated.read_text())
            self.assertEqual({},dexpatch.rewrite_anchored(str(work)))

    def test_comments_leave_native_text_theme_as_the_only_colour_owner(self):
        source=(ROOT/'inject/java/mi/tiktokmi/CommentStyle.java').read_text()
        self.assertNotIn('setTextColor(',source)
        self.assertNotIn('setHintTextColor(',source)

HARNESS=r'''package mi.tiktokmi;
import android.view.*;import android.os.*;
public class Test{
 static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 public static class Post{String id;Post(String id){this.id=id;}public String getAid(){return id;}}
 public static class Renderer{View view=new View();Surface surface=new Surface();public View getVideoView(){return view;}public Surface getSurface(){return surface;}}
 public static class Cell{Renderer render=new Renderer();public Renderer Q3(){return render;}}
 public static class Manager{public boolean active=true;public boolean isPlaying(){return active;}}
 public static class Controller{Manager manager=new Manager();public Manager getPlayerManager(){return manager;}Post post;Cell cell=new Cell();Controller(String id){post=new Post(id);}public Post LLJJJIL(){return post;}public Cell LLLILZ(){return cell;}}
 public static void main(String[] args){
  Controller current=new Controller("current");
  GlassVideo.resumed(current,"preloaded");check(GlassBackdrop.events==0,"preloaded callback ignored");
  GlassVideo.resumed(current,"current");View first=current.cell.render.view;int token=GlassVideo.generation();
  check(GlassVideo.render(first)==first && GlassBackdrop.selections==1,"exact renderer from selected cell");
  SystemClock.now=1100;GlassVideo.progress(current,"current",100);int events=GlassBackdrop.events;
  SystemClock.now=1300;GlassVideo.progress(current,"current",100);check(GlassBackdrop.events==events,"unchanged playback position does not request a copy");
  GlassVideo.progress(current,"neighbour",200);check(GlassBackdrop.events==events,"adjacent video progress ignored");
  SystemClock.now=1400;GlassVideo.progress(current,"current",200);check(GlassBackdrop.events==events+1,"new playback requests one copy");
  SystemClock.now=1410;GlassVideo.progress(current,"current",210);check(GlassBackdrop.events==events+1,"bounded update frequency");
  events=GlassBackdrop.events;GlassVideo.paused(current,"current");SystemClock.now=9000;
  check(!GlassVideo.playing() && Handler.posts==0,"pause and elapsed time schedule no polling or copies");events=GlassBackdrop.events;
  current.post=new Post("next");current.cell=new Cell();GlassVideo.progress(current,"current",900);
  check(GlassBackdrop.events==events && GlassVideo.generation()==token,"recycled stale event cannot rebind");
  GlassVideo.resumed(current,"next");View second=current.cell.render.view;
  check(GlassVideo.generation()>token && GlassVideo.render(second)==second,"selection changes callback generation");
  SystemClock.now=9500;check(GlassVideo.playing(),"playing manager permits live cadence");current.manager.active=false;SystemClock.now=9700;check(!GlassVideo.playing(),"native pause stops cadence even before another progress event");
  check(GlassVideo.render(first)==null,"old root cannot sample new player");
  second.visible=false;check(GlassVideo.render(second)==null,"off-screen renderer excluded");second.visible=true;
  current.cell.render.surface.valid=false;check(GlassVideo.surface()==null,"released surface excluded");
  Controller textureController=new Controller("texture");TextureView texture=new TextureView();textureController.cell.render.view=texture;
  GlassVideo.resumed(textureController,"texture");check(!GlassVideo.frameReady(texture),"do not copy a recycled texture before its first new frame");
  GlassVideo.frameUpdated(new android.graphics.SurfaceTexture());check(GlassBackdrop.frames==0,"adjacent decoder texture ignored");
  texture.texture.stamp=1;GlassVideo.frameUpdated(texture.texture);check(GlassBackdrop.frames==1 && GlassVideo.frameReady(texture),"actual new frame enables texture capture");
  GlassVideo.frameUpdated(texture.texture);check(GlassBackdrop.frames==1,"duplicate texture timestamp ignored");
  texture.texture.stamp=2;GlassVideo.frameUpdated(texture.texture);check(GlassBackdrop.frames==2,"new frames independent of sparse playback progress");
  textureController.post=new Post("recycled");texture.texture.stamp=3;GlassVideo.frameUpdated(texture.texture);check(GlassBackdrop.frames==2,"late frame from recycled binding ignored");
  Design.enabled=false;events=GlassBackdrop.events;SystemClock.now=10000;GlassVideo.progress(current,"next",1000);
  check(GlassBackdrop.events==events,"MD3 does no video capture work");
 }
}
'''

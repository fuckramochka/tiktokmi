"""Read-only backdrop traversal: exclude complete glass subtrees without hiding live views."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class BackdropSceneTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_capture_order_exclusion_and_unsupported_child(self):
        sources = {
            'android/graphics/Rect.java': 'package android.graphics;public class Rect{}',
            'android/graphics/Canvas.java': '''package android.graphics;
public class Canvas{public static class EdgeType{public static final EdgeType AA=new EdgeType();}
public StringBuilder log=new StringBuilder();int depth;public int save(){return ++depth;}
public int saveLayerAlpha(float a,float b,float c,float d,int alpha){return ++depth;}
public void restoreToCount(int count){depth=count-1;}public void clipRect(Rect r){}
public void clipRect(float a,float b,float c,float d){}public void translate(float x,float y){}
public void concat(Object m){}public boolean quickReject(float a,float b,float c,float d,EdgeType t){return false;}}
''',
            'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable;
public class Drawable{String label;public Drawable(String s){label=s;}public void draw(android.graphics.Canvas c){c.log.append(label).append(' ');}}''',
            'android/view/View.java': '''package android.view;
public class View{public static final int VISIBLE=0;public String label;public int visibility;public float alpha=1,z;public boolean broken;
public View(String s){label=s;}public int getVisibility(){return visibility;}public float getAlpha(){return alpha;}
public int getWidth(){return 100;}public int getHeight(){return 100;}public android.graphics.Rect getClipBounds(){return null;}
public void draw(android.graphics.Canvas c){if(broken)throw new IllegalArgumentException("hardware-only");c.log.append(label).append(' ');}
public float getZ(){return z;}public int getLeft(){return 0;}public int getTop(){return 0;}public Object getMatrix(){return null;}}
''',
            'android/view/ViewGroup.java': '''package android.view;
public class ViewGroup extends View{public java.util.ArrayList<View> children=new java.util.ArrayList<>();
public ViewGroup(String s){super(s);}public android.graphics.drawable.Drawable getBackground(){return new android.graphics.drawable.Drawable(label+"-bg");}
public android.graphics.drawable.Drawable getForeground(){return new android.graphics.drawable.Drawable(label+"-fg");}
public boolean getClipChildren(){return true;}public boolean getClipToPadding(){return true;}
public int getPaddingLeft(){return 0;}public int getPaddingTop(){return 0;}public int getPaddingRight(){return 0;}public int getPaddingBottom(){return 0;}
public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}
public int getScrollX(){return 0;}public int getScrollY(){return 0;}}
''',
            'mi/tiktokmi/Test.java': '''package mi.tiktokmi;
import android.view.*;import android.graphics.*;
public class Test{public static void main(String[] args){
 ViewGroup root=new ViewGroup("root"),owned=new ViewGroup("glass");owned.children.add(new View("own-text"));
 View image=new View("image"),raised=new View("raised"),hardware=new View("hardware"),hidden=new View("hidden");
 raised.z=3;hardware.broken=true;hidden.alpha=0;
 root.children.add(raised);root.children.add(image);root.children.add(owned);root.children.add(hardware);root.children.add(hidden);root.children.add(new View("after-hardware"));
 Canvas c=new Canvas();BackdropScene.draw(root,c,v->v==owned);
 String result=c.log.toString();if(!result.equals("root-bg image after-hardware raised root-fg "))throw new AssertionError(result);
 if(owned.visibility!=View.VISIBLE || owned.alpha!=1 || owned.children.size()!=1)throw new AssertionError("live UI mutated");
 Canvas again=new Canvas();BackdropScene.draw(root,again,v->false);
 if(!again.log.toString().contains("glass-bg own-text glass-fg"))throw new AssertionError("underlying window lost its own contents");
}}
''',
        }
        sources['mi/tiktokmi/BackdropScene.java'] = (ROOT / 'inject/java/mi/tiktokmi/BackdropScene.java').read_text()
        with tempfile.TemporaryDirectory() as directory:
            work = pathlib.Path(directory)
            for name, source in sources.items():
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)
            result = subprocess.run(['javac', '-d', str(work)] + [str(p) for p in work.rglob('*.java')], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            result = subprocess.run(['java', '-cp', str(work), 'mi.tiktokmi.Test'], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)

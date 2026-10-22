"""Oversized cropped renderers must stay eligible; adjacent feed cells must not."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]

class BackdropSourceTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
    def test_crop_selection_and_neighbour_exclusion(self):
        sources={
            'android/graphics/Rect.java':'''package android.graphics;public class Rect{public int left,top,right,bottom;public Rect(){}public Rect(int l,int t,int r,int b){set(l,t,r,b);}public void set(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}public int width(){return right-left;}public int height(){return bottom-top;}public int centerX(){return (left+right)/2;}public int centerY(){return (top+bottom)/2;}public boolean contains(int x,int y){return x>=left&&x<right&&y>=top&&y<bottom;}}''',
            'android/view/View.java':'''package android.view;import android.graphics.Rect;public class View{public View parent;public Rect box=new Rect(0,0,360,800);public boolean attached=true,shown=true;public boolean isAttachedToWindow(){return attached;}public boolean isShown(){return shown;}public Object getParent(){return parent;}public boolean getGlobalVisibleRect(Rect out){out.set(box.left,box.top,box.right,box.bottom);return out.width()>0&&out.height()>0;}public int getWidth(){return 1440;}public int getHeight(){return 3200;}}''',
            'com/ss/android/ugc/aweme/feed/VideoViewCellRootView.java':'package com.ss.android.ugc.aweme.feed;public class VideoViewCellRootView extends android.view.View{}',
            'com/ss/android/ugc/aweme/common/widget/VerticalViewPager.java':'package com.ss.android.ugc.aweme.common.widget;public class VerticalViewPager extends android.view.View{}',
            'cat/narezany/margyt/Test.java':'''package cat.narezany.margyt;
import android.view.View;import android.graphics.Rect;import com.ss.android.ugc.aweme.feed.VideoViewCellRootView;import com.ss.android.ugc.aweme.common.widget.VerticalViewPager;
public class Test{static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}public static void main(String[] args){
 VerticalViewPager pager=new VerticalViewPager();VideoViewCellRootView current=new VideoViewCellRootView(),next=new VideoViewCellRootView();current.parent=pager;next.parent=pager;
 View zoomed=new View();zoomed.parent=current;View adjacent=new View();adjacent.parent=next;
 current.box=new Rect(0,0,360,600);next.box=new Rect(0,600,360,800);adjacent.box=new Rect(0,600,360,800);
 check(BackdropSource.visible(zoomed),"cropped renderer rejected because its layout exceeds viewport");check(!BackdropSource.visible(adjacent),"next cell selected while preloading");
 current.box=new Rect(0,0,360,300);next.box=new Rect(0,300,360,800);adjacent.box=new Rect(0,300,360,800);
 check(!BackdropSource.visible(zoomed),"old cell survived viewport handover");check(BackdropSource.visible(adjacent),"displayed cell not selected after swipe");
 adjacent.attached=false;check(!BackdropSource.visible(adjacent),"released renderer selected");adjacent.attached=true;adjacent.shown=false;check(!BackdropSource.visible(adjacent),"hidden renderer selected");
 View standalone=new View();check(BackdropSource.visible(standalone),"non-feed backdrop lost");standalone.box=new Rect();check(!BackdropSource.visible(standalone),"zero-area renderer selected");
}}''',
        }
        sources['cat/narezany/margyt/BackdropSource.java']=(ROOT/'inject/java/cat/narezany/margyt/BackdropSource.java').read_text()
        with tempfile.TemporaryDirectory() as directory:
            work=pathlib.Path(directory)
            for name,source in sources.items():
                path=work/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source)
            compiled=subprocess.run(['javac','-d',str(work)]+[str(p) for p in work.rglob('*.java')],capture_output=True,text=True)
            self.assertEqual(0,compiled.returncode,compiled.stderr)
            result=subprocess.run(['java','-cp',str(work),'cat.narezany.margyt.Test'],capture_output=True,text=True)
            self.assertEqual(0,result.returncode,result.stderr)

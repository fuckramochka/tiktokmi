package cat.narezany.margyt;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;

/** Read-only view capture. Skipping an owned subtree never changes its actual visibility. */
final class BackdropScene {
    interface Skip { boolean skip(View view); }
    private BackdropScene(){}
    static void draw(View root,Canvas canvas,Skip skip){draw(root,canvas,skip,0,new int[]{5000});}
    private static void draw(View view,Canvas canvas,Skip skip,int depth,int[] budget){
        if(depth>60 || budget[0]--<=0 || view.getVisibility()!=View.VISIBLE
                || view.getAlpha()<.01f || view.getWidth()<1 || view.getHeight()<1 || skip.skip(view))return;
        int save=canvas.save();
        try{
            if(view.getAlpha()<.999f)canvas.saveLayerAlpha(0,0,view.getWidth(),view.getHeight(),Math.round(view.getAlpha()*255));
            Rect clip=view.getClipBounds();if(clip!=null)canvas.clipRect(clip);
            if(!(view instanceof ViewGroup)){view.draw(canvas);return;}
            ViewGroup group=(ViewGroup)view;
            Drawable background=group.getBackground();if(background!=null)background.draw(canvas);
            int children=canvas.save();
            if(group.getClipChildren()){
                if(group.getClipToPadding())canvas.clipRect(group.getPaddingLeft(),group.getPaddingTop(),group.getWidth()-group.getPaddingRight(),group.getHeight()-group.getPaddingBottom());
                else canvas.clipRect(0,0,group.getWidth(),group.getHeight());
            }
            ArrayList<View> order=new ArrayList<>();
            for(int i=0;i<group.getChildCount();i++)order.add(group.getChildAt(i));
            order.sort((a,b)->Float.compare(a.getZ(),b.getZ()));
            for(View child:order){
                int at=canvas.save();canvas.translate(child.getLeft()-group.getScrollX(),child.getTop()-group.getScrollY());canvas.concat(child.getMatrix());
                if(canvas.quickReject(0,0,child.getWidth(),child.getHeight(),Canvas.EdgeType.AA)){canvas.restoreToCount(at);continue;}
                draw(child,canvas,skip,depth+1,budget);canvas.restoreToCount(at);
            }
            canvas.restoreToCount(children);
            Drawable foreground=group.getForeground();if(foreground!=null)foreground.draw(canvas);
        }catch(RuntimeException unsupported){
            // A hardware-only native child must not erase successfully captured siblings.
        }finally{canvas.restoreToCount(save);}
    }
}

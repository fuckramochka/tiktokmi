package mi.tiktokmi;

import android.graphics.Rect;
import android.view.View;

/** Select the displayed feed cell, independently of the video's crop/zoom dimensions. */
final class BackdropSource {
    private BackdropSource(){}
    static boolean visible(View view){
        Rect visible=new Rect();
        if(!view.isAttachedToWindow() || !view.isShown() || !view.getGlobalVisibleRect(visible)
                || visible.width()<2 || visible.height()<2)return false;
        View cell=null;
        for(View step=view;step!=null;step=step.getParent() instanceof View?(View)step.getParent():null){
            String name=step.getClass().getName();
            if(name.equals("com.ss.android.ugc.aweme.feed.VideoViewCellRootView"))cell=step;
            if(cell!=null && name.equals("com.ss.android.ugc.aweme.common.widget.VerticalViewPager")){
                Rect viewport=new Rect(),shownCell=new Rect();
                return step.getGlobalVisibleRect(viewport) && cell.getGlobalVisibleRect(shownCell)
                        && shownCell.contains(viewport.centerX(),viewport.centerY());
            }
        }
        return true;
    }
}

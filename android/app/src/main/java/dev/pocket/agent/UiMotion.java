package dev.pocket.agent;

import android.animation.ValueAnimator;
import android.view.*;
import android.view.animation.PathInterpolator;

final class UiMotion {
    /** Both pages remain siblings under the Activity owner, never in ViewOverlay. */
    static void conversation(ViewGroup host,View oldPage,View newPage,android.graphics.Rect origin,boolean closing,String rowTag){
        if(closing)host.addView(newPage,0,new ViewGroup.LayoutParams(-1,-1));else{newPage.setAlpha(0);host.addView(newPage,new ViewGroup.LayoutParams(-1,-1));}
        newPage.requestApplyInsets();View moving=closing?oldPage:newPage;
        newPage.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){
            newPage.getViewTreeObserver().removeOnPreDrawListener(this);
            if(!newPage.isAttachedToWindow()||moving.getParent()!=host)return true;
            android.graphics.Rect source=new android.graphics.Rect(origin);View row=closing?newPage.findViewWithTag(rowTag):null;if(row!=null)row.getGlobalVisibleRect(source);
            int[] xy=new int[2];moving.getLocationOnScreen(xy);float x=source.left-xy[0],y=source.top-xy[1],sx=Math.max(.1f,(float)source.width()/Math.max(1,moving.getWidth())),sy=Math.max(.025f,(float)source.height()/Math.max(1,moving.getHeight()));
            Runnable finish=()->{if(oldPage.getParent()==host)host.removeView(oldPage);newPage.setAlpha(1);};
            if(!ValueAnimator.areAnimatorsEnabled()){finish.run();return true;}
            moving.setPivotX(0);moving.setPivotY(0);if(!closing){moving.setScaleX(sx);moving.setScaleY(sy);moving.setTranslationX(x);moving.setTranslationY(y);moving.setAlpha(.4f);}
            moving.animate().scaleX(closing?sx:1).scaleY(closing?sy:1).translationX(closing?x:0).translationY(closing?y:0).alpha(closing?0:1).setDuration(closing?280:340).setInterpolator(new PathInterpolator(.2f,0f,0f,1f)).withEndAction(finish).start();return true;
        }});
    }
    static void change(ViewGroup container,int direction){
        if(container==null||!container.isLaidOut()||!ValueAnimator.areAnimatorsEnabled())return;
        // Visibility transitions reparent departing ComposeViews into ViewOverlay,
        // which has no Activity lifecycle/saved-state owner. Animate the attached
        // container itself so every glass control stays inside its owned view tree.
        container.animate().cancel();container.setAlpha(.45f);
        container.setTranslationX(direction*AgentStyle.dp(container.getContext(),14));
        container.postOnAnimation(()->{
            if(!container.isAttachedToWindow()){container.setAlpha(1);container.setTranslationX(0);return;}
            container.animate().alpha(1).translationX(0).setDuration(direction==0?300:260)
                .setInterpolator(new PathInterpolator(.2f,0f,0f,1f)).start();
        });
    }
}

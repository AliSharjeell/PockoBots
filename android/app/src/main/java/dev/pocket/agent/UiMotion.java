package dev.pocket.agent;

import android.animation.ValueAnimator;
import android.view.*;
import android.view.animation.PathInterpolator;

final class UiMotion {
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

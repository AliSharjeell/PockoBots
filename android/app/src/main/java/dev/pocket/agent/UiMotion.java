package dev.pocket.agent;

import android.animation.ValueAnimator;
import android.transition.*;
import android.view.*;
import android.view.animation.PathInterpolator;

final class UiMotion {
    static void change(ViewGroup container,int direction){
        if(container==null||!container.isLaidOut()||!ValueAnimator.areAnimatorsEnabled())return;
        TransitionManager.endTransitions(container);
        TransitionSet transition=new TransitionSet().setOrdering(TransitionSet.ORDERING_TOGETHER);
        transition.addTransition(new Fade(Fade.OUT).setDuration(150));
        transition.addTransition(new Fade(Fade.IN).setDuration(direction==0?320:280));
        if(direction!=0){Slide slide=new Slide(direction>0?Gravity.END:Gravity.START);slide.setMode(Visibility.MODE_IN);slide.setDuration(300);transition.addTransition(slide);}
        transition.setInterpolator(new PathInterpolator(.2f,0f,0f,1f));TransitionManager.beginDelayedTransition(container,transition);
    }
}

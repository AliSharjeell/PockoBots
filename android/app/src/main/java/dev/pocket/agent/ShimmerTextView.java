package dev.pocket.agent;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.widget.TextView;

/** Native equivalent of the reference's 2.4-second white thinking-text shimmer. */
final class ShimmerTextView extends TextView {
    private LinearGradient glow;private final Matrix transform=new Matrix();private boolean active;
    ShimmerTextView(Context context,String text){super(context);setTypeface(AgentStyle.font(context));setTextColor(AgentStyle.TEXT);setTextSize(12);setLetterSpacing(-.06f);setText(text);}
    void active(boolean value){active=value;invalidate();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);glow=new LinearGradient(0,0,Math.max(1,w)*2.2f,0,AgentStyle.dark?new int[]{0xff71717a,0xffd4d4d8,0xffffffff,0xffd4d4d8,0xff71717a}:new int[]{0xff71717a,0xff27272a,0xff09090b,0xff27272a,0xff71717a},new float[]{0,.32f,.5f,.68f,1},Shader.TileMode.MIRROR);}
    @Override protected void onDraw(Canvas canvas){
        boolean animate=active;if(animate&&glow!=null){float phase=(SystemClock.uptimeMillis()%2400)/2400f;transform.setTranslate(getWidth()*(2-4*phase),0);glow.setLocalMatrix(transform);getPaint().setShader(glow);}else getPaint().setShader(null);
        super.onDraw(canvas);if(animate&&isShown()&&getWindowVisibility()==VISIBLE)postInvalidateDelayed(33);
    }
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)invalidate();}
}

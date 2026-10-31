package dev.pocket.agent;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Lightweight native placeholders; no network images or perpetual detached animation. */
final class LoadingSkeleton extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final String kind;private final int rows;private float phase;private ValueAnimator shimmer;
    LoadingSkeleton(Context context,String kind,int rows){super(context);this.kind=kind;this.rows=rows;setContentDescription("Loading "+kind);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),resolveSize((int)dp(rows*(kind.equals("chat")?92:82)+20),h));}
    private void block(float x,float y,float width,float height,float radius){canvas.drawRoundRect(x,y,x+Math.max(0,width),y+height,radius,radius,paint);}
    private Canvas canvas;
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);this.canvas=canvas;float w=getWidth(),band=Math.max(dp(120),w*.7f),x=phase*(w+band*2)-band;int base=AgentStyle.dark?0xff202023:0xffe5e5e9,light=AgentStyle.dark?0xff303034:0xfff3f3f5;paint.setShader(new LinearGradient(x-band,0,x+band,0,new int[]{base,light,base},null,Shader.TileMode.CLAMP));
        for(int i=0;i<rows;i++){float y=dp(14+i*(kind.equals("chat")?92:82));if(kind.equals("chat")){boolean right=i%2==1;float width=w*(right?.61f:.8f);block(right?w-width-dp(12):dp(12),y,width,dp(62),dp(20));}else{block(dp(10),y,dp(42),dp(42),dp(kind.equals("files")?10:15));block(dp(66),y+dp(3),w*.44f,dp(13),dp(6));block(dp(66),y+dp(26),w*.59f,dp(10),dp(5));}}paint.setShader(null);this.canvas=null;
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(ValueAnimator.areAnimatorsEnabled()){shimmer=ValueAnimator.ofFloat(0,1);shimmer.setDuration(1450);shimmer.setRepeatCount(ValueAnimator.INFINITE);shimmer.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});shimmer.start();}}
    @Override protected void onDetachedFromWindow(){if(shimmer!=null){shimmer.cancel();shimmer=null;}super.onDetachedFromWindow();}
}

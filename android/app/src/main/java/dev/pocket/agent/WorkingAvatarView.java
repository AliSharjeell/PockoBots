package dev.pocket.agent;

import android.animation.ValueAnimator;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.Locale;

/** The bot's own avatar gently breathes and tilts while its task is running. */
final class WorkingAvatarView extends FrameLayout {
    private final View image;private boolean working;private ValueAnimator animation;
    WorkingAvatarView(BotsActivity app,JSONObject bot,int textSize){
        super(app);setClipChildren(false);setClipToPadding(false);
        String avatar=bot.optString("avatar","");
        if(avatar.startsWith("bloub:")){
            String[] parts=avatar.split(":");BloubView bloub=new BloubView(app);if(parts.length>1)bloub.setShapeId(parts[1]);if(parts.length>2)bloub.setExpressionId(parts[2]);bloub.setColorHex(bot.optString("accent","#007AFF"));image=bloub;
        }else{
            TextView initials=app.text(avatar.isEmpty()?bot.optString("name","?").substring(0,1).toUpperCase(Locale.ROOT):avatar,textSize);initials.setGravity(Gravity.CENTER);initials.setPadding(0,0,0,0);initials.setTypeface(AgentStyle.font(app),Typeface.BOLD);initials.setTextColor(0xffffffff);int color;try{color=android.graphics.Color.parseColor(bot.optString("accent"));}catch(Exception ignored){color=AgentStyle.BLUE;}initials.setBackground(AgentStyle.surface(app,color,16,false));image=initials;
        }
        addView(image,new FrameLayout.LayoutParams(-1,-1));setContentDescription(bot.optString("name")+" avatar");
    }
    void setWorking(boolean value){if(working==value)return;working=value;if(value&&isAttachedToWindow())start();else if(!value)stop();}
    private void start(){
        if(animation!=null||!ValueAnimator.areAnimatorsEnabled())return;
        animation=ValueAnimator.ofFloat(0,1);animation.setDuration(1800);animation.setRepeatCount(ValueAnimator.INFINITE);
        animation.addUpdateListener(value->{double phase=SystemClock.uptimeMillis()%1800/1800d*Math.PI*2;float scale=.98f+.04f*(float)((Math.sin(phase)+1)/2);image.setScaleX(scale);image.setScaleY(scale);image.setRotation(3f*(float)Math.sin(phase));});animation.start();
    }
    private void stop(){if(animation!=null){animation.cancel();animation=null;}image.setScaleX(1);image.setScaleY(1);image.setRotation(0);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(working)start();}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}

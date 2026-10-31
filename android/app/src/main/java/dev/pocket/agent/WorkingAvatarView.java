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
    private final View image;private boolean working,ticking;
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
    private final Runnable frame=new Runnable(){public void run(){
        if(!working||!isAttachedToWindow()||getWindowVisibility()!=VISIBLE){ticking=false;return;}
        long now=SystemClock.uptimeMillis();double phase=now%1700/1700d*Math.PI*2;float wave=(float)Math.sin(phase);float scale=.96f+.075f*(wave+1)/2;
        image.setScaleX(scale);image.setScaleY(1.025f-.04f*(wave+1)/2);image.setRotation(9f*wave);image.setTranslationY(-AgentStyle.dp(getContext(),3)*(float)Math.cos(phase));
        if(image instanceof BloubView){long blink=now%3100;((BloubView)image).setWorkBlink(blink<150?Math.max(.12f,Math.abs(blink-75)/75f):1f);}postDelayed(this,33);
    }};
    private void start(){if(ticking)return;ticking=true;post(frame);}
    private void stop(){removeCallbacks(frame);ticking=false;image.setScaleX(1);image.setScaleY(1);image.setRotation(0);image.setTranslationY(0);if(image instanceof BloubView)((BloubView)image).setWorkBlink(1f);}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(image!=null){if(visibility==VISIBLE&&working)start();else stop();}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(working)start();}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}

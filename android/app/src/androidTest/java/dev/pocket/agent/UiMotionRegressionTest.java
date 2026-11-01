package dev.pocket.agent;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class UiMotionRegressionTest {
    @Test public void conversationExpandsAndCollapsesWithoutReparentingGlassIntoAnOverlay() throws Exception {
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();String app=inst.getTargetContext().getPackageName();org.junit.Assume.assumeTrue(app.endsWith(".qa"));Activity host=inst.startActivitySync(new Intent().setClassName(app,"dev.pocket.agent.UiMotionRegressionActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));FrameLayout[] pages=new FrameLayout[3];
        try{
            inst.runOnMainSync(()->{pages[0]=new FrameLayout(host);Button button=new Button(host);button.setText("Bot");pages[0].addView(GlassControls.wrap(button,24,false),new FrameLayout.LayoutParams(280,100));host.setContentView(pages[0]);});inst.waitForIdleSync();Thread.sleep(180);
            inst.runOnMainSync(()->{pages[1]=new FrameLayout(host);Button button=new Button(host);button.setText("Chat");pages[1].addView(GlassControls.wrap(button,24,false),new FrameLayout.LayoutParams(280,100));UiMotion.conversation(host.findViewById(android.R.id.content),pages[0],pages[1],new android.graphics.Rect(20,140,300,260),false,"");});inst.waitForIdleSync();Thread.sleep(600);
            inst.runOnMainSync(()->{assertNull(pages[0].getParent());assertNotNull(androidx.lifecycle.ViewTreeLifecycleOwner.get(pages[1]));pages[2]=new FrameLayout(host);Button row=new Button(host);row.setTag("bot-row-test");pages[2].addView(row,new FrameLayout.LayoutParams(280,100));UiMotion.conversation(host.findViewById(android.R.id.content),pages[1],pages[2],new android.graphics.Rect(20,140,300,260),true,"bot-row-test");});inst.waitForIdleSync();Thread.sleep(600);
            inst.runOnMainSync(()->{assertNull(pages[1].getParent());assertNotNull(androidx.lifecycle.ViewTreeLifecycleOwner.get(pages[2]));assertEquals(1,((ViewGroup)host.findViewById(android.R.id.content)).getChildCount());});
        }finally{inst.runOnMainSync(host::finish);}
    }
    @Test public void replacingAttachedGlassControlsKeepsTheirLifecycleOwnerAcrossNavigationAndThemeChanges() throws Exception {
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        String app=inst.getTargetContext().getPackageName();org.junit.Assume.assumeTrue("Use isolated QA package",app.endsWith(".qa"));
        Activity host=inst.startActivitySync(new Intent().setClassName(app,"dev.pocket.agent.UiMotionRegressionActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean dark=AgentStyle.dark;
        try{
            for(int direction:new int[]{1,0,-1,0,1,0,-1}){
                final View[] current={null};
                inst.runOnMainSync(()->{
                    FrameLayout parent=host.findViewById(android.R.id.content);
                    UiMotion.change(parent,direction);parent.removeAllViews();AgentStyle.dark=!AgentStyle.dark;
                    Button button=new Button(host);button.setText("Glass regression fixture");
                    current[0]=GlassControls.wrap(button,24f,false);
                    parent.addView(current[0],new FrameLayout.LayoutParams(220,80));
                });
                inst.waitForIdleSync();Thread.sleep(400);
                inst.runOnMainSync(()->{
                    assertTrue(current[0].isAttachedToWindow());
                    assertNotNull(androidx.lifecycle.ViewTreeLifecycleOwner.get(current[0]));
                    assertNotNull(androidx.savedstate.ViewTreeSavedStateRegistryOwner.get(current[0]));
                    assertEquals(1f,((ViewGroup)host.findViewById(android.R.id.content)).getAlpha(),.01f);
                });
            }
        }finally{inst.runOnMainSync(()->{AgentStyle.dark=dark;host.finish();});}
    }
}

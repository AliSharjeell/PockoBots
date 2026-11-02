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
    @Test public void glassPopupOwnsItsWindowLifecycleBeforeComposeAttaches() throws Exception {
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();String app=inst.getTargetContext().getPackageName();org.junit.Assume.assumeTrue("Use isolated QA package",app.endsWith(".qa"));
        Activity host=inst.startActivitySync(new Intent().setClassName(app,"dev.pocket.agent.UiMotionRegressionActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean original=AgentStyle.dark;
        try{for(boolean dark:new boolean[]{true,false,true}){
            final android.widget.PopupWindow[] popup={null};final View[] content={null};final android.widget.LinearLayout[] rows={null};
            inst.runOnMainSync(()->{
                AgentStyle.dark=dark;FrameLayout parent=host.findViewById(android.R.id.content);parent.removeAllViews();Button anchor=new Button(host);anchor.setText("Menu");parent.addView(anchor,new FrameLayout.LayoutParams(100,80));
                rows[0]=new android.widget.LinearLayout(host);rows[0].setOrientation(1);Button option=new Button(host);option.setText("Floating bot");rows[0].addView(option);
                content[0]=GlassControls.wrap(rows[0],22f,false);rows[0].setLayoutParams(new FrameLayout.LayoutParams(-1,-2));GlassControls.bindPopup(content[0],(androidx.activity.ComponentActivity)host);
                popup[0]=new android.widget.PopupWindow(content[0],300,-2,true);popup[0].setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));popup[0].showAsDropDown(anchor);
            });
            inst.waitForIdleSync();Thread.sleep(150);
            inst.runOnMainSync(()->{
                assertTrue(popup[0].isShowing());assertNotNull(androidx.lifecycle.ViewTreeLifecycleOwner.get(content[0].getRootView()));assertNotNull(androidx.savedstate.ViewTreeSavedStateRegistryOwner.get(content[0].getRootView()));
                Button effort=new Button(host);effort.setText("Max effort");rows[0].addView(effort);popup[0].update(300,-2);
            });inst.waitForIdleSync();inst.runOnMainSync(()->popup[0].dismiss());inst.waitForIdleSync();
        }}finally{inst.runOnMainSync(()->{AgentStyle.dark=original;host.finish();});}
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

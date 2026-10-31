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
    @Test public void replacingAttachedGlassControlsKeepsTheirLifecycleOwnerAcrossNavigationAndThemeChanges() throws Exception {
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        String app=inst.getTargetContext().getPackageName();assertTrue("Use isolated QA package",app.endsWith(".qa"));
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

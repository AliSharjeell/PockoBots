package dev.pocket.agent;

import android.content.Context;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Does not launch another app or change accounts, profiles, settings, or snapshots. */
public class AgentDisplayCapabilitiesTest {
    @Test public void ownDisplayProbeNeverClaimsHiddenAppControl()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        JSONObject capabilities=AgentDisplayCapabilities.inspect(context);
        assertFalse(capabilities.getBoolean("hiddenNativeAppControl"));assertFalse(capabilities.getBoolean("isolatedAppAccounts"));assertEquals("foreground",capabilities.getString("availableMode"));
        CountDownLatch complete=new CountDownLatch(1);JSONObject[] result={null};
        AgentDisplayCapabilities.probeOwnDisplay(context,"com.android.chrome",value->{result[0]=value;complete.countDown();});
        assertTrue("Display probe did not finish",complete.await(12,TimeUnit.SECONDS));assertNotNull(result[0]);
        assertTrue(result[0].getBoolean("probeReleased"));assertFalse(result[0].getBoolean("hiddenNativeAppControl"));assertFalse(result[0].getBoolean("isolatedAppAccounts"));assertFalse(result[0].getBoolean("targetActivityLaunched"));
        Io.write(new File(context.getFilesDir(),"display-probe.json"),result[0].toString());
        System.out.println("Display probe: "+result[0]);
        // A platform can deny private displays. That remains a valid unsupported result.
        // When it accepts this display, require actual owned marker pixels rather than a display ID alone.
        if(result[0].optBoolean("ownDisplayCreated"))assertTrue(result[0].toString(),result[0].getBoolean("ownContentVerified"));
    }

    @Test public void youtubeNewTaskLaunchIsMeasuredWithoutClaimingIsolation()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        org.junit.Assume.assumeTrue(context.getPackageName().endsWith(".qa"));
        android.app.UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String before=shell(automation,"dumpsys activity activities");
        org.junit.Assume.assumeFalse("Do not risk moving an existing YouTube task",before.contains("com.google.android.youtube"));
        CountDownLatch complete=new CountDownLatch(1);JSONObject[] result={null};
        AgentDisplayCapabilities.probeExternalAppLaunch(context,"com.google.android.youtube",value->{result[0]=value;complete.countDown();});
        assertTrue(complete.await(12,TimeUnit.SECONDS));assertNotNull(result[0]);
        String after=shell(automation,"dumpsys activity activities");
        result[0].put("mainTopActivityBefore",top(before));result[0].put("mainTopActivityAfter",top(after));result[0].put("mainTopActivityUnchanged",top(before).equals(top(after)));
        assertTrue(result[0].toString(),result[0].getBoolean("mainTopActivityUnchanged"));
        assertFalse(result[0].getBoolean("hiddenNativeAppControl"));assertFalse(result[0].getBoolean("isolatedAppAccounts"));assertTrue(result[0].getBoolean("probeReleased"));
        Io.write(new File(context.getFilesDir(),"youtube-display-probe.json"),result[0].toString());System.out.println("YouTube display probe: "+result[0]);
    }
    private static String shell(android.app.UiAutomation ui,String command)throws Exception{
        try(android.os.ParcelFileDescriptor fd=ui.executeShellCommand(command);java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){return new String(Io.bytes(in),java.nio.charset.StandardCharsets.UTF_8);}
    }
    private static String top(String dump){for(String line:dump.split("\n"))if(line.contains("topResumedActivity="))return line.trim();return "unknown";}
}

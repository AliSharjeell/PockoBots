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
}

package dev.pocket.agent;

import android.app.UiAutomation;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

/** Explicit shell-companion prototype; skipped unless its already-created display ID is provided. */
public class ShellYouTubeDisplayTest {
    @Test public void inputTargetsCompanionDisplayWhileMainApplicationStaysUnchanged()throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();Assume.assumeTrue(context.getPackageName().endsWith(".qa"));
        String requested=InstrumentationRegistry.getArguments().getString("companionDisplayId");Assume.assumeTrue(requested!=null&&requested.matches("[1-9][0-9]*"));int displayId=Integer.parseInt(requested);
        UiAutomation ui=InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        android.accessibilityservice.AccessibilityServiceInfo info=ui.getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;ui.setServiceInfo(info);
        long end=System.currentTimeMillis()+15000;AccessibilityNodeInfo root=null;
        while(root==null&&System.currentTimeMillis()<end){root=youtube(ui,displayId);if(root==null)Thread.sleep(250);}assertNotNull("YouTube window missing on companion display",root);
        String mainBefore=packages(ui,0),before=structure(root);assertFalse("Main display application must be observable",mainBefore.equals("[]"));String command="input -d "+displayId+" keyevent KEYCODE_TAB";String inputResult=shell(ui,command);
        boolean changed=false;String after=before;
        for(int i=0;i<5;i++){Thread.sleep(300);AccessibilityNodeInfo current=youtube(ui,displayId);if(current!=null)after=structure(current);if(!after.equals(before)){changed=true;break;}shell(ui,command);}
        if(!changed){inputResult+=shell(ui,"input -d "+displayId+" keyevent KEYCODE_SEARCH");Thread.sleep(600);AccessibilityNodeInfo current=youtube(ui,displayId);if(current!=null)after=structure(current);changed=!before.equals(after);}
        String mainAfter=packages(ui,0);JSONObject result=new JSONObject().put("mode","adb-shell-companion-prototype").put("displayId",displayId).put("youtubeWindowVerified",true).put("inputDispatchOutput",inputResult.trim()).put("inputChangedSecondaryUi",changed).put("mainApplicationBefore",mainBefore).put("mainApplicationAfter",mainAfter).put("mainApplicationUnchanged",mainBefore.equals(mainAfter)).put("separateAccountVerified",false).put("standaloneApkHiddenControl",false);
        Io.write(new File(context.getFilesDir(),"youtube-shell-display-probe.json"),result.toString());System.out.println("YouTube shell display: "+result);
        assertTrue(result.toString(),mainBefore.equals(mainAfter));assertTrue("Display-scoped input must change the secondary UI before it can be called verified",changed);
    }
    private static AccessibilityNodeInfo youtube(UiAutomation ui,int display){List<AccessibilityWindowInfo> windows=ui.getWindowsOnAllDisplays().get(display);if(windows!=null)for(AccessibilityWindowInfo window:windows){AccessibilityNodeInfo root=window.getRoot();if(root!=null&&"com.google.android.youtube".equals(String.valueOf(root.getPackageName())))return root;}return null;}
    private static String packages(UiAutomation ui,int display){List<String> packages=new ArrayList<>();List<AccessibilityWindowInfo> windows=ui.getWindowsOnAllDisplays().get(display);if(windows!=null)for(AccessibilityWindowInfo window:windows)if(window.getType()==AccessibilityWindowInfo.TYPE_APPLICATION){AccessibilityNodeInfo root=window.getRoot();if(root!=null)packages.add(String.valueOf(root.getPackageName()));}Collections.sort(packages);return packages.toString();}
    private static String structure(AccessibilityNodeInfo node){StringBuilder out=new StringBuilder();walk(node,out,0);return out.toString();}
    private static void walk(AccessibilityNodeInfo node,StringBuilder out,int depth){if(node==null||depth>25||out.length()>20000)return;out.append(node.getViewIdResourceName()).append(':').append(node.getClassName()).append(':').append(node.isFocused()).append(':').append(node.isAccessibilityFocused()).append(';');for(int i=0;i<node.getChildCount();i++)walk(node.getChild(i),out,depth+1);}
    private static String shell(UiAutomation ui,String command)throws Exception{try(ParcelFileDescriptor fd=ui.executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){return new String(Io.bytes(in),java.nio.charset.StandardCharsets.UTF_8);}}
}

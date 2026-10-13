package dev.pocket.agent;

import android.app.UiAutomation;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.artemis.helper.ArtemisAccessibilityService;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MobileTaskSmokeTest {
    private static String shell(UiAutomation automation,String command)throws Exception{try(ParcelFileDescriptor fd=automation.executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){return new String(Io.bytes(in),java.nio.charset.StandardCharsets.UTF_8).trim();}}
    private static JSONObject object(Object...pairs)throws Exception{JSONObject value=new JSONObject();for(int i=0;i<pairs.length;i+=2)value.put((String)pairs[i],pairs[i+1]);return value;}
    private static AccessibilityNodeInfo node(UiAutomation automation,String text,boolean edit)throws Exception{
        long deadline=System.currentTimeMillis()+5000;while(System.currentTimeMillis()<deadline){for(AccessibilityWindowInfo window:automation.getWindows()){AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;AccessibilityNodeInfo found=find(root,text,edit);if(found!=null)return found;}Thread.sleep(100);}throw new AssertionError("Missing floating control: "+text);
    }
    private static AccessibilityNodeInfo find(AccessibilityNodeInfo root,String text,boolean edit){
        if(edit?"android.widget.EditText".contentEquals(root.getClassName()==null?"":root.getClassName()):text.contentEquals(root.getText()==null?"":root.getText()))return root;
        for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo child=root.getChild(i);if(child!=null){AccessibilityNodeInfo found=find(child,text,edit);if(found!=null)return found;}}return null;
    }
    private static void toggle(UiAutomation automation)throws Exception{AccessibilityNodeInfo control=node(automation,"Needs your reply",false);while(control!=null&&!control.isClickable())control=control.getParent();assertNotNull(control);assertTrue(control.performAction(AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(450);}
    private static void capture(UiAutomation automation,Context context,String name)throws Exception{android.graphics.Bitmap preview=automation.takeScreenshot();if(preview!=null){try(OutputStream out=new FileOutputStream(new File(context.getFilesDir(),name))){preview.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}preview.recycle();shell(automation,"mkdir -p /sdcard/Download/pocket-agent-ui");shell(automation,"sh -c 'run-as "+context.getPackageName()+" cat files/"+name+" > /sdcard/Download/pocket-agent-ui/"+name+"'");}}
    private static JSONObject until(AgentApi api,String id,String status)throws Exception{long end=System.currentTimeMillis()+45000;JSONObject last=new JSONObject();while(System.currentTimeMillis()<end){last=api.get("/mobile/tasks/"+id);if(status.equals(last.optString("status")))return last;Thread.sleep(150);}fail("Expected "+status+": "+last);return last;}
    @Test public void artemisLaunchTapBackgroundQuestionAndReply()throws Exception{
        // Never grant broad accessibility access on someone's physical phone.
        Assume.assumeTrue(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"));
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();Context context=instrumentation.getTargetContext();
        UiAutomation automation=instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);
        if(Build.VERSION.SDK_INT>=33)automation.grantRuntimePermission(context.getPackageName(),android.Manifest.permission.POST_NOTIFICATIONS);
        String original=Settings.Secure.getString(context.getContentResolver(),"enabled_accessibility_services");
        String enabled=context.getPackageName()+"/com.artemis.helper.ArtemisAccessibilityService";
        if(original!=null&&!original.isEmpty()&&!original.equals("null"))enabled=original+":"+enabled;
        AgentApi api=null;JSONObject settings=null;String id="";
        try{
            shell(automation,"appops set "+context.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
            shell(automation,"settings put secure enabled_accessibility_services "+enabled);shell(automation,"settings put secure accessibility_enabled 1");
            instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long start=System.currentTimeMillis()+240000;while((!RuntimeService.ready||ArtemisAccessibilityService.getInstance()==null)&&System.currentTimeMillis()<start)Thread.sleep(200);
            assertTrue("Runtime: "+RuntimeService.status,RuntimeService.ready);assertNotNull("Accessibility helper did not connect",ArtemisAccessibilityService.getInstance());
            String token=(String)new JSONTokener(Io.text(new File(context.getFilesDir(),"state/token.json"))).nextValue();api=new AgentApi(token);
            settings=api.get("/state").getJSONObject("settings");assertFalse("Use a fresh test installation without a real provider key",settings.optBoolean("hasKey"));
            try(MobileMockModel mock=new MobileMockModel()){
                api.request("PUT","/settings",object("provider","openai","api","openai-completions","baseUrl",mock.baseUrl(),"model","mobile-mock","apiKey","mock-key"));
                JSONObject task=(JSONObject)api.request("POST","/mobile/tasks",object("prompt","Open the Battery page in Settings, ask me if ready, and verify completion"));id=task.getString("id");
                context.startForegroundService(new Intent(context,MobileOverlayService.class).putExtra("taskId",id));
                long armed=System.currentTimeMillis()+5000;while(!MobileAccess.allowed()&&System.currentTimeMillis()<armed)Thread.sleep(100);assertTrue(MobileAccess.allowed());
                // Settings remembers its previous page across our APK reinstalls.
                // Normalize only this disposable emulator's navigation stack.
                shell(automation,"am start -a android.settings.SETTINGS -f 0x10008000");Thread.sleep(600);
                shell(automation,"input keyevent KEYCODE_HOME"); // Activity no longer hosts the task loop.
                Thread.sleep(350);api.request("POST","/mobile/tasks/"+id+"/resume",object());
                JSONObject waiting;try{waiting=until(api,id,"waiting");}catch(AssertionError e){if(mock.failure!=null)throw mock.failure;throw new AssertionError(e.getMessage()+"\nObserved labels: "+mock.observedLabels,e);}if(mock.failure!=null)throw mock.failure;
                assertEquals(id,MobileOverlayService.activeTask);assertTrue(mock.sawImage);assertTrue(mock.stage>=6);
                long gated=System.currentTimeMillis()+6000;while(MobileAccess.allowed()&&System.currentTimeMillis()<gated)Thread.sleep(100);assertFalse("Device access must pause for user input",MobileAccess.allowed());
                // System settings can hide application overlays. The waiting
                // question must also remain usable on the normal home screen.
                shell(automation,"input keyevent KEYCODE_HOME");Thread.sleep(900);
                capture(automation,context,"mobile-waiting.png");toggle(automation);capture(automation,context,"mobile-island.png");toggle(automation);
                Bundle reply=new Bundle();reply.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"ready");assertTrue(node(automation,"reply",true).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,reply));
                assertTrue(node(automation,"Reply & continue",false).performAction(AccessibilityNodeInfo.ACTION_CLICK));
                JSONObject completed=until(api,id,"completed");if(mock.failure!=null)throw mock.failure;
                assertTrue(completed.getString("output").contains("Battery screen verified"));
                assertTrue(Io.text(new File(context.getFilesDir(),"state/mobile-tasks.json")).contains("completed"));
            }
        }finally{
            MobileAccess.authorize(false);context.stopService(new Intent(context,MobileOverlayService.class));
            if(api!=null&&!id.isEmpty())try{api.request("POST","/mobile/tasks/"+id+"/stop",object());}catch(Exception ignored){}
            if(api!=null&&settings!=null){settings.put("clearKey",true);for(int attempt=0;attempt<30;attempt++)try{api.request("PUT","/settings",settings);break;}catch(Exception e){Thread.sleep(100);}}
            if(original==null||original.isEmpty()||original.equals("null"))shell(automation,"settings delete secure enabled_accessibility_services");else shell(automation,"settings put secure enabled_accessibility_services "+original);
        }
    }
}

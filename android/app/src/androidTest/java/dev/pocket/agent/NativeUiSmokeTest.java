package dev.pocket.agent;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;

/** Real navigation/composer/key-visibility journey with emulator screenshot artifacts. */
@RunWith(AndroidJUnit4.class)
public class NativeUiSmokeTest {
    private Instrumentation instrumentation;private Activity activity;
    private View find(View root,String title){if(title.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())||(root instanceof TextView&&title.contentEquals(((TextView)root).getText())))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=find(((ViewGroup)root).getChildAt(i),title);if(found!=null)return found;}return null;}
    private View await(String title)throws Exception{long end=System.currentTimeMillis()+10000;View[] result=new View[1];while(System.currentTimeMillis()<end){instrumentation.runOnMainSync(() -> result[0]=find(activity.getWindow().getDecorView(),title));if(result[0]!=null)return result[0];Thread.sleep(100);}throw new AssertionError("Missing UI control: "+title);}
    private void click(String title)throws Exception{View control=await(title);instrumentation.runOnMainSync(control::performClick);Thread.sleep(350);}
    private void capture(Context context,String name)throws Exception{instrumentation.waitForIdleSync();Bitmap image=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(image);try(OutputStream out=new FileOutputStream(new File(context.getFilesDir(),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    private void edits(View root,java.util.List<EditText> result){if(root instanceof EditText)result.add((EditText)root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)edits(((ViewGroup)root).getChildAt(i),result);}
    private static JSONObject object(Object...pairs)throws Exception{JSONObject value=new JSONObject();for(int i=0;i<pairs.length;i+=2)value.put((String)pairs[i],pairs[i+1]);return value;}
    @Test public void chatDrawerModelAndMobileComposer()throws Exception{
        Assume.assumeTrue(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"));instrumentation=InstrumentationRegistry.getInstrumentation();Context context=instrumentation.getTargetContext();
        if(Build.VERSION.SDK_INT>=33)instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(),android.Manifest.permission.POST_NOTIFICATIONS);
        activity=instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));long end=System.currentTimeMillis()+240000;while(!RuntimeService.ready&&System.currentTimeMillis()<end)Thread.sleep(200);assertTrue(RuntimeService.status,RuntimeService.ready);
        String token=(String)new JSONTokener(Io.text(new File(context.getFilesDir(),"state/token.json"))).nextValue();AgentApi api=new AgentApi(token);JSONObject saved=api.get("/state").getJSONObject("settings");assertFalse("Use an emulator without a real saved key",saved.optBoolean("hasKey"));
        JSONObject project=(JSONObject)api.request("POST","/projects",object("name","UI-preview-"+System.currentTimeMillis()));JSONObject chat=(JSONObject)api.request("POST","/sessions",object("projectId",project.getString("id")));
        try(MockModel mock=new MockModel()){
            api.request("PUT","/settings",object("provider","openai","api","openai-completions","baseUrl",mock.baseUrl(),"model","android-mock","apiKey","android-mock-key"));api.request("POST","/sessions/"+chat.getString("id")+"/chat",object("text","Create a file in my project"));
            long done=System.currentTimeMillis()+30000;while(api.get("/sessions/"+chat.getString("id")).optBoolean("busy")&&System.currentTimeMillis()<done)Thread.sleep(100);assertFalse(api.get("/sessions/"+chat.getString("id")).optBoolean("busy"));if(mock.failure!=null)throw mock.failure;
        }finally{saved.put("clearKey",true);api.request("PUT","/settings",saved);}
        context.getSharedPreferences("native-ui",Context.MODE_PRIVATE).edit().putString("mode","Coding").putString("project",project.getString("id")).putString("session",chat.getString("id")).remove("mobile-task").commit();
        activity=instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));await("Send");await("Create a file in my project");capture(context,"ui-chat.png");
        click("Open navigation");await("Recent");await("Servers");capture(context,"ui-drawer.png");click("⚙  Settings");await("Bring Your Own Key");capture(context,"ui-settings.png");
        click("Anthropic Compatible");java.util.List<EditText> fields=new java.util.ArrayList<>();instrumentation.runOnMainSync(() -> edits(activity.getWindow().getDecorView(),fields));assertTrue(fields.size()>=3);assertEquals("https://api.anthropic.com",fields.get(0).getText().toString());
        instrumentation.runOnMainSync(() -> fields.get(1).setText("fixture-hidden"));assertNotNull(fields.get(1).getTransformationMethod());click("Show API key");assertNull(fields.get(1).getTransformationMethod());click("Hide API key");assertNotNull(fields.get(1).getTransformationMethod());instrumentation.runOnMainSync(() -> fields.get(1).setText(""));
        click("Mobile Use");await("Start task");capture(context,"ui-mobile.png");click("New mobile task");await("What should your phone do?");click("Coding");await("Send");
        instrumentation.runOnMainSync(activity::finish);
    }
}

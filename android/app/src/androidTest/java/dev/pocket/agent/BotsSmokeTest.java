package dev.pocket.agent;
import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BotsSmokeTest {
    private static JSONObject object(Object...pairs)throws Exception{JSONObject v=new JSONObject();for(int i=0;i<pairs.length;i+=2)v.put((String)pairs[i],pairs[i+1]);return v;}
    private static String shell(UiAutomation ui,String command)throws Exception{try(ParcelFileDescriptor fd=ui.executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){return new String(Io.bytes(in),java.nio.charset.StandardCharsets.UTF_8).trim();}}
    private static JSONObject until(AgentApi api,String bot,String status)throws Exception{long end=System.currentTimeMillis()+90000;JSONObject last=null;while(System.currentTimeMillis()<end){last=api.get("/bots/"+bot);JSONArray jobs=last.getJSONArray("jobs");if(jobs.length()>0&&jobs.getJSONObject(jobs.length()-1).optString("status").equals(status))return last;Thread.sleep(200);}throw new AssertionError("Bot status "+status+": "+last);}
    private static void capture(UiAutomation ui,Context context,String file)throws Exception{android.graphics.Bitmap image=ui.takeScreenshot();assertNotNull(image);try(OutputStream out=new FileOutputStream(new File(context.getFilesDir(),file))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    private static View find(View root,String title){if(title.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())||(root instanceof TextView&&title.contentEquals(((TextView)root).getText())))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=find(((ViewGroup)root).getChildAt(i),title);if(found!=null)return found;}return null;}
    private static View await(Instrumentation inst,Activity activity,String title)throws Exception{View[] found={null};long end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){inst.runOnMainSync(() -> found[0]=find(activity.getWindow().getDecorView(),title));if(found[0]!=null)return found[0];Thread.sleep(100);}throw new AssertionError("Missing bot UI: "+title);}
    @Test public void multipleBotsMcpRoutinePhoneAndBrowser()throws Exception{
        Assume.assumeTrue(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"));Instrumentation inst=InstrumentationRegistry.getInstrumentation();Context context=inst.getTargetContext();UiAutomation ui=inst.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        if(Build.VERSION.SDK_INT>=33)ui.grantRuntimePermission(context.getPackageName(),android.Manifest.permission.POST_NOTIFICATIONS);
        Activity activity=inst.startActivitySync(new Intent(context,BotsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));long ready=System.currentTimeMillis()+240000;while(!RuntimeService.ready&&System.currentTimeMillis()<ready)Thread.sleep(200);assertTrue(RuntimeService.status,RuntimeService.ready);
        AgentApi api=new AgentApi((String)new JSONTokener(Io.text(new File(context.getFilesDir(),"state/token.json"))).nextValue());JSONObject initial=api.get("/state"),saved=initial.getJSONObject("settings");Assume.assumeFalse("Use an emulator without real model credentials",saved.optBoolean("hasKey"));Assume.assumeTrue("Use an empty bot inbox for this fixture",initial.getJSONArray("bots").length()==0);
        String original=Settings.Secure.getString(context.getContentResolver(),"enabled_accessibility_services");boolean originalEngine=context.getSharedPreferences("bot-runtime",Context.MODE_PRIVATE).getBoolean("enabled",false);List<String> ids=new ArrayList<>();String connectorId="";boolean success=false;
        try(BotModelFixture model=new BotModelFixture()){
            api.request("PUT","/settings",object("provider","openai","api","openai-completions","baseUrl",model.baseUrl(),"model","local-bot-fixture","apiKey","fixture-key"));
            JSONObject coder=(JSONObject)api.request("POST","/bots",object("name","Coding buddy","role","Implement and verify files in your own workspace.","kind","coding"));ids.add(coder.getString("id"));
            JSONObject researcher=(JSONObject)api.request("POST","/bots",object("name","Research buddy","role","Read the local MCP note for me.","kind","coding","intervalMinutes",15,"heartbeat","Read the MCP note and report any update"));ids.add(researcher.getString("id"));
            String directory="";JSONArray projects=api.get("/state").getJSONArray("projects");for(int i=0;i<projects.length();i++)if(projects.getJSONObject(i).getString("id").equals(researcher.getString("projectId")))directory=projects.getJSONObject(i).getString("directory");assertFalse(directory.isEmpty());
            String receipt="/workspace/"+directory+"/mcp-receipt.txt";
            String script="const fs=require('fs');require('readline').createInterface({input:process.stdin}).on('line',l=>{const m=JSON.parse(l);if(!('id'in m))return;let r={};if(m.method==='initialize')r={protocolVersion:m.params.protocolVersion,capabilities:{tools:{}},serverInfo:{name:'phone-note',version:'1'}};if(m.method==='tools/list')r={tools:[{name:'read_note',description:'Read a local note',inputSchema:{type:'object',properties:{}}}]};if(m.method==='tools/call'){fs.writeFileSync('"+receipt+"','MCP_RAN');r={content:[{type:'text',text:'Local note: test the inbox'}]};}process.stdout.write(JSON.stringify({jsonrpc:'2.0',id:m.id,result:r})+'\\n');});";
            api.request("PUT","/file",object("projectId",researcher.getString("projectId"),"path","note-server.cjs","content",script));
            JSONObject connection=(JSONObject)api.request("POST","/connectors",object("name","Local phone notes","transport","stdio","command","node","args",new JSONArray().put("/workspace/"+directory+"/note-server.cjs")));connectorId=connection.getString("id");
            JSONObject inspected=(JSONObject)api.request("POST","/connectors/"+connectorId+"/test",object());assertEquals("read_note",inspected.getJSONArray("tools").getJSONObject(0).getString("name"));
            api.request("PUT","/bots/"+researcher.getString("id"),object("connectors",new JSONArray().put(connectorId)));
            context.startForegroundService(new Intent(context,RuntimeService.class).putExtra("botsEnabled",true));api.request("PUT","/bots/engine",object("enabled",true));
            api.request("POST","/bots/"+coder.getString("id")+"/messages",object("text","Create a file in my workspace"));api.request("POST","/bots/"+coder.getString("id")+"/messages",object("text","Update the file again"));
            shell(ui,"input keyevent KEYCODE_HOME");JSONObject completed=until(api,coder.getString("id"),"completed");assertEquals(2,completed.getJSONArray("jobs").length());assertEquals("completed",completed.getJSONArray("jobs").getJSONObject(0).getString("status"));
            assertEquals("Created by my local coding bot",api.get("/file?projectId="+coder.getString("projectId")+"&path=src/bot.txt").getString("content"));
            activity=inst.startActivitySync(new Intent(context,BotsActivity.class).putExtra("botId",researcher.getString("id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));View routine=await(inst,activity,"Run routine now");inst.runOnMainSync(routine::performClick);until(api,researcher.getString("id"),"completed");assertTrue(model.sawMcp);assertEquals("MCP_RAN",api.get("/file?projectId="+researcher.getString("projectId")+"&path=mcp-receipt.txt").getString("content"));
            Thread.sleep(1000);capture(ui,context,"bots-research.png");
            api.request("POST","/bots/"+coder.getString("id")+"/pause",object());api.request("POST","/bots/"+coder.getString("id")+"/messages",object("text","Resume this queued edit"));Thread.sleep(1600);assertEquals("queued",api.get("/bots/"+coder.getString("id")).getJSONArray("jobs").getJSONObject(2).getString("status"));api.request("POST","/bots/"+coder.getString("id")+"/resume",object());until(api,coder.getString("id"),"completed");
            shell(ui,"appops set "+context.getPackageName()+" SYSTEM_ALERT_WINDOW allow");String enabled=context.getPackageName()+"/com.artemis.helper.ArtemisAccessibilityService";if(original!=null&&!original.isEmpty()&&!original.equals("null"))enabled=original+":"+enabled;shell(ui,"settings put secure enabled_accessibility_services "+enabled);shell(ui,"settings put secure accessibility_enabled 1");long access=System.currentTimeMillis()+6000;while(com.artemis.helper.ArtemisAccessibilityService.getInstance()==null&&System.currentTimeMillis()<access)Thread.sleep(100);assertNotNull(com.artemis.helper.ArtemisAccessibilityService.getInstance());
            JSONObject browser=(JSONObject)api.request("POST","/bots",object("name","Browser scout","role","BROWSER_FIXTURE: open and verify the local browser check.","kind","browser"));ids.add(browser.getString("id"));api.request("POST","/bots/"+browser.getString("id")+"/messages",object("text","BROWSER_FIXTURE: open the local check page"));shell(ui,"input keyevent KEYCODE_HOME");until(api,browser.getString("id"),"completed");assertTrue(model.sawBrowser);capture(ui,context,"bots-browser.png");if(model.failure!=null)throw model.failure;
            try(MobileMockModel phoneModel=new MobileMockModel()){
                api.request("PUT","/settings",object("provider","openai","api","openai-completions","baseUrl",phoneModel.baseUrl(),"model","phone-fixture","apiKey","fixture-key"));JSONObject phone=(JSONObject)api.request("POST","/bots",object("name","Phone helper","role","Check Settings using the screen and ask me when needed.","kind","phone"));ids.add(phone.getString("id"));shell(ui,"am start -a android.settings.SETTINGS -f 0x10008000");Thread.sleep(500);shell(ui,"input keyevent KEYCODE_HOME");api.request("POST","/bots/"+phone.getString("id")+"/messages",object("text","Open the Battery page in Settings, ask if ready, then verify completion"));JSONObject waiting=until(api,phone.getString("id"),"waiting");assertTrue(phoneModel.sawImage);String mobile=waiting.getJSONObject("mobileTask").getString("id");assertEquals(mobile,MobileOverlayService.activeTask);assertFalse(MobileAccess.allowed());shell(ui,"input keyevent KEYCODE_HOME");Thread.sleep(700);capture(ui,context,"bots-question.png");
                context.startForegroundService(new Intent(context,MobileOverlayService.class).putExtra("taskId",mobile).putExtra("expand",true));Thread.sleep(200);api.request("POST","/mobile/tasks/"+mobile+"/answer",object("questionId",waiting.getJSONObject("mobileTask").getJSONObject("question").getString("id"),"text","ready"));until(api,phone.getString("id"),"completed");if(phoneModel.failure!=null)throw phoneModel.failure;
            }
            api.request("PUT","/bots/engine",object("enabled",false));api.request("PUT","/bots/"+researcher.getString("id"),object("connectors",new JSONArray(),"intervalMinutes",0));api.request("DELETE","/connectors/"+connectorId,null);connectorId="";
            activity=inst.startActivitySync(new Intent(context,BotsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));await(inst,activity,"Coding buddy");await(inst,activity,"Browser scout");Thread.sleep(500);capture(ui,context,"bots-inbox.png");success=true;
        }finally{
            MobileAccess.authorize(false);context.stopService(new Intent(context,MobileOverlayService.class));api.request("PUT","/bots/engine",object("enabled",false));
            if(!success){for(String id:ids)try{api.request("DELETE","/bots/"+id,null);}catch(Exception ignored){}if(!connectorId.isEmpty())try{api.request("DELETE","/connectors/"+connectorId,null);}catch(Exception ignored){}}
            saved.put("clearKey",true);api.request("PUT","/settings",saved);context.getSharedPreferences("bot-runtime",Context.MODE_PRIVATE).edit().putBoolean("enabled",originalEngine).apply();
            if(original==null||original.isEmpty()||original.equals("null"))shell(ui,"settings delete secure enabled_accessibility_services");else shell(ui,"settings put secure enabled_accessibility_services "+original);
        }
    }
}

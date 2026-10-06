package dev.pocket.agent;

import android.content.Context;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import static org.junit.Assert.*;

/** Exercises the tools packaged in the APK, with no installed Termux application. */
@RunWith(AndroidJUnit4.class)
public class RuntimeSmokeTest {
    private AgentApi api;
    private JSONObject object(Object... pairs) throws Exception {
        JSONObject value=new JSONObject();for(int i=0;i<pairs.length;i+=2)value.put((String)pairs[i],pairs[i+1]);return value;
    }
    private String text(JSONObject terminal) {
        return new String(android.util.Base64.decode(terminal.optString("output"),android.util.Base64.DEFAULT),java.nio.charset.StandardCharsets.UTF_8);
    }
    private void untilTerminal(String id,String expected) throws Exception {
        long end=System.currentTimeMillis()+20000;String last="";
        while(System.currentTimeMillis()<end){last=text(api.get("/terminals/"+id));if(last.contains(expected))return;Thread.sleep(100);}
        fail("Missing terminal output: "+expected+"\n"+last);
    }
    @Test public void firstLaunchProjectsGitNpmInteractiveShellAndRestart() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent open=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        InstrumentationRegistry.getInstrumentation().startActivitySync(open);
        long deadline=System.currentTimeMillis()+240000;
        while(!RuntimeService.ready&&System.currentTimeMillis()<deadline)Thread.sleep(200);
        assertTrue("Runtime failed to start: "+RuntimeService.status,RuntimeService.ready);
        String token=(String)new JSONTokener(Io.text(new File(context.getFilesDir(),"state/token.json"))).nextValue();api=new AgentApi(token);
        String name="smoke-"+System.currentTimeMillis();
        JSONObject project=(JSONObject)api.request("POST","/projects",object("name",name));String projectId=project.getString("id");
        JSONObject conversation=(JSONObject)api.request("POST","/sessions",object("projectId",projectId));assertEquals(projectId,conversation.getString("projectId"));
        api.request("PUT","/file",object("projectId",projectId,"path","src/index.js","content","console.log('phone')"));
        assertEquals("console.log('phone')",api.get("/file?projectId="+projectId+"&path=src/index.js").getString("content"));
        JSONObject terminal=(JSONObject)api.request("POST","/terminals",object("projectId",projectId));String id=terminal.getString("id");
        try {
            api.request("POST","/terminals/"+id+"/input",object("data","test -t 0 && test -t 1 && printf '\\n%s\\n' TTY_OK\r","cols",93,"rows",31));
            untilTerminal(id,"\r\nTTY_OK\r\n");
            api.request("POST","/terminals/"+id+"/input",object("data","stty size; pwd; node --version; npm --version; git --version; devtunnel --version && printf '\\n%s\\n' TUNNEL_CLI_OK\r"));
            untilTerminal(id,"31 93");untilTerminal(id,"/workspace/"+name);untilTerminal(id,"git version");
            untilTerminal(id,"\r\nTUNNEL_CLI_OK\r\n");
            api.request("POST","/terminals/"+id+"/input",object("data","git init source && git -C source -c user.name=Test -c user.email=test@example.com commit --allow-empty -m initial && git clone ./source cloned && test -d cloned/.git && printf '\\n%s\\n' CLONE_OK\r"));
            untilTerminal(id,"\r\nCLONE_OK\r\n");
            api.request("POST","/terminals/"+id+"/input",object("data","read -r answer; printf '\\nANSWER:%s\\n' \"$answer\"\r"));
            api.request("POST","/terminals/"+id+"/input",object("data","phone-input\r"));untilTerminal(id,"ANSWER:phone-input");
            api.request("POST","/terminals/"+id+"/input",object("data","sleep 60\r"));Thread.sleep(200);
            api.request("POST","/terminals/"+id+"/input",object("data","\u0003"));
            api.request("POST","/terminals/"+id+"/input",object("data","printf '\\n%s\\n' INTERRUPT_OK\r"));untilTerminal(id,"\r\nINTERRUPT_OK\r\n");
        } finally {api.request("DELETE","/terminals/"+id,null);}
        api.request("PUT","/file",object("projectId",projectId,"path","package.json","content","{\"scripts\":{\"dev\":\"node server.cjs\"}}"));
        api.request("PUT","/file",object("projectId",projectId,"path","server.cjs","content","require('http').createServer((q,s)=>s.end('PHONE_NPM_OK')).listen(3199,'127.0.0.1')"));
        JSONObject server=(JSONObject)api.request("POST","/servers",object("projectId",projectId,"port",3199,"command","npm run dev"));
        try {
            String response="";long end=System.currentTimeMillis()+20000;
            while(System.currentTimeMillis()<end){try(java.io.InputStream in=new java.net.URL("http://127.0.0.1:3199").openStream()){response=new String(Io.bytes(in));break;}catch(Exception ignored){Thread.sleep(100);}}
            assertEquals("PHONE_NPM_OK",response);
        }finally{api.request("POST","/processes/"+server.getString("id")+"/stop",object());}
        context.stopService(new Intent(context,RuntimeService.class));
        long stopped=System.currentTimeMillis()+15000;
        while(RuntimeService.ready&&System.currentTimeMillis()<stopped)Thread.sleep(100);
        assertFalse("Runtime did not stop",RuntimeService.ready);
        // Wait for the old PRoot process and its listening socket to exit before relaunch.
        while(System.currentTimeMillis()<stopped){try{api.get("/state");Thread.sleep(100);}catch(Exception e){break;}}
        context.startForegroundService(new Intent(context,RuntimeService.class));
        long restarted=System.currentTimeMillis()+60000;
        while(!RuntimeService.ready&&System.currentTimeMillis()<restarted)Thread.sleep(100);
        assertTrue("Runtime did not restart: "+RuntimeService.status,RuntimeService.ready);
        AgentApi reconnected=new AgentApi(token);assertTrue(reconnected.get("/state").getJSONArray("projects").toString().contains(projectId));
        assertEquals(projectId,reconnected.get("/sessions/"+conversation.getString("id")).getString("projectId"));
    }
}

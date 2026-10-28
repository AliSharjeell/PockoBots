package dev.pocket.agent;
import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.File;

/** Notification replies use the same durable inbox as the in-app composer. */
public class BotReplyReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String id=intent.getStringExtra("botId");Bundle result=RemoteInput.getResultsFromIntent(intent);CharSequence input=result==null?null:result.getCharSequence(BotNotifications.REPLY);
        if(id==null||!id.matches("[0-9a-f-]{36}")||input==null||input.toString().trim().isEmpty()||input.length()>16000)return;String text=input.toString().trim();
        context.getSharedPreferences("bot-chats",Context.MODE_PRIVATE).edit().putString("draft-"+id,text).apply();
        try{context.startForegroundService(new Intent(context,RuntimeService.class).putExtra("botsEnabled",true));}catch(RuntimeException ignored){}
        PendingResult pending=goAsync();new Thread(() -> {try{
            long until=System.currentTimeMillis()+3000;while(!RuntimeService.ready&&System.currentTimeMillis()<until)Thread.sleep(100);
            if(!RuntimeService.ready)throw new Exception("Runtime unavailable");AgentApi api=new AgentApi((String)new JSONTokener(Io.text(new File(context.getFilesDir(),"state/token.json"))).nextValue(),2000);
            api.request("PUT","/bots/engine",new JSONObject().put("enabled",true));api.request("POST","/bots/"+id+"/messages",new JSONObject().put("text",text));api.request("POST","/bots/"+id+"/resume",new JSONObject());
            if(text.equals(context.getSharedPreferences("bot-chats",Context.MODE_PRIVATE).getString("draft-"+id,"")))context.getSharedPreferences("bot-chats",Context.MODE_PRIVATE).edit().remove("draft-"+id).apply();
            context.getSystemService(NotificationManager.class).cancel(id.hashCode());
        }catch(Exception e){context.getSystemService(NotificationManager.class).notify(id.hashCode(),BotNotifications.message(context,id,"Reply saved","Open Pocket Agent to send your saved reply."));}
        finally{if(pending!=null)pending.finish();}},"bot-notification-reply").start();
    }
}

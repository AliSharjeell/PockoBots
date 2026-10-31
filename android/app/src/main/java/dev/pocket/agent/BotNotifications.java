package dev.pocket.agent;
import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;

final class BotNotifications {
    static final String REPLY="bot-reply";
    static PendingIntent reply(Context context,String botId){Intent intent=new Intent(context,BotReplyReceiver.class).putExtra("botId",botId);int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);return PendingIntent.getBroadcast(context,botId.hashCode(),intent,flags);}
    static Notification message(Context context,String botId,String name,String text){
        Intent open=new Intent(context,BotsActivity.class).putExtra("botId",botId).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return new Notification.Builder(context,"bot-results").setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle(name).setContentText(text.substring(0,Math.min(text.length(),180)))
            .setStyle(new Notification.BigTextStyle().bigText(text)).setCategory(Notification.CATEGORY_MESSAGE).setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context,botId.hashCode(),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE))
            .addAction(new Notification.Action.Builder(null,"Reply",reply(context,botId)).addRemoteInput(new RemoteInput.Builder(REPLY).setLabel("Message "+name).build()).setAllowGeneratedReplies(false).build()).build();
    }
    static void saveAlert(Context context,JSONObject alert)throws JSONException{
        android.content.SharedPreferences prefs=context.getSharedPreferences("bot-alerts",Context.MODE_PRIVATE);android.content.SharedPreferences.Editor edit=prefs.edit();
        if(prefs.getAll().size()>=100){String oldest=null;long time=Long.MAX_VALUE;for(String key:prefs.getAll().keySet())try{long created=new JSONObject(prefs.getString(key,"{}")).optLong("created");if(created<time&&!key.equals(BotAlertService.activeAlert)){oldest=key;time=created;}}catch(Exception ignored){}if(oldest!=null)edit.remove(oldest);}
        edit.putString(alert.getString("id"),alert.toString()).apply();
    }
    static JSONObject alert(Context context,String id)throws JSONException{return new JSONObject(context.getSharedPreferences("bot-alerts",Context.MODE_PRIVATE).getString(id,"{}"));}
    static JSONObject identity(Context context,JSONObject alert){
        JSONObject bot=new JSONObject();try{bot.put("name",alert.optString("botName","Bot")).put("avatar",alert.optString("botAvatar","bloub:cercle:neutre")).put("accent",alert.optString("botAccent","#007AFF"));
            org.json.JSONArray bots=new JSONObject(Io.text(new java.io.File(context.getFilesDir(),"state/bots.json"))).optJSONArray("bots");if(bots!=null)for(int i=0;i<bots.length();i++)if(bots.optJSONObject(i).optString("id").equals(alert.optString("botId")))return bots.optJSONObject(i);
        }catch(Exception ignored){}return bot;
    }
    private static android.graphics.drawable.Icon avatar(Context context,JSONObject alert){
        int size=AgentStyle.dp(context,64);WorkingAvatarView image=new WorkingAvatarView(context,identity(context,alert),28);image.measure(android.view.View.MeasureSpec.makeMeasureSpec(size,1073741824),android.view.View.MeasureSpec.makeMeasureSpec(size,1073741824));image.layout(0,0,size,size);android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);image.draw(new android.graphics.Canvas(bitmap));return android.graphics.drawable.Icon.createWithBitmap(bitmap);
    }
    static Notification priority(Context context,JSONObject alert,boolean missed){
        String id=alert.optString("id");PendingIntent open=PendingIntent.getActivity(context,id.hashCode(),new Intent(context,BotAlertActivity.class).putExtra("alertId",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent answer=PendingIntent.getActivity(context,id.hashCode()+1,new Intent(context,BotAlertActivity.class).putExtra("alertId",id).putExtra("answer",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent dismiss=PendingIntent.getService(context,id.hashCode(),new Intent(context,BotAlertService.class).setAction("dismiss").putExtra("alertId",id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(context,"bot-priority").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle((missed?"Missed alert · ":"Priority alert · ")+alert.optString("botName"))
            .setContentText(missed?"Open the bot message":"Answer to read and hear the message").setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setOngoing(!missed).setAutoCancel(missed)
            .addAction(new Notification.Action.Builder(null,missed?"Open":"Answer",missed?open:answer).build()).addAction(new Notification.Action.Builder(null,"Dismiss",dismiss).build());
        builder.setContentTitle(alert.optString("botName")).setContentText(missed?"Missed bot call":"Incoming bot call").setCategory(missed?Notification.CATEGORY_MISSED_CALL:Notification.CATEGORY_CALL);
        if(Build.VERSION.SDK_INT>=31&&!missed){android.app.Person caller=new android.app.Person.Builder().setName(alert.optString("botName","Bot")).setIcon(avatar(context,alert)).setImportant(true).build();builder.setActions(new Notification.Action[0]);builder.setStyle(Notification.CallStyle.forIncomingCall(caller,dismiss,answer).setIsVideo(false));}
        if(!missed&&(Build.VERSION.SDK_INT<34||context.getSystemService(NotificationManager.class).canUseFullScreenIntent()))builder.setFullScreenIntent(open,true);
        return builder.build();
    }
}

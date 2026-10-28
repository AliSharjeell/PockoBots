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
    static Notification priority(Context context,JSONObject alert,boolean missed){
        String id=alert.optString("id");PendingIntent open=PendingIntent.getActivity(context,id.hashCode(),new Intent(context,BotAlertActivity.class).putExtra("alertId",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent dismiss=PendingIntent.getService(context,31,new Intent(context,BotAlertService.class).setAction("dismiss"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(context,"bot-priority").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle((missed?"Missed alert · ":"Priority alert · ")+alert.optString("botName"))
            .setContentText(missed?"Open the bot message":"Answer to read and hear the message").setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setOngoing(!missed).setAutoCancel(missed)
            .addAction(new Notification.Action.Builder(null,missed?"Open":"Answer",open).build()).addAction(new Notification.Action.Builder(null,"Dismiss",dismiss).build());
        if(!missed&&(Build.VERSION.SDK_INT<34||context.getSystemService(NotificationManager.class).canUseFullScreenIntent()))builder.setFullScreenIntent(open,true);
        return builder.build();
    }
}

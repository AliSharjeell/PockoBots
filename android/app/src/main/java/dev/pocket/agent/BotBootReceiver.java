package dev.pocket.agent;
import android.content.*;

/** Resume only the bot engine the owner explicitly left enabled. */
public class BotBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())&&context.getSharedPreferences("bot-runtime",Context.MODE_PRIVATE).getBoolean("enabled",true))
            try{context.startForegroundService(new Intent(context,RuntimeService.class).putExtra("botsEnabled",true));}
            catch(RuntimeException e){android.util.Log.w("PocketBots","Android requires opening Pocket Agent before restarting bots",e);}
    }
}

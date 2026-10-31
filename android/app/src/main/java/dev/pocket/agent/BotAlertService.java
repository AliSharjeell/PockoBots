package dev.pocket.agent;
import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import org.json.*;

/** A bounded local ringing alert, not a carrier/telephone call. */
public class BotAlertService extends Service {
    private static volatile BotAlertService instance;
    static boolean isRinging(){BotAlertService service=instance;return service!=null&&service.sound!=null&&service.sound.isPlaying();}
    static volatile String activeAlert="",pendingAlert="";private Ringtone sound;private Vibrator vibrator;private final Handler main=new Handler(Looper.getMainLooper());private JSONObject alert;
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;if("dismiss".equals(intent.getAction())||"answer".equals(intent.getAction())){String target=intent.getStringExtra("alertId");if(target!=null&&!target.equals(activeAlert))return START_NOT_STICKY;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;}
        String id=intent.getStringExtra("alertId");if(id==null||id.equals(activeAlert))return START_NOT_STICKY;
        if(!activeAlert.isEmpty())return START_NOT_STICKY;
        try{alert=BotNotifications.alert(this,id);if(!alert.has("id")){pendingAlert="";stopSelf();return START_NOT_STICKY;}instance=this;activeAlert=id;pendingAlert="";startForeground(30,BotNotifications.priority(this,alert,false));
            if(!getSystemService(NotificationManager.class).areNotificationsEnabled()){stopSelf();return START_NOT_STICKY;}
            if(BotsActivity.foreground)try{startActivity(new Intent(this,BotAlertActivity.class).putExtra("alertId",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));}catch(RuntimeException ignored){}
            sound=RingtoneManager.getRingtone(this,android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);if(sound!=null){sound.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());if(Build.VERSION.SDK_INT>=28)sound.setLooping(true);sound.play();}
            vibrator=getSystemService(Vibrator.class);if(vibrator!=null&&vibrator.hasVibrator())vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,500,800,500,1200},0));
            main.postDelayed(() -> {stopForeground(STOP_FOREGROUND_DETACH);getSystemService(NotificationManager.class).notify(30,BotNotifications.priority(this,alert,true));stopSelf();},60000);
        }catch(Exception e){pendingAlert="";android.util.Log.w("PocketAlert","Could not ring local alert",e);stopSelf();}return START_NOT_STICKY;
    }
    @Override public void onDestroy(){main.removeCallbacksAndMessages(null);if(sound!=null)sound.stop();if(vibrator!=null)vibrator.cancel();if(instance==this)instance=null;activeAlert="";super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}

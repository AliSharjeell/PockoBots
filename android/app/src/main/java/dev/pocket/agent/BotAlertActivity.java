package dev.pocket.agent;
import android.app.*;
import android.content.*;
import android.os.*;
import android.speech.tts.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Lock-screen call-style page: answering reveals the message and starts Android TTS. */
public class BotAlertActivity extends Activity {
    private TextToSpeech speech;private JSONObject alert;private TextView message,status;private boolean answered;
    private int dp(int v){return AgentStyle.dp(this,v);}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try{alert=BotNotifications.alert(this,getIntent().getStringExtra("alertId"));if(!alert.has("id")){finish();return;}}catch(Exception e){finish();return;}
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setGravity(Gravity.CENTER_HORIZONTAL);page.setPadding(dp(24),dp(64),dp(24),dp(32));page.setBackgroundColor(AgentStyle.BACK);
        TextView badge=AgentStyle.text(this,"PRIORITY BOT ALERT",12);badge.setTextColor(AgentStyle.MUTED);page.addView(badge);ThinkingOrbView orb=new ThinkingOrbView(this);orb.state("waiting");LinearLayout.LayoutParams orbParams=new LinearLayout.LayoutParams(dp(100),dp(100));orbParams.setMargins(0,dp(44),0,dp(28));page.addView(orb,orbParams);
        TextView name=AgentStyle.text(this,alert.optString("botName"),28);name.setGravity(Gravity.CENTER);name.setTypeface(AgentStyle.font(this),android.graphics.Typeface.BOLD);page.addView(name);
        status=AgentStyle.text(this,"Incoming local alert",14);status.setTextColor(AgentStyle.MUTED);status.setGravity(Gravity.CENTER);status.setPadding(0,dp(16),0,dp(24));page.addView(status);
        ScrollView scroll=new ScrollView(this);message=AgentStyle.text(this,"Answer to read and hear the bot's message.",18);message.setGravity(Gravity.CENTER);message.setPadding(dp(12),dp(24),dp(12),dp(24));scroll.addView(message);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);Button dismiss=new Button(this);dismiss.setText("Dismiss");AgentStyle.button(dismiss,false);controls.addView(dismiss);dismiss.setOnClickListener(v -> {stopRinging("dismiss");finish();});
        Button answer=new Button(this);answer.setText("Answer");AgentStyle.button(answer,true);controls.addView(answer);answer.setOnClickListener(v -> {if(!answered){answered=true;answer.setText("Open chat");stopRinging("answer");message.setText(alert.optString("text"));speak();}else{startActivity(new Intent(this,BotsActivity.class).putExtra("botId",alert.optString("botId")));finish();}});page.addView(controls);setContentView(page);
    }
    private void stopRinging(String action){startService(new Intent(this,BotAlertService.class).setAction(action));}
    private void speak(){status.setText("Starting Android voice…");speech=new TextToSpeech(this,result -> runOnUiThread(() -> {if(isFinishing()||speech==null)return;if(result!=TextToSpeech.SUCCESS){status.setText("Voice unavailable · read the message above");return;}
        Voice offline=null;Set<Voice> voices=speech.getVoices();if(voices!=null)for(Voice voice:voices)if(!voice.isNetworkConnectionRequired()&&voice.getLocale().getLanguage().equals(Locale.getDefault().getLanguage())){offline=voice;break;}
        if(offline==null){status.setText("No offline Android voice installed · message shown above");return;}speech.setVoice(offline);speech.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String id){runOnUiThread(() -> status.setText("Reading the bot's message"));}public void onDone(String id){runOnUiThread(() -> status.setText("Message read · tap Open chat to reply"));}public void onError(String id){runOnUiThread(() -> status.setText("Voice unavailable · message shown above"));}});
        speech.speak(alert.optString("text"),TextToSpeech.QUEUE_FLUSH,null,"bot-alert");}));}
    @Override protected void onDestroy(){if(speech!=null){speech.stop();speech.shutdown();}super.onDestroy();}
}

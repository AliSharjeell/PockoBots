package dev.pocket.agent;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.File;
import java.util.concurrent.*;

/** Foreground mobile controller and draggable pill; independent of MainActivity. */
public class MobileOverlayService extends Service {
    public static volatile String activeTask="";
    private static volatile MobileOverlayService instance;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService requests=Executors.newFixedThreadPool(2);
    private final ScheduledExecutorService polling=Executors.newSingleThreadScheduledExecutor();
    private WindowManager windows;private WindowManager.LayoutParams layout;private LinearLayout surface;
    private AgentApi api;private JSONObject task=new JSONObject();private String id="",signature="",draft="";
    private boolean expanded,attached,closing;private EditText answer;private float startX,startY;private int windowX,windowY;
    private NotificationManager notifications;
    @Override public void onCreate(){
        super.onCreate();instance=this;notifications=getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("mobile-tasks","Mobile Use tasks",NotificationManager.IMPORTANCE_LOW));
        startForeground(2,notification("Preparing your mobile task"));
        windows=getSystemService(WindowManager.class);
        layout=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        layout.gravity=Gravity.TOP|Gravity.LEFT;layout.x=AgentStyle.dp(this,16);layout.y=AgentStyle.dp(this,100);
        surface=new LinearLayout(this);surface.setOrientation(LinearLayout.VERTICAL);surface.setPadding(dp(12),dp(10),dp(12),dp(10));
        surface.setElevation(dp(12));surface.setBackground(AgentStyle.surface(this,AgentStyle.PANEL,24,true));
        surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        try{api=new AgentApi((String)new JSONTokener(Io.text(new File(getFilesDir(),"state/token.json"))).nextValue());}
        catch(Exception e){MobileAccess.authorize(false);stopSelf();return;}
        polling.scheduleWithFixedDelay(this::poll,0,700,TimeUnit.MILLISECONDS);
    }
    int dp(int value){return AgentStyle.dp(this,value);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        String action=intent.getAction();
        if("pause".equals(action)||"stop".equals(action)){control(action);return START_NOT_STICKY;}
        if("close".equals(action)){control("pause");stopSelf();return START_NOT_STICKY;}
        String next=intent.getStringExtra("taskId");
        if(next!=null&&!next.equals(id)){id=next;activeTask=next;signature="";task=new JSONObject();expanded=false;draft="";}
        if(!Settings.canDrawOverlays(this)){MobileAccess.authorize(false);stopSelf();return START_NOT_STICKY;}
        MobileAccess.syncToken(this);MobileAccess.authorize(true);
        if(!attached){windows.addView(surface,layout);attached=true;}
        render();return START_NOT_STICKY;
    }
    private Notification notification(String value){
        Intent open=new Intent(this,MainActivity.class).putExtra("mobileTask",id);
        Notification.Builder builder=new Notification.Builder(this,"mobile-tasks").setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Pocket Agent · Mobile Use").setContentText(value).setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this,2,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        for(String action:new String[]{"pause","stop"})builder.addAction(new Notification.Action.Builder(null,action.equals("pause")?"Pause":"Stop",
            PendingIntent.getService(this,action.equals("pause")?3:4,new Intent(this,MobileOverlayService.class).setAction(action),PendingIntent.FLAG_IMMUTABLE)).build());
        return builder.build();
    }
    private void poll(){
        if(closing||id.isEmpty()||api==null)return;String selected=id;
        try{JSONObject result=api.get("/mobile/tasks/"+selected);main.post(() -> {
            if(closing||!selected.equals(id))return;String previous=task.optString("status");task=result;
            String status=task.optString("status");
            if(!status.equals("running")&&!status.equals("queued"))MobileAccess.authorize(false);
            if(status.equals("waiting")&&!previous.equals("waiting"))expanded=true;
            String changed=task.toString();if(!signature.equals(changed)){signature=changed;render();}
            notifications.notify(2,notification(status.equals("waiting")?"Your reply is needed":status+" · "+task.optInt("steps")+" steps"));
        });}catch(Exception e){main.post(() -> {if(!closing){MobileAccess.authorize(false);task=new JSONObject();try{task.put("status","paused").put("error","Connection lost. Reopen Pocket Agent to reconnect.");}catch(Exception ignored){}render();}});}
    }
    private void call(String endpoint,JSONObject body,Runnable done){
        String selected=id;requests.execute(() -> {try{api.request("POST","/mobile/tasks/"+selected+"/"+endpoint,body);main.post(done);}
            catch(Exception e){main.post(() -> {MobileAccess.authorize(false);try{task.put("error",e.getMessage());}catch(Exception ignored){}expanded=true;render();});}});
    }
    private void control(String action){MobileAccess.authorize(false);if(!id.isEmpty()&&api!=null)call(action,new JSONObject(),this::pollSoon);}
    private void pollSoon(){if(!closing&&!polling.isShutdown())polling.execute(this::poll);}
    private Button button(LinearLayout parent,String title,Runnable click,boolean primary){Button view=new Button(this);view.setText(title);AgentStyle.button(view,primary);parent.addView(view);view.setOnClickListener(v -> click.run());return view;}
    private TextView text(String value,int size){TextView view=AgentStyle.text(this,value,size);view.setPadding(0,dp(5),0,dp(5));view.setTextIsSelectable(true);return view;}
    private void render(){
        if(surface==null||!attached)return;if(answer!=null)draft=answer.getText().toString();answer=null;surface.removeAllViews();
        String status=task.optString("status","queued");
        String shortTitle=status.equals("waiting")?"●  Needs you":status.equals("running")?"●  Working · "+task.optInt("steps"):"○  "+status;
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView pill=text(shortTitle,12);pill.setTypeface(AgentStyle.font(this),android.graphics.Typeface.BOLD);header.addView(pill,new LinearLayout.LayoutParams(0,-2,1));
        TextView chevron=text(expanded?"−":"↗",16);chevron.setPadding(dp(12),0,0,0);header.addView(chevron);surface.addView(header);
        surface.setOnTouchListener(!expanded?(v,event) -> {
            switch(event.getAction()){
                case MotionEvent.ACTION_DOWN:startX=event.getRawX();startY=event.getRawY();windowX=layout.x;windowY=layout.y;return true;
                case MotionEvent.ACTION_MOVE:layout.x=Math.max(0,Math.min(getResources().getDisplayMetrics().widthPixels-surface.getWidth(),windowX+(int)(event.getRawX()-startX)));
                    layout.y=Math.max(0,Math.min(getResources().getDisplayMetrics().heightPixels-dp(64),windowY+(int)(event.getRawY()-startY)));windows.updateViewLayout(surface,layout);return true;
                case MotionEvent.ACTION_UP:if(Math.abs(event.getRawX()-startX)<dp(8)&&Math.abs(event.getRawY()-startY)<dp(8)){expanded=true;render();}return true;
                default:return true;
            }
        }:null);
        header.setOnClickListener(v -> {expanded=!expanded;render();});
        if(expanded){
            TextView title=text("MOBILE USE",10);title.setTextColor(AgentStyle.MUTED);surface.addView(title);
            TextView goal=text(task.optString("prompt"),13);goal.setMaxLines(3);surface.addView(goal);
            ScrollView scroll=new ScrollView(this);LinearLayout logs=new LinearLayout(this);logs.setOrientation(LinearLayout.VERTICAL);scroll.addView(logs);
            String output=task.optString("output");if(!output.isEmpty())logs.addView(text(output,13));
            JSONArray trace=task.optJSONArray("trace");if(trace!=null&&trace.length()>0){TextView current=text(trace.optJSONObject(trace.length()-1).optString("text"),11);current.setTextColor(AgentStyle.MUTED);logs.addView(current);}
            if(!task.optString("error").isEmpty())logs.addView(text(task.optString("error"),12));
            surface.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(190),output.length()>700?dp(190):dp(110))));
            JSONObject question=task.optJSONObject("question");
            if(question!=null){
                surface.addView(text(question.optString("text"),13));answer=new EditText(this);AgentStyle.input(answer);answer.setHint("Reply or say ready after handling it in the app");answer.setText(draft);answer.setMaxLines(3);surface.addView(answer);
                button(surface,"Reply & continue",() -> {String reply=answer.getText().toString().trim();if(reply.isEmpty())return;
                    MobileAccess.authorize(true);JSONObject body=new JSONObject();try{body.put("questionId",question.optString("id")).put("text",reply);}catch(Exception ignored){}
                    call("answer",body,() -> {draft="";if(answer!=null)answer.setText("");expanded=false;pollSoon();render();});},true);
            }
            LinearLayout controls=new LinearLayout(this);surface.addView(controls);
            if(status.equals("paused")||status.equals("queued"))button(controls,"Resume",() -> {MobileAccess.authorize(true);call("resume",new JSONObject(),this::pollSoon);},true);
            if(status.equals("running")||status.equals("waiting"))button(controls,"Pause",() -> control("pause"),false);
            if(!status.equals("completed")&&!status.equals("failed")&&!status.equals("stopped"))button(controls,"Stop",() -> control("stop"),false);
            button(controls,"Open",() -> startActivity(new Intent(this,MainActivity.class).putExtra("mobileTask",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)),false);
            button(controls,"×",() -> {control("pause");stopSelf();},false);
        }
        layout.width=expanded?Math.min(dp(328),getResources().getDisplayMetrics().widthPixels-dp(24)):dp(186);
        layout.flags=WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|(expanded?0:WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        layout.x=Math.min(layout.x,Math.max(0,getResources().getDisplayMetrics().widthPixels-layout.width));
        if(attached)windows.updateViewLayout(surface,layout);
    }
    /** Hide our own control surface while Artemis observes/acts, never the target app. */
    public static void suspendOverlay(boolean hide){
        MobileOverlayService service=instance;if(service==null||!service.attached)return;
        CountDownLatch ready=new CountDownLatch(1);service.main.post(() -> {try{
            if(service.attached){service.surface.setAlpha(hide?0:1);if(hide)service.layout.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                else service.layout.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;service.windows.updateViewLayout(service.surface,service.layout);}
        }finally{ready.countDown();}});
        try{ready.await(1,TimeUnit.SECONDS);if(hide)Thread.sleep(60);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    @Override public void onDestroy(){
        closing=true;MobileAccess.authorize(false);if(instance==this){instance=null;activeTask="";}
        if(api!=null&&!id.isEmpty())requests.execute(() -> {try{api.request("POST","/mobile/tasks/"+id+"/pause",new JSONObject());}catch(Exception ignored){}});
        polling.shutdownNow();requests.shutdown();main.removeCallbacksAndMessages(null);if(attached){windows.removeView(surface);attached=false;}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}

package dev.pocket.agent;

import android.app.*;
import android.animation.ValueAnimator;
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
    private boolean expanded,attached,closing,suspended,pinned,sending,built,renderedCircle;private EditText answer;private float startX,startY;private int windowX,windowY;
    private String ownerId="",bodySignature="",feedback="";private JSONObject botState=new JSONObject();
    private LinearLayout header,captions,meta,logs,controls;private TextView heading,detail,questionLabel;private ScrollView logScroll;private AgentIcon send;
    private int keyboardBottom,beforeKeyboardY=-1;private long clockStart,clockEnd;private boolean clockLive;private String builtAvatar="";
    private long armingUntil;private int passThroughRequests;
    private NotificationManager notifications;private ChatMarkdown markdown;
    private WorkingAvatarView botAvatar;private JSONObject avatarBot=new JSONObject();private String avatarOwner="";private ValueAnimator morph;private boolean renderedExpanded;private int targetHeight,targetWidth;
    @Override public void onCreate(){
        super.onCreate();instance=this;markdown=new ChatMarkdown(this);notifications=getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("mobile-tasks","Mobile Use tasks",NotificationManager.IMPORTANCE_LOW));
        startForeground(2,notification("Preparing your mobile task"));
        windows=getSystemService(WindowManager.class);
        layout=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        layout.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;layout.x=0;layout.y=dp(24);
        surface=new LinearLayout(this);surface.setOrientation(LinearLayout.VERTICAL);surface.setPadding(dp(12),dp(10),dp(12),dp(10));
        AgentStyle.applyTheme(this);surface.setElevation(dp(10));applyGlass();surface.setClipToOutline(true);
        layout.softInputMode=(Build.VERSION.SDK_INT>=30?WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING:WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        surface.setOnApplyWindowInsetsListener((view,insets)->{if(Build.VERSION.SDK_INT>=30){int bottom=windows.getCurrentWindowMetrics().getWindowInsets().getInsets(WindowInsets.Type.ime()).bottom;if(bottom!=keyboardBottom){if(bottom>0&&keyboardBottom==0)beforeKeyboardY=layout.y;if(bottom==0&&beforeKeyboardY>=0){layout.y=beforeKeyboardY;beforeKeyboardY=-1;}keyboardBottom=bottom;main.post(this::sizePanel);}}return insets;});
        surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        try{api=new AgentApi((String)new JSONTokener(Io.text(new File(getFilesDir(),"state/token.json"))).nextValue());}
        catch(Exception e){MobileAccess.authorize(false);stopSelf();return;}
        polling.scheduleWithFixedDelay(this::poll,0,700,TimeUnit.MILLISECONDS);
    }
    int dp(int value){return AgentStyle.dp(this,value);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){String restored=getSharedPreferences("floating-bot",0).getString("botId","");if(restored.isEmpty()){stopSelf();return START_NOT_STICKY;}intent=new Intent(this,MobileOverlayService.class).putExtra("botId",restored);}
        String action=intent.getAction();
        if("pause".equals(action)||"stop".equals(action)){control(action);return START_NOT_STICKY;}
        if("close".equals(action)){dismiss();return START_NOT_STICKY;}
        String owner=intent.getStringExtra("botId");if(owner!=null&&!owner.isEmpty()){if(!owner.equals(ownerId)){hideKeyboard();answer=null;ownerId=owner;id="";activeTask="";task=new JSONObject();botState=new JSONObject();avatarBot=new JSONObject();draft="";built=false;}pinned=true;getSharedPreferences("floating-bot",0).edit().putString("botId",owner).apply();}
        String next=intent.getStringExtra("taskId");
        if(next!=null&&!next.equals(id)){String hint=intent.getStringExtra("ownerBotId");if(hint==null||!hint.equals(ownerId)){hideKeyboard();answer=null;draft="";ownerId=hint==null?"":hint;botState=new JSONObject();avatarBot=new JSONObject();built=false;}id=next;activeTask=next;signature="";task=new JSONObject();}
        if(intent.getBooleanExtra("expand",false))expanded=true;
        if(!Settings.canDrawOverlays(this)){MobileAccess.authorize(false);stopSelf();return START_NOT_STICKY;}
        MobileAccess.syncToken(this);if(next!=null){armingUntil=System.currentTimeMillis()+5000;MobileAccess.authorize(true);}
        if(!attached){windows.addView(surface,layout);attached=true;}
        render();pollSoon();return pinned?START_STICKY:START_NOT_STICKY;
    }
    private Notification notification(String value){
        Intent open=new Intent(this,BotsActivity.class).putExtra("botId",ownerId);
        Notification.Builder builder=new Notification.Builder(this,"mobile-tasks").setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("PockoBots · Floating bot").setContentText(value).setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this,2,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        if(mobileLive())for(String action:new String[]{"pause","stop"})builder.addAction(new Notification.Action.Builder(null,action.equals("pause")?"Pause":"Stop",
            PendingIntent.getService(this,action.equals("pause")?3:4,new Intent(this,MobileOverlayService.class).setAction(action),PendingIntent.FLAG_IMMUTABLE)).build());
        builder.addAction(new Notification.Action.Builder(null,"Dismiss",PendingIntent.getService(this,5,new Intent(this,MobileOverlayService.class).setAction("close"),PendingIntent.FLAG_IMMUTABLE)).build());return builder.build();
    }
    public static boolean pinnedFor(String bot){MobileOverlayService service=instance;return service!=null&&service.pinned&&service.ownerId.equals(bot);}
    static boolean visibleFor(String bot){MobileOverlayService service=instance;return service!=null&&service.attached&&service.ownerId.equals(bot)&&service.surface.getAlpha()>0;}
    private void poll(){
        if(closing||api==null||id.isEmpty()&&ownerId.isEmpty())return;
        String selected=id,owner=ownerId;
        try{
            JSONObject mobile=null;try{if(!selected.isEmpty())mobile=api.get("/mobile/tasks/"+selected);}catch(Exception error){if(owner.isEmpty())throw error;}
            if(owner.isEmpty()&&mobile!=null)owner=mobile.optString("ownerBotId");
            JSONObject state=owner.isEmpty()?null:api.get("/bots/"+owner);
            if(state!=null&&state.optJSONObject("mobileTask")!=null)mobile=state.optJSONObject("mobileTask");
            final JSONObject fetched=mobile,identity=state;final String fetchedOwner=owner;
            main.post(()->{
                if(closing||!selected.equals(id)||!ownerId.isEmpty()&&!ownerId.equals(fetchedOwner))return;
                if(identity!=null){botState=identity;avatarBot=identity.optJSONObject("bot");if(avatarBot==null)avatarBot=new JSONObject();ownerId=fetchedOwner;}
                String previous=task.optString("status");
                if(fetched!=null){task=fetched;id=task.optString("id");activeTask=id;}else if(identity!=null){task=new JSONObject();id="";activeTask="";}
                if(!mobileLive()&&System.currentTimeMillis()>armingUntil)MobileAccess.authorize(false);
                if(task.optString("status").equals("waiting")&&!previous.equals("waiting")&&!expanded){expanded=true;built=false;}
                feedback="";render();notifications.notify(2,notification(avatarBot.optString("name","Bot")+(busy()?" is working":" is ready")));
            });
        }catch(Exception error){main.post(()->{if(!closing){feedback="Connection lost. Reconnecting\u2026";render();}});}
    }
    private boolean mobileLive(){return !id.isEmpty()&&(task.optString("status").equals("running")||task.optString("status").equals("waiting"));}
    private JSONObject activeJob(){JSONArray jobs=botState.optJSONArray("jobs");if(jobs!=null)for(int i=0;i<jobs.length();i++){JSONObject job=jobs.optJSONObject(i);if(job!=null&&java.util.Arrays.asList("running","waiting","arming","queued").contains(job.optString("status")))return job;}return null;}
    private boolean busy(){return mobileLive()||activeJob()!=null;}
    private void applyGlass(){
        android.graphics.drawable.GradientDrawable glass=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,AgentStyle.dark?new int[]{0xd0303034,0xbf1b1b1f,0xd5242428}:new int[]{0xe2eeeef1,0xc9d6d6dc,0xe2f2f2f4});
        glass.setCornerRadius(dp(28));glass.setStroke(dp(1),AgentStyle.dark?0x55ffffff:0x99ffffff);surface.setBackground(glass);
    }
    private void hideKeyboard(){if(answer!=null){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(answer.getWindowToken(),0);answer.clearFocus();}keyboardBottom=0;if(beforeKeyboardY>=0){layout.y=beforeKeyboardY;beforeKeyboardY=-1;}}
    private void toggle(){hideKeyboard();expanded=!expanded;built=false;render();if(ValueAnimator.areAnimatorsEnabled()){surface.setAlpha(.85f);surface.setScaleX(.97f);surface.setScaleY(.97f);surface.animate().alpha(1).scaleX(1).scaleY(1).setDuration(180).start();}}
    private void dismiss(){hideKeyboard();pinned=false;getSharedPreferences("floating-bot",0).edit().remove("botId").apply();if(mobileLive())control("pause");stopSelf();}
    private void call(String endpoint,JSONObject body,Runnable done){
        String selected=id;requests.execute(() -> {try{api.request("POST","/mobile/tasks/"+selected+"/"+endpoint,body);main.post(done);}
            catch(Exception e){main.post(() -> {MobileAccess.authorize(false);try{task.put("error",e.getMessage());}catch(Exception ignored){}expanded=true;render();});}});
    }
    private void control(String action){armingUntil=0;MobileAccess.authorize(false);if(!id.isEmpty()&&api!=null)call(action,new JSONObject(),this::pollSoon);}
    private void pollSoon(){if(!closing&&!polling.isShutdown())polling.execute(this::poll);}
    private Button button(LinearLayout parent,String title,Runnable click,boolean primary){Button view=new Button(this);view.setText(title);AgentStyle.button(view,primary);parent.addView(view);view.setOnClickListener(v -> click.run());return view;}
    private void icon(LinearLayout parent,String symbol,String description,Runnable action){AgentIcon view=new AgentIcon(this,symbol,description,action);view.setIconTint(0xfff4f4f5);if(symbol.equals("close"))view.setBackground(AgentStyle.surface(this,0xffe93636,28,false));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(44),dp(44));params.setMargins(dp(4),0,0,0);parent.addView(view,params);}
    private TextView markdownText(String value,int size){TextView view=text("",size);markdown.set(view,value);android.text.SpannableString links=new android.text.SpannableString(view.getText());for(android.text.style.URLSpan link:links.getSpans(0,links.length(),android.text.style.URLSpan.class)){int start=links.getSpanStart(link),end=links.getSpanEnd(link);links.removeSpan(link);links.setSpan(new android.text.style.ClickableSpan(){public void onClick(View widget){try{android.net.Uri uri=android.net.Uri.parse(link.getURL());if("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))startActivity(new Intent(Intent.ACTION_VIEW,uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Exception ignored){}}},start,end,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}view.setText(links);return view;}
    private TextView text(String value,int size){TextView view=AgentStyle.text(this,value,size);view.setPadding(0,dp(5),0,dp(5));view.setTextIsSelectable(true);return view;}
    private void render(){
        if(surface==null||!attached)return;
        boolean live=busy(),circle=pinned&&!expanded&&!live;
        if(!built||renderedExpanded!=expanded||renderedCircle!=circle){
            if(answer!=null)draft=answer.getText().toString();
            answer=null;surface.removeAllViews();renderedExpanded=expanded;renderedCircle=circle;built=true;bodySignature="";
            surface.setPadding(dp(circle?10:12),dp(10),dp(circle?10:12),dp(10));
            header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setClipChildren(false);
            botAvatar=new WorkingAvatarView(this,avatarBot,16);builtAvatar=avatarBot.toString();
            header.addView(botAvatar,new LinearLayout.LayoutParams(dp(34),dp(34)));
            if(!circle){
                captions=new LinearLayout(this);captions.setOrientation(LinearLayout.VERTICAL);captions.setPadding(dp(10),0,0,0);
                heading=new ShimmerTextView(this,"");heading.setTypeface(AgentStyle.font(this),android.graphics.Typeface.BOLD);heading.setTextSize(13);heading.setMaxLines(2);captions.addView(heading);
                meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);meta.setPadding(0,dp(3),0,0);
                detail=text("",10);detail.setTextIsSelectable(false);detail.setTextColor(AgentStyle.MUTED);detail.setPadding(0,0,dp(8),0);meta.addView(detail);captions.addView(meta);
                header.addView(captions,new LinearLayout.LayoutParams(0,-2,1));
                icon(header,expanded?"down":"up",expanded?"Collapse panel":"Expand panel",this::toggle);
                if(expanded)icon(header,"close","Dismiss floating bot",this::dismiss);
            }
            surface.addView(header);header.setOnClickListener(v->toggle());header.setOnTouchListener(this::drag);surface.setOnTouchListener(expanded?null:this::drag);surface.setOnClickListener(v->{if(!expanded)toggle();});
            if(expanded){
                logScroll=new ScrollView(this);logs=new LinearLayout(this);logs.setOrientation(LinearLayout.VERTICAL);logScroll.addView(logs);surface.addView(logScroll,new LinearLayout.LayoutParams(-1,dp(170)));
                questionLabel=text("",12);questionLabel.setMaxLines(3);questionLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);surface.addView(questionLabel);
                LinearLayout composer=new LinearLayout(this);composer.setGravity(Gravity.CENTER_VERTICAL);composer.setPadding(0,dp(8),0,0);
                answer=new EditText(this);AgentStyle.input(answer);answer.setText(draft);answer.setMaxLines(3);answer.setMinLines(1);answer.setTextSize(13);answer.setSingleLine(false);answer.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);answer.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);answer.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(16000)});
                composer.addView(answer,new LinearLayout.LayoutParams(0,-2,1));
                send=new AgentIcon(this,"send","Send message to bot",this::sendMessage);send.setIconTint(0xffffffff);send.setBackground(AgentStyle.surface(this,AgentStyle.BLUE,24,false));LinearLayout.LayoutParams sendParams=new LinearLayout.LayoutParams(dp(42),dp(42));sendParams.leftMargin=dp(6);composer.addView(send,sendParams);surface.addView(composer);
                answer.setOnEditorActionListener((view,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEND){sendMessage();return true;}return false;});
                answer.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence value,int start,int count,int after){}public void onTextChanged(CharSequence value,int start,int before,int count){draft=value.toString();if(send!=null)send.setAlpha(draft.trim().isEmpty()?.5f:1f);}public void afterTextChanged(android.text.Editable value){main.post(MobileOverlayService.this::sizePanel);}});
                controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);controls.setPadding(0,dp(8),0,0);surface.addView(controls);
            }
        }
        if(botAvatar!=null){if(!builtAvatar.equals(avatarBot.toString())){header.removeView(botAvatar);botAvatar=new WorkingAvatarView(this,avatarBot,16);header.addView(botAvatar,0,new LinearLayout.LayoutParams(dp(34),dp(34)));builtAvatar=avatarBot.toString();}botAvatar.setWorking(live);}
        if(!circle){
            String name=avatarBot.optString("name","Bot");heading.setText(name+(live?" is working":" \u00b7 Ready"));((ShimmerTextView)heading).active(live);
            JSONObject progress=progress();JSONArray tools=progress.optJSONArray("tools");int steps=mobileLive()?task.optInt("steps"):tools==null?0:tools.length();detail.setText(steps+" steps");
            JSONObject job=activeJob();long started=mobileLive()?task.optLong("created"):progress.optLong("started",job==null?System.currentTimeMillis():job.optLong("created"));long ended=mobileLive()?task.optLong("updated"):progress.optLong("updated",started);
            if(meta.getChildCount()<2||clockStart!=started||clockLive!=live||!live&&clockEnd!=ended){if(meta.getChildCount()>1)meta.removeViewAt(1);ElapsedTimeView clock=new ElapsedTimeView(this,started,ended,live);clock.setTextSize(10);clock.setIncludeFontPadding(detail.getIncludeFontPadding());meta.addView(clock);clockStart=started;clockEnd=ended;clockLive=live;}
            if(expanded){
                String body=panelText();if(!bodySignature.equals(body)){boolean follow=logScroll.getChildCount()==0||logs.getHeight()-logScroll.getScrollY()-logScroll.getHeight()<dp(36);int old=logScroll.getScrollY();bodySignature=body;logs.removeAllViews();logs.addView(markdownText(body,12));logScroll.post(()->{if(follow)logScroll.fullScroll(View.FOCUS_DOWN);else logScroll.scrollTo(0,old);});}
                JSONObject question=task.optJSONObject("question");String notice=!feedback.isEmpty()?feedback:question==null?"":question.optString("text");questionLabel.setText(notice);questionLabel.setVisibility(notice.isEmpty()?View.GONE:View.VISIBLE);
                answer.setHint(question==null?"Message "+name+"\u2026":"Reply to continue\u2026");send.setEnabled(!sending);send.setAlpha(sending||draft.trim().isEmpty()?.5f:1f);
                controls.removeAllViews();String status=task.optString("status");
                if(!id.isEmpty()&&(status.equals("paused")||status.equals("queued")))icon(controls,"play","Resume Mobile Use",()->{armingUntil=System.currentTimeMillis()+5000;MobileAccess.authorize(true);call("resume",new JSONObject(),this::pollSoon);});
                if(mobileLive())icon(controls,"pause","Pause Mobile Use",()->control("pause"));
                if(mobileLive())icon(controls,"stop","Stop Mobile Use",()->control("stop"));
                if(!mobileLive()&&job!=null)icon(controls,"stop","Stop current bot task",()->requests.execute(()->{try{api.request("POST","/bots/"+ownerId+"/jobs/"+job.optString("id")+"/cancel",new JSONObject());pollSoon();}catch(Exception error){main.post(()->{feedback=error.getMessage();render();});}}));
                icon(controls,"open","Open bot chat",()->startActivity(new Intent(this,BotsActivity.class).putExtra("botId",ownerId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            }
        }
        sizePanel();
    }
    private JSONObject progress(){JSONObject session=botState.optJSONObject("session");JSONObject progress=session==null?null:session.optJSONObject("progress");return progress==null?new JSONObject():progress;}
    private String panelText(){
        if(mobileLive()||ownerId.isEmpty()){
            String result=task.optString("output");JSONArray trace=task.optJSONArray("trace");if(trace!=null&&trace.length()>0)result+="\n\n"+trace.optJSONObject(trace.length()-1).optString("text");
            if(result.isEmpty())result=task.optString("prompt");JSONObject question=task.optJSONObject("question");if(question!=null)result+="\n\n"+question.optString("text");return result+(task.optString("error").isEmpty()?"":"\n\n"+task.optString("error"));
        }
        JSONObject progress=progress();String result=progress.optString("text");JSONArray tools=progress.optJSONArray("tools");if(busy()&&tools!=null&&tools.length()>0){JSONObject tool=tools.optJSONObject(tools.length()-1);result+="\n\n"+tool.optString("name")+" \u00b7 "+tool.optString("status");}
        if(result.isEmpty()){JSONArray messages=botState.optJSONArray("messages");if(messages!=null)for(int i=messages.length()-1;i>=0;i--){JSONObject message=messages.optJSONObject(i);if(message.optString("role").equals("assistant")){result=message.optString("text");break;}}}
        return result.isEmpty()?"Send a message to keep working with your bot.":result.substring(Math.max(0,result.length()-6000));
    }
    private void sendMessage(){
        if(answer==null||sending)return;String value=answer.getText().toString().trim();if(value.isEmpty())return;
        JSONObject question=task.optJSONObject("question");String target=ownerId,taskId=id;JSONObject body=new JSONObject();String endpoint;
        try{
            body.put("text",value);
            if(question!=null){if(value.length()>4000){feedback="Replies to a question can be up to 4000 characters.";render();return;}body.put("questionId",question.optString("id"));endpoint="/mobile/tasks/"+taskId+"/answer";armingUntil=System.currentTimeMillis()+5000;MobileAccess.authorize(true);}
            else endpoint=target.isEmpty()?"/mobile/tasks/"+taskId+"/steer":"/bots/"+target+"/messages";
        }catch(JSONException ignored){return;}
        sending=true;render();requests.execute(()->{try{JSONObject sent=(JSONObject)api.request("POST",endpoint,body);main.post(()->{if(closing)return;sending=false;if(answer!=null&&answer.getText().toString().trim().equals(value)){draft="";answer.setText("");}feedback=sent.optString("delivery").equals("steered")?"Update sent to the running task":"Message sent";render();pollSoon();});}catch(Exception error){main.post(()->{sending=false;feedback="Couldn't send: "+error.getMessage();render();});}});
    }
    private boolean drag(View view,MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:if(morph!=null)morph.cancel();startX=event.getRawX();startY=event.getRawY();windowX=layout.x;windowY=layout.y;return true;
            case MotionEvent.ACTION_MOVE:layout.x=windowX+(int)(event.getRawX()-startX);layout.y=windowY-(int)(event.getRawY()-startY);clampPosition(surface.getWidth(),surface.getHeight());if(attached)windows.updateViewLayout(surface,layout);return true;
            case MotionEvent.ACTION_UP:if(Math.abs(event.getRawX()-startX)<dp(8)&&Math.abs(event.getRawY()-startY)<dp(8))view.performClick();return true;
            default:return true;
        }
    }
    private void sizePanel(){
        if(surface==null||!attached||closing)return;
        layout.flags=WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|(expanded?0:WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)|(suspended||passThroughRequests>0?WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE:0);
        int width=renderedCircle?dp(54):Math.min(dp(expanded?344:280),getResources().getDisplayMetrics().widthPixels-dp(24));
        if(expanded&&logScroll!=null){int available=getResources().getDisplayMetrics().heightPixels-keyboardBottom-dp(230);logScroll.getLayoutParams().height=Math.max(dp(60),Math.min(dp(190),available));logScroll.requestLayout();if(controls!=null)controls.setVisibility(keyboardBottom>0&&available<dp(100)?View.GONE:View.VISIBLE);}
        surface.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.max(dp(150),getResources().getDisplayMetrics().heightPixels-keyboardBottom-dp(40)),View.MeasureSpec.AT_MOST));
        int height=surface.getMeasuredHeight();layout.width=width;layout.height=height;clampPosition(width,height);windows.updateViewLayout(surface,layout);targetWidth=width;targetHeight=height;
    }
    private void clampPosition(int width,int height){
        int horizontal=Math.max(0,(getResources().getDisplayMetrics().widthPixels-width)/2-dp(8));
        layout.x=Math.max(-horizontal,Math.min(horizontal,layout.x));
        int maxBottom=Math.max(dp(12),getResources().getDisplayMetrics().heightPixels-height-dp(12));
        layout.y=Math.max(keyboardBottom+dp(12),Math.min(maxBottom,layout.y));
    }
    /** Let gestures reach the app without making the progress island disappear. */
    public static void passThrough(boolean active){MobileOverlayService service=instance;if(service==null)return;CountDownLatch ready=new CountDownLatch(1);service.main.post(()->{try{service.passThroughRequests=Math.max(0,service.passThroughRequests+(active?1:-1));if(service.attached){if(service.passThroughRequests>0||service.suspended)service.layout.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;else service.layout.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;service.windows.updateViewLayout(service.surface,service.layout);}}finally{ready.countDown();}});try{ready.await(1,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    /** Hide our own control surface while Artemis observes/acts, never the target app. */
    public static void suspendOverlay(boolean hide){
        MobileOverlayService service=instance;if(service==null||!service.attached)return;
        CountDownLatch ready=new CountDownLatch(1);service.main.post(() -> {try{
            if(service.attached){service.suspended=hide;service.surface.setAlpha(hide?0:1);if(hide)service.layout.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                else if(service.passThroughRequests==0)service.layout.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;service.windows.updateViewLayout(service.surface,service.layout);}
        }finally{ready.countDown();}});
        try{ready.await(1,TimeUnit.SECONDS);if(hide)Thread.sleep(60);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    @Override public void onDestroy(){
        if(morph!=null)morph.cancel();
        closing=true;MobileAccess.authorize(false);if(instance==this){instance=null;activeTask="";}
        if(api!=null&&mobileLive())requests.execute(() -> {try{api.request("POST","/mobile/tasks/"+id+"/pause",new JSONObject());}catch(Exception ignored){}});
        polling.shutdownNow();requests.shutdown();main.removeCallbacksAndMessages(null);if(attached){windows.removeView(surface);attached=false;}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}

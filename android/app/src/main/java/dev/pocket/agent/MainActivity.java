package dev.pocket.agent;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.File;
import java.util.concurrent.*;

/** Native UI backed by the app-owned Linux runtime; no WebView or Termux pairing. */
public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newFixedThreadPool(3);
    private final ExecutorService inputs = Executors.newSingleThreadExecutor();
    private AgentApi api;
    private JSONObject state = new JSONObject();
    private String project = "", projectName = "", session = "", tab = "Projects", directory = ".", terminalId = "", pendingClone = "";
    private LinearLayout body, messages, jobs;
    private FrameLayout shellRoot,drawer;
    private ScrollView chatScroll;
    private EditText prompt;
    private TextView setupStatus;
    private String mode="Coding",mobileTask="",mobileDraft="";
    private EditText mobilePrompt;
    private LinearLayout mobileDetails;
    private TerminalCanvas terminal;
    private boolean visible, listening, loadingChat, chatDirty, chatScheduled, followChat = true;
    private int generation;
    private final Runnable chatUpdate = () -> { chatScheduled=false;refreshChat(); };
    int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    android.content.SharedPreferences prefs() { return getSharedPreferences("native-ui", MODE_PRIVATE); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); project = prefs().getString("project", ""); session = prefs().getString("session", "");
        mode=prefs().getString("mode","Coding");mobileTask=prefs().getString("mobile-task","");
        acceptMobileIntent(getIntent());
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::back);
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        setup();
    }
    private void acceptMobileIntent(Intent intent){if(intent!=null&&intent.hasExtra("mobileTask")){mode="Mobile Use";mobileTask=intent.getStringExtra("mobileTask");tab="Tasks";}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);acceptMobileIntent(intent);if(api!=null)show(mode.equals("Mobile Use")?"Tasks":tab);}
    @Override protected void onResume() {
        super.onResume(); visible = true; startForegroundService(new Intent(this, RuntimeService.class));
        if (!RuntimeService.ready) { api=null;setup();waitForRuntime(); }
        else if (api == null) waitForRuntime(); else { listen(); loadState(() -> show(tab)); }
    }
    @Override protected void onPause() {
        visible = false; generation++; listening = false; if (api != null) api.closeEvents();
        handler.removeCallbacks(chatUpdate);chatScheduled=false; super.onPause();
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); work.shutdownNow(); inputs.shutdownNow(); super.onDestroy(); }
    LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); return view; }
    TextView label(String text, int size) {
        TextView view = AgentStyle.text(this,text,size>=20?18:size);
        view.setPadding(dp(8),dp(8),dp(8),dp(8)); return view;
    }
    Button button(LinearLayout parent, String text, Runnable action) {
        Button view = new Button(this); view.setText(text); AgentStyle.button(view,text.equals("Send")||text.equals("Start task")||text.startsWith("Save ")); parent.addView(view); view.setOnClickListener(v -> action.run()); return view;
    }
    EditText field(LinearLayout parent, String hint, String value, boolean secret) {
        parent.addView(label(hint,13)); EditText view = new EditText(this); view.setSingleLine(true);
        view.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        AgentStyle.input(view);view.setText(value); parent.addView(view); return view;
    }
    private void shell() {
        LinearLayout page = column(); page.setBackgroundColor(AgentStyle.BACK); page.setPadding(dp(8),0,dp(8),0);
        page.setOnApplyWindowInsetsListener((v,insets) -> {
            v.setPadding(dp(8)+insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),dp(8)+insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        LinearLayout brand=row();brand.setGravity(Gravity.CENTER_VERTICAL);brand.setPadding(dp(10),dp(12),dp(10),dp(10));
        TextView wordmark=label("Pocket Agent",18);wordmark.setTypeface(AgentStyle.font(this),Typeface.BOLD);brand.addView(wordmark,new LinearLayout.LayoutParams(0,-2,1));
        TextView context=label(mode.equals("Coding")?projectName:"Mobile Use",11);context.setTextColor(AgentStyle.MUTED);context.setSingleLine(true);context.setMaxWidth(dp(150));brand.addView(context);page.addView(brand);
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(4),0,dp(4),dp(12));page.addView(top);
        top.addView(new AgentIcon(this,tab.equals("Settings")?"back":"menu",tab.equals("Settings")?"Back":"Open navigation",() -> {if(tab.equals("Settings"))show(mode.equals("Mobile Use")?"Tasks":project.isEmpty()?"Projects":"Chat");else openDrawer();}),new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout modes=AgentStyle.segment(this);LinearLayout.LayoutParams modeParams=new LinearLayout.LayoutParams(0,dp(44),1);modeParams.setMargins(dp(10),0,dp(10),0);top.addView(modes,modeParams);
        for(String option:new String[]{"Mobile Use","Coding"}){
            Button choice=button(modes,option,() -> {
                if(option.equals("Coding")&&!MobileOverlayService.activeTask.isEmpty()){
                    MobileAccess.authorize(false);request("POST","/mobile/tasks/"+MobileOverlayService.activeTask+"/pause",json(),v -> {});
                }
                mode=option;prefs().edit().putString("mode",mode).apply();show(mode.equals("Mobile Use")?"Tasks":project.isEmpty()?"Projects":"Chat");
            });
            choice.setLayoutParams(new LinearLayout.LayoutParams(0,dp(36),1));AgentStyle.selected(choice,mode.equals(option));
        }
        top.addView(new AgentIcon(this,"settings","Model and settings",() -> show("Settings")),new LinearLayout.LayoutParams(dp(44),dp(44)));
        if(mode.equals("Coding")&&!tab.equals("Settings")&&!tab.equals("Projects")){
            LinearLayout tabs=AgentStyle.segment(this);LinearLayout.LayoutParams tabParams=new LinearLayout.LayoutParams(-1,dp(44));tabParams.setMargins(dp(32),0,dp(32),dp(10));page.addView(tabs,tabParams);
            for(String name:new String[]{"Chat","Terminal","Files"}){Button choice=button(tabs,name,() -> show(name));choice.setLayoutParams(new LinearLayout.LayoutParams(0,dp(36),1));AgentStyle.selected(choice,tab.equals(name));}
        }
        body=column();page.addView(body,new LinearLayout.LayoutParams(-1,0,1));shellRoot=new FrameLayout(this);shellRoot.addView(page);drawer=null;setContentView(shellRoot);
    }
    private void closeDrawer(){if(drawer!=null){shellRoot.removeView(drawer);drawer=null;}}
    private void openDrawer(){
        loadState(this::showDrawer);
    }
    private void showDrawer(){
        if(drawer!=null)return;drawer=new FrameLayout(this);drawer.setBackgroundColor(0x99000000);shellRoot.addView(drawer,new FrameLayout.LayoutParams(-1,-1));drawer.setOnClickListener(v -> closeDrawer());
        LinearLayout panel=column();panel.setClickable(true);panel.setBackgroundColor(AgentStyle.BACK);panel.setPadding(dp(12),dp(24),dp(12),dp(16));
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(Math.min(dp(304),getResources().getDisplayMetrics().widthPixels-dp(48)),-1,Gravity.LEFT);drawer.addView(panel,position);
        LinearLayout heading=row();heading.setGravity(Gravity.CENTER_VERTICAL);TextView title=label("Pocket Agent",20);title.setTypeface(AgentStyle.font(this),Typeface.BOLD);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));heading.addView(new AgentIcon(this,"close","Close navigation",this::closeDrawer),new LinearLayout.LayoutParams(dp(44),dp(44)));panel.addView(heading);
        drawerRow(panel,"＋  "+(mode.equals("Mobile Use")?"New task":"New chat"),false,() -> {closeDrawer();if(mode.equals("Mobile Use")){mobileTask="";show("Tasks");}else if(!project.isEmpty())newChat();else createProject();});
        ScrollView scroll=new ScrollView(this);LinearLayout list=column();scroll.addView(list);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        list.addView(label("Recent",14));
        if(mode.equals("Mobile Use")){
            JSONArray tasks=state.optJSONArray("mobileTasks");if(tasks!=null)for(int i=tasks.length()-1;i>=0;i--){JSONObject task=tasks.optJSONObject(i);if(task==null)continue;drawerRow(list,"▢  "+task.optString("prompt"),task.optString("id").equals(mobileTask),() -> {mobileTask=task.optString("id");prefs().edit().putString("mobile-task",mobileTask).apply();closeDrawer();show("Tasks");});}
        }else{
            JSONArray sessions=state.optJSONArray("sessions");if(sessions!=null)for(int i=sessions.length()-1;i>=0;i--){JSONObject item=sessions.optJSONObject(i);if(item==null)continue;JSONObject owner=findProject(item.optString("projectId"));if(owner==null)continue;drawerRow(list,"▢  "+item.optString("title"),item.optString("id").equals(session),() -> {project=owner.optString("id");projectName=owner.optString("name");terminalId="";directory=".";prefs().edit().putString("project",project).apply();setSession(item);closeDrawer();show("Chat");});}
            list.addView(label("Workspace",14));for(String destination:new String[]{"Projects","Files","Terminal","Servers"})drawerRow(list,destination,tab.equals(destination),() -> {closeDrawer();show(destination);});
        }
        drawerRow(panel,"⚙  Settings",tab.equals("Settings"),() -> {closeDrawer();show("Settings");});panel.setTranslationX(-position.width);panel.animate().translationX(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }
    private void drawerRow(LinearLayout parent,String title,boolean selected,Runnable action){
        TextView item=AgentStyle.text(this,title,14);item.setSingleLine(true);item.setEllipsize(android.text.TextUtils.TruncateAt.END);item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(14),0,dp(14),0);item.setBackground(AgentStyle.surface(this,selected?AgentStyle.PANEL:Color.TRANSPARENT,16,false));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(48));params.setMargins(0,dp(3),0,dp(3));parent.addView(item,params);item.setOnClickListener(v -> action.run());
    }
    LinearLayout scrolling() {
        ScrollView scroll=new ScrollView(this); LinearLayout content=column(); content.setPadding(dp(6),dp(6),dp(6),dp(16));
        scroll.addView(content);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));return content;
    }
    private void setup() {
        LinearLayout page=column();page.setPadding(dp(24),dp(40),dp(24),dp(24));page.setBackgroundColor(AgentStyle.BACK);
        page.addView(label("Pocket Agent",28));page.addView(label("Your projects and coding tools, on your phone.",17));
        setupStatus=label(RuntimeService.status,15);page.addView(setupStatus);page.addView(new ProgressBar(this));
        button(page,"Retry setup",() -> { startForegroundService(new Intent(this,RuntimeService.class));waitForRuntime(); });
        button(page,"View setup log",() -> {try {new AlertDialog.Builder(this).setTitle("Environment log").setMessage(Io.text(new File(getFilesDir(),"runtime.log"))).setPositiveButton("Close",null).show();}catch(Exception e){error(e);}});
        setContentView(page);
    }
    private void waitForRuntime() {
        if(!visible||isFinishing()||api!=null)return;setupStatus.setText(RuntimeService.status);
        if(RuntimeService.ready) {
            try {
                api=new AgentApi((String)new JSONTokener(Io.text(new File(getFilesDir(),"state/token.json"))).nextValue());listen();
                MobileAccess.syncToken(this);
                loadState(() -> {JSONObject selected=findProject(project);if(selected==null){project="";session="";}else projectName=selected.optString("name");show(mode.equals("Mobile Use")?"Tasks":project.isEmpty()?"Projects":"Chat");});
            }catch(Exception e){api=null;error(e);handler.postDelayed(this::waitForRuntime,1000);}
        }else handler.postDelayed(this::waitForRuntime,500);
    }
    interface Result { void done(Object value) throws Exception; }
    void request(String method,String path,JSONObject data,Result result) {
        if(api==null)return;AgentApi connection=api;
        work.execute(() -> {try {Object value=connection.request(method,path,data);handler.post(() -> {if(!isDestroyed())try {result.done(value);}catch(Exception e){error(e);}});}
            catch(Exception e){handler.post(() -> {if(!isDestroyed())error(e);});}});
    }
    JSONObject json(Object... values) {
        JSONObject data=new JSONObject();try {for(int i=0;i<values.length;i+=2)data.put((String)values[i],values[i+1]);}catch(JSONException e){throw new IllegalArgumentException(e);}return data;
    }
    void error(Exception e) {if(!isDestroyed())new AlertDialog.Builder(this).setTitle("Couldn't complete that").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
    void loadState(Runnable done){request("GET","/state",null,value -> {state=(JSONObject)value;done.run();});}
    private JSONObject findProject(String id) {
        JSONArray projects=state.optJSONArray("projects");if(projects!=null)for(int i=0;i<projects.length();i++)if(id.equals(projects.optJSONObject(i).optString("id")))return projects.optJSONObject(i);return null;
    }
    private void selectProject(JSONObject selected) {
        project=selected.optString("id");projectName=selected.optString("name");session="";terminalId="";directory=".";
        prefs().edit().putString("project",project).putString("session","").apply();show("Chat");
    }
    private void show(String name) {
        if(api==null)return;if(mobilePrompt!=null){mobileDraft=mobilePrompt.getText().toString();mobilePrompt=null;}mobileDetails=null;
        tab=name;terminal=null;handler.removeCallbacks(chatUpdate);chatScheduled=false;shell();
        if(mode.equals("Mobile Use")){if(name.equals("Settings"))settings();else mobileTasks();return;}
        if(project.isEmpty()&&!name.equals("Projects")&&!name.equals("Settings")){body.addView(label("Create or select a project to get started.",18));button(body,"Projects",() -> show("Projects"));return;}
        switch(name){case "Projects":projects();break;case "Chat":chat();break;case "Terminal":terminal();break;case "Files":files();break;case "Servers":servers();break;case "Settings":settings();break;}
    }
    private void projects() {
        LinearLayout content=scrolling();content.addView(label("Your projects",23));button(content,"Create project / clone repository",this::createProject);
        request("GET","/projects",null,value -> {if(!tab.equals("Projects"))return;JSONArray projects=(JSONArray)value;
            if(projects.length()==0)content.addView(label("Start with an empty project, or clone a Git repository.",16));
            for(int i=0;i<projects.length();i++){JSONObject selected=projects.getJSONObject(i);button(content,selected.getString("name"),() -> selectProject(selected));}});
    }
    private void mobileTasks(){
        ScrollView scroll=new ScrollView(this);LinearLayout content=column();content.setPadding(dp(10),dp(8),dp(10),dp(20));scroll.addView(content);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(mobileTask.isEmpty())welcome(content,"What should your phone do?","Describe a task. Follow along and reply from the floating island while your agent works.");
        LinearLayout permissionCard=column();permissionCard.setPadding(dp(8),dp(8),dp(8),dp(8));AgentStyle.card(permissionCard);content.addView(permissionCard);
        LinearLayout permissions=row();permissionCard.addView(permissions);
        button(permissions,"Accessibility",() -> startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button(permissions,"Floating pill",() -> startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));
        String ready=com.artemis.helper.ArtemisAccessibilityService.getInstance()!=null?"Accessibility connected":"Enable Pocket Agent · Mobile Use in Accessibility";
        TextView permissionHint=label(ready+(android.provider.Settings.canDrawOverlays(this)?" · Floating permission ready":" · Allow display over other apps"),11);permissionHint.setTextColor(AgentStyle.MUTED);permissionCard.addView(permissionHint);
        if(com.artemis.helper.ArtemisAccessibilityService.getInstance()!=null&&android.provider.Settings.canDrawOverlays(this))permissionCard.setVisibility(View.GONE);
        mobileDetails=column();content.addView(mobileDetails);
        if(!mobileTask.isEmpty())request("GET","/mobile/tasks/"+mobileTask,null,value -> renderMobileTask((JSONObject)value));
        LinearLayout composer=row();composer.setGravity(Gravity.CENTER_VERTICAL);composer.setPadding(dp(4),dp(4),dp(4),dp(4));composer.setBackground(AgentStyle.surface(this,AgentStyle.PANEL,28,true));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(dp(4),dp(8),dp(4),dp(12));body.addView(composer,params);
        composer.addView(new AgentIcon(this,"plus","New mobile task",() -> {mobileTask="";prefs().edit().remove("mobile-task").apply();show("Tasks");}),new LinearLayout.LayoutParams(dp(44),dp(44)));
        mobilePrompt=new EditText(this);AgentStyle.input(mobilePrompt);mobilePrompt.setBackgroundColor(Color.TRANSPARENT);mobilePrompt.setMinLines(1);mobilePrompt.setMaxLines(5);mobilePrompt.setHint("Ask Pocket Agent to use your phone…");mobilePrompt.setText(mobileDraft);composer.addView(mobilePrompt,new LinearLayout.LayoutParams(0,-2,1));
        AgentIcon start=new AgentIcon(this,"send","Start task",this::beginMobileTask);start.setBackground(AgentStyle.surface(this,AgentStyle.BLUE,28,false));composer.addView(start,new LinearLayout.LayoutParams(dp(44),dp(44)));
    }
    private void beginMobileTask(){
            String goal=mobilePrompt.getText().toString().trim();if(goal.isEmpty()){mobilePrompt.setError("Describe a task first");return;}
            if(!mobileReady())return;
            new AlertDialog.Builder(this).setTitle("Start Mobile Use?").setMessage("Pocket Agent will read the visible screen, send observations to your configured model, and tap or type for this task. It continues outside this app. Pause or stop from the floating pill or notification. Enter passwords and payment details only in the target app.")
                .setPositiveButton("Start",(dialog,which) -> request("POST","/mobile/tasks",json("prompt",goal),value -> {
                    mobileTask=((JSONObject)value).optString("id");prefs().edit().putString("mobile-task",mobileTask).apply();mobileDraft="";mobilePrompt.setText("");startMobile(mobileTask,true);
                })).setNegativeButton("Cancel",null).show();
    }
    private boolean mobileReady(){
        if(com.artemis.helper.ArtemisAccessibilityService.getInstance()==null){new AlertDialog.Builder(this).setTitle("Enable Mobile Use accessibility").setMessage("In Android Accessibility settings, enable Pocket Agent · Mobile Use. Android requires you to grant this permission yourself.").setPositiveButton("Open settings",(d,w) -> startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("Cancel",null).show();return false;}
        if(!android.provider.Settings.canDrawOverlays(this)){new AlertDialog.Builder(this).setTitle("Allow the floating pill").setMessage("Allow Pocket Agent to display over other apps so you can follow progress, pause, and reply without returning here.").setPositiveButton("Open settings",(d,w) -> startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())))).setNegativeButton("Cancel",null).show();return false;}return true;
    }
    private void startMobile(String id,boolean run){
        if(!mobileReady())return;AgentApi connection=api;
        work.execute(() -> {try{
            JSONArray all=(JSONArray)connection.request("GET","/mobile/tasks",null);
            for(int i=0;i<all.length();i++){JSONObject other=all.getJSONObject(i);if(!id.equals(other.optString("id"))&&("running".equals(other.optString("status"))||"waiting".equals(other.optString("status"))))throw new Exception("Pause or stop the current mobile task first");}
            handler.post(() -> startForegroundService(new Intent(this,MobileOverlayService.class).putExtra("taskId",id)));
            long until=System.currentTimeMillis()+3000;while((!id.equals(MobileOverlayService.activeTask)||!MobileAccess.allowed())&&System.currentTimeMillis()<until)Thread.sleep(50);
            if(run){
                CountDownLatch backgrounded=new CountDownLatch(1);handler.post(() -> {moveTaskToBack(true);backgrounded.countDown();});
                if(!backgrounded.await(2,TimeUnit.SECONDS))throw new Exception("Could not background the activity");
                Thread.sleep(350);connection.request("POST","/mobile/tasks/"+id+"/resume",json());
            }
        }catch(Exception e){handler.post(() -> {stopService(new Intent(this,MobileOverlayService.class));error(e);});}});
    }
    private void renderMobileTask(JSONObject task){
        if(mobileDetails==null||!mode.equals("Mobile Use")||!task.optString("id").equals(mobileTask))return;
        mobileDetails.removeAllViews();mobileDetails.setPadding(0,dp(12),0,0);
        String status=task.optString("status");bubble(mobileDetails,task.optString("prompt"),true,task.optLong("createdAt"));TextView stateLabel=label(status.toUpperCase(java.util.Locale.ROOT)+" · "+task.optInt("steps")+" steps",11);stateLabel.setTextColor(AgentStyle.MUTED);mobileDetails.addView(stateLabel);
        String output=task.optString("output");if(!output.isEmpty())bubble(mobileDetails,output,false,0);
        JSONArray trace=task.optJSONArray("trace");if(trace!=null&&trace.length()>0){StringBuilder details=new StringBuilder();for(int i=0;i<trace.length();i++)details.append(trace.optJSONObject(i).optString("text")).append("\n");disclosure(mobileDetails,"Activity · "+trace.length()+" events",details.toString());}
        if(status.equals("running"))working(mobileDetails,"Agent is working");
        if(!task.optString("error").isEmpty())mobileDetails.addView(label(task.optString("error"),12));
        JSONObject question=task.optJSONObject("question");if(question!=null){mobileDetails.addView(label(question.optString("text"),14));button(mobileDetails,"Reply in floating panel",() -> startMobile(mobileTask,false));}
        LinearLayout actions=row();mobileDetails.addView(actions);
        if(status.equals("queued")||status.equals("paused"))button(actions,"Resume",() -> startMobile(mobileTask,true));
        if(status.equals("running")||status.equals("waiting")){button(actions,"Pause",() -> {MobileAccess.authorize(false);request("POST","/mobile/tasks/"+mobileTask+"/pause",json(),v -> {});});button(actions,"Show pill",() -> startMobile(mobileTask,false));}
        if(!status.equals("completed")&&!status.equals("failed")&&!status.equals("stopped"))button(actions,"Stop",() -> {MobileAccess.authorize(false);request("POST","/mobile/tasks/"+mobileTask+"/stop",json(),v -> {});});
        if(!status.equals("running")&&!status.equals("waiting"))button(actions,"Delete",() -> {String id=mobileTask;request("DELETE","/mobile/tasks/"+id,null,v -> {mobileTask="";prefs().edit().remove("mobile-task").apply();show("Tasks");});});
    }
    private void createProject() {
        LinearLayout form=column();form.setPadding(dp(20),0,dp(20),0);EditText name=field(form,"Project name","",false);EditText repo=field(form,"Git URL (optional)","",false);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("New project").setView(form).setPositiveButton("Create",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String url=repo.getText().toString().trim();if(!url.isEmpty()){Uri uri=Uri.parse(url);if(!("https".equals(uri.getScheme())||"ssh".equals(uri.getScheme()))||uri.getHost()==null){repo.setError("Enter an HTTPS or SSH Git URL");return;}}
            request("POST","/projects",json("name",name.getText().toString().trim()),value -> {dialog.dismiss();selectProject((JSONObject)value);if(!url.isEmpty()){pendingClone="git clone -- "+quote(url)+" .\r";show("Terminal");}});
        }));dialog.show();
    }
    static String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
    private void chat() {
        chatScroll=new ScrollView(this);messages=column();chatScroll.addView(messages);body.addView(chatScroll,new LinearLayout.LayoutParams(-1,0,1));
        messages.setPadding(dp(8),dp(12),dp(8),dp(16));
        chatScroll.setOnScrollChangeListener((v,x,y,oldx,oldy) -> followChat=y+chatScroll.getHeight()>=messages.getHeight()-dp(50));
        LinearLayout composer=row();composer.setGravity(Gravity.CENTER_VERTICAL);composer.setPadding(dp(4),dp(4),dp(4),dp(4));composer.setBackground(AgentStyle.surface(this,AgentStyle.PANEL,28,true));LinearLayout.LayoutParams composerParams=new LinearLayout.LayoutParams(-1,-2);composerParams.setMargins(dp(4),dp(8),dp(4),dp(12));body.addView(composer,composerParams);
        composer.addView(new AgentIcon(this,"plus","New chat",this::newChat),new LinearLayout.LayoutParams(dp(44),dp(44)));
        prompt=new EditText(this);AgentStyle.input(prompt);prompt.setBackgroundColor(Color.TRANSPARENT);prompt.setHint("Ask Pocket Agent…");prompt.setMinLines(1);prompt.setMaxLines(5);composer.addView(prompt,new LinearLayout.LayoutParams(0,-2,1));
        composer.addView(new AgentIcon(this,"stop","Stop agent",() -> {if(!session.isEmpty())request("POST","/sessions/"+session+"/stop",json(),v -> refreshChat());}),new LinearLayout.LayoutParams(dp(40),dp(44)));
        AgentIcon send=new AgentIcon(this,"send","Send",() -> {String text=prompt.getText().toString().trim();if(text.isEmpty())return;
            if(session.isEmpty())request("POST","/sessions",json("projectId",project),value -> {setSession((JSONObject)value);sendPrompt(text);});else sendPrompt(text);});send.setBackground(AgentStyle.surface(this,AgentStyle.BLUE,28,false));composer.addView(send,new LinearLayout.LayoutParams(dp(44),dp(44)));
        if(session.isEmpty()){welcome(messages,"What do you want to build?","Edit files, run commands, and bring an idea to life in "+projectName+".");
            JSONObject settings=state.optJSONObject("settings");if(settings!=null&&!settings.optBoolean("hasKey"))button(messages,"Set up your model / BYOK key",() -> show("Settings"));}else refreshChat();
    }
    private void welcome(LinearLayout parent,String title,String subtitle){
        LinearLayout empty=column();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(20),dp(64),dp(20),dp(48));ThinkingOrbView orb=new ThinkingOrbView(this);empty.addView(orb,new LinearLayout.LayoutParams(dp(64),dp(64)));TextView heading=label(title,20);heading.setGravity(Gravity.CENTER);heading.setTypeface(AgentStyle.font(this),Typeface.BOLD);empty.addView(heading);TextView hint=label(subtitle,13);hint.setTextColor(AgentStyle.MUTED);hint.setGravity(Gravity.CENTER);empty.addView(hint);parent.addView(empty);
    }
    private void bubble(LinearLayout parent,String text,boolean user,long timestamp){
        LinearLayout line=column();line.setGravity(user?Gravity.RIGHT:Gravity.LEFT);LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);outer.setMargins(0,dp(8),0,dp(8));parent.addView(line,outer);
        TextView view=AgentStyle.text(this,text,14);view.setLineSpacing(dp(3),1);view.setTextIsSelectable(true);view.setPadding(dp(14),dp(12),dp(14),dp(12));view.setMaxWidth(getResources().getDisplayMetrics().widthPixels-dp(user?64:40));
        android.graphics.drawable.GradientDrawable shape=AgentStyle.surface(this,user?AgentStyle.BLUE:AgentStyle.PANEL,20,!user);float r=dp(20),tip=dp(3);shape.setCornerRadii(user?new float[]{r,r,r,r,tip,tip,r,r}:new float[]{r,r,r,r,r,r,tip,tip});view.setBackground(shape);line.addView(view,new LinearLayout.LayoutParams(-2,-2));
        if(timestamp>0){TextView time=label(new java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(timestamp)),10);time.setTextColor(AgentStyle.MUTED);line.addView(time);}
    }
    private void disclosure(LinearLayout parent,String title,String detail){
        LinearLayout group=column();TextView heading=label(title+"  ›",12);heading.setTextColor(AgentStyle.MUTED);group.addView(heading);TextView log=label(detail,12);log.setTypeface(Typeface.MONOSPACE);log.setTextIsSelectable(true);log.setVisibility(View.GONE);AgentStyle.card(log);group.addView(log);heading.setOnClickListener(v -> {boolean open=log.getVisibility()!=View.VISIBLE;log.setVisibility(open?View.VISIBLE:View.GONE);heading.setText(title+(open?"  ⌄":"  ›"));});parent.addView(group);
    }
    private void working(LinearLayout parent,String status){
        LinearLayout wrap=row();wrap.setGravity(Gravity.CENTER);LinearLayout pill=AgentStyle.segment(this);pill.setPadding(dp(10),dp(6),dp(14),dp(6));ThinkingOrbView orb=new ThinkingOrbView(this);orb.state("running");pill.addView(orb,new LinearLayout.LayoutParams(dp(24),dp(24)));ShimmerTextView caption=new ShimmerTextView(this,status);caption.active(true);caption.setPadding(dp(8),0,0,0);pill.addView(caption);wrap.addView(pill);parent.addView(wrap);
    }
    private void setSession(JSONObject selected){session=selected.optString("id");prefs().edit().putString("session",session).apply();}
    private void newChat(){request("POST","/sessions",json("projectId",project),value -> {setSession((JSONObject)value);show("Chat");});}
    private void sendPrompt(String text){request("POST","/sessions/"+session+"/chat",json("text",text),v -> {prompt.setText("");refreshChat();});}
    private void conversations() {
        loadState(() -> {JSONArray all=state.optJSONArray("sessions");java.util.ArrayList<JSONObject> sessions=new java.util.ArrayList<>();
            for(int i=0;i<all.length();i++)if(project.equals(all.optJSONObject(i).optString("projectId")))sessions.add(all.optJSONObject(i));
            String[] names=new String[sessions.size()];for(int i=0;i<names.length;i++)names[i]=sessions.get(i).optString("title");
            new AlertDialog.Builder(this).setTitle("Project conversations").setItems(names,(d,index) -> {
                JSONObject selected=sessions.get(index);
                new AlertDialog.Builder(this).setTitle(selected.optString("title")).setPositiveButton("Open",(dialog,which) -> {setSession(selected);show("Chat");})
                    .setNeutralButton("Delete",(dialog,which) -> request("DELETE","/sessions/"+selected.optString("id"),null,result -> {if(session.equals(selected.optString("id"))){session="";prefs().edit().putString("session","").apply();}show("Chat");}))
                    .setNegativeButton("Cancel",null).show();
            }).setNegativeButton("Close",null).show();});
    }
    private void refreshChat() {
        if(!visible||!tab.equals("Chat")||session.isEmpty())return;if(loadingChat){chatDirty=true;return;}loadingChat=true;String id=session;
        work.execute(() -> {try {JSONObject value=api.get("/sessions/"+id);handler.post(() -> {loadingChat=false;if(tab.equals("Chat")&&session.equals(id))renderChat(value);
            if(chatDirty){chatDirty=false;scheduleChat();}});}catch(Exception e){handler.post(() -> {loadingChat=false;error(e);});}});
    }
    private String messageText(JSONObject message) {
        Object content=message.opt("content");if(content instanceof String)return (String)content;StringBuilder text=new StringBuilder();
        if(content instanceof JSONArray)for(int i=0;i<((JSONArray)content).length();i++){JSONObject part=((JSONArray)content).optJSONObject(i);if(part==null)continue;
            if(part.has("text"))text.append(part.optString("text")).append("\n");if(part.optString("type").equals("toolCall"))text.append("→ ").append(part.optString("name")).append(" ").append(part.opt("arguments")).append("\n");}
        if(message.has("errorMessage"))text.append(message.optString("errorMessage"));return text.toString().trim();
    }
    private void renderChat(JSONObject value) {
        int old=chatScroll.getScrollY();boolean follow=followChat;messages.removeAllViews();JSONArray all=value.optJSONArray("messages");
        for(int i=0;i<all.length();i++){JSONObject message=all.optJSONObject(i);String role=message.optString("role");if(role.equals("system"))continue;String text=messageText(message);if(text.isEmpty())continue;
            if(role.equals("toolResult")){disclosure(messages,"Tool · "+message.optString("toolName"),text);continue;}
            JSONArray parts=message.optJSONArray("content");StringBuilder visibleText=new StringBuilder(),tools=new StringBuilder();if(parts!=null)for(int j=0;j<parts.length();j++){JSONObject part=parts.optJSONObject(j);if(part==null)continue;if(part.optString("type").equals("text"))visibleText.append(part.optString("text"));if(part.optString("type").equals("toolCall"))tools.append(part.optString("name")).append("\n").append(part.opt("arguments")).append("\n");}
            String content=parts==null?text:visibleText.toString().trim();if(!content.isEmpty())bubble(messages,content,role.equals("user"),message.optLong("timestamp"));if(tools.length()>0)disclosure(messages,"Tools used",tools.toString());if(message.has("errorMessage"))bubble(messages,message.optString("errorMessage"),false,0);}
        JSONObject streaming=value.optJSONObject("streaming");if(streaming!=null){String preview=messageText(streaming);if(!preview.isEmpty())bubble(messages,preview,false,0);}
        JSONArray approvals=value.optJSONArray("approvals");for(int i=0;i<approvals.length();i++){JSONObject approval=approvals.optJSONObject(i);messages.addView(label("Allow "+approval.optString("tool")+"?\n"+approval.opt("args"),15));LinearLayout buttons=row();messages.addView(buttons);
            button(buttons,"Allow",() -> request("POST","/approvals/"+approval.optString("id"),json("allow",true),v -> refreshChat()));button(buttons,"Deny",() -> request("POST","/approvals/"+approval.optString("id"),json("allow",false),v -> refreshChat()));}
        if(value.optBoolean("busy"))working(messages,"Agent is working");chatScroll.post(() -> {if(follow)chatScroll.fullScroll(View.FOCUS_DOWN);else chatScroll.scrollTo(0,old);});
    }
    private void terminal() {
        String selected=project;terminalId=prefs().getString("terminal-"+selected,"");LinearLayout keys=row();HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.addView(keys);body.addView(scroll);
        String[] names={"Esc","Tab","Ctrl-C","Ctrl-D","↑","↓","←","→"};String[] values={"\u001b","\t","\u0003","\u0004","\u001b[A","\u001b[B","\u001b[D","\u001b[C"};
        for(int i=0;i<values.length;i++){String value=values[i];button(keys,names[i],() -> terminalSend(value));}
        ToggleButton ctrl=new ToggleButton(this);ctrl.setTextOn("Ctrl ●");ctrl.setTextOff("Ctrl");ctrl.setChecked(false);keys.addView(ctrl);ctrl.setOnCheckedChangeListener((view,checked) -> {if(terminal!=null)terminal.latchControl(checked);});
        button(keys,"Paste",() -> {if(terminal!=null)terminal.paste();});button(keys,"New shell",() -> {if(!terminalId.isEmpty())request("DELETE","/terminals/"+terminalId,null,v -> {});prefs().edit().remove("terminal-"+selected).apply();terminalId="";show("Terminal");});
        terminal=new TerminalCanvas(this,new TerminalCanvas.Input(){public void send(String value){terminalSend(value);}public void resize(int cols,int rows){if(!terminalId.isEmpty())terminalRequest(json("data","","cols",cols,"rows",rows));}public void controlUsed(){ctrl.setChecked(false);}});
        body.addView(terminal,new LinearLayout.LayoutParams(-1,0,1));
        if(terminalId.isEmpty())openTerminal(selected);else{String id=terminalId;work.execute(() -> {try{JSONObject value=api.get("/terminals/"+id);handler.post(() -> {if(!tab.equals("Terminal")||!selected.equals(project))return;
            if(value.optBoolean("exited")){terminalId="";openTerminal(selected);}else terminal.append(value.optString("output"),value.optLong("offset"));});}catch(Exception e){handler.post(() -> {if(tab.equals("Terminal")&&selected.equals(project)){terminalId="";openTerminal(selected);}});}});}
    }
    private void openTerminal(String selected) {
        request("POST","/terminals",json("projectId",selected,"cwd","."),value -> {String id=((JSONObject)value).getString("id");prefs().edit().putString("terminal-"+selected,id).apply();if(!selected.equals(project)||!tab.equals("Terminal"))return;terminalId=id;
            terminalRequest(json("data","","cols",terminal.columns(),"rows",terminal.rows()));
            request("GET","/terminals/"+id,null,output -> {if(terminal!=null&&terminalId.equals(id))terminal.append(((JSONObject)output).optString("output"),((JSONObject)output).optLong("offset"));});
            if(!pendingClone.isEmpty()){String command=pendingClone;pendingClone="";terminalSend(command);}});
    }
    private void terminalSend(String value){if(!terminalId.isEmpty())terminalRequest(json("data",value));}
    private void terminalRequest(JSONObject data){String id=terminalId;AgentApi connection=api;inputs.execute(() -> {try{connection.request("POST","/terminals/"+id+"/input",data);}catch(Exception e){handler.post(() -> error(e));}});}
    private void files() {
        LinearLayout content=scrolling();content.addView(label(directory,18));button(content,"Up",() -> {int slash=directory.lastIndexOf('/');directory=slash<0?".":directory.substring(0,slash);show("Files");});
        button(content,"New file",() -> {EditText name=new EditText(this);name.setHint("path/to/file.txt");new AlertDialog.Builder(this).setTitle("Create file").setView(name).setPositiveButton("Create",(d,w) -> editFile((directory.equals(".")?"":directory+"/")+name.getText(),"")).setNegativeButton("Cancel",null).show();});
        String selected=project,pathDirectory=directory;
        request("GET","/files?projectId="+AgentApi.encode(selected)+"&path="+AgentApi.encode(directory),null,value -> {if(!tab.equals("Files")||!project.equals(selected)||!directory.equals(pathDirectory))return;JSONArray list=(JSONArray)value;
            for(int i=0;i<list.length();i++){JSONObject entry=list.getJSONObject(i);String path=(directory.equals(".")?"":directory+"/")+entry.getString("name");button(content,(entry.optBoolean("directory")?"▸ ":"  ")+entry.getString("name"),() -> {
                if(entry.optBoolean("directory")){directory=path;show("Files");}else request("GET","/file?projectId="+AgentApi.encode(selected)+"&path="+AgentApi.encode(path),null,file -> editFile(path,((JSONObject)file).getString("content")));});}});
    }
    private void editFile(String path,String text) {
        String selected=project;EditText editor=new EditText(this);editor.setGravity(Gravity.TOP);editor.setTypeface(Typeface.MONOSPACE);editor.setTextSize(13);
        editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);editor.setText(text);editor.setMinLines(12);ScrollView scroll=new ScrollView(this);scroll.addView(editor);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(path).setView(scroll).setPositiveButton("Save",null).setNegativeButton("Close",null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> request("PUT","/file",json("projectId",selected,"path",path,"content",editor.getText().toString()),result -> {dialog.dismiss();show("Files");})));dialog.show();
    }
    private void servers() {
        LinearLayout content=scrolling();content.addView(label("Development servers",22));EditText command=field(content,"Command","npm run dev -- --host 0.0.0.0 --port 3000",false);EditText port=field(content,"Port","3000",false);port.setInputType(InputType.TYPE_CLASS_NUMBER);
        button(content,"Start server",() -> {try{request("POST","/servers",json("projectId",project,"cwd",".","port",Integer.parseInt(port.getText().toString()),"command",command.getText().toString()),v -> loadState(() -> show("Servers")));}catch(Exception e){error(e);}});
        content.addView(label("Microsoft Dev Tunnels",22));button(content,"Sign in",() -> request("POST","/tunnels/login",json(),v -> loadState(() -> show("Servers"))));CheckBox anonymous=new CheckBox(this);anonymous.setText("Allow anonymous visitors");content.addView(anonymous);
        button(content,"Share this port",() -> {try{request("POST","/tunnels",json("port",Integer.parseInt(port.getText().toString()),"anonymous",anonymous.isChecked()),v -> loadState(() -> show("Servers")));}catch(Exception e){error(e);}});
        jobs=column();content.addView(jobs);renderJobs();
    }
    private void renderJobs() {
        if(jobs==null||!tab.equals("Servers"))return;jobs.removeAllViews();JSONArray list=state.optJSONArray("jobs");if(list==null)return;
        for(int i=list.length()-1;i>=0;i--){JSONObject job=list.optJSONObject(i);jobs.addView(label(job.optString("label")+" · "+job.optString("status"),16));if(job.optString("status").equals("running"))button(jobs,"Stop",() -> request("POST","/processes/"+job.optString("id")+"/stop",json(),v -> {}));
            int port=job.optInt("port");if(port>0)button(jobs,"Preview localhost:"+port,() -> openUrl("http://127.0.0.1:"+port));String output=job.optString("output");TextView log=label(output.length()>16000?output.substring(output.length()-16000):output,12);log.setTypeface(Typeface.MONOSPACE);log.setTextIsSelectable(true);jobs.addView(log);
            java.util.regex.Matcher matches=java.util.regex.Pattern.compile("https://[^\\s<>\"']+").matcher(output);java.util.HashSet<String> urls=new java.util.HashSet<>();while(matches.find()){String url=matches.group();String host=Uri.parse(url).getHost();if(host!=null&&(host.endsWith(".devtunnels.ms")||host.equals("github.com")||host.equals("microsoft.com")||host.endsWith(".microsoft.com"))&&urls.add(url))button(jobs,"Open "+host,() -> openUrl(url));}}
    }
    private void settings() {
        LinearLayout content=scrolling();content.setPadding(dp(14),dp(14),dp(14),dp(24));JSONObject saved=state.optJSONObject("settings");
        LinearLayout heading=row();heading.setGravity(Gravity.CENTER_VERTICAL);TextView byok=label("Bring Your Own Key",17);byok.setTypeface(AgentStyle.font(this),Typeface.BOLD);heading.addView(byok,new LinearLayout.LayoutParams(0,-2,1));TextView badge=label("BYOK",11);badge.setTextColor(AgentStyle.BACK);badge.setBackground(AgentStyle.surface(this,AgentStyle.WHITE,24,false));heading.addView(badge);content.addView(heading);
        Spinner protocol=new Spinner(this);protocol.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"OpenAI Responses","OpenAI-compatible Chat Completions","Anthropic Messages"}));
        String savedApi=saved.optString("api");protocol.setSelection(savedApi.equals("anthropic-messages")?2:savedApi.equals("openai-completions")?1:0);
        LinearLayout protocols=AgentStyle.segment(this);LinearLayout.LayoutParams protocolsParams=new LinearLayout.LayoutParams(-1,dp(46));protocolsParams.setMargins(0,dp(18),0,dp(14));content.addView(protocols,protocolsParams);
        Button openai=button(protocols,"OpenAI Compatible",() -> protocol.setSelection(1)),anthropic=button(protocols,"Anthropic Compatible",() -> protocol.setSelection(2));for(Button choice:new Button[]{openai,anthropic}){choice.setTextSize(11);choice.setLayoutParams(new LinearLayout.LayoutParams(0,dp(38),1));}AgentStyle.selected(openai,protocol.getSelectedItemPosition()!=2);AgentStyle.selected(anthropic,protocol.getSelectedItemPosition()==2);
        EditText base=field(content,"Base URL",saved.optString("baseUrl"),false),key=field(content,saved.optBoolean("hasKey")?"API Key · saved; leave blank to keep":"API Key","",true),model=field(content,"Model Name",saved.optString("model"),false);base.setTypeface(Typeface.MONOSPACE);model.setTypeface(Typeface.MONOSPACE);
        TextView reveal=label("Show API key",11);reveal.setTextColor(AgentStyle.MUTED);content.addView(reveal);reveal.setOnClickListener(v -> {boolean hidden=key.getTransformationMethod()!=null;key.setTransformationMethod(hidden?null:android.text.method.PasswordTransformationMethod.getInstance());reveal.setText(hidden?"Hide API key":"Show API key");key.setSelection(key.length());});
        LinearLayout advanced=column();TextView advancedToggle=label("Advanced model options  ›",12);advancedToggle.setTextColor(AgentStyle.MUTED);content.addView(advancedToggle);content.addView(advanced);advanced.setVisibility(View.GONE);advancedToggle.setOnClickListener(v -> advanced.setVisibility(advanced.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));advanced.addView(label("API protocol",12));advanced.addView(protocol);
        EditText context=field(advanced,"Context tokens",String.valueOf(saved.optInt("contextWindow")),false),max=field(advanced,"Maximum output tokens",String.valueOf(saved.optInt("maxTokens")),false);context.setInputType(InputType.TYPE_CLASS_NUMBER);max.setInputType(InputType.TYPE_CLASS_NUMBER);
        protocol.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){int previous=protocol.getSelectedItemPosition();public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int pos,long id){AgentStyle.selected(openai,pos!=2);AgentStyle.selected(anthropic,pos==2);if((previous==2)!=(pos==2)){base.setText(pos==2?"https://api.anthropic.com":"https://api.openai.com/v1");if(pos==2)model.setText("claude-sonnet-4-6");key.setText("");}previous=pos;}});
        java.util.function.Supplier<JSONObject> connection=() -> json("provider",protocol.getSelectedItemPosition()==2?"anthropic":"openai","api",protocol.getSelectedItemPosition()==2?"anthropic-messages":protocol.getSelectedItemPosition()==1?"openai-completions":"openai-responses","baseUrl",base.getText().toString().trim(),"model",model.getText().toString().trim(),"apiKey",key.getText().toString(),"contextWindow",Integer.parseInt(context.getText().toString()),"maxTokens",Integer.parseInt(max.getText().toString()));
        if(mode.equals("Coding")&&!session.isEmpty()){
            content.addView(label("PERMISSIONS · SHELL COMMANDS",11));LinearLayout choices=row();content.addView(choices);TextView review=permissionCard(choices,"Ask for review","Confirm each shell command"),full=permissionCard(choices,"Full access","Run shell commands directly");
            java.util.function.Consumer<Boolean> paint=trusted -> {permissionSelected(review,!trusted);permissionSelected(full,trusted);};String conversation=session;
            request("GET","/sessions/"+conversation,null,v -> paint.accept(((JSONObject)v).optBoolean("trusted")));
            review.setOnClickListener(v -> request("PUT","/sessions/"+conversation+"/trust",json("trust",false),result -> paint.accept(false)));full.setOnClickListener(v -> request("PUT","/sessions/"+conversation+"/trust",json("trust",true),result -> paint.accept(true)));
        }
        LinearLayout saveActions=row();saveActions.setPadding(0,dp(18),0,dp(12));content.addView(saveActions);
        Button save=button(saveActions,"Save Settings",() -> {try{request("PUT","/settings",connection.get(),v -> {state.put("settings",v);key.setText("");Toast.makeText(this,"Connection saved",Toast.LENGTH_SHORT).show();});}catch(Exception e){error(e);}});save.setLayoutParams(new LinearLayout.LayoutParams(0,dp(46),1));
        button(saveActions,"Test API",() -> {try{request("POST","/settings/test",connection.get(),v -> Toast.makeText(this,"Connected · "+((JSONObject)v).optLong("latencyMs")+" ms",Toast.LENGTH_LONG).show());}catch(Exception e){error(e);}});
        LinearLayout workspace=column();TextView workspaceToggle=label("Workspace & environment  ›",12);workspaceToggle.setTextColor(AgentStyle.MUTED);content.addView(workspaceToggle);content.addView(workspace);workspace.setVisibility(View.GONE);workspaceToggle.setOnClickListener(v -> workspace.setVisibility(workspace.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        button(workspace,"Remove saved key",() -> request("PUT","/settings",json("clearKey",true),v -> {state.put("settings",v);show("Settings");}));
        workspace.addView(label("Git commit identity",20));
        EditText gitName=field(workspace,"Your name","",false),gitEmail=field(workspace,"Your email","",false);
        request("GET","/git/identity",null,v -> {if(tab.equals("Settings")){gitName.setText(((JSONObject)v).optString("name"));gitEmail.setText(((JSONObject)v).optString("email"));}});
        button(workspace,"Save Git identity",() -> request("PUT","/git/identity",json("name",gitName.getText().toString(),"email",gitEmail.getText().toString()),v -> Toast.makeText(this,"Git identity saved",Toast.LENGTH_SHORT).show()));
        content.addView(label("Files and tools run on this device. Model requests go to your provider. Closing this screen keeps servers running; use the notification to stop the environment.",14));
        button(workspace,"Stop coding environment",() -> {MobileAccess.authorize(false);stopService(new Intent(this,MobileOverlayService.class));startService(new Intent(this,RuntimeService.class).setAction("stop"));api.closeEvents();api=null;setup();});
    }
    private TextView permissionCard(LinearLayout parent,String title,String subtitle){TextView view=label(title+"\n"+subtitle,12);view.setPadding(dp(12),dp(14),dp(12),dp(14));view.setLineSpacing(dp(6),1);view.setMinHeight(dp(80));view.setBackground(AgentStyle.surface(this,AgentStyle.BACK,16,true));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(dp(3),dp(8),dp(3),0);parent.addView(view,params);return view;}
    private void permissionSelected(TextView view,boolean selected){android.graphics.drawable.GradientDrawable shape=AgentStyle.surface(this,selected?AgentStyle.PANEL:AgentStyle.BACK,16,true);if(selected)shape.setStroke(dp(1),AgentStyle.BLUE);view.setBackground(shape);view.setSelected(selected);view.setContentDescription(view.getText()+", "+(selected?"selected":"not selected"));}
    private void listen() {
        if(listening||!visible||api==null)return;listening=true;int epoch=++generation;AgentApi connection=api;
        new Thread(() -> {try{connection.listen(value -> handler.post(() -> {if(visible&&epoch==generation)event(value);}));}catch(Exception ignored){}
            finally{handler.post(() -> {if(epoch==generation){listening=false;if(visible&&api!=null)handler.postDelayed(this::listen,1000);}});}},"pocket-events").start();
    }
    private void event(JSONObject value) {
        if(value.optString("type").equals("mobile")&&mode.equals("Mobile Use")&&tab.equals("Tasks")){
            JSONObject task=value.optJSONObject("task");if(task!=null&&task.optString("id").equals(mobileTask))renderMobileTask(task);
        }
        String type=value.optString("type");if(type.equals("terminal")&&terminal!=null&&terminalId.equals(value.optString("id")))terminal.append(value.optString("data"),value.optLong("offset"));
        if(type.equals("terminal_exit")&&terminalId.equals(value.optString("id")))Toast.makeText(this,"Shell exited",Toast.LENGTH_SHORT).show();
        if(type.equals("process")){JSONObject job=value.optJSONObject("job");JSONArray list=state.optJSONArray("jobs");if(list!=null&&job!=null){boolean found=false;for(int i=0;i<list.length();i++)if(list.optJSONObject(i).optString("id").equals(job.optString("id"))){try{list.put(i,job);}catch(JSONException ignored){}found=true;break;}if(!found)list.put(job);renderJobs();}}
        if(type.equals("error"))new AlertDialog.Builder(this).setTitle("Agent error").setMessage(value.optString("error")).setPositiveButton("OK",null).show();
        if(value.optString("sessionId").equals(session)&&tab.equals("Chat"))scheduleChat();
        if(type.equals("connected")&&tab.equals("Chat"))refreshChat();
    }
    private void scheduleChat(){if(!chatScheduled){chatScheduled=true;handler.postDelayed(chatUpdate,100);}}
    private void openUrl(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){error(e);}}
    private void back(){if(drawer!=null){closeDrawer();return;}if(mode.equals("Mobile Use")){if(!tab.equals("Tasks"))show("Tasks");else finish();}else if(!tab.equals("Projects"))show("Projects");else finish();}
    @Override public void onBackPressed(){back();}
}

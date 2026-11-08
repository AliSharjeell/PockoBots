package dev.pocket.agent;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Project tools embedded in the bot shell, including a shared /workspace shell. */
final class BotWorkspace {
    private final BotsActivity app;private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout body;private String project="",directory=".",screen="",terminalId="";private int generation;private TerminalCanvas terminal;private TextView terminalStatus;
    BotWorkspace(BotsActivity app){this.app=app;}
    void resetDirectory(){directory=".";}
    void stop(){generation++;main.removeCallbacksAndMessages(null);terminal=null;}
    private int dp(int n){return app.dp(n);}
    private LinearLayout column(){return app.column();}
    private LinearLayout row(){return app.row();}
    private TextView text(String s,int n){return app.text(s,n);}
    private Button button(LinearLayout p,String s,Runnable click){return app.button(p,s,click,false);}
    private LinearLayout scrolling(){ScrollView scroll=new ScrollView(app);scroll.setClipToPadding(false);if(scope().equals("root"))scroll.setPadding(0,0,0,dp(96));LinearLayout content=column();content.setPadding(dp(4),dp(12),dp(4),dp(16));scroll.addView(content);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));return content;}
    void show(LinearLayout parent,String tab,String id){stop();body=parent;body.removeAllViews();if(!project.equals(id)){directory=".";}project=id;screen=tab;if(tab.equals("Browser"))browser();else if(tab.equals("Terminal"))terminal();else if(tab.equals("Files"))files();else if(tab.equals("Settings"))settingsHome();else if(tab.equals("BYOK"))settings();else if(tab.equals("Subscriptions"))subscriptions();else if(tab.equals("Connections"))connectionsPage();else if(tab.equals("Permissions"))app.permissionSettings(scrolling());else if(tab.equals("Servers"))servers();else if(tab.equals("Webhooks"))webhooksPage();}
    private void browser(){
        body.removeAllViews();
        String botId = project.isEmpty() ? app.getSelectedBotId() : project;
        BotBrowserView browserView = new BotBrowserView(app, botId);
        browserView.setOnHandbackCallback(() -> app.showScreen("Chat"));
        body.addView(browserView, new LinearLayout.LayoutParams(-1, -1));
    }
    private String scope(){return app.terminalScope();}
    private void glassButton(LinearLayout parent,String label,Runnable action,boolean primary){
        Button key=app.button(parent,label,action,primary);key.setTextColor(primary?0xffffffff:AgentStyle.TEXT);parent.removeView(key);parent.addView(GlassControls.wrap(key,22f,primary));
    }
    private void terminalButton(LinearLayout parent,String label,Runnable action){glassButton(parent,label,action,false);}
    private void terminal(){
        final int epoch=generation;final String key="shell-"+scope();terminalId=app.getSharedPreferences("bot-tools",0).getString(key,"");
        LinearLayout keys=row();HorizontalScrollView strip=new HorizontalScrollView(app);strip.addView(keys);body.addView(strip);
        String[] names={"Esc","Tab","Ctrl-C","Ctrl-D","Up","Down"};String[] values={"\u001b","\t","\u0003","\u0004","\u001b[A","\u001b[B"};for(int i=0;i<values.length;i++){String value=values[i];terminalButton(keys,names[i],()->send(app.json("data",value)));}
        terminalButton(keys,"Paste",()->{if(terminal!=null)terminal.paste();});terminalButton(keys,"Restart terminal",()->{if(!terminalId.isEmpty())app.requestQuiet("DELETE","/terminals/"+terminalId,null,v->{},()->{});app.getSharedPreferences("bot-tools",0).edit().remove(key).apply();show(body,"Terminal",project);});
        terminalStatus=app.muted("",12);terminalStatus.setVisibility(View.GONE);body.addView(terminalStatus);
        terminal=new TerminalCanvas(app,new TerminalCanvas.Input(){public void send(String s){BotWorkspace.this.send(app.json("data",s));}public void resize(int cols,int rows){if(epoch==generation)BotWorkspace.this.send(app.json("data","","cols",cols,"rows",rows));}});if(scope().equals("root"))terminal.setPadding(dp(12),dp(12),dp(12),dp(92));body.addView(terminal,new LinearLayout.LayoutParams(-1,0,1));
        if(terminalId.isEmpty())app.marketplaceRequest("POST","/terminals",app.json("projectId",project,"root",project.isEmpty()),v->{String id=((JSONObject)v).optString("id");app.getSharedPreferences("bot-tools",0).edit().putString(key,id).apply();if(epoch!=generation)return;terminalId=id;send(app.json("data","","cols",terminal.columns(),"rows",terminal.rows()));pollTerminal(epoch);},error->{if(epoch==generation&&terminalStatus!=null){terminalStatus.setText("Shell unavailable. Tap Restart terminal to retry.");terminalStatus.setVisibility(View.VISIBLE);}});else pollTerminal(epoch);
    }
    private void send(JSONObject data){if(!terminalId.isEmpty())app.terminalInput(terminalId,data);}
    private void pollTerminal(int epoch){
        if(epoch!=generation||terminal==null)return;
        app.marketplaceRequest("GET","/terminals/"+terminalId,null,value->{
            if(epoch!=generation||terminal==null)return;JSONObject output=(JSONObject)value;terminal.append(output.optString("output"),output.optLong("offset"));
            if(output.optBoolean("exited")){terminalStatus.setText("Shell exited. Tap Restart terminal to restart.");terminalStatus.setVisibility(View.VISIBLE);}
            else{terminalStatus.setVisibility(View.GONE);main.postDelayed(()->pollTerminal(epoch),400);}
        },error->{
            if(epoch!=generation||terminal==null)return;
            if(error!=null&&(error.contains("Terminal not found")||error.contains("Terminal is closed"))){app.getSharedPreferences("bot-tools",0).edit().remove("shell-"+scope()).apply();terminalId="";show(body,"Terminal",project);}
            else main.postDelayed(()->pollTerminal(epoch),1200);
        });
    }
    private String filesProject(){return "";}
    // Friendly labels only: existing project paths and running servers keep working.
    private String fileLabel(String path){
        int slash=path.indexOf('/');String first=slash<0?path:path.substring(0,slash);
        JSONArray projects=app.currentState().optJSONArray("projects"),bots=app.currentState().optJSONArray("bots");
        if(projects!=null&&bots!=null)for(int i=0;i<projects.length();i++){
            JSONObject project=projects.optJSONObject(i);if(project==null||!first.equals(project.optString("directory")))continue;
            for(int j=0;j<bots.length();j++){
                JSONObject bot=bots.optJSONObject(j);if(bot==null||!project.optString("id").equals(bot.optString("projectId")))continue;
                String name=bot.optString("name").trim();if(!name.isEmpty())return name+" files"+(slash<0?"":path.substring(slash));
            }
        }
        return path;
    }
    private void files(){
        final int epoch=generation;LinearLayout content=scrolling();LinearLayout bar=row();content.addView(bar);TextView path=text(directory.equals(".")?"Files":fileLabel(directory),16);path.setTypeface(AgentStyle.font(app),Typeface.BOLD);bar.addView(path,new LinearLayout.LayoutParams(0,-2,1));glassButton(bar,"Up",()->{int slash=directory.lastIndexOf('/');directory=slash<0?".":directory.substring(0,slash);show(body,"Files",project);},false);glassButton(bar,"New",this::newFile,true);

        content.addView(app.muted("Tap a file to edit. Folders open here.",12));LoadingSkeleton loadingFiles=new LoadingSkeleton(app,"files",6);content.addView(loadingFiles,new LinearLayout.LayoutParams(-1,-2));
        app.requestQuiet("GET","/files?projectId="+AgentApi.encode(filesProject())+"&path="+AgentApi.encode(directory),null,v->{if(epoch!=generation)return;UiMotion.change(content,0);content.removeView(loadingFiles);JSONArray entries=(JSONArray)v;if(entries.length()==0)content.addView(app.muted("This folder is empty.",14));for(int i=0;i<entries.length();i++){JSONObject entry=entries.optJSONObject(i);String pathName=(directory.equals(".")?"":directory+"/")+entry.optString("name");LinearLayout item=row();item.setPadding(dp(10),dp(12),dp(10),dp(12));TextView icon=text(entry.optBoolean("directory")?"DIR":"FILE",10);icon.setTextColor(AgentStyle.MUTED);item.addView(icon,new LinearLayout.LayoutParams(dp(48),-2));TextView name=text(directory.equals(".")&&entry.optBoolean("directory")?fileLabel(entry.optString("name")):entry.optString("name"),14);name.setTypeface(AgentStyle.font(app));item.addView(name,new LinearLayout.LayoutParams(0,-2,1));item.addView(app.muted(entry.optBoolean("directory")?">":entry.optLong("size")+" B",11));content.addView(item);View line=new View(app);line.setBackgroundColor(AgentStyle.BORDER);content.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));item.setOnClickListener(w->{if(entry.optBoolean("directory")){directory=pathName;show(body,"Files",project);}else app.request("GET","/file?projectId="+AgentApi.encode(filesProject())+"&path="+AgentApi.encode(pathName),null,result->editFile(pathName,((JSONObject)result).optString("content")));});}},()->{if(epoch!=generation)return;content.removeView(loadingFiles);content.addView(app.muted("Couldn't load files.",13));button(content,"Retry",()->show(body,"Files",project));});
    }
    private void newFile(){
        AgentSheet sheet=new AgentSheet(app,"New file");EditText name=app.field(sheet.content,"File name","",false);name.setHint("src/example.js");
        sheet.action("Create",()->{String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("Enter a file name");return;}sheet.dialog.dismiss();editFile((directory.equals(".")?"":directory+"/")+value,"",true);},true);sheet.action("Cancel",sheet.dialog::dismiss,false);sheet.show();
    }
    private void editFile(String path,String source){editFile(path,source,false);}
    private void editFile(String path,String source,boolean fresh){String id=filesProject();ProjectEditor.open(app,id,path,source,fresh,()->{if(screen.equals("Files"))show(body,"Files",project);});}
    private void settings(){
        final int epoch=generation;LinearLayout content=scrolling();JSONObject saved=app.currentState().optJSONObject("settings");if(saved==null)return;content.addView(text("Bring Your Own Key",22));content.addView(app.muted("One model connection for all your bots.",12));
        if(saved.optBoolean("backupAvailable")){
            Switch backup=new Switch(app);backup.setText("Use MiniMax backup");backup.setTextColor(AgentStyle.TEXT);backup.setChecked(saved.optBoolean("backupEnabled"));backup.setPadding(dp(4),dp(14),dp(4),dp(14));content.addView(backup);
            content.addView(app.muted(saved.optString("backupModel")+" · Switch off to return to your saved connection.",12));
            backup.setOnCheckedChangeListener((view,on)->{backup.setEnabled(false);app.marketplaceRequest("PUT","/settings/backup",app.json("enabled",on),value->{try{app.currentState().put("settings",value);}catch(Exception ignored){}backup.setEnabled(true);},error->{backup.setOnCheckedChangeListener(null);backup.setChecked(!on);backup.setEnabled(true);AgentSheet.message(app,"Couldn't switch model",error);show(body,"BYOK",project);});});
        }
        final int[] protocol={saved.optString("api").equals("anthropic-messages")?2:saved.optString("api").equals("openai-completions")?1:0};
        LinearLayout protocols=AgentStyle.segment(app);Button openai=button(protocols,"OpenAI Compatible",()->{}),anthropic=button(protocols,"Anthropic Compatible",()->{});
        for(Button choice:new Button[]{openai,anthropic}){choice.setTextSize(11);choice.setLayoutParams(new LinearLayout.LayoutParams(0,dp(42),1));}
        Runnable renderProtocol=()->{AgentStyle.selected(openai,protocol[0]!=2);AgentStyle.selected(anthropic,protocol[0]==2);};
        openai.setOnClickListener(v->{protocol[0]=1;renderProtocol.run();});anthropic.setOnClickListener(v->{protocol[0]=2;renderProtocol.run();});renderProtocol.run();content.addView(protocols,new LinearLayout.LayoutParams(-1,dp(48)));
        EditText base=app.field(content,"Base URL",saved.optString("baseUrl"),false),key=app.field(content,saved.optBoolean("hasKey")?"API key - saved; blank keeps it":"API key","",true),model=app.field(content,"Model name",saved.optString("model"),false);
        if(saved.optBoolean("hasKey")){key.setHint("Loading saved key...");app.marketplaceRequest("GET","/settings/key",null,value->{if(epoch==generation&&key.getText().length()==0){key.setText(((JSONObject)value).optString("apiKey"));key.setHint("API key");}},error->{if(epoch==generation)key.setHint("Saved key; leave blank to keep or enter a replacement");});}
        CheckBox reveal=new CheckBox(app);reveal.setText("Show API key");reveal.setTextColor(AgentStyle.MUTED);content.addView(reveal);reveal.setOnCheckedChangeListener((v,on)->key.setTransformationMethod(on?null:android.text.method.PasswordTransformationMethod.getInstance()));
        LinearLayout advanced=column();button(content,"Advanced model options",()->advanced.setVisibility(advanced.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));content.addView(advanced);advanced.setVisibility(View.GONE);CheckBox responses=new CheckBox(app);responses.setText("Use OpenAI Responses API");responses.setTextColor(AgentStyle.TEXT);responses.setChecked(protocol[0]==0);advanced.addView(responses);responses.setOnCheckedChangeListener((v,on)->{protocol[0]=on?0:1;renderProtocol.run();});EditText context=app.field(advanced,"Context tokens",saved.optString("contextWindow"),false),max=app.field(advanced,"Maximum output tokens",saved.optString("maxTokens"),false);
        java.util.function.Supplier<JSONObject> connection=()->app.json("executionProvider","byok","provider",protocol[0]==2?"anthropic":"openai","api",protocol[0]==2?"anthropic-messages":protocol[0]==1?"openai-completions":"openai-responses","baseUrl",base.getText().toString().trim(),"model",model.getText().toString().trim(),"apiKey",key.getText().toString(),"contextWindow",Integer.parseInt(context.getText().toString()),"maxTokens",Integer.parseInt(max.getText().toString()));
        LinearLayout actions=row();content.addView(actions);app.button(actions,"Save settings",()->{try{app.request("PUT","/settings",connection.get(),v->{app.currentState().put("settings",v);Toast.makeText(app,"Model connection saved",Toast.LENGTH_SHORT).show();});}catch(Exception e){app.error(e);}},true);button(actions,"Test API",()->{try{app.request("POST","/settings/test",connection.get(),v->Toast.makeText(app,"Connected",Toast.LENGTH_SHORT).show());}catch(Exception e){app.error(e);}});
        content.addView(app.muted("Files and tools run on your phone. Model requests go to your selected provider.",12));
    }
    private void settingsRow(LinearLayout parent,String title,String subtitle,Runnable action,int icon){
        LinearLayout item=row();item.setPadding(dp(8),dp(18),dp(8),dp(18));
        if(icon!=0){ImageView glyph=new ImageView(app);glyph.setImageResource(icon);glyph.setColorFilter(AgentStyle.MUTED);glyph.setPadding(dp(3),dp(3),dp(3),dp(3));item.addView(glyph,new LinearLayout.LayoutParams(dp(32),dp(32)));}
        LinearLayout labels=column();TextView heading=text(title,17);heading.setTypeface(AgentStyle.font(app),Typeface.BOLD);labels.addView(heading);if(!subtitle.isEmpty())labels.addView(app.muted(subtitle,12));item.addView(labels,new LinearLayout.LayoutParams(0,-2,1));item.addView(app.muted(">",20));item.setOnClickListener(v->action.run());parent.addView(item);View divider=new View(app);divider.setBackgroundColor(AgentStyle.BORDER);parent.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    private void settingsHome(){
        LinearLayout content=scrolling();
        settingsRow(content,"Bring your own subscription","Install and sign in to a vendor CLI",()->app.showSettingsPage("Subscriptions"),R.drawable.lucide_store);
        settingsRow(content,"Bring your own key","Choose your model connection",()->app.showSettingsPage("BYOK"),R.drawable.lucide_key_round);
        settingsRow(content,"Permissions & background","Review access and Android permissions",()->app.showSettingsPage("Permissions"),R.drawable.lucide_shield_check);
        if(!app.getSharedPreferences("bot-setup",0).getBoolean("seen-v2",false))app.button(content,"Continue to Home",app::completeSetup,true);
    }
    private void connectionsPage(){LinearLayout content=scrolling();settingsRow(content,"Custom MCP connections","Manage advanced connections",app::showAdvancedConnections,R.drawable.lucide_link);settingsRow(content,"Servers & sharing","Local servers and Dev Tunnels",()->app.showSettingsPage("Servers"),R.drawable.lucide_square_terminal);settingsRow(content,"External webhooks","Trigger bots and calls from external apps",()->app.showSettingsPage("Webhooks"),R.drawable.lucide_external_link);}
    private void subscriptions(){
        final int epoch=generation;
        body.removeAllViews();
        SubscriptionsController controller=new SubscriptionsController(app,terminal->login(terminal,epoch));
        body.addView(ModernSubscriptions.create(app,controller),new LinearLayout.LayoutParams(-1,-1));
        controller.load();
    }
    void login(JSONObject terminal,int epoch){
        String id=terminal.optString("id");app.getSharedPreferences("bot-tools",0).edit().putString("shell-root",id).apply();AgentSheet sheet=new AgentSheet(app,"Sign in to your subscription");TextView output=text("Starting vendor sign-in...",13);output.setTextIsSelectable(true);sheet.content.addView(output);sheet.action("Open sign-in terminal",()->{sheet.dialog.dismiss();app.openMainTerminal();},true);sheet.action("Refresh account status",()->{sheet.dialog.dismiss();show(body,"Subscriptions",project);},false);sheet.show();pollLogin(id,output,sheet,epoch,new java.util.HashSet<>());
    }
    private void pollLogin(String id,TextView output,AgentSheet sheet,int epoch,java.util.Set<String> opened){
        if(epoch!=generation||!sheet.dialog.isShowing())return;app.marketplaceRequest("GET","/terminals/"+id,null,value->{if(epoch!=generation||!sheet.dialog.isShowing())return;JSONObject terminal=(JSONObject)value;String clean=new String(android.util.Base64.decode(terminal.optString("output"),android.util.Base64.DEFAULT),java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\x1B\\[[0-?]*[ -/]*[@-~]","");output.setText(clean.substring(Math.max(0,clean.length()-5000)));java.util.regex.Matcher urls=java.util.regex.Pattern.compile("https://[a-zA-Z0-9./?&=_~%+:#-]+").matcher(clean);while(urls.find()){String url=urls.group();try{String host=Uri.parse(url).getHost();if(host!=null&&(host.equals("auth.openai.com")||host.equals("chatgpt.com")||host.equals("claude.ai")||host.equals("console.anthropic.com")||host.equals("accounts.google.com")||host.equals("opencode.ai"))&&opened.add(url)){app.openUrl(url);break;}}catch(Exception ignored){}}main.postDelayed(()->pollLogin(id,output,sheet,epoch,opened),1500);},error->{if(epoch==generation&&sheet.dialog.isShowing()){output.setText(error);main.postDelayed(()->pollLogin(id,output,sheet,epoch,opened),2500);}});
    }
    private void servers(){
        LinearLayout content=scrolling();content.addView(text("Servers & sharing",22));content.addView(app.muted("Local previews open on this device. A Dev Tunnel provides a public link for another phone.",12));EditText port=app.field(content,"Port","3000",false),command=app.field(content,"Server command","npm run dev -- --host 0.0.0.0 --port 3000",false);button(content,"Start server",()->{try{app.request("POST","/servers",app.json("projectId",project,"cwd",".","port",Integer.parseInt(port.getText().toString()),"command",command.getText().toString()),v->show(body,"Servers",project));}catch(Exception e){app.error(e);}});
        button(content,"Sign in to Dev Tunnels",()->app.request("POST","/tunnels/login",app.json(),v->show(body,"Servers",project)));CheckBox publicAccess=new CheckBox(app);publicAccess.setText("Allow anyone with the link");publicAccess.setTextColor(AgentStyle.TEXT);content.addView(publicAccess);button(content,"Create public link",()->{try{app.request("POST","/tunnels",app.json("port",Integer.parseInt(port.getText().toString()),"anonymous",publicAccess.isChecked()),v->show(body,"Servers",project));}catch(Exception e){app.error(e);}});button(content,"Refresh links & logs",()->show(body,"Servers",project));
        app.request("GET","/state",null,v->{if(!screen.equals("Servers"))return;JSONArray list=((JSONObject)v).optJSONArray("jobs");for(int i=list.length()-1;i>=0;i--){JSONObject job=list.optJSONObject(i);content.addView(text(job.optString("label")+" - "+job.optString("status"),15));if(job.optInt("port")>0){String url="http://127.0.0.1:"+job.optInt("port");button(content,"Open local preview :"+job.optInt("port"),()->app.openUrl(url));}TextView log=text(job.optString("output"),12);log.setTypeface(Typeface.MONOSPACE);log.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);log.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());log.setTextIsSelectable(true);content.addView(log);if(job.optString("status").equals("running"))button(content,"Stop process",()->app.request("POST","/processes/"+job.optString("id")+"/stop",app.json(),r->show(body,"Servers",project)));}});
    }
    private void webhooksPage(){
        final int epoch=generation;LinearLayout content=scrolling();content.addView(text("External Webhooks",22));
        content.addView(app.muted("Allow local apps and servers running on this phone (such as computer vision scripts, automation daemons, or background services) to trigger bot messages and emergency ringing calls.",13));
        app.request("GET","/webhooks/config",null,result->{
            if(epoch!=generation)return;JSONObject config=(JSONObject)result;boolean enabled=config.optBoolean("enabled",true);String url=config.optString("url","http://127.0.0.1:18878/api/webhooks/bot");String secret=config.optString("secret","");boolean allowLocal=config.optBoolean("allowLocalWithoutSecret",true);
            CheckBox toggle=new CheckBox(app);toggle.setText("Enable external webhook mode");toggle.setTextColor(AgentStyle.TEXT);toggle.setChecked(enabled);content.addView(toggle);
            toggle.setOnCheckedChangeListener((v,isChecked)->app.request("PUT","/webhooks/config",app.json("enabled",isChecked),res->show(body,"Webhooks",project)));
            View divider=new View(app);divider.setBackgroundColor(AgentStyle.BORDER);content.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
            content.addView(text("Webhook URL",16));TextView urlView=app.muted(url,13);urlView.setTextIsSelectable(true);content.addView(urlView);
            button(content,"Copy webhook URL",()->{android.content.ClipboardManager cb=(android.content.ClipboardManager)app.getSystemService(android.content.Context.CLIPBOARD_SERVICE);cb.setPrimaryClip(android.content.ClipData.newPlainText("Webhook URL",url));Toast.makeText(app,"Webhook URL copied",Toast.LENGTH_SHORT).show();});
            content.addView(text("Secret token",16));TextView secretView=app.muted(secret.isEmpty()?"(none)":secret,13);secretView.setTextIsSelectable(true);content.addView(secretView);
            LinearLayout secretActions=row();
            app.button(secretActions,"Copy secret",()->{android.content.ClipboardManager cb=(android.content.ClipboardManager)app.getSystemService(android.content.Context.CLIPBOARD_SERVICE);cb.setPrimaryClip(android.content.ClipData.newPlainText("Webhook Secret",secret));Toast.makeText(app,"Secret copied",Toast.LENGTH_SHORT).show();},false);
            app.button(secretActions,"Regenerate",()->app.request("POST","/webhooks/config/regenerate",app.json(),res->show(body,"Webhooks",project)),false);
            content.addView(secretActions);
            CheckBox localToggle=new CheckBox(app);localToggle.setText("Allow local phone apps without secret");localToggle.setTextColor(AgentStyle.TEXT);localToggle.setChecked(allowLocal);content.addView(localToggle);
            content.addView(app.muted("When enabled, scripts on this phone (127.0.0.1) do not need to send the secret token.",12));
            localToggle.setOnCheckedChangeListener((v,isChecked)->app.request("PUT","/webhooks/config",app.json("allowLocalWithoutSecret",isChecked),res->show(body,"Webhooks",project)));
            View divider2=new View(app);divider2.setBackgroundColor(AgentStyle.BORDER);content.addView(divider2,new LinearLayout.LayoutParams(-1,dp(1)));
            content.addView(text("How to trigger from external apps",16));
            content.addView(app.muted("Example: Computer Vision fire detector calling your bot:\n\nPOST "+url+"\nHeaders:\n  X-Webhook-Secret: "+secret+"\n  Content-Type: application/json\n\nJSON Body:\n{\n  \"bot\": \"any bot name\",\n  \"message\": \"Fire detected!\",\n  \"call\": true\n}\n\nSetting \"call\": true rings the emergency call screen immediately.",13));
            String curlCmd="curl -X POST "+url+" -H \"Content-Type: application/json\" "+(allowLocal?"":"-H \"X-Webhook-Secret: "+secret+"\" ")+"-d '{\"bot\": \"any bot name\", \"message\": \"Fire detected!\", \"call\": true}'";
            button(content,"Copy cURL example",()->{android.content.ClipboardManager cb=(android.content.ClipboardManager)app.getSystemService(android.content.Context.CLIPBOARD_SERVICE);cb.setPrimaryClip(android.content.ClipData.newPlainText("cURL example",curlCmd));Toast.makeText(app,"cURL command copied",Toast.LENGTH_SHORT).show();});
        });
    }
}

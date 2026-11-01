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
    void show(LinearLayout parent,String tab,String id){stop();body=parent;body.removeAllViews();if(!project.equals(id)){directory=".";}project=id;screen=tab;if(tab.equals("Terminal"))terminal();else if(tab.equals("Files"))files();else if(tab.equals("Settings"))settingsHome();else if(tab.equals("BYOK"))settings();else if(tab.equals("Subscriptions"))subscriptions();else if(tab.equals("Connections"))connectionsPage();else if(tab.equals("Permissions"))app.permissionSettings(scrolling());else if(tab.equals("Servers"))servers();}
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
    private void files(){
        final int epoch=generation;LinearLayout content=scrolling();LinearLayout bar=row();content.addView(bar);TextView path=text(directory.equals(".")?"Files":directory,16);path.setTypeface(AgentStyle.font(app),Typeface.BOLD);bar.addView(path,new LinearLayout.LayoutParams(0,-2,1));glassButton(bar,"Up",()->{int slash=directory.lastIndexOf('/');directory=slash<0?".":directory.substring(0,slash);show(body,"Files",project);},false);glassButton(bar,"New",this::newFile,true);

        content.addView(app.muted("Tap a file to edit. Folders open here.",12));LoadingSkeleton loadingFiles=new LoadingSkeleton(app,"files",6);content.addView(loadingFiles,new LinearLayout.LayoutParams(-1,-2));
        app.requestQuiet("GET","/files?projectId="+AgentApi.encode(filesProject())+"&path="+AgentApi.encode(directory),null,v->{if(epoch!=generation)return;UiMotion.change(content,0);content.removeView(loadingFiles);JSONArray entries=(JSONArray)v;if(entries.length()==0)content.addView(app.muted("This folder is empty.",14));for(int i=0;i<entries.length();i++){JSONObject entry=entries.optJSONObject(i);String pathName=(directory.equals(".")?"":directory+"/")+entry.optString("name");LinearLayout item=row();item.setPadding(dp(10),dp(12),dp(10),dp(12));TextView icon=text(entry.optBoolean("directory")?"DIR":"FILE",10);icon.setTextColor(AgentStyle.MUTED);item.addView(icon,new LinearLayout.LayoutParams(dp(48),-2));TextView name=text(entry.optString("name"),14);name.setTypeface(AgentStyle.font(app));item.addView(name,new LinearLayout.LayoutParams(0,-2,1));item.addView(app.muted(entry.optBoolean("directory")?">":entry.optLong("size")+" B",11));content.addView(item);View line=new View(app);line.setBackgroundColor(AgentStyle.BORDER);content.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));item.setOnClickListener(w->{if(entry.optBoolean("directory")){directory=pathName;show(body,"Files",project);}else app.request("GET","/file?projectId="+AgentApi.encode(filesProject())+"&path="+AgentApi.encode(pathName),null,result->editFile(pathName,((JSONObject)result).optString("content")));});}},()->{if(epoch!=generation)return;content.removeView(loadingFiles);content.addView(app.muted("Couldn't load files.",13));button(content,"Retry",()->show(body,"Files",project));});
    }
    private void newFile(){
        AgentSheet sheet=new AgentSheet(app,"New file");EditText name=app.field(sheet.content,"File name","",false);name.setHint("src/example.js");
        sheet.action("Create",()->{String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("Enter a file name");return;}sheet.dialog.dismiss();editFile((directory.equals(".")?"":directory+"/")+value,"",true);},true);sheet.action("Cancel",sheet.dialog::dismiss,false);sheet.show();
    }
    private void editFile(String path,String source){editFile(path,source,false);}
    private void editFile(String path,String source,boolean fresh){String id=filesProject();ProjectEditor.open(app,id,path,source,fresh,()->{if(screen.equals("Files"))show(body,"Files",project);});}
    private void settings(){
        final int epoch=generation;LinearLayout content=scrolling();JSONObject saved=app.currentState().optJSONObject("settings");if(saved==null)return;content.addView(text("Bring Your Own Key",22));content.addView(app.muted("One model connection for all your bots.",12));
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
    private void settingsRow(LinearLayout parent,String title,String subtitle,Runnable action){
        LinearLayout item=row();item.setPadding(dp(8),dp(20),dp(8),dp(20));LinearLayout labels=column();labels.addView(text(title,17));if(!subtitle.isEmpty())labels.addView(app.muted(subtitle,12));item.addView(labels,new LinearLayout.LayoutParams(0,-2,1));item.addView(app.muted(">",20));item.setOnClickListener(v->action.run());parent.addView(item);View divider=new View(app);divider.setBackgroundColor(AgentStyle.BORDER);parent.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    private void settingsHome(){
        LinearLayout content=scrolling();settingsRow(content,"Bring your own key","Choose your model connection",()->app.showSettingsPage("BYOK"));settingsRow(content,"Bring your own subscription","Install and sign in to a vendor CLI",()->app.showSettingsPage("Subscriptions"));settingsRow(content,"Connections & sharing","MCP tools, local previews and public links",()->app.showSettingsPage("Connections"));settingsRow(content,"Permissions & background","Review access and Android permissions",()->app.showSettingsPage("Permissions"));
        if(!app.getSharedPreferences("bot-setup",0).getBoolean("seen-v2",false))app.button(content,"Continue to Home",app::completeSetup,true);
    }
    private void connectionsPage(){LinearLayout content=scrolling();settingsRow(content,"Custom MCP connections","Manage advanced connections",app::showAdvancedConnections);settingsRow(content,"Servers & sharing","Local servers and Dev Tunnels",()->app.showSettingsPage("Servers"));}
    private void subscriptions(){
        final int epoch=generation;LinearLayout content=scrolling();content.addView(app.muted("Install a vendor CLI on this phone, then sign in with its own account flow. Vendor eligibility and usage limits apply.",13));JSONObject settings=app.currentState().optJSONObject("settings");if(settings==null)return;
        EditText model=app.field(content,"Model (default uses vendor setting)",settings.optString("providerModel","default"),false);LinearLayout catalog=column();content.addView(catalog);LoadingSkeleton loading=new LoadingSkeleton(app,"subscriptions",4);catalog.addView(loading,new LinearLayout.LayoutParams(-1,-2));
        app.marketplaceRequest("GET","/providers",null,result->{if(epoch!=generation)return;UiMotion.change(catalog,0);catalog.removeAllViews();JSONArray providers=(JSONArray)result;for(int i=0;i<providers.length();i++){
            JSONObject provider=providers.optJSONObject(i);String id=provider.optString("id");LinearLayout item=column();item.setPadding(dp(4),dp(16),dp(4),dp(16));item.addView(text(provider.optString("name"),18));catalog.addView(item);boolean installed=provider.optBoolean("installed"),signed=provider.optBoolean("authenticated");
            if(!provider.optBoolean("supported")){item.addView(app.muted(provider.optString("reason"),12));continue;}
            TextView status=app.muted(installed?"Installed "+provider.optString("version")+(signed?" - signed in":" - sign-in needed"):"Not installed on this phone",12);item.addView(status);
            JSONObject job=provider.optJSONObject("installation");if(job!=null&&job.optString("status").equals("running")){watchInstall(item,status,id,job,epoch);continue;}
            if(!installed){if(job!=null&&job.optInt("exitCode",-1)!=0)item.addView(app.muted("Last installation failed. Retry to see the output.",12));Button install=app.button(item,"Install CLI",()->{},true);install.setOnClickListener(v->{install.setVisibility(View.GONE);app.marketplaceRequest("POST","/providers/"+id+"/install",app.json(),value->watchInstall(item,status,id,(JSONObject)value,epoch),error->{install.setVisibility(View.VISIBLE);status.setText(error);});});}
            else if(!signed)app.button(item,"Sign in",()->app.marketplaceRequest("POST","/providers/"+id+"/login",app.json(),value->login((JSONObject)value,epoch),error->status.setText(error)),true);
            else app.button(item,settings.optString("executionProvider").equals(id)?"Selected":"Use this subscription",()->app.marketplaceRequest("PUT","/settings",app.json("executionProvider",id,"providerModel",model.getText().toString().trim()),value->{app.currentState().put("settings",value);show(body,"Subscriptions",project);},error->status.setText(error)),true);
        }},error->{if(epoch!=generation)return;catalog.removeAllViews();catalog.addView(app.muted(error,13));button(catalog,"Retry",()->show(body,"Subscriptions",project));});
    }
    private void watchInstall(LinearLayout item,TextView status,String id,JSONObject job,int epoch){
        if(epoch!=generation)return;status.setText("Installing on this phone...");ProgressBar progress=new ProgressBar(app,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(AgentStyle.BLUE));item.addView(progress,new LinearLayout.LayoutParams(-1,dp(8)));TextView log=text("Waiting for npm output...",12);log.setTextIsSelectable(true);item.addView(log);pollInstall(item,status,progress,log,id,job.optString("id"),epoch);
    }
    private void pollInstall(LinearLayout item,TextView status,ProgressBar progress,TextView log,String id,String jobId,int epoch){
        if(epoch!=generation)return;app.marketplaceRequest("GET","/providers/"+id+"/install/"+jobId,null,value->{if(epoch!=generation)return;JSONObject job=(JSONObject)value;String output=job.optString("output");log.setText(output.isEmpty()?"Downloading and installing CLI packages...":output.substring(Math.max(0,output.length()-6000)));if(job.optString("status").equals("running"))main.postDelayed(()->pollInstall(item,status,progress,log,id,jobId,epoch),1200);else{progress.setVisibility(View.GONE);boolean ok=job.optInt("exitCode",-1)==0;status.setText(ok?"Installation complete":"Installation failed ("+job.optString("exitCode")+")");if(ok)app.button(item,"Sign in",()->app.marketplaceRequest("POST","/providers/"+id+"/login",app.json(),result->login((JSONObject)result,epoch),error->status.setText(error)),true);else button(item,"Retry installation",()->show(body,"Subscriptions",project));}},error->{if(epoch==generation){status.setText("Reconnecting to installation...");main.postDelayed(()->pollInstall(item,status,progress,log,id,jobId,epoch),2000);}});
    }
    private void login(JSONObject terminal,int epoch){
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
}

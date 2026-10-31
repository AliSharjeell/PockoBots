package dev.pocket.agent;

import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Categorized app directory and native connection sheets. Credentials never enter the catalogue. */
final class MarketplaceUi {
    private final BotsActivity app;
    private LinearLayout body;
    private JSONArray entries;
    private String searchQuery="";
    private static final List<String> ESSENTIALS=Arrays.asList("github","supabase","google-workspace","cloudflare","neon","railway","render","vercel","brave-search","huggingface","figma");
    private final Set<String> pending=new HashSet<>();
    MarketplaceUi(BotsActivity app){this.app=app;}
    private int dp(int value){return app.dp(value);}
    private TextView text(String value,int size){return app.text(value,size);}
    private TextView muted(String value,int size){return app.muted(value,size);}
    private void request(String method,String path,JSONObject data,BotsActivity.Result done){
        app.marketplaceRequest(method,path,data,done,error->{pending.clear();if(body.isAttachedToWindow()){if(!method.equals("GET"))show(body);message("Couldn't connect",error==null?"Please try again.":error);}});
    }
    void show(LinearLayout parent){
        body=parent;parent.removeAllViews();LinearLayout list=app.column();list.setPadding(dp(4),0,dp(4),dp(12));
        parent.addView(new AgentSearchBar(app,"Search apps",searchQuery,value->{searchQuery=value;render(list);}));
        ScrollView scroll=new ScrollView(app);scroll.setClipToPadding(false);scroll.setPadding(0,0,0,dp(96));scroll.addView(list);parent.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));render(list);
        request("GET","/marketplace",null,value->{if(!parent.isAttachedToWindow())return;entries=(JSONArray)value;render(list);});
    }
    private void render(LinearLayout list){
        list.removeAllViews();if(entries==null){list.addView(muted("Loading apps...",13));return;}
        String query=searchQuery.trim().toLowerCase(Locale.ROOT);int shown=0;List<JSONObject> essentials=new ArrayList<>();
        Map<String,List<JSONObject>> sections=new LinkedHashMap<>();
        for(String category:new String[]{"productivity","coding","research","data-analytics","design","automation","finance","other"})sections.put(category,new ArrayList<>());
        for(int i=0;i<entries.length();i++){
            JSONObject item=entries.optJSONObject(i);String section=item.optString("category","other");
            String terms=item.optString("name")+" "+item.optString("description")+" "+category(section)+" "+section+(ESSENTIALS.contains(item.optString("id"))?" essentials":"");
            if(!terms.toLowerCase(Locale.ROOT).contains(query))continue;
            if(ESSENTIALS.contains(item.optString("id")))essentials.add(item);else sections.computeIfAbsent(section,key->new ArrayList<>()).add(item);
        }
        if(!essentials.isEmpty()){
            essentials.sort(Comparator.comparingInt(item->ESSENTIALS.indexOf(item.optString("id"))));TextView title=text("Essentials",18);title.setTypeface(AgentStyle.font(app),Typeface.BOLD);title.setPadding(dp(8),dp(16),dp(8),dp(12));list.addView(title);
            for(JSONObject item:essentials)appRow(list,item);shown+=essentials.size();
        }
        for(Map.Entry<String,List<JSONObject>> section:sections.entrySet()){
            if(section.getValue().isEmpty())continue;
            TextView heading=text(category(section.getKey()),18);heading.setTypeface(AgentStyle.font(app),Typeface.BOLD);heading.setPadding(dp(8),dp(24),dp(8),dp(8));list.addView(heading);
            section.getValue().sort(Comparator.comparing(item->item.optString("name"),String.CASE_INSENSITIVE_ORDER));
            for(JSONObject item:section.getValue()){appRow(list,item);shown++;}
        }
        if(shown==0)list.addView(muted("No apps found",14));
    }
    private String category(String id){switch(id){case "productivity":return "Productivity";case "coding":return "Developer tools";case "research":return "Search & research";case "data-analytics":return "Data & analytics";case "design":return "Design";case "automation":return "Automation";case "finance":return "Finance";default:return "More apps";}}
    private JSONObject connection(JSONObject item){return item.optJSONObject("connection");}
    private String status(JSONObject item){
        JSONObject c=connection(item);
        if(c!=null){if(c.optBoolean("oauth"))return c.optString("authStatus").equals("authorized")?"Signed in":"Sign in to finish";return "Added";}
        if(item.optString("availability").equals("external-host-required"))return "Needs a desktop host";
        if(item.optBoolean("oauth"))return "Browser sign-in";
        JSONArray fields=item.optJSONArray("fields");return fields!=null&&fields.length()>0?"Credentials required":"Ready to add";
    }
    private void appRow(LinearLayout list,JSONObject item){
        LinearLayout row=app.row();row.setPadding(dp(8),dp(14),dp(2),dp(14));
        ImageView icon=new ImageView(app);icon.setImageResource(MarketplaceIcons.resource(item.optString("id")));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setPadding(dp(6),dp(6),dp(6),dp(6));icon.setBackground(AgentStyle.surface(app,item.optString("id").equals("huggingface")?0xff27272a:0xffffffff,12,false));row.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout copy=app.column();copy.setPadding(dp(10),0,dp(6),0);
        TextView name=text(item.optString("name"),16);name.setTypeface(AgentStyle.font(app),Typeface.BOLD);copy.addView(name);
        TextView description=muted(item.optString("description"),12);description.setMaxLines(2);description.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(description);copy.addView(muted(status(item),11));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        boolean added=connection(item)!=null,busy=pending.contains(item.optString("id"));row.addView(addControl(item),new LinearLayout.LayoutParams(added?dp(56):dp(64),dp(36)));
        AgentIcon more=new AgentIcon(app,"more","Configure "+item.optString("name"),()->options(item));more.setBackground(null);more.setEnabled(!busy);row.addView(more,new LinearLayout.LayoutParams(dp(30),dp(44)));list.addView(row);
        View rule=new View(app);rule.setBackgroundColor(AgentStyle.BORDER);LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,dp(1));line.setMargins(dp(60),0,dp(8),0);list.addView(rule,line);
    }
    private View addControl(JSONObject item){
        boolean added=connection(item)!=null,busy=pending.contains(item.optString("id"));
        if(added){TextView value=text("Added",12);value.setGravity(Gravity.CENTER);value.setOnClickListener(v->options(item));return value;}
        Button button=new Button(app);button.setText(busy?"...":"Add");AgentStyle.button(button,true);button.setTextSize(12);button.setTypeface(AgentStyle.font(app),Typeface.NORMAL);button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(0);button.setMinimumHeight(0);button.setPadding(dp(12),0,dp(12),0);button.setEnabled(!busy);button.setContentDescription("Add "+item.optString("name"));button.setOnClickListener(v->connect(item));return button;
    }
    private void connect(JSONObject item){
        if(item.optString("availability").equals("external-host-required")){help(item);return;}
        JSONArray fields=item.optJSONArray("fields");if(connection(item)==null&&fields!=null&&fields.length()>0){configure(item);return;}
        save(item,new JSONObject(),false,"",null);
    }
    private void save(JSONObject item,JSONObject credentials,boolean apiKey,String token,Dialog form){
        String itemId=item.optString("id");if(!pending.add(itemId))return;show(body);
        JSONObject input=app.json("credentials",credentials,"useApiKey",apiKey);if(apiKey&&!token.isEmpty())try{input.put("token",token);}catch(JSONException ignored){}
        request("POST","/marketplace/"+itemId+"/install",input,result->{
            if(form!=null)form.dismiss();JSONObject c=((JSONObject)result).getJSONObject("connection");
            if(c.optBoolean("oauth"))signIn(item,c);else check(item,c);
        });
    }
    private void signIn(JSONObject item,JSONObject c){
        pending.add(item.optString("id"));show(body);
        request("POST","/connectors/"+c.optString("id")+"/oauth/start",app.json(),result->{
            pending.remove(item.optString("id"));show(body);JSONObject auth=(JSONObject)result;String url=auth.optString("authorizationUrl");
            if(auth.optString("mode").equals("github_device"))githubSignIn(item,c);else if(!url.isEmpty())app.openUrl(url);else if(auth.optString("status").equals("authorized"))check(item,c);else message("Sign-in unavailable","Open Configure to review this app's connection settings.");
        });
    }
    private void githubSignIn(JSONObject item,JSONObject connection){
        Sheet sheet=new Sheet("Sign in to GitHub");TextView status=muted("Preparing GitHub's official sign-in tool...",13),code=text("",26);code.setTypeface(AgentStyle.font(app),Typeface.BOLD);code.setGravity(Gravity.CENTER);sheet.content.addView(status);sheet.content.addView(code);ProgressBar progress=new ProgressBar(app);sheet.content.addView(progress,new LinearLayout.LayoutParams(-1,dp(32)));
        Button open=sheet.action("Copy code & open GitHub",()->{String value=code.getText().toString();if(value.isEmpty())return;android.content.ClipboardManager clipboard=(android.content.ClipboardManager)app.getSystemService(android.content.Context.CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("GitHub verification code",value));app.openUrl("https://github.com/login/device");},true);open.setEnabled(false);
        boolean[] finished={false};String path="/connectors/"+connection.optString("id")+"/oauth";sheet.dialog.setOnDismissListener(v->{if(!finished[0])app.marketplaceRequest("POST",path+"/cancel",app.json(),r->{},error->{});});sheet.action("Cancel",sheet.dialog::dismiss,false);sheet.show();pollGitHub(item,connection,sheet,status,code,progress,open,finished);
    }
    private void pollGitHub(JSONObject item,JSONObject connection,Sheet sheet,TextView status,TextView code,ProgressBar progress,Button open,boolean[] finished){
        if(!sheet.dialog.isShowing())return;app.marketplaceRequest("GET","/connectors/"+connection.optString("id")+"/oauth/status",null,value->{
            if(!sheet.dialog.isShowing())return;JSONObject auth=(JSONObject)value;String state=auth.optString("status");
            if(state.equals("authorized")){finished[0]=true;sheet.dialog.dismiss();show(body);check(item,connection);return;}
            if(state.equals("waiting")){progress.setVisibility(View.GONE);code.setText(auth.optString("userCode"));status.setText("Copy this one-time code, then authorize GitHub CLI on GitHub's own page. Return here after signing in.");open.setEnabled(true);}
            if(state.equals("failed")||state.equals("cancelled")){progress.setVisibility(View.GONE);status.setText(auth.optString("message","Sign-in cancelled. Close and retry when ready."));open.setEnabled(false);finished[0]=true;return;}
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->pollGitHub(item,connection,sheet,status,code,progress,open,finished),1500);
        },error->{if(sheet.dialog.isShowing()){status.setText("Reconnecting to sign-in...");new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->pollGitHub(item,connection,sheet,status,code,progress,open,finished),2500);}});
    }
    private void check(JSONObject item,JSONObject c){
        pending.add(item.optString("id"));show(body);
        request("POST","/connectors/"+c.optString("id")+"/test",app.json(),result->{pending.remove(item.optString("id"));show(body);JSONObject checked=(JSONObject)result;JSONArray tools=checked.optJSONArray("tools");message("Connected to "+item.optString("name"),(tools==null?0:tools.length())+" tools are available. Choose this connection in your bot's profile to let it use this app.");});
    }
    private void configure(JSONObject item){
        if(item.optString("availability").equals("external-host-required")){help(item);return;}
        Sheet sheet=new Sheet("Connect "+item.optString("name"));sheet.content.addView(muted(item.optString("description"),13));JSONObject c=connection(item);boolean added=c!=null;
        Map<String,EditText> fields=new LinkedHashMap<>();JSONArray required=item.optJSONArray("fields");
        if(required!=null)for(int i=0;i<required.length();i++){JSONObject field=required.optJSONObject(i);String id=field.optString("id");EditText input=app.field(sheet.content,field.optString("label")+(added?" (saved; leave blank to keep)":""),"",!field.optBoolean("url")&&!id.contains("client-id"));fields.put(id,input);}
        CheckBox useKey=null;EditText token=null;
        if(item.optBoolean("oauth")&&item.optString("transport").equals("http")){
            sheet.content.addView(muted("Browser sign-in opens the app's own authorization page. An API key is optional if the service supports one.",13));
            useKey=new CheckBox(app);useKey.setText("Use an API key instead");useKey.setTextColor(AgentStyle.TEXT);useKey.setTypeface(AgentStyle.font(app));useKey.setLetterSpacing(-0.06f);useKey.setChecked(added&&!c.optBoolean("oauth"));sheet.content.addView(useKey);
            token=app.field(sheet.content,"API key"+(added&&c.optBoolean("hasToken")?" (saved; leave blank to keep)":""),"",true);token.setVisibility(useKey.isChecked()?View.VISIBLE:View.GONE);EditText secret=token;useKey.setOnCheckedChangeListener((button,checked)->secret.setVisibility(checked?View.VISIBLE:View.GONE));
        }
        if(item.optString("id").equals("google-workspace"))sheet.content.addView(muted("Google requires your own OAuth client ID and secret for this local connector. See Connection help for the one-time Google setup.",12));
        CheckBox keyChoice=useKey;EditText keyInput=token;
        sheet.action(item.optBoolean("oauth")?"Continue":"Connect",()->{
            JSONObject credentials=new JSONObject();try{for(Map.Entry<String,EditText> field:fields.entrySet())credentials.put(field.getKey(),field.getValue().getText().toString());}catch(JSONException ignored){}
            save(item,credentials,keyChoice!=null&&keyChoice.isChecked(),keyInput==null?"":keyInput.getText().toString(),sheet.dialog);
        },true);sheet.action("Connection help",()->{sheet.dialog.dismiss();help(item);},false);sheet.show();
    }
    private void options(JSONObject item){
        Sheet sheet=new Sheet(item.optString("name"));sheet.content.addView(muted(status(item),13));JSONObject c=connection(item);
        if(c==null)sheet.action("Connect",()->{sheet.dialog.dismiss();connect(item);},true);
        else{
            if(c.optBoolean("oauth"))sheet.action(c.optString("authStatus").equals("authorized")?"Sign in again":"Sign in",()->{sheet.dialog.dismiss();signIn(item,c);},true);
            sheet.action("Check connection",()->{sheet.dialog.dismiss();check(item,c);},false);
        }
        sheet.action("Configure",()->{sheet.dialog.dismiss();configure(item);},false);sheet.action("Connection help",()->{sheet.dialog.dismiss();help(item);},false);
        if(c!=null)sheet.action("Remove connection",()->{sheet.dialog.dismiss();Sheet confirm=new Sheet("Remove "+item.optString("name")+"?");confirm.content.addView(muted("Bots using this connection will lose access. You can add it again later.",13));confirm.action("Remove",()->request("DELETE","/connectors/"+c.optString("id"),null,result->{confirm.dialog.dismiss();show(body);}),true);confirm.show();},false);
        sheet.show();
    }
    private void help(JSONObject item){
        Sheet sheet=new Sheet(item.optString("name"));sheet.content.addView(muted(item.optString("setup"),13));String url=item.optString("setupUrl",item.optString("website"));if(!url.isEmpty())sheet.action("Open guide",()->{sheet.dialog.dismiss();app.openUrl(url);},true);
        if(item.optString("transport").equals("stdio"))sheet.action("Open Terminal",()->{sheet.dialog.dismiss();app.openMainTerminal();},false);sheet.show();
    }
    private void message(String title,String detail){Sheet sheet=new Sheet(title);sheet.content.addView(muted(detail,13));sheet.action("Done",sheet.dialog::dismiss,true);sheet.show();}
    private final class Sheet extends AgentSheet {
        Sheet(String title){super(MarketplaceUi.this.app,title);}
    }
}

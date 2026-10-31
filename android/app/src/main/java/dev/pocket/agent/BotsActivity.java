package dev.pocket.agent;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Modern native bot shell for chat, terminals, files, model settings and connectors. */
public class BotsActivity extends androidx.activity.ComponentActivity {
    private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newFixedThreadPool(3),inputs=Executors.newSingleThreadExecutor();
    private AgentApi api;private JSONObject state=new JSONObject(),detail;private String selected="",screen="Chat",draft="",signature="";
    private LinearLayout page,body,thread,replyBanner;private FrameLayout root;private EditText composer;private ScrollView chatScroll;private boolean visible,polling,follow=true;
    private String replyQuote="";
    private BotWorkspace workspace;private ChatMarkdown markdown;private final Runnable poll=()->refresh(false);
    interface Result {void done(Object value)throws Exception;}
    int dp(int n){return AgentStyle.dp(this,n);}
    JSONObject json(Object...pairs){JSONObject value=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)value.put((String)pairs[i],pairs[i+1]);}catch(Exception e){throw new IllegalArgumentException(e);}return value;}
    JSONObject currentState(){return state;}
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    TextView text(String value,int size){TextView v=AgentStyle.text(this,value,size);v.setPadding(dp(8),dp(6),dp(8),dp(6));return v;}
    TextView muted(String value,int size){TextView v=text(value,size);v.setTextColor(AgentStyle.MUTED);return v;}
    Button button(LinearLayout parent,String title,Runnable action,boolean primary){Button v=new Button(this);v.setText(title);AgentStyle.button(v,primary);parent.addView(v);v.setOnClickListener(w->action.run());return v;}
    EditText field(LinearLayout parent,String label,String value,boolean secret){parent.addView(text(label,12));EditText v=new EditText(this);AgentStyle.input(v);v.setSingleLine(true);if(secret)v.setInputType(129);v.setText(value);parent.addView(v);return v;}
    void request(String method,String path,JSONObject data,Result done){requestQuiet(method,path,data,done,()->{} ,true);}
    void requestQuiet(String method,String path,JSONObject data,Result done,Runnable failed){requestQuiet(method,path,data,done,failed,false);}
    private void requestQuiet(String method,String path,JSONObject data,Result done,Runnable failed,boolean showError){if(api==null)return;AgentApi connection=api;io.execute(()->{try{Object value=connection.request(method,path,data);main.post(()->{if(!isDestroyed())try{done.done(value);}catch(Exception e){android.util.Log.e("POCKET_AGENT","done callback error",e);error(e);}});}catch(Exception e){android.util.Log.e("POCKET_AGENT","request error: "+method+" "+path,e);main.post(()->{if(!isDestroyed()){failed.run();if(showError)error(e);}});}});}
    void terminalInput(String id,JSONObject data){AgentApi connection=api;if(connection==null)return;inputs.execute(()->{try{connection.request("POST","/terminals/"+id+"/input",data);}catch(Exception e){main.post(()->{if(!isDestroyed())error(e);});}});}
    void error(Exception e){new AlertDialog.Builder(this).setTitle("Pocket Agent").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
    void openUrl(String url){try{Uri parsed=Uri.parse(url);if(!Arrays.asList("http","https").contains(parsed.getScheme()))throw new IllegalArgumentException("Use an HTTP or HTTPS link");startActivity(new Intent(Intent.ACTION_VIEW,parsed));}catch(Exception e){error(e);}}
    private void showLoadingScreen(String subtitle){
        FrameLayout loading=new FrameLayout(this);
        loading.setBackgroundColor(AgentStyle.BACK);
        LinearLayout center=new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        center.setPadding(dp(32),dp(32),dp(32),dp(32));
        
        BloubView logo=new BloubView(this);
        logo.setShapeId("squircle");
        logo.setColorHex("#4F46E5");
        logo.setExpressionId("heureux");
        center.addView(logo,new LinearLayout.LayoutParams(dp(84),dp(84)));
        
        TextView title=new TextView(this);
        title.setText("Pocko Bots");
        title.setTextColor(AgentStyle.TEXT);
        title.setTextSize(26);
        title.setTypeface(AgentStyle.font(this),Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-2,-2);
        tlp.topMargin=dp(20);
        center.addView(title,tlp);
        
        TextView sub=new TextView(this);
        sub.setText(subtitle!=null&&!subtitle.isEmpty()?subtitle:"Starting your local bots...");
        sub.setTextColor(AgentStyle.MUTED);
        sub.setTextSize(14);
        sub.setTypeface(AgentStyle.font(this));
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-2,-2);
        slp.topMargin=dp(6);
        slp.bottomMargin=dp(32);
        center.addView(sub,slp);
        
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        if(bar.getIndeterminateDrawable()!=null){
            bar.getIndeterminateDrawable().setColorFilter(AgentStyle.BLUE,android.graphics.PorterDuff.Mode.SRC_IN);
        }
        center.addView(bar,new LinearLayout.LayoutParams(dp(220),dp(6)));
        
        loading.addView(center,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        setContentView(loading);
    }
    @Override public void onCreate(Bundle saved){super.onCreate(saved);AgentStyle.applyTheme(this);if(!AgentStyle.dark)setTheme(R.style.AppThemeLight);androidx.core.view.WindowInsetsControllerCompat bars=new androidx.core.view.WindowInsetsControllerCompat(getWindow(),getWindow().getDecorView());bars.setAppearanceLightStatusBars(!AgentStyle.dark);bars.setAppearanceLightNavigationBars(!AgentStyle.dark);workspace=new BotWorkspace(this);markdown=new ChatMarkdown(this);selected=getIntent().getStringExtra("botId");if(selected==null||!selected.matches("[0-9a-f-]{36}"))selected="";if(saved!=null){selected=saved.getString("bot",selected);screen=saved.getString("screen","Chat");}if(!getSharedPreferences("bot-setup",0).getBoolean("seen-v2",false))screen="Setup";showLoadingScreen("Starting your local bots...");}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("bot",selected);out.putString("screen",screen);super.onSaveInstanceState(out);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);String id=intent.getStringExtra("botId");if(id!=null&&id.matches("[0-9a-f-]{36}")){if(api==null)selected=id;else open(id);}}
    @Override protected void onResume(){super.onResume();visible=true;getSharedPreferences("bot-runtime",0).edit().putBoolean("enabled",true).apply();startForegroundService(new Intent(this,RuntimeService.class).putExtra("botsEnabled",true));connect();}
    private void connect(){if(!visible)return;if(!RuntimeService.ready){showLoadingScreen(RuntimeService.status);main.postDelayed(this::connect,1000);return;}try{api=new AgentApi((String)new JSONTokener(Io.text(new File(getFilesDir(),"state/token.json"))).nextValue());refresh(true);}catch(Exception e){error(e);}}
    @Override protected void onPause(){saveDraft();visible=false;main.removeCallbacks(poll);workspace.stop();super.onPause();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);workspace.stop();io.shutdownNow();inputs.shutdownNow();super.onDestroy();}
    private void saveDraft(){if(composer!=null)getSharedPreferences("bot-chats",0).edit().putString("draft-"+selected,composer.getText().toString()).apply();}
    private void refresh(boolean rebuild){if(!visible||api==null)return;if(polling){main.postDelayed(()->refresh(rebuild),100);return;}polling=true;String id=selected;io.execute(()->{try{JSONObject next=api.get("/state"),nextDetail=id.isEmpty()?null:api.get("/bots/"+id);main.post(()->{polling=false;if(!visible||!id.equals(selected))return;state=next;detail=nextDetail;if(!next.optBoolean("botEngineEnabled"))request("PUT","/bots/engine",json("enabled",true),v->{});String changed=(id.isEmpty()?next.optJSONArray("bots"):nextDetail).toString()+next.optJSONArray("groups");if(rebuild||body==null){shell();signature="";}if(!signature.equals(changed)){signature=changed;if(screen.equals("Chat")){if(selected.isEmpty())inbox();else renderThread();}}main.postDelayed(poll,1000);});}catch(Exception e){main.post(()->{polling=false;if(visible){main.postDelayed(poll,2000);if(rebuild)error(e);}});}});}
    private void open(String id){saveDraft();replyQuote="";selected=id;screen="Chat";draft=getSharedPreferences("bot-chats",0).getString("draft-"+id,"");detail=null;signature="";composer=null;workspace.stop();main.removeCallbacks(poll);refresh(true);}
    void showSetup(){showScreen("Setup");}
    private void showScreen(String next){if(page!=null){getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(page.getWindowToken(),0);page.clearFocus();}saveDraft();screen=next;signature="";shell();if(screen.equals("Chat")){if(selected.isEmpty())inbox();else renderThread();}}
    private void shell(){
        workspace.stop();page=column();page.setBackgroundColor(AgentStyle.BACK);page.setPadding(dp(12),dp(8),dp(12),dp(8));
        page.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(12)+insets.getSystemWindowInsetLeft(),dp(8)+insets.getSystemWindowInsetTop(),dp(12)+insets.getSystemWindowInsetRight(),dp(8)+insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        
        FrameLayout header=new FrameLayout(this);
        boolean botHeader=!selected.isEmpty()&&Arrays.asList("Chat","Terminal","Files").contains(screen)&&detail!=null&&detail.optJSONObject("bot")!=null;
        if(!selected.isEmpty()){
            AgentIcon back=new AgentIcon(this,"back","Back to messages",()->open(""));
            header.addView(back,new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.START|Gravity.CENTER_VERTICAL));
        }
        if(botHeader){
            JSONObject bot=detail.optJSONObject("bot");
            LinearLayout identity=row();identity.setPadding(dp(12),dp(7),dp(12),dp(7));
            identity.setBackground(AgentStyle.surface(this,AgentStyle.PANEL,24,true));
            identity.setContentDescription("Bot profile");identity.setFocusable(true);identity.setOnClickListener(v->editBot(bot));
            View avatar;
            if(bot.optString("avatar","").startsWith("bloub:")){
                String[] parts=bot.optString("avatar").split(":");BloubView bloub=new BloubView(this);
                if(parts.length>1)bloub.setShapeId(parts[1]);bloub.setColorHex(bot.optString("accent","#4F46E5"));
                if(parts.length>2)bloub.setExpressionId(parts[2]);avatar=bloub;
            }else{
                TextView chip=text(bot.optString("avatar",bot.optString("name","?").substring(0,1).toUpperCase(Locale.ROOT)),13);
                chip.setGravity(Gravity.CENTER);chip.setPadding(0,0,0,0);chip.setTextColor(0xffffffff);
                chip.setBackground(AgentStyle.surface(this,accent(bot.optString("accent")),10,false));avatar=chip;
            }
            identity.addView(avatar,new LinearLayout.LayoutParams(dp(28),dp(28)));
            TextView name=text(bot.optString("name"),17);name.setTypeface(AgentStyle.font(this),Typeface.BOLD);
            name.setSingleLine(true);name.setMaxWidth(Math.max(dp(60),getResources().getDisplayMetrics().widthPixels-dp(180)));name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            identity.addView(name);header.addView(identity,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        }else{
            String title=screen.equals("Chat")?"PockoBots":screen.equals("Setup")?"Settings":screen.equals("Servers")?"Servers & sharing":screen.equals("Terminal")?"Main terminal":screen;
            TextView name=text(title,screen.equals("Chat")?24:19);
            name.setTypeface(Build.VERSION.SDK_INT>=28?Typeface.create(AgentStyle.font(this),screen.equals("Chat")?900:700,false):Typeface.create(AgentStyle.font(this),Typeface.BOLD));
            FrameLayout.LayoutParams titlePosition=new FrameLayout.LayoutParams(-2,-2,Gravity.START|Gravity.CENTER_VERTICAL);
            titlePosition.leftMargin=selected.isEmpty()?dp(4):dp(50);header.addView(name,titlePosition);
            if(selected.isEmpty()&&screen.equals("Chat")){
                AgentIcon create=new AgentIcon(this,"plus","New conversation",this::newConversation);
                header.addView(create,new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.END|Gravity.CENTER_VERTICAL));
            }
        }
        page.addView(header,new LinearLayout.LayoutParams(-1,dp(56)));

        if(!selected.isEmpty()&&Arrays.asList("Chat","Terminal","Files").contains(screen)){
            LinearLayout tabs=AgentStyle.segment(this);
            for(String tab:new String[]{"Chat","Terminal","Files"}){
                Button choice=button(tabs,tab,()->showScreen(tab),false);
                choice.setLayoutParams(new LinearLayout.LayoutParams(0,dp(36),1));
                AgentStyle.selected(choice,screen.equals(tab));
            }
            LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,dp(44));
            tlp.setMargins(0,dp(4),0,dp(6));
            page.addView(tabs,tlp);
        }

        body=column();page.addView(body,new LinearLayout.LayoutParams(-1,0,1));composer=null;thread=null;root=new FrameLayout(this);root.addView(page);setContentView(root);
        if(screen.equals("Setup")){setup();return;}
        if(!screen.equals("Chat")){if(screen.equals("Marketplace"))marketplace();else workspace.show(body,screen,selected.isEmpty()?"":detail.optJSONObject("bot").optString("projectId"));dock();return;}
        if(selected.isEmpty()){inbox();dock();return;}
        
        chatScroll=new ScrollView(this);thread=column();thread.setPadding(0,dp(4),0,dp(8));chatScroll.addView(thread);body.addView(chatScroll,new LinearLayout.LayoutParams(-1,0,1));
        chatScroll.setOnScrollChangeListener((v,x,y,ox,oy)->follow=y+chatScroll.getHeight()>=thread.getHeight()-dp(60));
        
        // Cupertino Telegram ChatBottomBar matching 6xingyv/compound
        LinearLayout bottomBarContainer=column();
        bottomBarContainer.setPadding(0,dp(2),0,dp(4));

        replyBanner=row();replyBanner.setVisibility(View.GONE);
        replyBanner.setPadding(dp(12),dp(6),dp(12),dp(6));
        replyBanner.setBackground(AgentStyle.surface(this,AgentStyle.PANEL,16,true));
        bottomBarContainer.addView(replyBanner,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout bottomRow=row();
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomRow.setPadding(0,dp(4),0,dp(2));

        // Left: Cupertino frosted '+' action button
        AgentIcon attach=new AgentIcon(this,"plus","Conversation actions",()->{
            CompoundMessaging.showAttachmentSheet(this,prompt->{
                if(composer!=null)composer.setText(prompt);
            },this::selectConnections,()->{
                new AlertDialog.Builder(this).setTitle("Clear messages").setMessage("Clear message history for this bot?").setPositiveButton("Clear",(d,w)->{
                    request("DELETE","/bots/"+selected+"/messages",json(),r->refresh(false));
                }).setNegativeButton("Cancel",null).show();
            });
        });
        attach.setBackground(AgentStyle.surface(this,AgentStyle.PANEL,22,true));
        bottomRow.addView(attach,new LinearLayout.LayoutParams(dp(42),dp(42)));

        View space1=new View(this);
        bottomRow.addView(space1,new LinearLayout.LayoutParams(dp(8),dp(1)));

        // Center: Smooth Cupertino Capsule Container
        LinearLayout inputCapsule=row();
        inputCapsule.setGravity(Gravity.CENTER_VERTICAL);
        inputCapsule.setPadding(dp(14),dp(4),dp(12),dp(4));
        inputCapsule.setBackground(AgentStyle.surface(this,AgentStyle.PANEL,24,true));

        composer=new EditText(this);
        AgentStyle.input(composer);
        composer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        String botHint=detail!=null&&detail.optJSONObject("bot")!=null?detail.optJSONObject("bot").optString("name"):"bot";
        composer.setHint("Message "+botHint+"...");
        composer.setMaxLines(5);
        composer.setText(getSharedPreferences("bot-chats",0).getString("draft-"+selected,draft));
        inputCapsule.addView(composer,new LinearLayout.LayoutParams(0,-2,1));
        bottomRow.addView(inputCapsule,new LinearLayout.LayoutParams(0,-2,1));

        View space2=new View(this);
        bottomRow.addView(space2,new LinearLayout.LayoutParams(dp(8),dp(1)));

        // Right: Cupertino Telegram Send Button
        AgentIcon send=new AgentIcon(this,"send","Send to bot",this::send);
        send.setBackground(AgentStyle.surface(this,0xFF007AFF,22,false));
        bottomRow.addView(send,new LinearLayout.LayoutParams(dp(42),dp(42)));

        bottomBarContainer.addView(bottomRow,new LinearLayout.LayoutParams(-1,-2));
        page.addView(bottomBarContainer);
    }
    private void dock(){
        int tab=screen.equals("Marketplace")?1:screen.equals("Terminal")&&selected.isEmpty()?2:screen.equals("Settings")?3:0;
        page.addView(GlassDock.create(this,AgentStyle.dark,tab,index->{
            if(index==0)open("");
            if(index==1)showScreen("Marketplace");
            if(index==2){saveDraft();composer=null;selected="";detail=null;showScreen("Terminal");}
            if(index==3)showScreen("Settings");
        }),new LinearLayout.LayoutParams(-1,dp(70)));
    }
    private void newConversation(){
        new AlertDialog.Builder(this).setTitle("New conversation").setItems(new String[]{"Create bot","Create group"},(d,n)->{
            if(n==0)editBot(null);else createGroup();
        }).show();
    }
    void chooseAppearance(){appearance();}
    void openMainTerminal(){saveDraft();composer=null;selected="";detail=null;showScreen("Terminal");}
    private void appearance(){
        String[] choices={"System","Light","Dark"};
        new AlertDialog.Builder(this).setTitle("Appearance").setSingleChoiceItems(choices,Arrays.asList("system","light","dark").indexOf(getSharedPreferences("appearance",0).getString("theme","system")),(d,n)->{
            getSharedPreferences("appearance",0).edit().putString("theme",choices[n].toLowerCase(Locale.ROOT)).apply();
            d.dismiss();recreate();
        }).setNegativeButton("Cancel",null).show();
    }
    static class ConnectorBrand {
        final int iconBg, iconColor, tagBg, tagColor;
        final String monogram, category;
        ConnectorBrand(int iconBg, int iconColor, String monogram, String category, int tagBg, int tagColor){
            this.iconBg=iconBg; this.iconColor=iconColor; this.monogram=monogram;
            this.category=category; this.tagBg=tagBg; this.tagColor=tagColor;
        }
        static ConnectorBrand resolve(String name, String id){
            String lower = (name + " " + id).toLowerCase(Locale.ROOT);
            if(lower.contains("git")) return new ConnectorBrand(0xFF24292E, 0xFFF0F6FC, "🐙", "CODING", 0x33388BFD, 0xFF388BFD);
            if(lower.contains("brave")) return new ConnectorBrand(0x33FB542B, 0xFFFB542B, "🦁", "SEARCH", 0x33FB542B, 0xFFFB542B);
            if(lower.contains("postgres")||lower.contains("sql")||lower.contains("database")) return new ConnectorBrand(0x33336791, 0xFF336791, "🐘", "DATABASE", 0x33336791, 0xFF336791);
            if(lower.contains("cloudflare")) return new ConnectorBrand(0x33F38020, 0xFFF38020, "⚡", "DEVOPS", 0x33F38020, 0xFFF38020);
            if(lower.contains("airtable")) return new ConnectorBrand(0x3318BFFF, 0xFF18BFFF, "▦", "DATA", 0x3318BFFF, 0xFF18BFFF);
            if(lower.contains("canva")) return new ConnectorBrand(0x3300C4CC, 0xFF00C4CC, "🎨", "DESIGN", 0x3300C4CC, 0xFF00C4CC);
            if(lower.contains("atlassian")||lower.contains("jira")) return new ConnectorBrand(0x330052CC, 0xFF0052CC, "❖", "MANAGEMENT", 0x332684FF, 0xFF2684FF);
            if(lower.contains("slack")) return new ConnectorBrand(0x334A154B, 0xFFE01E5A, "💬", "MESSAGING", 0x33E01E5A, 0xFFE01E5A);
            if(lower.contains("browser")||lower.contains("puppeteer")) return new ConnectorBrand(0x334285F4, 0xFF4285F4, "🌐", "BROWSER", 0x334285F4, 0xFF4285F4);
            if(lower.contains("apple")||lower.contains("icloud")) return new ConnectorBrand(0x33A2AAAD, 0xFFA2AAAD, "🍎", "ECOSYSTEM", 0x33A2AAAD, 0xFFA2AAAD);
            if(lower.contains("aave")) return new ConnectorBrand(0x33B6509E, 0xFFB6509E, "👻", "FINANCE", 0x33B6509E, 0xFFB6509E);
            if(lower.contains("composio")||lower.contains("cobrain")) return new ConnectorBrand(0x338B5CF6, 0xFF8B5CF6, "✦", "AUTOMATION", 0x338B5CF6, 0xFF8B5CF6);
            if(lower.contains("google")||lower.contains("drive")) return new ConnectorBrand(0x3334A853, 0xFF34A853, "📁", "STORAGE", 0x3334A853, 0xFF34A853);
            if(lower.contains("linear")) return new ConnectorBrand(0x335E6AD2, 0xFF5E6AD2, "✓", "TRACKING", 0x335E6AD2, 0xFF5E6AD2);
            if(lower.contains("notion")) return new ConnectorBrand(0x33FFFFFF, 0xFFFFFFFF, "📝", "DOCS", 0x3394A3B8, 0xFF94A3B8);
            return new ConnectorBrand(0x334F46E5, 0xFF4F46E5, "🧩", "EXTENSION", 0x334F46E5, 0xFF4F46E5);
        }
    }
    private void marketplace(){
        body.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = column();
        scroll.addView(list);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        // Header with accessibility description
        LinearLayout hero = column();
        AgentStyle.card(hero);
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.setMargins(0, dp(4), 0, dp(10));
        hero.setLayoutParams(hlp);

        TextView title = text("MCP marketplace", 20);
        title.setContentDescription("MCP marketplace");
        title.setTypeface(AgentStyle.font(this), Typeface.BOLD);
        hero.addView(title);
        hero.addView(muted("Connect official Model Context Protocol tools to your bots", 12));

        LinearLayout heroActions = row();
        heroActions.setPadding(0, dp(10), 0, 0);
        button(heroActions, "Installed connections", this::connections, false);
        hero.addView(heroActions);
        list.addView(hero);

        request("GET","/marketplace",null,value->{
            JSONArray entries=value instanceof JSONArray?(JSONArray)value:((JSONObject)value).optJSONArray("plugins");
            if(entries==null) return;
            for(int i=0;i<entries.length();i++){
                JSONObject item=entries.optJSONObject(i);
                ConnectorBrand brand = ConnectorBrand.resolve(item.optString("name"), item.optString("id"));

                LinearLayout card=column();
                AgentStyle.card(card);
                card.setPadding(dp(14),dp(12),dp(14),dp(12));
                LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-1,-2);
                clp.setMargins(0,dp(4),0,dp(6));
                card.setLayoutParams(clp);

                LinearLayout cardRow=row();

                // Brand Icon Squircle Container
                FrameLayout iconBox=new FrameLayout(this);
                iconBox.setBackground(AgentStyle.surface(this, brand.iconBg, 14, true));
                TextView iconText=new TextView(this);
                iconText.setText(brand.monogram);
                iconText.setTextSize(20);
                iconText.setGravity(Gravity.CENTER);
                iconBox.addView(iconText, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
                cardRow.addView(iconBox, new LinearLayout.LayoutParams(dp(44), dp(44)));

                LinearLayout details=column();
                details.setPadding(dp(12), 0, dp(8), 0);

                LinearLayout titleRow=row();
                TextView nameView=text(item.optString("name"), 15);
                nameView.setTypeface(AgentStyle.font(this), Typeface.BOLD);
                titleRow.addView(nameView, new LinearLayout.LayoutParams(0, -2, 1));

                TextView catBadge=text(brand.category, 9);
                catBadge.setTypeface(AgentStyle.font(this), Typeface.BOLD);
                catBadge.setTextColor(brand.tagColor);
                catBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
                catBadge.setBackground(AgentStyle.surface(this, brand.tagBg, 6, false));
                titleRow.addView(catBadge);
                details.addView(titleRow);

                TextView desc=muted(item.optString("description"), 12);
                desc.setMaxLines(2);
                desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
                details.addView(desc);

                cardRow.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

                Button setupBtn=button(cardRow, "Set up", () -> installPlugin(item), false);
                setupBtn.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(36)));
                card.addView(cardRow);
                list.addView(card);
            }
        });
    }
    private void installPlugin(JSONObject plugin){
        LinearLayout form=column();form.setPadding(dp(16),dp(8),dp(16),dp(16));
        form.addView(muted(plugin.optString("description"),13));
        Map<String,EditText> fields=new LinkedHashMap<>();JSONArray required=plugin.optJSONArray("fields");
        if(required!=null)for(int i=0;i<required.length();i++){
            JSONObject field=required.optJSONObject(i);
            fields.put(field.optString("id"),field(form,field.optString("label"),"",!field.optString("id").toLowerCase(Locale.ROOT).contains("url")));
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Connect "+plugin.optString("name")).setView(scroll).setPositiveButton("Configure",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{
            JSONObject credentials=new JSONObject();try{for(Map.Entry<String,EditText> e:fields.entrySet())credentials.put(e.getKey(),e.getValue().getText().toString());}catch(Exception e){error(e);return;}
            request("POST","/marketplace/"+plugin.optString("id")+"/install",json("credentials",credentials),result->{
                dialog.dismiss();JSONObject configured=(JSONObject)result;
                String setup=configured.opt("setup")==null?"Configured. Test the connection, then select its tools for your bots.":String.valueOf(configured.opt("setup"));
                new AlertDialog.Builder(this).setTitle("Connection configured").setMessage(setup).setPositiveButton("Connections",(d,n)->connections()).setNeutralButton("Main terminal",(d,n)->openMainTerminal()).show();
            });
        }));dialog.show();
    }
    private void createGroup(){
        LinearLayout form=column();EditText name=field(form,"Group name","",false);
        JSONArray bots=state.optJSONArray("bots");Map<String,CheckBox> selectedBots=new LinkedHashMap<>();
        if(bots!=null)for(int i=0;i<bots.length();i++){
            JSONObject bot=bots.optJSONObject(i);
            CheckBox check=new CheckBox(this);check.setText(bot.optString("name"));check.setTextColor(AgentStyle.TEXT);form.addView(check);
            selectedBots.put(bot.optString("id"),check);
        }
        new AlertDialog.Builder(this).setTitle("Bot group").setView(form).setPositiveButton("Create",(d,n)->{
            JSONArray ids=new JSONArray();for(Map.Entry<String,CheckBox> e:selectedBots.entrySet())if(e.getValue().isChecked())ids.put(e.getKey());
            request("POST","/groups",json("name",name.getText().toString(),"botIds",ids),v->openGroup(((JSONObject)v).optString("id")));
        }).setNegativeButton("Cancel",null).show();
    }
    private void openGroup(String id){
        request("GET","/groups/"+id,null,v->{
            JSONObject group=(JSONObject)v;LinearLayout contents=column();
            ScrollView scroll=new ScrollView(this);LinearLayout messages=column();scroll.addView(messages);
            contents.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            JSONArray history=group.optJSONArray("messages");
            if(history!=null)for(int i=0;i<history.length();i++){
                JSONObject m=history.optJSONObject(i);
                messages.addView(text(m.optString("botName",m.optString("role"))+"\n"+m.optString("text"),14));
            }
            EditText input=new EditText(this);AgentStyle.input(input);input.setHint("Message the group...");
            contents.addView(input);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle(group.optJSONObject("group").optString("name","Bot group")).setView(contents).setPositiveButton("Send",null).setNegativeButton("Close",null).create();
            dialog.setOnShowListener(d->{
                dialog.getWindow().setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*.8));
                Button closeBtn=dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                if(closeBtn!=null){
                    closeBtn.setAllCaps(false);
                    closeBtn.setText("Close");
                    closeBtn.setContentDescription("Close");
                }
                dialog.getButton(-1).setOnClickListener(w->request("POST","/groups/"+id+"/messages",json("text",input.getText().toString()),r->{dialog.dismiss();openGroup(id);}));
            });
            dialog.show();
            final Runnable[] pollGroup={null};
            pollGroup[0]=()->{
                if(!dialog.isShowing()||!visible)return;
                requestQuiet("GET","/groups/"+id,null,next->{
                    if(!dialog.isShowing())return;
                    messages.removeAllViews();JSONArray entries=((JSONObject)next).optJSONArray("messages");
                    if(entries!=null)for(int k=0;k<entries.length();k++){
                        JSONObject message=entries.optJSONObject(k);messages.addView(muted(message.optString("botName",message.optString("role")),11));
                        TextView text=text("",14);markdown.set(text,message.optString("text"));messages.addView(text);
                    }
                    main.postDelayed(pollGroup[0],1000);
                },()->{});
            };main.postDelayed(pollGroup[0],1000);
        });
    }
    private void inbox(){
        body.removeAllViews();ScrollView scroll=new ScrollView(this);LinearLayout list=column();scroll.addView(list);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        
        // Group chips / list
        requestQuiet("GET","/groups",null,g->{
            JSONArray groups=g instanceof JSONArray?(JSONArray)g:((JSONObject)g).optJSONArray("groups");
            if(groups!=null&&groups.length()>0){
                for(int n=0;n<groups.length();n++){
                    JSONObject group=groups.optJSONObject(n);
                    button(list,"Group: "+group.optString("name"),()->openGroup(group.optString("id")),false);
                }
            }
        },()->{});

        JSONArray bots=state.optJSONArray("bots");
        if(bots==null||bots.length()==0){
            LinearLayout hero=column();AgentStyle.card(hero);hero.setPadding(dp(18),dp(20),dp(18),dp(20));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(12),0,dp(12));hero.setLayoutParams(lp);
            hero.addView(text("Meet your local bots",22));
            hero.addView(muted("Give a bot a name and instructions. Every bot can code, browse and use your phone directly on this device.",14));
            hero.addView(muted("Tap + to create a bot or group.",13));
            list.addView(hero);
            return;
        }

        // Flat conversations separated by inset rules
        for(int i=0;i<bots.length();i++){
            JSONObject bot=bots.optJSONObject(i);
            LinearLayout card=column();
            card.setPadding(dp(8),dp(18),dp(8),dp(18));
            card.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
            android.util.TypedValue ripple=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackground,ripple,true);card.setBackgroundResource(ripple.resourceId);
            
            LinearLayout item=row();
            View avatarView;
            if(bot.optString("avatar","").startsWith("bloub:")){
                String[] parts=bot.optString("avatar").split(":");
                BloubView bloub=new BloubView(this);
                if(parts.length>1)bloub.setShapeId(parts[1]);
                bloub.setColorHex(bot.optString("accent","#4F46E5"));
                if(parts.length>2)bloub.setExpressionId(parts[2]);
                avatarView=bloub;
            }else{
                TextView avatar=text(bot.optString("avatar").isEmpty()?bot.optString("name","?").substring(0,1).toUpperCase(Locale.ROOT):bot.optString("avatar"),18);
                avatar.setGravity(Gravity.CENTER);avatar.setTextColor(0xffffffff);
                avatar.setBackground(AgentStyle.surface(this,accent(bot.optString("accent")),16,false));
                avatarView=avatar;
            }
            item.addView(avatarView,new LinearLayout.LayoutParams(dp(44),dp(44)));
            
            LinearLayout copy=column();copy.setPadding(dp(12),0,0,0);
            LinearLayout title=row();
            TextView name=text(bot.optString("name"),16);name.setTypeface(AgentStyle.font(this),Typeface.BOLD);
            title.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            
            copy.addView(title);
            
            JSONObject last=bot.optJSONObject("lastMessage");
            TextView preview=muted(last==null?bot.optString("role"):last.optString("text"),12);
            preview.setMaxLines(2);preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
            copy.addView(preview);
            if(bot.optInt("queued")>0)copy.addView(muted(bot.optInt("queued")+" queued",10));
            item.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            card.addView(item);
            card.setOnClickListener(v->open(bot.optString("id")));
            list.addView(card);
            View divider=new View(this);divider.setBackgroundColor(AgentStyle.BORDER);
            LinearLayout.LayoutParams rule=new LinearLayout.LayoutParams(-1,dp(1));rule.setMargins(dp(68),0,dp(8),0);list.addView(divider,rule);
        }
    }
    public void copyConversation(){
        if(detail==null)return;JSONArray messages=detail.optJSONArray("messages");StringBuilder text=new StringBuilder();if(messages!=null)for(int i=0;i<messages.length();i++){JSONObject message=messages.optJSONObject(i);if(message!=null)text.append(message.optString("role")).append(": ").append(message.optString("text")).append("\n\n");}
        getSystemService(android.content.ClipboardManager.class).setPrimaryClip(android.content.ClipData.newPlainText("Bot conversation",text.toString()));Toast.makeText(this,"Conversation copied",Toast.LENGTH_SHORT).show();
    }
    private void send(){
        String value=composer.getText().toString().trim();if(value.isEmpty())return;
        if(state.optJSONObject("settings").optString("executionProvider","byok").equals("byok")&&!state.optJSONObject("settings").optBoolean("hasKey")){
            new AlertDialog.Builder(this).setTitle("Connect your model").setMessage("Save your BYOK connection before sending tasks. Your draft will be kept.").setPositiveButton("Open settings",(d,n)->showScreen("Settings")).setNegativeButton("Cancel",null).show();
            return;
        }
        String id=selected;
        request("POST","/bots/"+id+"/messages",json("text",replyQuote.isEmpty()?value:replyQuote+"\n\n"+value),v->{
            replyQuote="";if(replyBanner!=null){replyBanner.setVisibility(View.GONE);replyBanner.removeAllViews();}
            if(id.equals(selected)&&composer!=null)composer.setText("");
            draft="";getSharedPreferences("bot-chats",0).edit().remove("draft-"+id).apply();
            request("POST","/bots/"+id+"/resume",json(),r->refresh(false));
        });
    }
    private void setReplyQuote(String author, String snippet){
        if(replyBanner==null)return;
        replyQuote="> "+author+": "+snippet.substring(0,Math.min(800,snippet.length())).replace("\n","\n> ");
        replyBanner.removeAllViews();
        replyBanner.setVisibility(View.VISIBLE);
        View bar=new View(this);bar.setBackgroundColor(0xFF007AFF);
        replyBanner.addView(bar,new LinearLayout.LayoutParams(dp(3),-1));
        LinearLayout textCol=column();textCol.setPadding(dp(8),0,dp(8),0);
        TextView title=text("Replying to "+author,12);title.setTypeface(AgentStyle.font(this),Typeface.BOLD);
        textCol.addView(title);
        TextView quote=muted(snippet.replace("\n"," "),11);quote.setMaxLines(1);
        textCol.addView(quote);
        replyBanner.addView(textCol,new LinearLayout.LayoutParams(0,-2,1));
        AgentIcon close=new AgentIcon(this,"close","Cancel reply",()->{
            replyQuote="";replyBanner.setVisibility(View.GONE);
            replyBanner.removeAllViews();
        });
        replyBanner.addView(close,new LinearLayout.LayoutParams(dp(28),dp(28)));
    }
    private void bubble(String value,boolean user){
        bubble(value,user,true,true);
    }
    private void bubble(String value,boolean user,boolean isFirst,boolean isLast){bubble(value,user,isFirst,isLast,System.currentTimeMillis());}
    private void bubble(String value,boolean user,boolean isFirst,boolean isLast,long time){
        if(!user&&ChatCards.render(this,thread,value,markdown))return;
        String botName=detail!=null&&detail.optJSONObject("bot")!=null?detail.optJSONObject("bot").optString("name"):"";
        String botAvatar=detail!=null&&detail.optJSONObject("bot")!=null?detail.optJSONObject("bot").optString("avatar",""):"";
        String botAccent=detail!=null&&detail.optJSONObject("bot")!=null?detail.optJSONObject("bot").optString("accent","#4F46E5"):"#4F46E5";
        CompoundMessaging.renderBubble(this,thread,value,user,isFirst,isLast,botName,botAvatar,botAccent,markdown,time,(author,snippet)->{
            setReplyQuote(author,snippet);
        });
    }
    private void renderThread(){
        if(thread==null||detail==null||!screen.equals("Chat"))return;
        int old=chatScroll.getScrollY();boolean scroll=follow;thread.removeAllViews();
        JSONObject bot=detail.optJSONObject("bot");JSONArray messages=detail.optJSONArray("messages");
        for(int i=0;i<messages.length();i++){
            JSONObject message=messages.optJSONObject(i);
            if(message.optString("role").equals("system")){
                thread.addView(muted(message.optString("text"),11));
            }else{
                boolean isUser=message.optString("role").equals("user");
                boolean isFirst=i==0||!messages.optJSONObject(i-1).optString("role").equals(message.optString("role"));
                boolean isLast=i==messages.length()-1||!messages.optJSONObject(i+1).optString("role").equals(message.optString("role"));
                bubble(message.optString("text"),isUser,isFirst,isLast,message.optLong("time"));
            }
        }
        JSONObject session=detail.optJSONObject("session");
        if(session!=null){
            JSONObject streaming=session.optJSONObject("streaming");
            if(streaming!=null){
                StringBuilder output=new StringBuilder();JSONArray parts=streaming.optJSONArray("content");
                if(parts!=null)for(int i=0;i<parts.length();i++)if(parts.optJSONObject(i).optString("type").equals("text"))output.append(parts.optJSONObject(i).optString("text"));
                if(output.length()>0)bubble(output.toString(),false,true,true);
            }
            JSONArray approvals=session.optJSONArray("approvals");
            if(approvals!=null)for(int i=0;i<approvals.length();i++){
                JSONObject approval=approvals.optJSONObject(i);
                thread.addView(text("Allow "+approval.optString("tool")+"?\n"+approval.opt("args"),12));
                LinearLayout actions=row();thread.addView(actions);
                for(boolean allow:new boolean[]{true,false})button(actions,allow?"Allow":"Deny",()->request("POST","/approvals/"+approval.optString("id"),json("allow",allow),v->refresh(false)),allow);
            }
        }
        JSONObject phone=detail.optJSONObject("mobileTask");
        if(phone!=null&&phone.optJSONObject("question")!=null){
            JSONObject question=phone.optJSONObject("question");bubble(question.optString("text"),false,true,true);
            EditText reply=new EditText(this);AgentStyle.input(reply);reply.setHint("Reply to your bot...");thread.addView(reply);
            button(thread,"Send reply",()->{
                String answer=reply.getText().toString().trim();if(answer.isEmpty())return;
                startForegroundService(new Intent(this,MobileOverlayService.class).putExtra("taskId",phone.optString("id")).putExtra("expand",true));
                main.postDelayed(()->request("POST","/mobile/tasks/"+phone.optString("id")+"/answer",json("questionId",question.optString("id"),"text",answer),v->refresh(false)),400);
            },true);
        }
        JSONArray jobs=detail.optJSONArray("jobs");boolean busy=false,paused=false;
        for(int i=Math.max(0,jobs.length()-8);i<jobs.length();i++){
            JSONObject job=jobs.optJSONObject(i);String status=job.optString("status");
            if(Arrays.asList("completed","cancelled","failed").contains(status))continue;
            busy|=Arrays.asList("running","arming","waiting").contains(status);paused|=status.equals("paused");
            LinearLayout card=column();AgentStyle.card(card);
            card.addView(muted(status.toUpperCase(Locale.ROOT)+" - "+job.optString("text"),11));
            if(!job.optString("error").isEmpty())card.addView(muted(job.optString("error"),12));
            if(job.optBoolean("phoneArming")){
                card.addView(muted("Waiting for phone access. Enable setup permissions and unlock your device.",12));
                button(card,"Open setup",this::showSetup,false);
            }
            button(card,"Cancel task",()->request("POST","/bots/"+selected+"/jobs/"+job.optString("id")+"/cancel",json(),v->refresh(false)),false);
            thread.addView(card);
        }
        if(busy){
            LinearLayout thinking=row();ThinkingOrbView orb=new ThinkingOrbView(this);orb.state("running");
            thinking.addView(orb,new LinearLayout.LayoutParams(dp(30),dp(30)));
            thinking.addView(muted("Working - you can queue another task",12));
            thread.addView(thinking);
        }
        if(busy||paused||!bot.optBoolean("enabled")){
            LinearLayout controls=row();thread.addView(controls);
            boolean resume=paused||!bot.optBoolean("enabled");
            button(controls,resume?"Resume bot":"Pause bot",()->request("POST","/bots/"+selected+(resume?"/resume":"/pause"),json(),v->refresh(false)),false);
            if(paused)button(controls,"Setup & permissions",this::showSetup,false);
        }
        if(bot.optInt("intervalMinutes")>0)button(thread,"Run routine now",()->request("POST","/bots/"+selected+"/routine/run",json(),v->refresh(false)),false);
        chatScroll.post(()->{if(scroll)chatScroll.fullScroll(View.FOCUS_DOWN);else chatScroll.scrollTo(0,old);});
    }
    private int accent(String value){try{return android.graphics.Color.parseColor(value);}catch(Exception e){return 0xff4F46E5;}}
    private void editBot(JSONObject bot){
        boolean create=bot==null;

        ScrollView scroll=new ScrollView(this);
        LinearLayout form=column();
        form.setPadding(dp(16),dp(8),dp(16),dp(16));
        scroll.addView(form);

        final String[] selectedShape={"cercle"};
        final String[] selectedColor={"#3b93f0"};
        final String[] selectedExpression={"neutre"};

        if(!create&&bot.optString("avatar").startsWith("bloub:")){
            String[] parts=bot.optString("avatar").split(":");
            if(parts.length>1)selectedShape[0]=parts[1];
            if(parts.length>2)selectedExpression[0]=parts[2];
            selectedColor[0]=bot.optString("accent","#3b93f0");
        }

        // Live Hero Avatar Preview
        FrameLayout previewBox=new FrameLayout(this);
        previewBox.setPadding(0,dp(4),0,dp(12));
        BloubView bloubPreview=new BloubView(this);
        bloubPreview.setShapeId(selectedShape[0]);
        bloubPreview.setColorHex(selectedColor[0]);
        bloubPreview.setExpressionId(selectedExpression[0]);
        previewBox.addView(bloubPreview,new FrameLayout.LayoutParams(dp(90),dp(90),Gravity.CENTER));
        form.addView(previewBox);

        // Bloub Section 1: Body Shape
        form.addView(text("Body Shape (jeremy-prt/bloub)",13));
        HorizontalScrollView shapeScroll=new HorizontalScrollView(this);
        shapeScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout shapeRow=row();
        shapeRow.setPadding(dp(2),dp(4),dp(2),dp(8));
        shapeScroll.addView(shapeRow);
        form.addView(shapeScroll);

        List<TextView> shapeChips=new ArrayList<>();
        for(BloubView.Companion.ShapeOption opt:BloubView.Companion.getSHAPES()){
            TextView chip=text(opt.getIcon()+" "+opt.getLabel(),12);
            chip.setPadding(dp(12),dp(6),dp(12),dp(6));
            chip.setTypeface(AgentStyle.font(this),Typeface.BOLD);
            chip.setBackground(AgentStyle.surface(this,selectedShape[0].equals(opt.getId())?AgentStyle.BLUE:AgentStyle.PANEL,14,false));
            chip.setTextColor(selectedShape[0].equals(opt.getId())?0xFFFFFFFF:AgentStyle.MUTED);
            chip.setOnClickListener(v->{
                selectedShape[0]=opt.getId();
                bloubPreview.setShapeId(opt.getId());
                for(int i=0;i<shapeChips.size();i++){
                    boolean sel=BloubView.Companion.getSHAPES().get(i).getId().equals(opt.getId());
                    shapeChips.get(i).setBackground(AgentStyle.surface(this,sel?AgentStyle.BLUE:AgentStyle.PANEL,14,false));
                    shapeChips.get(i).setTextColor(sel?0xFFFFFFFF:AgentStyle.MUTED);
                }
            });
            shapeChips.add(chip);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);
            lp.rightMargin=dp(6);
            shapeRow.addView(chip,lp);
        }

        // Bloub Section 2: Skin Color
        form.addView(text("Avatar Color",13));
        HorizontalScrollView colorScroll=new HorizontalScrollView(this);
        colorScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout colorRow=row();
        colorRow.setPadding(dp(2),dp(4),dp(2),dp(8));
        colorScroll.addView(colorRow);
        form.addView(colorScroll);

        List<FrameLayout> colorDots=new ArrayList<>();
        for(BloubView.Companion.ColorOption opt:BloubView.Companion.getCOLORS()){
            FrameLayout dotContainer=new FrameLayout(this);
            int parsed;
            try{parsed=android.graphics.Color.parseColor(opt.getHex());}catch(Exception e){parsed=0xFF3B93F0;}
            dotContainer.setBackground(AgentStyle.surface(this,selectedColor[0].equalsIgnoreCase(opt.getHex())?0xFFFFFFFF:0x00000000,18,false));
            dotContainer.setPadding(dp(3),dp(3),dp(3),dp(3));

            View innerDot=new View(this);
            innerDot.setBackground(AgentStyle.surface(this,parsed,15,false));
            dotContainer.addView(innerDot,new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.CENTER));

            dotContainer.setOnClickListener(v->{
                selectedColor[0]=opt.getHex();
                bloubPreview.setColorHex(opt.getHex());
                for(int i=0;i<colorDots.size();i++){
                    boolean sel=BloubView.Companion.getCOLORS().get(i).getHex().equalsIgnoreCase(opt.getHex());
                    colorDots.get(i).setBackground(AgentStyle.surface(this,sel?0xFFFFFFFF:0x00000000,18,false));
                }
            });
            colorDots.add(dotContainer);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(36),dp(36));
            lp.rightMargin=dp(6);
            colorRow.addView(dotContainer,lp);
        }

        // Bloub Section 3: Expression
        form.addView(text("Rest Expression",13));
        HorizontalScrollView expScroll=new HorizontalScrollView(this);
        expScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout expRow=row();
        expRow.setPadding(dp(2),dp(4),dp(2),dp(8));
        expScroll.addView(expRow);
        form.addView(expScroll);

        List<TextView> expChips=new ArrayList<>();
        for(BloubView.Companion.ExpressionOption opt:BloubView.Companion.getEXPRESSIONS()){
            TextView chip=text(opt.getEmoji()+" "+opt.getLabel(),12);
            chip.setPadding(dp(12),dp(6),dp(12),dp(6));
            chip.setTypeface(AgentStyle.font(this),Typeface.BOLD);
            chip.setBackground(AgentStyle.surface(this,selectedExpression[0].equals(opt.getId())?AgentStyle.BLUE:AgentStyle.PANEL,14,false));
            chip.setTextColor(selectedExpression[0].equals(opt.getId())?0xFFFFFFFF:AgentStyle.MUTED);
            chip.setOnClickListener(v->{
                selectedExpression[0]=opt.getId();
                bloubPreview.setExpressionId(opt.getId());
                for(int i=0;i<expChips.size();i++){
                    boolean sel=BloubView.Companion.getEXPRESSIONS().get(i).getId().equals(opt.getId());
                    expChips.get(i).setBackground(AgentStyle.surface(this,sel?AgentStyle.BLUE:AgentStyle.PANEL,14,false));
                    expChips.get(i).setTextColor(sel?0xFFFFFFFF:AgentStyle.MUTED);
                }
            });
            expChips.add(chip);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);
            lp.rightMargin=dp(6);
            expRow.addView(chip,lp);
        }

        // Section 4: Identity & Instructions (Only 2 EditTexts in creation mode!)
        form.addView(text("Identity & Instructions",14));
        EditText name=field(form,"Name",create?"":bot.optString("name"),false);
        EditText role=field(form,"Instructions",create?"":bot.optString("role"),false);
        role.setSingleLine(false);
        role.setMinLines(3);
        role.setGravity(Gravity.TOP);
        role.setHint("What should this bot help you with?");

        EditText avatar=null,accent=null,personality=null;
        if(!create){
            avatar=field(form,"Bot icon (emoji or initials)",bot.optString("avatar"),false);
            accent=field(form,"Accent color",bot.optString("accent","#4F46E5"),false);
            personality=field(form,"Personality",bot.optString("personality"),false);
            personality.setSingleLine(false);
            button(form,"Tools, alerts & routines",()->botOptions(bot),false);
            button(form,"Select MCP connections",this::selectConnections,false);
        }

        final EditText fAvatar=avatar,fAccent=accent,fPersonality=personality;
        final boolean[] submitted={false};
        final AlertDialog[] dialogRef={null};

        final Runnable submit=()->{
            if(submitted[0])return;
            submitted[0]=true;
            String avatarStr="bloub:"+selectedShape[0]+":"+selectedExpression[0];
            if(!create&&fAvatar!=null&&!fAvatar.getText().toString().isEmpty()){
                avatarStr=fAvatar.getText().toString();
            }
            String accentStr=selectedColor[0];
            if(!create&&fAccent!=null&&!fAccent.getText().toString().isEmpty()){
                accentStr=fAccent.getText().toString();
            }
            String personalityStr=selectedExpression[0];
            if(!create&&fPersonality!=null&&!fPersonality.getText().toString().isEmpty()){
                personalityStr=fPersonality.getText().toString();
            }

            request(create?"POST":"PUT",create?"/bots":"/bots/"+bot.optString("id"),
                json("name",name.getText().toString().trim(),
                     "role",role.getText().toString().trim(),
                     "avatar",avatarStr,
                     "accent",accentStr,
                     "personality",personalityStr,
                     "kind","unified",
                     "allowShell",true,
                     "allowUrgent",true,
                     "dailyLimit",0),
                result->{
                    if(dialogRef[0]!=null)try{dialogRef[0].dismiss();}catch(Exception ignored){}
                    open(((JSONObject)result).optString("id"));
                });
        };

        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(create?"Create a bot":"Bot profile")
            .setView(scroll)
            .setPositiveButton(create?"CREATE BOT":"Save",(d,w)->submit.run())
            .setNegativeButton("Cancel",null)
            .create();
        dialogRef[0]=dialog;

        dialog.setOnShowListener(d->{
            android.widget.Button pos=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if(pos!=null)pos.setOnClickListener(w->submit.run());
        });
        dialog.show();
    }
    private void botOptions(JSONObject bot){
        LinearLayout form=column();form.setPadding(dp(16),dp(12),dp(16),dp(16));
        CheckBox shell=new CheckBox(this);shell.setText("Allow shell commands without asking");shell.setTextColor(AgentStyle.TEXT);shell.setChecked(bot.optBoolean("allowShell",true));form.addView(shell);
        CheckBox urgent=new CheckBox(this);urgent.setText("Allow ringing priority alerts");urgent.setTextColor(AgentStyle.TEXT);urgent.setChecked(bot.optBoolean("allowUrgent",true));form.addView(urgent);
        EditText interval=field(form,"Routine interval in minutes - 0 = off",bot.optString("intervalMinutes","0"),false),routine=field(form,"Routine task",bot.optString("heartbeat"),false);
        routine.setSingleLine(false);
        button(form,"Remove bot",()->new AlertDialog.Builder(this).setTitle("Remove this bot?").setMessage("Its workspace files will be kept.").setPositiveButton("Remove",(d,n)->request("DELETE","/bots/"+bot.optString("id"),null,v->open(""))).setNegativeButton("Cancel",null).show(),false);
        new AlertDialog.Builder(this).setTitle("Bot preferences").setView(form).setPositiveButton("Save",(d,n)->{
            try{request("PUT","/bots/"+bot.optString("id"),json("allowShell",shell.isChecked(),"allowUrgent",urgent.isChecked(),"dailyLimit",0,"intervalMinutes",Integer.parseInt(interval.getText().toString()),"heartbeat",routine.getText().toString()),v->refresh(false));}catch(Exception e){error(e);}
        }).setNegativeButton("Cancel",null).show();
    }
    private void selectConnections(){
        if(detail==null)return;JSONObject bot=detail.optJSONObject("bot");
        JSONArray all=state.optJSONArray("connectors"),current=bot.optJSONArray("connectors");
        LinearLayout form=column();Map<String,CheckBox> choices=new LinkedHashMap<>();
        if(all!=null)for(int i=0;i<all.length();i++){
            JSONObject connection=all.optJSONObject(i);CheckBox check=new CheckBox(this);check.setText(connection.optString("name"));check.setTextColor(AgentStyle.TEXT);check.setChecked(current!=null&&current.toString().contains(connection.optString("id")));form.addView(check);choices.put(connection.optString("id"),check);
        }
        button(form,"Manage MCP connections",this::connections,false);
        new AlertDialog.Builder(this).setTitle("Tools for "+bot.optString("name")).setView(form).setPositiveButton("Save",(d,n)->{
            JSONArray ids=new JSONArray();for(Map.Entry<String,CheckBox> entry:choices.entrySet())if(entry.getValue().isChecked())ids.put(entry.getKey());
            request("PUT","/bots/"+bot.optString("id"),json("connectors",ids),v->refresh(false));
        }).setNegativeButton("Cancel",null).show();
    }
    private boolean accessibility(){return com.artemis.helper.ArtemisAccessibilityService.getInstance()!=null;}
    private void setup(){
        body.removeAllViews();ScrollView scroll=new ScrollView(this);LinearLayout content=column();scroll.addView(content);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        content.addView(text("Set up your local bots",22));content.addView(muted("Every bot can code, browse and operate your phone. Shell commands and priority ringing are enabled by default; you can change them in each bot's preferences.",13));
        content.addView(text("Shell commands: allowed by default",14));content.addView(text("Priority ringing alerts: allowed by default",14));
        content.addView(muted("Alerts can ring while locked. Answer shows the message and uses an installed offline Android voice. Phone actions require an unlocked screen.",12));
        boolean notifications=getSystemService(NotificationManager.class).areNotificationsEnabled();
        button(content,(notifications?"Allowed: ":"Enable: ")+"Notifications & replies",()->{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1);else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));},false);
        if(Build.VERSION.SDK_INT>=34)button(content,(getSystemService(NotificationManager.class).canUseFullScreenIntent()?"Allowed: ":"Enable: ")+"Full-screen alert permission",()->startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName()))),false);
        button(content,"Background app capabilities",()->new AlertDialog.Builder(this).setTitle("Phone execution capabilities").setMessage(AgentDisplayCapabilities.inspect(this).toString()).setPositiveButton("Close",null).setNeutralButton("Probe private display",(d,n)->AgentDisplayCapabilities.probeOwnDisplay(this,"com.android.chrome",result->new AlertDialog.Builder(this).setTitle("Measured display support").setMessage(result.toString()).setPositiveButton("Close",null).show())).show(),false);
        button(content,"Android voice data",()->{try{startActivity(new Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));}catch(ActivityNotFoundException e){Toast.makeText(this,"Install an offline voice in Android text-to-speech settings",Toast.LENGTH_LONG).show();}},false);
        button(content,(accessibility()?"Enabled: ":"Enable: ")+"Phone accessibility",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),false);
        button(content,(Settings.canDrawOverlays(this)?"Allowed: ":"Allow: ")+"Floating island",()->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))),false);
        button(content,"Model / BYOK settings",()->showScreen("Settings"),false);
        content.addView(muted("Android requires you to grant these permissions in its own settings. Missing phone permissions do not block coding. Bots continue outside the app; Android battery restrictions still apply.",12));
        button(content,"Continue to bots",()->{getSharedPreferences("bot-setup",0).edit().putBoolean("seen-v2",true).apply();open("");},true);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(screen.equals("Setup"))setup();}
    @Override public void onBackPressed(){if(!screen.equals("Chat")){showScreen("Chat");return;}if(!selected.isEmpty())open("");else super.onBackPressed();}
    private void connections(){
        request("GET","/connectors",null,value -> {
            JSONArray list=(JSONArray)value;LinearLayout form=column();form.setPadding(dp(12),0,dp(12),dp(12));
            button(form,"Add MCP connection",() -> connectionForm(null),true);
            for(int i=0;i<list.length();i++){
                JSONObject c=list.optJSONObject(i);
                button(form,c.optString("name")+" · "+c.optString("transport"),() -> new AlertDialog.Builder(this).setTitle(c.optString("name")).setItems(new String[]{"Sign in with OAuth","Test connection","Edit","Remove"},(d,n) -> {
                    if(n==0)request("POST","/connectors/"+c.optString("id")+"/oauth/start",json(),r->{JSONObject auth=(JSONObject)r;String url=auth.optString("authorizationUrl");if(!url.isEmpty())openUrl(url);else if("authorized".equals(auth.optString("status")))Toast.makeText(this,"Already signed in. Test the connection to load its tools.",Toast.LENGTH_LONG).show();else throw new IllegalStateException("The connector did not provide a sign-in link.");});
                    if(n==1)request("POST","/connectors/"+c.optString("id")+"/test",json(),r -> new AlertDialog.Builder(this).setTitle("Connected").setMessage(((JSONObject)r).optJSONArray("tools").length()+" tools available").setPositiveButton("OK",null).show());
                    if(n==2)connectionForm(c);
                    if(n==3)request("DELETE","/connectors/"+c.optString("id"),null,r -> {refresh(false);});
                }).show(),false);
            }
            ScrollView scroll=new ScrollView(this);scroll.addView(form);
            new AlertDialog.Builder(this).setTitle("MCP connections").setView(scroll).setPositiveButton("Close",null).show();
        });
    }
    private void connectionForm(JSONObject previous){
        LinearLayout form=column();form.setPadding(dp(16),0,dp(16),dp(16));
        EditText name=field(form,"Name",previous==null?"":previous.optString("name"),false);
        Spinner transport=new Spinner(this);transport.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"HTTP endpoint","Local stdio command"}));
        form.addView(transport);if(previous!=null)transport.setSelection(previous.optString("transport").equals("stdio")?1:0);
        EditText url=field(form,"HTTP URL",previous==null?"":previous.optString("url"),false),token=field(form,"Bearer token · blank keeps saved token","",true),command=field(form,"Local executable",previous==null?"npx":previous.optString("command"),false),args=field(form,"Arguments · JSON array",previous==null?"[]":previous.optJSONArray("args").toString(),false);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("MCP connection").setView(scroll).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(w -> {
            try{
                JSONObject data=json("name",name.getText().toString(),"transport",transport.getSelectedItemPosition()==0?"http":"stdio","url",url.getText().toString(),"token",token.getText().toString(),"command",command.getText().toString(),"args",new JSONArray(args.getText().toString()));
                if(previous!=null)data.put("id",previous.optString("id"));
                request("POST","/connectors",data,r -> {dialog.dismiss();refresh(false);});
            }catch(Exception e){error(e);}
        }));dialog.show();
    }
}

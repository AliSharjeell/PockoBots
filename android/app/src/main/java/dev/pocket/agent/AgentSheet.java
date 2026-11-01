package dev.pocket.agent;

import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

/** Shared native sheet for connection forms, file creation and confirmation prompts. */
class AgentSheet {
    final Dialog dialog;
    final LinearLayout content;
    private final BotsActivity app;private final LinearLayout panel;
    AgentSheet(BotsActivity app,String title){
        this.app=app;dialog=new Dialog(app);content=app.column();
        panel=app.column();panel.setPadding(dp(16),dp(12),dp(16),dp(20));panel.setBackground(AgentStyle.asymmetricSurface(app,AgentStyle.PANEL,28,28,0,0,true));
        View handle=new View(app);handle.setBackground(AgentStyle.surface(app,AgentStyle.MUTED,2,false));LinearLayout.LayoutParams grip=new LinearLayout.LayoutParams(dp(38),dp(4));grip.gravity=Gravity.CENTER_HORIZONTAL;grip.bottomMargin=dp(14);panel.addView(handle,grip);
        ScrollView scroll=new ScrollView(app){@Override protected void onMeasure(int width,int height){super.onMeasure(width,View.MeasureSpec.makeMeasureSpec((int)(getResources().getDisplayMetrics().heightPixels*.72f),View.MeasureSpec.AT_MOST));}};
        scroll.addView(content);panel.addView(scroll);TextView heading=app.text(title,23);heading.setTypeface(AgentStyle.font(app),Typeface.BOLD);content.addView(heading);
        dialog.setContentView(panel);dialog.setCanceledOnTouchOutside(true);
    }
    private int dp(int value){return app.dp(value);}
    Button action(String label,Runnable action,boolean primary){Button button=app.button(content,label,action,primary);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(48));params.topMargin=dp(10);button.setLayoutParams(params);return button;}
    void show(){dialog.show();Window window=dialog.getWindow();if(window!=null){androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false);window.getDecorView().setPadding(0,0,0,0);if(android.os.Build.VERSION.SDK_INT>=30){WindowManager.LayoutParams attrs=window.getAttributes();attrs.setFitInsetsTypes(0);window.setAttributes(attrs);}window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);if(android.os.Build.VERSION.SDK_INT>=29)window.setNavigationBarContrastEnforced(false);new androidx.core.view.WindowInsetsControllerCompat(window,window.getDecorView()).setAppearanceLightNavigationBars(!AgentStyle.dark);androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(panel,(view,insets)->{int bottom=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;view.setPadding(dp(16),dp(12),dp(16),dp(20)+bottom);return insets;});androidx.core.view.ViewCompat.requestApplyInsets(panel);window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));window.setLayout(-1,-2);window.setGravity(Gravity.BOTTOM);window.setDimAmount(.45f);window.setWindowAnimations(android.R.style.Animation_InputMethod);}}
    static void message(BotsActivity app,String title,String detail){AgentSheet sheet=new AgentSheet(app,title);sheet.content.addView(app.muted(detail==null?"Please try again.":detail,13));sheet.action("Done",sheet.dialog::dismiss,true);sheet.show();}
}

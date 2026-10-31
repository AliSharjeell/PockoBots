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
    private final BotsActivity app;
    AgentSheet(BotsActivity app,String title){
        this.app=app;dialog=new Dialog(app);content=app.column();
        LinearLayout panel=app.column();panel.setPadding(dp(16),dp(12),dp(16),dp(20));panel.setBackground(AgentStyle.surface(app,AgentStyle.PANEL,28,true));
        View handle=new View(app);handle.setBackground(AgentStyle.surface(app,AgentStyle.MUTED,2,false));LinearLayout.LayoutParams grip=new LinearLayout.LayoutParams(dp(38),dp(4));grip.gravity=Gravity.CENTER_HORIZONTAL;grip.bottomMargin=dp(14);panel.addView(handle,grip);
        ScrollView scroll=new ScrollView(app){@Override protected void onMeasure(int width,int height){super.onMeasure(width,View.MeasureSpec.makeMeasureSpec((int)(getResources().getDisplayMetrics().heightPixels*.72f),View.MeasureSpec.AT_MOST));}};
        scroll.addView(content);panel.addView(scroll);TextView heading=app.text(title,23);heading.setTypeface(AgentStyle.font(app),Typeface.BOLD);content.addView(heading);
        dialog.setContentView(panel);dialog.setCanceledOnTouchOutside(true);
    }
    private int dp(int value){return app.dp(value);}
    void action(String label,Runnable action,boolean primary){Button button=app.button(content,label,action,primary);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(48));params.topMargin=dp(10);button.setLayoutParams(params);}
    void show(){dialog.show();Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));window.setLayout(-1,-2);window.setGravity(Gravity.BOTTOM);window.setDimAmount(.45f);window.setWindowAnimations(android.R.style.Animation_InputMethod);}}
    static void message(BotsActivity app,String title,String detail){AgentSheet sheet=new AgentSheet(app,title);sheet.content.addView(app.muted(detail==null?"Please try again.":detail,13));sheet.action("Done",sheet.dialog::dismiss,true);sheet.show();}
}

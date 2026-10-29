package dev.pocket.agent;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.*;

/** Native equivalents of form-filling-ai's zinc sidebar tokens and compact controls. */
final class AgentStyle {
    static int BACK=Color.rgb(9,9,11), PANEL=Color.rgb(24,24,27), BORDER=Color.rgb(39,39,42),
        TEXT=Color.rgb(244,244,245), MUTED=Color.rgb(161,161,170), WHITE=Color.rgb(250,250,250), BLUE=Color.rgb(59,118,255);
    static boolean dark=true;
    static void applyTheme(Context context){String choice=context.getSharedPreferences("appearance",0).getString("theme","system");dark=choice.equals("dark")||choice.equals("system")&&(context.getResources().getConfiguration().uiMode&48)==32;BACK=dark?0xff09090b:0xfff4f6fa;PANEL=dark?0xff18181b:0xffffffff;BORDER=dark?0xff27272a:0xffdde2eb;TEXT=dark?0xfff4f4f5:0xff161b26;MUTED=dark?0xffa1a1aa:0xff657087;WHITE=dark?0xfffafafa:0xff202a3c;}
    static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    static GradientDrawable surface(Context context,int color,int radius,boolean border){
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(context,radius));
        if(border)shape.setStroke(dp(context,1),BORDER);return shape;
    }
    static Typeface font(Context context){return context.getResources().getFont(R.font.inter);}
    static TextView text(Context context,String value,int size){
        TextView view=new TextView(context);view.setText(value);view.setTextColor(TEXT);view.setTextSize(size);view.setTypeface(font(context));return view;
    }
    static void button(Button view,boolean primary){
        Context context=view.getContext();view.setAllCaps(false);view.setTextSize(12);view.setTypeface(font(context));
        view.setTextColor(TEXT);view.setMinHeight(dp(context,44));view.setMinimumHeight(dp(context,44));
        view.setMinWidth(dp(context,44));view.setMinimumWidth(dp(context,44));view.setPadding(dp(context,12),dp(context,7),dp(context,12),dp(context,7));
        view.setStateListAnimator(null);view.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.rgb(63,63,70)),
                surface(context,primary?BLUE:PANEL,24,!primary),null));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);params.setMargins(dp(context,3),dp(context,3),dp(context,3),dp(context,3));view.setLayoutParams(params);
    }
    static void input(EditText view){
        Context context=view.getContext();view.setTextSize(14);view.setTextColor(TEXT);view.setHintTextColor(MUTED);view.setTypeface(font(context));
        view.setPadding(dp(context,12),dp(context,12),dp(context,12),dp(context,12));view.setBackground(surface(context,PANEL,12,true));
    }
    static void card(View view){view.setBackground(surface(view.getContext(),PANEL,12,true));}
    static void selected(Button view,boolean selected){view.setTextColor(selected?BACK:MUTED);view.setBackground(surface(view.getContext(),selected?WHITE:Color.TRANSPARENT,24,false));}
    static LinearLayout segment(Context context){LinearLayout view=new LinearLayout(context);view.setGravity(android.view.Gravity.CENTER_VERTICAL);view.setPadding(dp(context,3),dp(context,3),dp(context,3),dp(context,3));view.setBackground(surface(context,PANEL,28,true));return view;}
}

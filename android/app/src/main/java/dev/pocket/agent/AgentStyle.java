package dev.pocket.agent;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.*;

/** Elevated modern zinc & obsidian design tokens and smooth rounded controls. */
final class AgentStyle {
    // Deep obsidian dark tokens and clean minimal porcelain light tokens
    static int BACK=Color.rgb(9,10,15), PANEL=Color.rgb(18,20,28), BORDER=Color.rgb(36,40,56),
        TEXT=Color.rgb(248,250,252), MUTED=Color.rgb(148,163,184), WHITE=Color.rgb(255,255,255),
        BLUE=Color.rgb(79,70,229), ACCENT=Color.rgb(6,182,212);
    static boolean dark=true;

    static void applyTheme(Context context){
        String choice=context.getSharedPreferences("appearance",0).getString("theme","system");
        dark=choice.equals("dark")||choice.equals("system")&&(context.getResources().getConfiguration().uiMode&48)==32;
        BACK=dark?0xff090A0F:0xffF8FAFC;
        PANEL=dark?0xff12141C:0xffFFFFFF;
        BORDER=dark?0xff242838:0xffE2E8F0;
        TEXT=dark?0xffF8FAFC:0xff0F172A;
        MUTED=dark?0xff94A3B8:0xff64748B;
        WHITE=dark?0xffFFFFFF:0xff0F172A;
        BLUE=dark?0xff4F46E5:0xff4338CA;
    }

    static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}

    static GradientDrawable surface(Context context,int color,int radius,boolean border){
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context,radius));
        if(border) shape.setStroke(dp(context,1),BORDER);
        return shape;
    }

    static GradientDrawable asymmetricSurface(Context context,int color,float tl,float tr,float br,float bl,boolean border){
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color);
        float d=context.getResources().getDisplayMetrics().density;
        shape.setCornerRadii(new float[]{tl*d,tl*d, tr*d,tr*d, br*d,br*d, bl*d,bl*d});
        if(border) shape.setStroke(dp(context,1),BORDER);
        return shape;
    }

    static Typeface font(Context context){return context.getResources().getFont(R.font.inter);}

    static TextView text(Context context,String value,int size){
        TextView view=new TextView(context);
        view.setText(value);
        view.setTextColor(TEXT);
        view.setTextSize(size);
        view.setTypeface(font(context));
        view.setLetterSpacing(-0.06f);
        return view;
    }

    static void button(Button view,boolean primary){
        Context context=view.getContext();
        view.setAllCaps(false);
        view.setTextSize(13);
        view.setTypeface(font(context),Typeface.BOLD);
        view.setLetterSpacing(-0.06f);
        view.setTextColor(primary?0xffffffff:TEXT);
        view.setMinHeight(dp(context,44));
        view.setMinimumHeight(dp(context,44));
        view.setMinWidth(dp(context,44));
        view.setMinimumWidth(dp(context,44));
        view.setPadding(dp(context,16),dp(context,8),dp(context,16),dp(context,8));
        view.setStateListAnimator(null);
        int rippleColor = dark ? Color.argb(40,255,255,255) : Color.argb(30,0,0,0);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor),
                surface(context,primary?BLUE:PANEL,22,!primary),null));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);
        params.setMargins(dp(context,4),dp(context,4),dp(context,4),dp(context,4));
        view.setLayoutParams(params);
    }

    static void input(EditText view){
        Context context=view.getContext();
        view.setTextSize(14);
        view.setTextColor(TEXT);
        view.setHintTextColor(MUTED);
        view.setTypeface(font(context));
        view.setLetterSpacing(-0.06f);
        view.setPadding(dp(context,14),dp(context,12),dp(context,14),dp(context,12));
        view.setBackground(surface(context,PANEL,16,true));
    }

    static void card(View view){view.setBackground(surface(view.getContext(),PANEL,18,true));}

    static void selected(Button view,boolean selected){
        view.setTextColor(selected?(dark?0xff090A0F:0xffFFFFFF):MUTED);
        view.setBackground(surface(view.getContext(),selected?WHITE:Color.TRANSPARENT,22,false));
    }

    static LinearLayout segment(Context context){
        LinearLayout view=new LinearLayout(context);
        view.setGravity(android.view.Gravity.CENTER_VERTICAL);
        view.setPadding(dp(context,4),dp(context,4),dp(context,4),dp(context,4));
        view.setBackground(surface(context,PANEL,26,true));
        return view;
    }
}

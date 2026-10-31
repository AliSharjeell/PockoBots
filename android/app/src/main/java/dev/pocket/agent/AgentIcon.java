package dev.pocket.agent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Licensed Lucide outline icons shared with the Compose navigation dock. */
final class AgentIcon extends View {
    private Drawable icon;private int glyphSize=21;void setGlyphSize(int size){glyphSize=size;invalidate();}private int iconTint;private String symbol="";
    void setIconTint(int color){iconTint=color;invalidate();}
    AgentIcon(Context context,String name,String description,Runnable action){
        super(context);
        setSymbol(name);
        setContentDescription(description);setFocusable(true);setClickable(true);
        setBackground(AgentStyle.surface(context,AgentStyle.PANEL,28,true));
        setOnClickListener(v->action.run());
    }
    void setSymbol(String name){if(name.equals(symbol))return;symbol=name;
        int resource;
        switch(name){
            case "menu":resource=R.drawable.lucide_menu;break;
            case "sun":resource=R.drawable.phosphor_sun;break;
            case "moon":resource=R.drawable.phosphor_moon;break;
            case "search":resource=R.drawable.lucide_search;break;
            case "more":resource=R.drawable.lucide_ellipsis_vertical;break;
            case "plus":resource=R.drawable.lucide_plus;break;
            case "back":resource=R.drawable.lucide_arrow_left;break;
            case "send":resource=R.drawable.lucide_send;break;
            case "phone":resource=R.drawable.lucide_phone;break;
            case "pause":resource=R.drawable.lucide_pause;break;
            case "play":resource=R.drawable.lucide_play;break;
            case "open":resource=R.drawable.lucide_external_link;break;
            case "up":resource=R.drawable.lucide_chevron_up;break;
            case "down":resource=R.drawable.lucide_chevron_down;break;
            case "stop":resource=R.drawable.lucide_square;break;
            case "close":resource=R.drawable.lucide_x;break;
            default:resource=R.drawable.lucide_settings_2;
        }
        icon=getContext().getDrawable(resource).mutate();invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);int size=AgentStyle.dp(getContext(),glyphSize);
        int left=(getWidth()-size)/2,top=(getHeight()-size)/2;
        icon.setTint(iconTint==0?AgentStyle.TEXT:iconTint);icon.setBounds(left,top,left+size,top+size);icon.draw(canvas);
    }
}

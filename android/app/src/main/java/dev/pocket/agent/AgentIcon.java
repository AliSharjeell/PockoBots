package dev.pocket.agent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Licensed Lucide outline icons shared with the Compose navigation dock. */
final class AgentIcon extends View {
    private final Drawable icon;
    AgentIcon(Context context,String name,String description,Runnable action){
        super(context);
        int resource;
        switch(name){
            case "menu":resource=R.drawable.lucide_menu;break;
            case "plus":resource=R.drawable.lucide_plus;break;
            case "back":resource=R.drawable.lucide_arrow_left;break;
            case "send":resource=R.drawable.lucide_send;break;
            case "stop":resource=R.drawable.lucide_square;break;
            case "close":resource=R.drawable.lucide_x;break;
            default:resource=R.drawable.lucide_settings_2;
        }
        icon=context.getDrawable(resource).mutate();
        setContentDescription(description);setFocusable(true);setClickable(true);
        setBackground(AgentStyle.surface(context,AgentStyle.PANEL,28,true));
        setOnClickListener(v->action.run());
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);int size=AgentStyle.dp(getContext(),21);
        int left=(getWidth()-size)/2,top=(getHeight()-size)/2;
        icon.setTint(AgentStyle.TEXT);icon.setBounds(left,top,left+size,top+size);icon.draw(canvas);
    }
}

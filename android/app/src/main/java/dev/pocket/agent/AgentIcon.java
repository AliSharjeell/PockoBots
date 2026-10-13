package dev.pocket.agent;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small native outline icons, drawn at a consistent 24-unit stroke grid. */
final class AgentIcon extends View {
    private final String name;private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    AgentIcon(Context context,String name,String description,Runnable action){super(context);this.name=name;setContentDescription(description);setFocusable(true);setClickable(true);setBackground(AgentStyle.surface(context,AgentStyle.PANEL,28,true));setOnClickListener(v -> action.run());}
    @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();float s=AgentStyle.dp(getContext(),21)/24f;c.translate((getWidth()-24*s)/2,(getHeight()-24*s)/2);c.scale(s,s);ink.setColor(AgentStyle.TEXT);ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.8f);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);Path p=new Path();
        switch(name){case "menu":c.drawLine(4,8,20,8,ink);c.drawLine(4,16,20,16,ink);break;
        case "send":p.moveTo(3,10);p.lineTo(21,3);p.lineTo(14,21);p.lineTo(11,13);p.close();c.drawPath(p,ink);c.drawLine(11,13,21,3,ink);break;
        case "plus":c.drawLine(12,4,12,20,ink);c.drawLine(4,12,20,12,ink);break;
        case "back":c.drawLine(20,12,4,12,ink);c.drawLine(4,12,11,5,ink);c.drawLine(4,12,11,19,ink);break;
        case "stop":c.drawRoundRect(6,6,18,18,2,2,ink);break;
        case "close":c.drawLine(6,6,18,18,ink);c.drawLine(6,18,18,6,ink);break;
        default:c.drawCircle(12,12,4,ink);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,ink);}break;}
        c.restore();}
}

package dev.pocket.agent;

import android.content.Context;
import android.widget.TextView;
import java.util.Locale;

/** Advances even when no new provider tokens arrive; detaching stops its timer. */
final class ElapsedTimeView extends TextView {
    private final long started,ended;private final boolean live;
    ElapsedTimeView(Context context,long started,long ended,boolean live){super(context);this.started=started;this.ended=ended;this.live=live;setTypeface(AgentStyle.font(context));setLetterSpacing(-.06f);setTextColor(AgentStyle.MUTED);setTextSize(11);update();}
    private void update(){long seconds=Math.max(0,((live?System.currentTimeMillis():ended)-started)/1000);setText(String.format(Locale.getDefault(),"%d:%02d",seconds/60,seconds%60));}
    private final Runnable tick=new Runnable(){public void run(){update();if(live&&isAttachedToWindow())postDelayed(this,1000);}};
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(live)post(tick);}
    @Override protected void onDetachedFromWindow(){removeCallbacks(tick);super.onDetachedFromWindow();}
}

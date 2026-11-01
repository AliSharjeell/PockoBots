package dev.pocket.agent;

import android.app.Activity;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationViewportTest {
    private Activity host(android.app.Instrumentation inst){String app=inst.getTargetContext().getPackageName();org.junit.Assume.assumeTrue(app.endsWith(".qa"));return inst.startActivitySync(new Intent().setClassName(app,"dev.pocket.agent.UiMotionRegressionActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    @Test public void aSmallManualScrollAndMovementDuringStreamingAreNeverOverwritten() throws Exception {
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();Activity activity=host(inst);ChatScrollView[] scroll={null};TextView[] text={null};int[] expected={0};
        try{
            inst.runOnMainSync(()->{scroll[0]=new ChatScrollView(activity,value->{});text[0]=new TextView(activity);text[0].setHeight(9000);scroll[0].addView(text[0]);activity.setContentView(scroll[0]);});inst.waitForIdleSync();Thread.sleep(180);
            inst.runOnMainSync(()->{long t=android.os.SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,0,100,100,0),move=MotionEvent.obtain(t,t+50,2,100,300,0);scroll[0].onInterceptTouchEvent(down);scroll[0].onInterceptTouchEvent(move);down.recycle();move.recycle();scroll[0].scrollTo(0,scroll[0].getScrollY()-8);assertFalse("Even eight pixels above the end is history",scroll[0].isFollowing());scroll[0].beginUpdate();text[0].setHeight(9600);scroll[0].scrollBy(0,-30);expected[0]=scroll[0].getScrollY();scroll[0].finishUpdate();});inst.waitForIdleSync();Thread.sleep(180);
            inst.runOnMainSync(()->{assertEquals("Preserve finger movement after the stream snapshot",expected[0],scroll[0].getScrollY());assertFalse(scroll[0].isFollowing());scroll[0].latest();});inst.waitForIdleSync();Thread.sleep(180);
            inst.runOnMainSync(()->{assertTrue(scroll[0].isFollowing());assertEquals(text[0].getHeight()-scroll[0].getHeight(),scroll[0].getScrollY());});
        }finally{inst.runOnMainSync(activity::finish);}
    }
    @Test public void chatStartsAtEndFollowsStreamingAndPreservesManualHistoryPosition() throws Exception {
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();Activity activity=host(inst);ChatScrollView[] scroll={null};LinearLayout[] thread={null};
        try{
            inst.runOnMainSync(()->{scroll[0]=new ChatScrollView(activity,value->{});scroll[0].setPadding(0,140,0,100);thread[0]=new LinearLayout(activity);thread[0].setOrientation(1);TextView text=new TextView(activity);text.setText("Conversation fixture");text.setHeight(8500);thread[0].addView(text);scroll[0].addView(thread[0]);activity.setContentView(scroll[0]);});inst.waitForIdleSync();Thread.sleep(180);
            inst.runOnMainSync(()->{assertEquals(thread[0].getHeight()+240-scroll[0].getHeight(),scroll[0].getScrollY());scroll[0].beginUpdate();((TextView)thread[0].getChildAt(0)).setHeight(9000);scroll[0].finishUpdate();});inst.waitForIdleSync();Thread.sleep(180);
            inst.runOnMainSync(()->{assertEquals(thread[0].getHeight()+240-scroll[0].getHeight(),scroll[0].getScrollY());long t=android.os.SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,0,200,200,0),move=MotionEvent.obtain(t,t+50,2,200,400,0);scroll[0].onInterceptTouchEvent(down);scroll[0].onInterceptTouchEvent(move);down.recycle();move.recycle();scroll[0].scrollTo(0,1000);assertFalse(scroll[0].isFollowing());scroll[0].beginUpdate();thread[0].removeAllViews();TextView text=new TextView(activity);text.setHeight(9500);thread[0].addView(text);scroll[0].finishUpdate();});inst.waitForIdleSync();Thread.sleep(180);
            inst.runOnMainSync(()->{assertEquals(1000,scroll[0].getScrollY());assertFalse(scroll[0].isFollowing());scroll[0].latest();});inst.waitForIdleSync();Thread.sleep(180);
            inst.runOnMainSync(()->assertEquals(thread[0].getHeight()+240-scroll[0].getHeight(),scroll[0].getScrollY()));
        }finally{inst.runOnMainSync(activity::finish);}
    }
    @Test public void terminalHistorySurvivesEmptyPollsAndIncomingOutput() throws Exception {
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();Activity activity=host(inst);TerminalCanvas[] terminal={null};
        java.lang.reflect.Field row=TerminalCanvas.class.getDeclaredField("topRow"),height=TerminalCanvas.class.getDeclaredField("cellHeight"),engine=TerminalCanvas.class.getDeclaredField("emulator");row.setAccessible(true);height.setAccessible(true);engine.setAccessible(true);
        try{
            inst.runOnMainSync(()->{terminal[0]=new TerminalCanvas(activity,new TerminalCanvas.Input(){public void send(String value){}public void resize(int c,int r){}});activity.setContentView(terminal[0]);});inst.waitForIdleSync();Thread.sleep(180);
            StringBuilder output=new StringBuilder();for(int i=0;i<240;i++)output.append("line ").append(i).append("\r\n");byte[] bytes=output.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);String encoded=android.util.Base64.encodeToString(bytes,2);
            inst.runOnMainSync(()->{try{TerminalCanvas canvas=terminal[0];canvas.append(encoded,0);int cell=height.getInt(canvas);long t=android.os.SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,0,100,100,0),move=MotionEvent.obtain(t,t+50,2,100,100+cell*5,0);canvas.onTouchEvent(down);canvas.onTouchEvent(move);down.recycle();move.recycle();int top=row.getInt(canvas);assertTrue(top<0);com.termux.terminal.TerminalEmulator em=(com.termux.terminal.TerminalEmulator)engine.get(canvas);String visible=em.getScreen().getSelectedText(0,top,em.mColumns,top,false);canvas.append(encoded,0);assertEquals(top,row.getInt(canvas));canvas.append(android.util.Base64.encodeToString("next line\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8),2),bytes.length);assertEquals(visible,em.getScreen().getSelectedText(0,row.getInt(canvas),em.mColumns,row.getInt(canvas),false));}catch(ReflectiveOperationException e){throw new AssertionError(e);}});
        }finally{inst.runOnMainSync(activity::finish);}
    }
}

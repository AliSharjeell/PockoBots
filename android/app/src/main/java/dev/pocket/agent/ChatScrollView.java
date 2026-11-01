package dev.pocket.agent;

import android.content.Context;
import android.view.*;
import android.widget.ScrollView;

/** Follow new text only while at the end. Layout/focus changes are not user scrolls. */
final class ChatScrollView extends ScrollView {
    private boolean following=true,updating,layingOut,userScrolling;private int savedY;private float downY;
    private final java.util.function.Consumer<Boolean> changed;
    ChatScrollView(Context context,java.util.function.Consumer<Boolean> changed){super(context);this.changed=changed;setSmoothScrollingEnabled(false);}
    boolean isFollowing(){return following;}
    void beginUpdate(){savedY=getScrollY();updating=true;}
    void finishUpdate(){requestLayout();}
    void latest(){following=true;changed.accept(true);requestLayout();}
    private int end(){return getChildCount()==0?0:Math.max(0,getChildAt(0).getHeight()+getPaddingTop()+getPaddingBottom()-getHeight());}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int position=updating?savedY:getScrollY();layingOut=true;super.onLayout(changed,l,t,r,b);
        scrollTo(0,following?end():position);updating=false;layingOut=false;
        if(following)this.changed.accept(true);
    }
    @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);if(userScrolling&&!layingOut){if(updating)savedY=y;following=y>=end()-1;changed.accept(following);}}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downY=event.getY();userScrolling=false;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.abs(event.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop()){
            userScrolling=true;if(updating)savedY=getScrollY();if(event.getY()>downY&&end()>0){following=false;changed.accept(false);}
        }
        return super.onInterceptTouchEvent(event);
    }
    // ScrollView otherwise posts a focus-based scroll after the layout restore,
    // which can pull a long Markdown bubble back to its top during streaming.
    @Override public boolean requestChildRectangleOnScreen(View child,android.graphics.Rect rectangle,boolean immediate){return child instanceof android.widget.EditText&&super.requestChildRectangleOnScreen(child,rectangle,immediate);}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){requestLayout();}
}

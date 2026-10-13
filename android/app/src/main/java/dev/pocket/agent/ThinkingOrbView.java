package dev.pocket.agent;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.*;

/** Canvas port of thinking-orbs 0.3.2's MIT-licensed solving/rubik painter. */
final class ThinkingOrbView extends View {
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<double[]> points=new ArrayList<>();
    private final double[][] moves=new double[14][3];
    private final double[] amount=new double[14];
    private final double[][] dots;
    private String state="idle";
    private final long started=SystemClock.uptimeMillis();
    ThinkingOrbView(Context context){
        super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        for(int i=0;i<14;i++){moves[i][0]=Math.min(2,Math.floor(hash(i,2.3)*3));moves[i][1]=-1+.5*Math.min(3,Math.floor(hash(i,5.9)*4));moves[i][2]=(hash(i,7.7)<.5?1:-1)*Math.PI/2;}
        // Match the reference's small-size density tuning without allocating per frame.
        for(int i=0;i<=6;i++){double lat=-Math.PI/2+i/6.0*Math.PI,c=Math.cos(lat),y=Math.sin(lat);int n=Math.max(1,(int)Math.round(Math.abs(c)*16));for(int j=0;j<n;j++){double lon=j/(double)n*Math.PI*2;points.add(new double[]{c*Math.cos(lon),y,c*Math.sin(lon)});}}
        dots=new double[points.size()][5];
    }
    private static double hash(double a,double b){double h=Math.sin(a*12.9898+b*78.233)*43758.5453;return h-Math.floor(h);}
    void state(String value){state=value;invalidate();}
    private boolean animated(){return state.equals("running")||state.equals("waiting")||state.equals("queued");}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float size=Math.min(getWidth(),getHeight()),cx=getWidth()/2f,cy=getHeight()/2f;
        double t=animated()&&ValueAnimator.areAnimatorsEnabled()?(SystemClock.uptimeMillis()-started)/1000.0*1.95:1.4;
        Arrays.fill(amount,0);double tc=t%(28*.42+1.2);int active=-1;
        if(tc<28*.42){int slot=(int)(tc/.42);double p=Math.min(1,(tc-slot*.42)/.42/.7),ep=1-Math.pow(1-p,3);int u=slot<14?slot:27-slot;for(int i=0;i<u;i++)amount[i]=1;amount[u]=slot<14?ep:1-ep;active=u;}
        double yaw=t*.55,tilt=.35+.1*Math.sin(t*.9),rs=Math.pow(size/300,.6)*1.9;
        for(int n=0;n<points.size();n++){
            double[] point=points.get(n);double x=point[0],y=point[1],z=point[2];boolean lit=false;
            for(int i=0;i<14;i++){if(amount[i]<=0)continue;int axis=(int)moves[i][0];double coord=axis==0?x:axis==1?y:z;if(coord<moves[i][1]||coord>=moves[i][1]+.5)continue;
                lit|=i==active;double a=moves[i][2]*amount[i],c=Math.cos(a),s=Math.sin(a),v;
                if(axis==0){v=y*c-z*s;z=y*s+z*c;y=v;}else if(axis==1){v=x*c+z*s;z=-x*s+z*c;x=v;}else{v=x*c-y*s;y=x*s+y*c;x=v;}}
            double x1=x*Math.cos(yaw)+z*Math.sin(yaw),z1=-x*Math.sin(yaw)+z*Math.cos(yaw),y1=y*Math.cos(tilt)-z1*Math.sin(tilt),z2=y*Math.sin(tilt)+z1*Math.cos(tilt),depth=(z2+1)/2;
            double[] d=dots[n];d[0]=cx+x1*size*.41;d[1]=cy-y1*size*.41;d[2]=z2;d[3]=Math.max(.3,(.6+1.7*depth+(lit?.3:0))*rs);d[4]=Math.min(1,Math.max(0,1-(.62-.54*depth-(lit?.14:0))));
        }
        Arrays.sort(dots,Comparator.comparingDouble(d -> d[2]));
        for(double[] d:dots){int v=(int)(255*d[4]);ink.setColor(state.equals("waiting")?Color.rgb(v,(int)(v*.82),(int)(v*.48)):state.equals("completed")?Color.rgb((int)(v*.6),v,(int)(v*.8)):Color.rgb(v,v,v));canvas.drawCircle((float)d[0],(float)d[1],(float)d[3],ink);}
        if(animated()&&ValueAnimator.areAnimatorsEnabled()&&isShown()&&getWindowVisibility()==VISIBLE)postInvalidateDelayed(33);
    }
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)invalidate();}
}

package dev.pocket.agent;

import android.app.ActivityManager;
import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/** Tests only app-owned display rendering. This is not an arbitrary-app cloning/control API. */
public final class AgentDisplayCapabilities {
    private static final int WIDTH=320, HEIGHT=480, MARKER=Color.rgb(21,101,192);
    private AgentDisplayCapabilities() {}

    public static JSONObject inspect(Context context) {
        JSONObject result=new JSONObject();
        try {
            result.put("apiLevel",Build.VERSION.SDK_INT);
            result.put("secondaryActivityFeature",context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
            result.put("foregroundPhoneControl",true);
            result.put("hiddenNativeAppControl",false);
            result.put("isolatedAppAccounts",false);
            result.put("agentDisplayInputVerified",false);
            result.put("reason","This app has no verified privileged companion for isolated app launch, screen capture and input. A private display alone does not clone an app or its login.");
            result.put("availableMode","foreground");
        } catch(Exception ignored) {}
        return result;
    }

    /** Run from any thread; callback runs once on the main thread. All probe resources are released. */
    public static void probeOwnDisplay(Context context,String packageName,Consumer<JSONObject> callback) {
        Context application=context.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(()->new Probe(application,packageName,callback).start());
    }

    private static final class Probe {
        final Context context; final String target; final Consumer<JSONObject> callback;
        final Handler main=new Handler(Looper.getMainLooper()); final JSONObject result;
        HandlerThread thread; ImageReader reader; VirtualDisplay display; Presentation presentation;
        boolean done;
        Probe(Context context,String target,Consumer<JSONObject> callback){this.context=context;this.target=target;this.callback=callback;result=inspect(context);}
        void put(String key,Object value){try{result.put(key,value);}catch(Exception ignored){}}
        void start() {
            put("probe","app-owned-display");put("ownDisplayCreated",false);put("surfaceFrameReceived",false);put("ownContentVerified",false);put("targetActivityLaunched",false);
            try {
                DisplayManager manager=context.getSystemService(DisplayManager.class);
                if(manager==null)throw new IllegalStateException("Display service unavailable");
                thread=new HandlerThread("pocko-display-probe");thread.start();
                reader=ImageReader.newInstance(WIDTH,HEIGHT,PixelFormat.RGBA_8888,2);
                reader.setOnImageAvailableListener(source->{
                    boolean marker=false;
                    try(Image image=source.acquireLatestImage()) {
                        if(image==null)return;
                        Image.Plane plane=image.getPlanes()[0];ByteBuffer pixels=plane.getBuffer();
                        for(int y=8;y<HEIGHT;y+=32)for(int x=8;x<WIDTH;x+=32){int offset=y*plane.getRowStride()+x*plane.getPixelStride();if(offset+2<pixels.limit()&&(pixels.get(offset)&255)==21&&(pixels.get(offset+1)&255)==101&&(pixels.get(offset+2)&255)==192)marker=true;}
                    }catch(Exception ignored){}
                    final boolean verified=marker;
                    main.post(()->{if(done)return;put("surfaceFrameReceived",true);if(verified){put("ownContentVerified",true);finish();}});
                },new Handler(thread.getLooper()));
                display=manager.createVirtualDisplay("PockoBots capability probe",WIDTH,HEIGHT,160,reader.getSurface(),DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY|DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);
                if(display==null)throw new IllegalStateException("Android did not create a display");
                put("ownDisplayCreated",true);put("probeDisplayId",display.getDisplay().getDisplayId());
                if(target!=null&&!target.isEmpty()) {
                    put("targetPackage",target);
                    Intent launch=context.getPackageManager().getLaunchIntentForPackage(target);
                    put("targetLaunchIntentAvailable",launch!=null);
                    if(launch!=null&&Build.VERSION.SDK_INT>=29)put("targetLaunchPolicyAllows",ActivityManager.isActivityStartAllowedOnDisplay(context,display.getDisplay().getDisplayId(),launch));
                    put("targetLaunchPolicyMeaning","A policy check is not a verified app launch, separate login, screen capture or input route.");
                }
                presentation=new Presentation(context,display.getDisplay());
                TextView marker=new TextView(presentation.getContext());marker.setText("PockoBots private display probe");marker.setTextColor(Color.WHITE);marker.setBackgroundColor(MARKER);marker.setGravity(Gravity.CENTER);
                presentation.setContentView(marker);if(presentation.getWindow()!=null){presentation.getWindow().setBackgroundDrawable(new ColorDrawable(MARKER));presentation.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);}
                presentation.show();
                main.postDelayed(()->{if(!done){put("probeNote","Own display created but marker pixels were not verified within five seconds");finish();}},5000);
            }catch(Exception error){put("probeError",error.getClass().getSimpleName()+": "+error.getMessage());finish();}
        }
        void finish(){if(done)return;done=true;if(presentation!=null)try{presentation.dismiss();}catch(Exception ignored){}if(display!=null)display.release();if(reader!=null)reader.close();if(thread!=null)thread.quitSafely();put("probeReleased",true);callback.accept(result);}
    }
}

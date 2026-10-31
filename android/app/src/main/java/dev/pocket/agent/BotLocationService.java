package dev.pocket.agent;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.TimeZone;

/** Owner-enabled approximate location, independent of phone accessibility or screen control. */
public final class BotLocationService extends Service implements LocationListener {
    private LocationManager manager;private boolean tracking;
    static boolean permitted(Context context){return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    static boolean enabled(Context context){return context.getSharedPreferences("bot-location",0).getBoolean("enabled",false);}
    static void enable(Context context){context.getSharedPreferences("bot-location",0).edit().putBoolean("enabled",true).apply();context.startForegroundService(new Intent(context,BotLocationService.class));}
    static void disable(Context context){context.getSharedPreferences("bot-location",0).edit().putBoolean("enabled",false).apply();context.stopService(new Intent(context,BotLocationService.class));write(context,false,null);}
    private static synchronized void write(Context context,boolean enabled,Location location){
        try{JSONObject value=new JSONObject().put("enabled",enabled);if(location!=null)value.put("latitude",Math.round(location.getLatitude()*1000d)/1000d).put("longitude",Math.round(location.getLongitude()*1000d)/1000d).put("accuracyMeters",Math.max(150,Math.round(location.getAccuracy()))).put("timestamp",location.getTime()).put("provider",location.getProvider()).put("timezone",TimeZone.getDefault().getID());File directory=new File(context.getFilesDir(),"state");directory.mkdirs();File temp=new File(directory,"device-location.tmp"),target=new File(directory,"device-location.json");Files.write(temp.toPath(),value.toString().getBytes(StandardCharsets.UTF_8));Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception ignored){}
    }
    @Override public void onCreate(){super.onCreate();manager=getSystemService(LocationManager.class);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!enabled(this)||!permitted(this)){write(this,false,null);stopSelf();return START_NOT_STICKY;}
        NotificationManager notifications=getSystemService(NotificationManager.class);notifications.createNotificationChannel(new NotificationChannel("bot-location","Bot location sharing",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,61,new Intent(this,BotsActivity.class),PendingIntent.FLAG_IMMUTABLE);
        Notification notice=new Notification.Builder(this,"bot-location").setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Approximate location enabled").setContentText("Your bots can use it for nearby requests. Turn off in app settings.").setContentIntent(open).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(61,notice,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);else startForeground(61,notice);
        if(tracking)return START_NOT_STICKY;tracking=true;Location newest=null;
        for(String provider:new String[]{LocationManager.NETWORK_PROVIDER,"fused",LocationManager.PASSIVE_PROVIDER})try{if(!manager.getAllProviders().contains(provider))continue;Location previous=manager.getLastKnownLocation(provider);if(previous!=null&&(newest==null||previous.getTime()>newest.getTime()))newest=previous;manager.requestLocationUpdates(provider,60000,100,this,Looper.getMainLooper());}catch(SecurityException denied){write(this,false,null);}catch(IllegalArgumentException ignored){}
        write(this,true,newest);return START_NOT_STICKY;
    }
    @Override public void onLocationChanged(Location location){if(enabled(this)&&permitted(this))write(this,true,location);else{write(this,false,null);stopSelf();}}
    @Override public void onProviderEnabled(String provider){}
    @Override public void onProviderDisabled(String provider){}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
    @Override public void onDestroy(){if(manager!=null)manager.removeUpdates(this);if(!enabled(this)||!permitted(this))write(this,false,null);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}

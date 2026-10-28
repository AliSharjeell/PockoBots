package dev.pocket.agent;

import android.app.*;
import android.content.Intent;
import android.os.*;
import java.io.*;
import java.util.*;
import org.json.*;

public class RuntimeService extends Service {
    static volatile String status = "Starting coding environment…";
    static volatile boolean ready = false;
    private static final Object runtimeLock = new Object();
    private static volatile java.lang.Process lastRuntime;
    private static volatile int lastRuntimePid;
    private static volatile RuntimeService active;
    private volatile java.lang.Process backend;
    private volatile int runtimePid;
    private volatile boolean starting;
    private PowerManager.WakeLock wakeLock;
    private volatile boolean stopping;
    private AgentApi botApi;private long lastBotPoll;private final Handler main=new Handler(Looper.getMainLooper());
    @Override public void onCreate() {
        super.onCreate();
        active = this; ready = false; status = "Starting coding environment…";
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("runtime", "Coding environment", NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel("bot-results", "Bot messages", NotificationManager.IMPORTANCE_DEFAULT));
        NotificationChannel priority=new NotificationChannel("bot-priority","Priority bot calls",NotificationManager.IMPORTANCE_HIGH);priority.setDescription("Opt-in ringing bot alerts. Android lock-screen, volume and Do Not Disturb settings apply.");priority.setSound(null,new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build());priority.enableVibration(true);manager.createNotificationChannel(priority);
        Intent open = new Intent(this, BotsActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, RuntimeService.class).setAction("stop");
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "runtime")
            .setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("Pocket Agent")
            .setContentText("Your local coding environment is running")
            .setContentIntent(content).addAction(new Notification.Action.Builder(null, "Stop environment", stopIntent).build())
            .setOngoing(true).build();
        startForeground(1, notification);
        wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketAgent:runtime");
        wakeLock.acquire();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent!=null&&intent.hasExtra("botsEnabled"))getSharedPreferences("bot-runtime",MODE_PRIVATE).edit().putBoolean("enabled",intent.getBooleanExtra("botsEnabled",false)).apply();
        if (intent != null && "stop".equals(intent.getAction())) {
            stopping = true;MobileAccess.authorize(false);getSharedPreferences("bot-runtime",MODE_PRIVATE).edit().putBoolean("enabled",false).apply();
            main.postDelayed(this::stopSelf,3000);new Thread(() -> {try{AgentApi connection=new AgentApi((String)new JSONTokener(Io.text(new File(getFilesDir(),"state/token.json"))).nextValue());connection.request("PUT","/bots/engine",new JSONObject().put("enabled",false));}catch(Exception ignored){}finally{stopSelf();}},"pocket-bots-stop").start();
            return START_NOT_STICKY;
        }
        if (!starting) {
            starting = true;
            new Thread(this::startRuntime, "pocket-runtime").start();
        }
        return getSharedPreferences("bot-runtime",MODE_PRIVATE).getBoolean("enabled",true)?START_STICKY:START_NOT_STICKY;
    }
    private void startRuntime() {
        try {
            if(Arrays.asList(Build.SUPPORTED_ABIS).contains("x86_64")&&android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)>4096)
                throw new IOException("This experimental 16 KB Intel emulator cannot run the included Linux tools. Restart this AVD in 4 KB mode using the development guide. Your projects are preserved.");
            File root = RuntimeInstaller.install(this, message -> status = message);
            if (stopping) return;
            status = "Starting Node.js and the coding agent…";
            File files = getFilesDir();
            for (String dir : new String[]{"projects", "state", "home", "tmp"}) new File(files, dir).mkdirs();
            String nativeDir = getApplicationInfo().nativeLibraryDir;
            List<String> command = new ArrayList<>(Arrays.asList(nativeDir + "/libproot.so", "--kill-on-exit", "-0", "-r", root.getPath(),
                "-b", "/dev", "-b", "/proc", "-b", "/sys", "-b", files + "/projects:/workspace",
                "-b", files + "/state:/state", "-b", files + "/home:/root", "-b", files + "/tmp:/tmp",
                "-w", "/opt/pocket-agent", "/usr/bin/env", "-i", "HOME=/root", "USER=root", "LANG=C.UTF-8",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "POCKET_WORKSPACE=/workspace", "POCKET_STATE=/state", "POCKET_PORT=8787",
                "LD_PRELOAD=/usr/local/lib/libpocket-compat.so", "UV_USE_IO_URING=0",
                "node", "/opt/pocket-agent/dist/server.js"));
            File appBackend=PackagedBackend.prepare(this);
            command.addAll(command.indexOf("-w"),Arrays.asList("-b",appBackend+"/dist:/opt/pocket-agent/dist","-b",appBackend+"/runtime:/opt/pocket-agent/runtime"));
            File logFile = new File(files, "runtime.log");
            // A pipe reader can block Process.destroy() on Android. Write directly
            // to a file so lifecycle shutdown never waits on a stdout reader lock.
            File pidFile = new File(files, "runtime.pid");
            List<String> launcher = new ArrayList<>(Arrays.asList("/system/bin/sh", "-c",
                "printf '%s\\n' \"$$\" > \"$1\"; shift; export LD_PRELOAD=\"$1\"; shift; exec \"$@\"", "pocket-runtime", pidFile.getPath(), nativeDir + "/libpocket-host-compat.so"));
            launcher.addAll(command);
            ProcessBuilder builder = new ProcessBuilder(launcher).redirectErrorStream(true).redirectOutput(logFile);
            builder.environment().put("PROOT_LOADER", nativeDir + "/libproot-loader.so");
            builder.environment().put("PROOT_LOADER_32", nativeDir + "/libproot-loader32.so");
            builder.environment().put("PROOT_TMP_DIR", files + "/tmp");
            builder.environment().put("PROOT_NO_SECCOMP", "1");
            builder.environment().put("LD_LIBRARY_PATH", nativeDir);
            synchronized (runtimeLock) {
                if (lastRuntime != null && lastRuntime.isAlive()) {
                    status = "Waiting for the previous environment to stop…";
                    stopProcess(lastRuntime, lastRuntimePid);
                }
                if (stopping) return;
                Io.write(pidFile, ""); backend = builder.start(); lastRuntime = backend;
                long pidDeadline = System.currentTimeMillis() + 3000;
                while (runtimePid == 0 && backend.isAlive() && System.currentTimeMillis() < pidDeadline) {
                    String value = Io.text(pidFile).trim();
                    if (!value.isEmpty()) runtimePid = Integer.parseInt(value);
                    else Thread.sleep(25);
                }
                lastRuntimePid = runtimePid;
                if (runtimePid == 0) throw new IOException("Could not identify the coding environment process");
            }
            long logSize = -1;
            while (!stopping && backend.isAlive()) {
                if (active == this && logFile.length() != logSize) {
                    logSize = logFile.length(); String output = Io.text(logFile).trim();
                    if (output.contains("Pocket Agent listening")) { ready = true; status = "Ready"; }
                    else if (!ready && !output.isEmpty()) status = output.substring(output.lastIndexOf('\n') + 1);
                }
                Thread.sleep(200);
                if(ready&&System.currentTimeMillis()-lastBotPoll>1000){lastBotPoll=System.currentTimeMillis();pollBots();}
            }
            if (!stopping && active == this) { ready = false; status = "Environment stopped (" + backend.waitFor() + "). Reopen the app to restart. See runtime logs for details."; }
        } catch (Exception e) { if (!stopping && active == this) { ready = false; status = "Setup failed: " + e.getMessage(); } }
        finally { starting = false; if (!stopping && active == this) stopSelf(); }
    }
    private void pollBots(){
        if(!getSharedPreferences("bot-runtime",MODE_PRIVATE).getBoolean("enabled",true))return;
        try{
            if(botApi==null)botApi=new AgentApi((String)new JSONTokener(Io.text(new File(getFilesDir(),"state/token.json"))).nextValue());
            JSONObject inbox=botApi.get("/bots");JSONArray bots=inbox.optJSONArray("bots");
            if(bots!=null)for(int i=0;i<bots.length();i++){
                JSONObject bot=bots.optJSONObject(i),message=bot.optJSONObject("lastMessage");if(message==null||!message.optString("role").equals("assistant")||message.optString("text").startsWith("Priority alert: ")||message.optString("text").startsWith("Notification: "))continue;
                String key="notified-"+bot.optString("id"),messageId=message.optString("id");if(messageId.equals(getSharedPreferences("bot-runtime",MODE_PRIVATE).getString(key,"")))continue;
                Notification notice=BotNotifications.message(this,bot.optString("id"),bot.optString("name"),message.optString("text"));
                getSystemService(NotificationManager.class).notify(bot.optString("id").hashCode(),notice);getSharedPreferences("bot-runtime",MODE_PRIVATE).edit().putString(key,messageId).apply();
            }
            Object alerts=botApi.request("GET","/bots/alerts",null);if(alerts instanceof JSONArray)for(int i=0;i<((JSONArray)alerts).length();i++){
                JSONObject alert=((JSONArray)alerts).getJSONObject(i);String alertId=alert.getString("id");android.content.SharedPreferences prefs=getSharedPreferences("bot-runtime",MODE_PRIVATE);String delivered="delivered-"+alertId;
                if(!prefs.getBoolean(delivered,false)){
                    if(alert.optBoolean("urgent")&&(!BotAlertService.activeAlert.isEmpty()||!BotAlertService.pendingAlert.isEmpty()))continue;
                    BotNotifications.saveAlert(this,alert);prefs.edit().putBoolean(delivered,true).apply();
                    if(alert.optBoolean("urgent")){BotAlertService.pendingAlert=alertId;main.post(() -> {try{startForegroundService(new Intent(this,BotAlertService.class).putExtra("alertId",alertId));}catch(RuntimeException e){BotAlertService.pendingAlert="";getSystemService(NotificationManager.class).notify(30,BotNotifications.priority(this,alert,false));}});}
                    else getSystemService(NotificationManager.class).notify(alert.optString("botId").hashCode(),BotNotifications.message(this,alert.optString("botId"),alert.optString("botName"),alert.optString("text")));
                }
                botApi.request("POST","/bots/alerts/"+alertId+"/ack",new JSONObject());prefs.edit().remove(delivered).apply();
            }
            Object pending=botApi.request("GET","/bots/phone",null);if(!(pending instanceof JSONObject))return;JSONObject request=(JSONObject)pending;String task=request.optString("taskId");if(task.isEmpty())return;
            if(getSystemService(KeyguardManager.class).isDeviceLocked()||!android.provider.Settings.canDrawOverlays(this)||com.artemis.helper.ArtemisAccessibilityService.getInstance()==null)return;
            if(task.equals(MobileOverlayService.activeTask)){if(MobileAccess.allowed())botApi.request("POST","/bots/phone/ready",new JSONObject().put("taskId",task));}
            else main.post(() -> {try{startForegroundService(new Intent(this,MobileOverlayService.class).putExtra("taskId",task).putExtra("botName",request.optString("botName")));}catch(RuntimeException e){android.util.Log.w("PocketBots","Open the app to start this phone task",e);}});
        }catch(Exception e){android.util.Log.w("PocketBots","Bot controller poll will retry: "+e.getClass().getSimpleName());}
    }
    private static void stopProcess(java.lang.Process process, int pid) throws InterruptedException, IOException {
        if (!process.isAlive()) return;
        if (pid <= 1) throw new IOException("No valid environment process ID was recorded");
        // PRoot deliberately ignores SIGTERM. SIGQUIT invokes its handler that
        // kills every traced child before exiting, including terminals/servers.
        try { android.system.Os.kill(pid, android.system.OsConstants.SIGQUIT); }
        catch (android.system.ErrnoException e) { if (e.errno != android.system.OsConstants.ESRCH) throw new IOException(e); }
        if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
            try { android.system.Os.kill(pid, android.system.OsConstants.SIGKILL); }
            catch (android.system.ErrnoException e) { if (e.errno != android.system.OsConstants.ESRCH) throw new IOException(e); }
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("The previous environment has not stopped yet. Retry setup.");
        }
    }
    @Override public void onDestroy() {
        stopping = true; if (active == this) ready = false;
        java.lang.Process process = backend;
        if (process != null) {
            new Thread(() -> { try {
                synchronized (runtimeLock) { stopProcess(process, runtimePid); }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
              catch (IOException e) { android.util.Log.e("PocketRuntime", "Could not stop environment", e); }
            }, "pocket-stop").start();
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}

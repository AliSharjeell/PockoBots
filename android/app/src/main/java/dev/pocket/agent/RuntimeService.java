package dev.pocket.agent;

import android.app.*;
import android.content.Intent;
import android.os.*;
import java.io.*;
import java.util.*;

public class RuntimeService extends Service {
    static volatile String status = "Starting coding environment…";
    static volatile boolean ready = false;
    private volatile java.lang.Process backend;
    private volatile boolean starting;
    private PowerManager.WakeLock wakeLock;
    private volatile boolean stopping;
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("runtime", "Coding environment", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, MainActivity.class);
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
        if (intent != null && "stop".equals(intent.getAction())) { stopping = true; stopSelf(); return START_NOT_STICKY; }
        if (!starting) {
            starting = true;
            new Thread(this::startRuntime, "pocket-runtime").start();
        }
        return START_NOT_STICKY;
    }
    private void startRuntime() {
        try {
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
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            builder.environment().put("PROOT_LOADER", nativeDir + "/libproot-loader.so");
            builder.environment().put("PROOT_LOADER_32", nativeDir + "/libproot-loader32.so");
            builder.environment().put("PROOT_TMP_DIR", files + "/tmp");
            builder.environment().put("PROOT_NO_SECCOMP", "1");
            builder.environment().put("LD_LIBRARY_PATH", nativeDir);
            backend = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(backend.getInputStream()));
                 FileWriter log = new FileWriter(new File(files, "runtime.log"), false)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.write(line + "\n"); log.flush();
                    if (line.contains("Pocket Agent listening")) { ready = true; status = "Ready"; }
                    else if (!ready) status = line;
                }
            }
            ready = false;
            if (!stopping) status = "Environment stopped (" + backend.waitFor() + "). Reopen the app to restart. See runtime logs for details.";
        } catch (Exception e) { ready = false; status = "Setup failed: " + e.getMessage(); }
        finally { starting = false; if (!stopping) stopSelf(); }
    }
    @Override public void onDestroy() {
        stopping = true; ready = false;
        if (backend != null) backend.destroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}

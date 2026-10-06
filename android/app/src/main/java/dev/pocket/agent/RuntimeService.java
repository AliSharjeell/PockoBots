package dev.pocket.agent;

import android.app.*;
import android.content.Intent;
import android.os.*;
import java.io.*;
import java.util.*;

public class RuntimeService extends Service {
    static volatile String status = "Starting coding environment…";
    static volatile boolean ready = false;
    private static final Object runtimeLock = new Object();
    private static volatile java.lang.Process lastRuntime;
    private static volatile RuntimeService active;
    private volatile java.lang.Process backend;
    private volatile boolean starting;
    private PowerManager.WakeLock wakeLock;
    private volatile boolean stopping;
    @Override public void onCreate() {
        super.onCreate();
        active = this; ready = false; status = "Starting coding environment…";
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
            File logFile = new File(files, "runtime.log");
            // A pipe reader can block Process.destroy() on Android. Write directly
            // to a file so lifecycle shutdown never waits on a stdout reader lock.
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(logFile);
            builder.environment().put("PROOT_LOADER", nativeDir + "/libproot-loader.so");
            builder.environment().put("PROOT_LOADER_32", nativeDir + "/libproot-loader32.so");
            builder.environment().put("PROOT_TMP_DIR", files + "/tmp");
            builder.environment().put("PROOT_NO_SECCOMP", "1");
            builder.environment().put("LD_LIBRARY_PATH", nativeDir);
            synchronized (runtimeLock) {
                if (lastRuntime != null && lastRuntime.isAlive()) {
                    status = "Waiting for the previous environment to stop…";
                    lastRuntime.destroy();
                    if (!lastRuntime.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        lastRuntime.destroyForcibly();
                        if (!lastRuntime.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
                            throw new IOException("The previous environment has not stopped yet. Retry setup.");
                    }
                }
                if (stopping) return;
                backend = builder.start(); lastRuntime = backend;
            }
            long logSize = -1;
            while (!stopping && backend.isAlive()) {
                if (active == this && logFile.length() != logSize) {
                    logSize = logFile.length(); String output = Io.text(logFile).trim();
                    if (output.contains("Pocket Agent listening")) { ready = true; status = "Ready"; }
                    else if (!ready && !output.isEmpty()) status = output.substring(output.lastIndexOf('\n') + 1);
                }
                Thread.sleep(200);
            }
            if (!stopping && active == this) { ready = false; status = "Environment stopped (" + backend.waitFor() + "). Reopen the app to restart. See runtime logs for details."; }
        } catch (Exception e) { if (!stopping && active == this) { ready = false; status = "Setup failed: " + e.getMessage(); } }
        finally { starting = false; if (!stopping && active == this) stopSelf(); }
    }
    @Override public void onDestroy() {
        stopping = true; if (active == this) ready = false;
        java.lang.Process process = backend;
        if (process != null) {
            new Thread(() -> { try {
                process.destroy();
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }, "pocket-stop").start();
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}

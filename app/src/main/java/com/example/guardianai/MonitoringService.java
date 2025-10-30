package com.example.guardianai;

import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
// --- Needed for app usage and foreground app detection ---
import android.app.usage.UsageStatsManager;
import android.app.usage.UsageEvents;


import androidx.core.app.NotificationCompat;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * GuardianAI MonitoringService
 * Hybrid detection system:
 *  - Android 10+ : AppOpsManager (safe reflection for last access)
 *  - Android 7–9 : Hardware probing fallback
 */
public class MonitoringService extends Service {

    private static final String TAG = "MonitoringService";
    private static final String CHANNEL_ID = "GuardianAISensorChannel";
    private static final int NOTIFICATION_ID = 1;

    // --- Core components ---
    private SensorMonitorManager monitorManager;
    private ScheduledExecutorService scheduler;
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipListener;

    // --- Database and logging ---
    private SensorLogDao sensorLogDao;
    private ExecutorService logExecutor;
    private RecommendationDao recommendationDao;
    private ExecutorService databaseExecutor;
    private Handler mainHandler;

    // --- UI / state ---
    private String currentStatusText = "Protecting device sensors.";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created and initializing.");

        monitorManager = new SensorMonitorManager(this);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildForegroundNotification());

        try {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
            recommendationDao = db.recommendationDao();
            databaseExecutor = Executors.newSingleThreadExecutor();
            sensorLogDao = db.sensorLogDao();
            logExecutor = Executors.newSingleThreadExecutor();
            mainHandler = new Handler(Looper.getMainLooper());
            Log.d(TAG, "Database and executors initialized successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Fatal error initializing DB/Executors.", e);
            stopSelf();
            return;
        }

        setupClipboardMonitoring();

        scheduler = Executors.newSingleThreadScheduledExecutor();
        startMonitoringLogic();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started or restarted.");
        startMonitoringLogic();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed, cleaning up.");
        if (clipboardManager != null && clipListener != null)
            clipboardManager.removePrimaryClipChangedListener(clipListener);
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // -------------------------------------------------------------------------
    // MONITORING CORE
    // -------------------------------------------------------------------------

    private void startMonitoringLogic() {
        if (scheduler != null && !scheduler.isShutdown())
            scheduler.scheduleAtFixedRate(this::checkSensorStatus, 0, 18, TimeUnit.SECONDS);
    }

    private void checkSensorStatus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                detectCameraMicAccessAppOps();       // Android 10+
            else
                detectCameraMicAccessFallback();     // Android 7–9
        } catch (Exception e) {
            Log.e(TAG, "Error during sensor status check", e);
        }
    }

    // -------------------------------------------------------------------------
    // ANDROID 10+  :  AppOpsManager-based detection (safe reflection)
    // -------------------------------------------------------------------------

    private void detectCameraMicAccessAppOps() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return;

        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        long currentTime = System.currentTimeMillis();

        for (ApplicationInfo appInfo : apps) {
            String pkg = appInfo.packageName;
            if (pkg.equals(getPackageName()) || (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                continue;

            try {
                long camTime = getLastAccessTimeSafe(appOps, AppOpsManager.OPSTR_CAMERA, appInfo.uid, pkg);
                long micTime = getLastAccessTimeSafe(appOps, AppOpsManager.OPSTR_RECORD_AUDIO, appInfo.uid, pkg);

                String name = pm.getApplicationLabel(appInfo).toString();

                if (monitorManager.isCameraMonitoringEnabled()
                        && camTime > 0 && (currentTime - camTime) < 10_000) {
                    logSensorEvent(pkg, name, "CAMERA", true);
                    Log.i(TAG, "Camera accessed by: " + name);
                }

                if (monitorManager.isMicMonitoringEnabled()
                        && micTime > 0 && (currentTime - micTime) < 10_000) {
                    logSensorEvent(pkg, name, "MICROPHONE", true);
                    Log.i(TAG, "Microphone accessed by: " + name);
                }

            } catch (SecurityException se) {
                // Some system apps disallow queries — ignore safely
            } catch (Exception e) {
                Log.w(TAG, "Error checking ops for " + pkg, e);
            }
        }
    }

    /**
     * Safe wrapper around AppOpsManager.getLastAccessedTime() (Android 11+).
     * Uses reflection to avoid SDK issues. Falls back to unsafeCheckOpNoThrow for Android 10.
     */
    /**
     * Safe wrapper around AppOpsManager.getLastAccessedTime() (Android 11+) and
     * unsafeCheckOpNoThrow() (Android 10).  Works with minSdk 24.
     */
    @SuppressLint({"MissingPermission", "NewApi"})
    private long getLastAccessTimeSafe(AppOpsManager appOps, String op, int uid, String pkg) {
        // Check whether GuardianAI has usage access permission first
        if (checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Usage-access permission not granted for GuardianAI.");
            return 0L;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ → reflectively call getLastAccessedTime()
                try {
                    return (long) AppOpsManager.class
                            .getMethod("getLastAccessedTime", String.class, int.class, String.class)
                            .invoke(appOps, op, uid, pkg);
                } catch (Throwable t) {
                    Log.w(TAG, "Reflection failed for getLastAccessedTime()", t);
                    return 0L;
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 → use unsafeCheckOpNoThrow()
                try {
                    int mode = appOps.unsafeCheckOpNoThrow(op, uid, pkg);
                    if (mode == AppOpsManager.MODE_ALLOWED) {
                        // Assume recent access (no timestamp available)
                        return System.currentTimeMillis();
                    }
                } catch (SecurityException se) {
                    Log.w(TAG, "Usage access denied while checking AppOps", se);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling unsafeCheckOpNoThrow()", t);
                }
                return 0L;
            } else {
                // Android 7–9 → no API support
                return 0L;
            }
        } catch (SecurityException se) {
            Log.w(TAG, "SecurityException: usage access not granted", se);
            return 0L;
        } catch (Exception e) {
            Log.w(TAG, "Unexpected exception in getLastAccessTimeSafe()", e);
            return 0L;
        }
    }


    // -------------------------------------------------------------------------
    // ANDROID 7–9  :  Hardware probe fallback detection
    // -------------------------------------------------------------------------

    // --- Fallback Detection for Android 7–9 (Debounced) ---
    private long lastMicLogTime = 0;
    private long lastCamLogTime = 0;
    private static final long MIN_LOG_INTERVAL_MS = 20_000; // 20 seconds

    private void detectCameraMicAccessFallback() {
        long now = System.currentTimeMillis();
        boolean cameraInUse = isCameraBusy();
        boolean micInUse = isMicrophoneBusy();

        // --- CAMERA detection ---
        if (cameraInUse && monitorManager.isCameraMonitoringEnabled()
                && now - lastCamLogTime > MIN_LOG_INTERVAL_MS) {

            String pkg = GuardianAccessibilityService.currentForegroundApp;
            String appName;
            try {
                ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
                appName = getPackageManager().getApplicationLabel(ai).toString();
            } catch (PackageManager.NameNotFoundException e) {
                appName = pkg; // fallback to package name
            }

            logSensorEvent(pkg, appName, "CAMERA", true);
            lastCamLogTime = now;
            Log.i(TAG, "Camera hardware busy (possible usage detected) by " + appName);
        }

        // --- MICROPHONE detection ---
        if (micInUse && monitorManager.isMicMonitoringEnabled()
                && now - lastMicLogTime > MIN_LOG_INTERVAL_MS) {

            String pkg = GuardianAccessibilityService.currentForegroundApp;
            String appName;
            try {
                ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
                appName = getPackageManager().getApplicationLabel(ai).toString();
            } catch (PackageManager.NameNotFoundException e) {
                appName = pkg;
            }

            logSensorEvent(pkg, appName, "MICROPHONE", true);
            lastMicLogTime = now;
            Log.i(TAG, "Microphone hardware busy (possible usage detected) by " + appName);
        }
    }



    private boolean isCameraBusy() {
        android.hardware.Camera cam = null;
        try {
            cam = android.hardware.Camera.open();
            return false;
        } catch (RuntimeException e) {
            return true;
        } finally {
            if (cam != null) try { cam.release(); } catch (Exception ignored) {}
        }
    }

    private boolean isMicrophoneBusy() {
        AudioRecord recorder = null;
        try {
            int buf = AudioRecord.getMinBufferSize(44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    44100, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, buf);
            recorder.startRecording();
            int state = recorder.getRecordingState();
            return state != AudioRecord.RECORDSTATE_RECORDING;
        } catch (Exception e) {
            return true;
        } finally {
            if (recorder != null) try { recorder.stop(); recorder.release(); } catch (Exception ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // CLIPBOARD MONITORING
    // -------------------------------------------------------------------------

    private void setupClipboardMonitoring() {
        clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboardManager == null) return;

        clipListener = () -> {
            if (monitorManager.isClipboardMonitoringEnabled()) {
                Log.i(TAG, "CLIPBOARD ACCESS DETECTED!");
                logSensorEvent("SYSTEM", "System Clipboard", "CLIPBOARD", false);
            }
        };
        clipboardManager.addPrimaryClipChangedListener(clipListener);
    }

    // -------------------------------------------------------------------------
    // DATABASE LOGGING
    // -------------------------------------------------------------------------

    private void logSensorEvent(String pkg, String appName, String sensorType, boolean isAlert) {
        if (sensorLogDao == null || logExecutor == null) {
            Log.e(TAG, "Logging system not initialized!");
            return;
        }

        SensorLogEntry entry = new SensorLogEntry(
                System.currentTimeMillis(), pkg, appName, sensorType, isAlert);

        logExecutor.execute(() -> {
            sensorLogDao.insertLogEntry(entry);
            Log.i(TAG, "Sensor Logged: " + sensorType + " by " + appName +
                    (isAlert ? " (ALERT!)" : ""));
        });
    }

    // -------------------------------------------------------------------------
    // NOTIFICATIONS
    // -------------------------------------------------------------------------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Sensor Monitoring Service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildForegroundNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GuardianAI Active")
                .setContentText(currentStatusText)
                .setSmallIcon(R.drawable.ic_shield)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    // --- Helper: Get the name of the currently foreground app ---
    private String getForegroundAppName(Context context) {
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();

            // Look at the last 10 seconds of activity events
            UsageEvents events = usm.queryEvents(now - 10000, now);

            UsageEvents.Event event = new UsageEvents.Event();
            String lastPkg = "UNKNOWN";

            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastPkg = event.getPackageName();
                }
            }

            if (lastPkg.equals("UNKNOWN")) return "Unknown App";

            // Convert package name to human-readable app name
            ApplicationInfo ai = getPackageManager().getApplicationInfo(lastPkg, 0);
            return getPackageManager().getApplicationLabel(ai).toString();

        } catch (Exception e) {
            Log.e(TAG, "Failed to get foreground app name", e);
            return "Unknown App";
        }
    }

}

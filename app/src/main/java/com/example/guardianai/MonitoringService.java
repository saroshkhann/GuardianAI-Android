package com.example.guardianai;

import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
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

import androidx.core.app.NotificationCompat;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MonitoringService for GuardianAI
 *
 * - Android 10+ : AppOpsManager (safe, version-checks & permission checks)
 * - Android 7-9 : Hardware-probe fallback
 *
 * Uses UsageStatsManager to resolve foreground app names (getForegroundAppName).
 * Keeps clipboard monitoring, DB logging, notification and debounce logic.
 */
public class MonitoringService extends Service {

    private static final String TAG = "MonitoringService";
    private static final String CHANNEL_ID = "GuardianAISensorChannel";
    private static final int NOTIFICATION_ID = 1;

    // Scheduler & monitoring
    private ScheduledExecutorService scheduler;
    private static final long SCHEDULER_INTERVAL_SECONDS = 15L; // 15s polling

    // Clipboard
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipListener;

    // DB & executors
    private SensorLogDao sensorLogDao;
    private ExecutorService logExecutor;
    private RecommendationDao recommendationDao;
    private ExecutorService databaseExecutor;
    private Handler mainHandler;

    // Preferences / toggles
    private SensorMonitorManager monitorManager;

    // State & debouncing
    private String currentStatusText = "Protecting device sensors.";
    private long lastCamLogTime = 0L;
    private long lastMicLogTime = 0L;
    private long lastLocationLogTime = 0L;
    private static final long MIN_LOG_INTERVAL_MS = 20_000L;      // 20 s debounce
    private static final long LOCATION_LOG_INTERVAL_MS = 30_000L; // 30 s debounce

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
            Log.d(TAG, "DB and executors initialized.");
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
        Log.d(TAG, "Service started/restarted.");
        startMonitoringLogic();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed. Cleaning up.");
        if (clipboardManager != null && clipListener != null) {
            clipboardManager.removePrimaryClipChangedListener(clipListener);
        }
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // -------------------------
    // Monitoring core
    // -------------------------
    private void startMonitoringLogic() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.scheduleAtFixedRate(this::checkSensorStatus, 0, 18, TimeUnit.SECONDS);

        }
    }

    private void checkSensorStatus() {
        try {
            long now = System.currentTimeMillis();

            // Android 10+ — try AppOps detection (if usage access granted)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                detectCameraMicLocationAppOps();
            } else {
                // older devices rely on fallback
                detectCameraMicAccessFallback();
                detectLocationAccessFallback();
            }

            // Also run fallback detection (debounced) on all versions as a secondary conservative measure
            detectCameraMicAccessFallback();
            detectLocationAccessFallback();

            // Optionally update notification text (dynamic)
            // updateForegroundNotification(currentStatusText);

        } catch (Exception e) {
            Log.e(TAG, "checkSensorStatus error", e);
        }
    }

    // -------------------------
    // AppOpsManager-based detection (Android 10+)
    // -------------------------
    private void detectCameraMicLocationAppOps() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return;

        PackageManager pm = getPackageManager();
        long now = System.currentTimeMillis();

        // Quick guard: ensure the app has usage access; otherwise AppOps timestamps will be unavailable/zero
        boolean hasUsageAccess = checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
                == PackageManager.PERMISSION_GRANTED;
        if (!hasUsageAccess) {
            Log.w(TAG, "Usage access not granted; AppOps detection limited.");
            return;
        }

        try {
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            for (ApplicationInfo appInfo : apps) {
                String pkg = appInfo.packageName;
                if (pkg.equals(getPackageName()) || (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;

                try {
                    long camTime = getLastAccessTimeSafe(appOps, AppOpsManager.OPSTR_CAMERA, appInfo.uid, pkg);
                    long micTime = getLastAccessTimeSafe(appOps, AppOpsManager.OPSTR_RECORD_AUDIO, appInfo.uid, pkg);
                    long fineLocTime = getLastAccessTimeSafe(appOps, AppOpsManager.OPSTR_FINE_LOCATION, appInfo.uid, pkg);
                    long coarseLocTime = getLastAccessTimeSafe(appOps, AppOpsManager.OPSTR_COARSE_LOCATION, appInfo.uid, pkg);

                    // CAMERA
                    if (monitorManager.isCameraMonitoringEnabled() && camTime > 0 && (now - camTime) < MIN_LOG_INTERVAL_MS) {
                        String appName = safeGetAppName(pm, pkg);
                        logSensorEvent(pkg, appName, "CAMERA", true);
                        Log.i(TAG, "AppOps CAMERA: " + appName);
                    }

                    // MICROPHONE
                    if (monitorManager.isMicMonitoringEnabled() && micTime > 0 && (now - micTime) < MIN_LOG_INTERVAL_MS) {
                        String appName = safeGetAppName(pm, pkg);
                        logSensorEvent(pkg, appName, "MICROPHONE", true);
                        Log.i(TAG, "AppOps MICROPHONE: " + appName);
                    }

                    // LOCATION
                    long locTime = Math.max(fineLocTime, coarseLocTime);
                    if (monitorManager.isLocationMonitoringEnabled() && locTime > 0 && (now - locTime) < LOCATION_LOG_INTERVAL_MS) {
                        String appName = safeGetAppName(pm, pkg);
                        logSensorEvent(pkg, appName, "LOCATION", true);
                        Log.i(TAG, "AppOps LOCATION: " + appName);
                    }
                } catch (SecurityException se) {
                    // ignore system-protected packages
                } catch (Exception ex) {
                    Log.w(TAG, "Error checking app ops for " + pkg, ex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "detectCameraMicLocationAppOps failed", e);
        }
    }

    /**
     * Safe wrapper around AppOps timestamps:
     * - Android 11+ uses reflection to call getLastAccessedTime()
     * - Android 10 uses unsafeCheckOpNoThrow() as an approximation
     * - Returns 0 if unavailable or permission not granted
     */
    @SuppressLint({"MissingPermission", "NewApi"})
    private long getLastAccessTimeSafe(AppOpsManager appOps, String op, int uid, String pkg) {
        // Only proceed if usage access granted
        if (checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
                != PackageManager.PERMISSION_GRANTED) {
            return 0L;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API 30+
                try {
                    return (long) AppOpsManager.class
                            .getMethod("getLastAccessedTime", String.class, int.class, String.class)
                            .invoke(appOps, op, uid, pkg);
                } catch (Throwable t) {
                    return 0L;
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29
                try {
                    int mode = appOps.unsafeCheckOpNoThrow(op, uid, pkg);
                    return mode == AppOpsManager.MODE_ALLOWED ? System.currentTimeMillis() : 0L;
                } catch (SecurityException se) {
                    Log.w(TAG, "Usage access denied while checking AppOps", se);
                    return 0L;
                } catch (Throwable t) {
                    return 0L;
                }
            } else {
                return 0L;
            }
        } catch (Exception e) {
            Log.w(TAG, "getLastAccessTimeSafe error", e);
            return 0L;
        }
    }

    // -------------------------
    // Fallback detection (hardware probes) + correlate with foreground app via UsageStats helper
    // -------------------------
    private void detectCameraMicAccessFallback() {
        long now = System.currentTimeMillis();

        boolean cameraInUse = false;
        boolean micInUse = false;
        try { cameraInUse = isCameraBusy(); } catch (Exception ignored) { cameraInUse = false; }
        try { micInUse = isMicrophoneBusy(); } catch (Exception ignored) { micInUse = false; }

        PackageManager pm = getPackageManager();

        // CAMERA
        if (cameraInUse && monitorManager.isCameraMonitoringEnabled() && now - lastCamLogTime > MIN_LOG_INTERVAL_MS) {
            String pkg = getForegroundPackageSafely();
            String appName = safeGetAppName(pm, pkg);
            logSensorEvent(pkg, appName, "CAMERA", true);
            lastCamLogTime = now;
            Log.i(TAG, "Fallback CAMERA (possible) by " + appName);
        }

        // MICROPHONE
        if (micInUse && monitorManager.isMicMonitoringEnabled() && now - lastMicLogTime > MIN_LOG_INTERVAL_MS) {
            String pkg = getForegroundPackageSafely();
            String appName = safeGetAppName(pm, pkg);
            logSensorEvent(pkg, appName, "MICROPHONE", true);
            lastMicLogTime = now;
            Log.i(TAG, "Fallback MICROPHONE (possible) by " + appName);
        }
    }

    // Location fallback (GPS active) - correlate with foreground app when possible
    private void detectLocationAccessFallback() {
        long now = System.currentTimeMillis();
        try {
            android.location.LocationManager lm = (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return;
            boolean gpsEnabled = false;
            try { gpsEnabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
            if (gpsEnabled && monitorManager.isLocationMonitoringEnabled() && now - lastLocationLogTime > LOCATION_LOG_INTERVAL_MS) {
                String pkg = getForegroundPackageSafely();
                String appName = safeGetAppName(getPackageManager(), pkg);
                logSensorEvent(pkg, appName, "LOCATION", true);
                lastLocationLogTime = now;
                Log.i(TAG, "Fallback LOCATION (possible) by " + appName);
            }
        } catch (Exception e) {
            Log.w(TAG, "detectLocationAccessFallback error", e);
        }
    }

    // -------------------------
    // Hardware probe helpers
    // -------------------------
    private boolean isCameraBusy() {
        android.hardware.Camera cam = null;
        try {
            cam = android.hardware.Camera.open();
            return false;
        } catch (RuntimeException e) {
            return true;
        } finally {
            if (cam != null) {
                try { cam.release(); } catch (Exception ignored) {}
            }
        }
    }

    private boolean isMicrophoneBusy() {
        AudioRecord recorder = null;
        try {
            int bufferSize = AudioRecord.getMinBufferSize(44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(bufferSize, 2048));
            recorder.startRecording();
            int state = recorder.getRecordingState();
            return state != AudioRecord.RECORDSTATE_RECORDING;
        } catch (Exception e) {
            return true;
        } finally {
            if (recorder != null) {
                try { recorder.stop(); recorder.release(); } catch (Exception ignored) {}
            }
        }
    }

    // -------------------------
    // Clipboard monitoring
    // -------------------------
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

    // -------------------------
    // DB logging
    // -------------------------
    private void logSensorEvent(String packageName, String appName, String sensorType, boolean isAlert) {
        if (sensorLogDao == null || logExecutor == null) {
            Log.e(TAG, "Logging system not initialized!");
            return;
        }
        SensorLogEntry entry = new SensorLogEntry(System.currentTimeMillis(), packageName, appName, sensorType, isAlert);
        logExecutor.execute(() -> {
            sensorLogDao.insertLogEntry(entry);
            Log.i(TAG, "Sensor Logged: " + sensorType + " by " + appName + (isAlert ? " (ALERT!)" : ""));
        });
    }

    // -------------------------
    // Helpers
    // -------------------------
    private String safeGetAppName(PackageManager pm, String pkg) {
        if (pkg == null || pkg.isEmpty() || "UNKNOWN".equals(pkg)) return "Unknown App";
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /**
     * Attempts to derive the current foreground package using UsageStatsManager.
     * Returns package name or "UNKNOWN" on failure.
     * Requires PACKAGE_USAGE_STATS granted by user (Settings > Usage access).
     */
    private String getForegroundPackageSafely() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return "UNKNOWN";

            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(now - 15_000L, now); // last 15s
            UsageEvents.Event evt = new UsageEvents.Event();
            String lastPkg = "UNKNOWN";

            while (events.hasNextEvent()) {
                events.getNextEvent(evt);
                if (evt.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastPkg = evt.getPackageName();
                }
            }
            return lastPkg != null ? lastPkg : "UNKNOWN";
        } catch (Exception e) {
            Log.w(TAG, "getForegroundPackageSafely failed", e);
            return "UNKNOWN";
        }
    }

    // -------------------------
    // Notifications
    // -------------------------
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Sensor Monitoring Service", NotificationManager.IMPORTANCE_LOW);
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
}

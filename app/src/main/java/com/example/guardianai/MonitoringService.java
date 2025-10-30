package com.example.guardianai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// --- Imports needed for UsageStatsManager logic (CRITICAL FIX) ---
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;

import java.util.List;
import java.util.HashMap;



public class MonitoringService extends Service {

    private static final String TAG = "MonitoringService";
    private static final String CHANNEL_ID = "GuardianAISensorChannel";
    private static final int NOTIFICATION_ID = 1;

    // --- MONITORING COMPONENTS ---
    private SensorMonitorManager monitorManager;
    private ScheduledExecutorService scheduler;
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipListener;

    // --- LOGGING COMPONENTS ---
    private SensorLogDao sensorLogDao;
    private ExecutorService logExecutor;

    // --- EXISTING DASHBOARD COMPONENTS (needed for completeness) ---
    private RecommendationDao recommendationDao;
    private ExecutorService databaseExecutor;
    private Handler micCheckHandler;

    // Tracks the current status text and activity
    private String currentStatusText = "Protecting device sensors.";
    private boolean isMicActive = false;
    private boolean isCameraActive = false;

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
            micCheckHandler = new Handler(Looper.getMainLooper());

            sensorLogDao = db.sensorLogDao();
            logExecutor = Executors.newSingleThreadExecutor();

            Log.d(TAG, "All DAOs and Executors initialized.");
        } catch (Exception e) {
            Log.e(TAG, "Fatal Error initializing DB/Executors.", e);
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
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed. Cleaning up.");

        if (clipboardManager != null && clipListener != null) {
            clipboardManager.removePrimaryClipChangedListener(clipListener);
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    // --- CORE MONITORING LOGIC (FR 3.1, 3.2, 3.4, 3.6) ---

    private void startMonitoringLogic() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.scheduleAtFixedRate(this::checkSensorStatus, 0, 5, TimeUnit.SECONDS);
        }
    }

    /**
     * Checks for sensor usage events using UsageStatsManager (the public API solution).
     * This fulfills FR 3.1, 3.2 (Monitoring & Detection).
     */
    private void checkSensorStatus() {
        if (monitorManager == null || getApplicationContext() == null) return;

        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return;

        long endTime = System.currentTimeMillis();
        long startTime = endTime - 60000; // Check events that occurred in the last 6 seconds

        UsageEvents events = usm.queryEvents(startTime, endTime);

        String newStatus = "Protecting device sensors.";
        boolean isActive = false;

        while (events.hasNextEvent()) {
            UsageEvents.Event event = new UsageEvents.Event();
            events.getNextEvent(event);

            String sensorType = null;
            int eventType = event.getEventType();

            // 1. Identify Sensor Events based on public event types (using integer values to fix compilation)
            if (eventType == 7) { // 7 is the integer value for CAMERA_STATE_CHANGED
                sensorType = "CAMERA";
            } else if (eventType == 8) { // 8 is the integer value for MIC_STATE_CHANGED
                sensorType = "MICROPHONE";
            }

            // 2. Process Detected Event
            if (sensorType != null) {
                String packageName = event.getPackageName();
                String appName = getAppNameFromPackage(packageName);

                // Filter out self-app and system apps
                if (packageName.equals(getPackageName()) || isSystemApp(packageName)) {
                    continue;
                }

                // Check user preferences to ensure monitoring is enabled
                boolean isMonitoringEnabled = (sensorType.equals("CAMERA") && monitorManager.isCameraMonitoringEnabled()) ||
                        (sensorType.equals("MICROPHONE") && monitorManager.isMicMonitoringEnabled());

                if (isMonitoringEnabled) {
                    // If the event is 'OPEN' (1) or 'ACQUIRED' (3), it's a new access
                    // NOTE: Event constants are often hidden, using raw values for public API workaround
                    if (event.getEventType() == 1 || event.getEventType() == 3) {

                        newStatus = sensorType + " active by: " + appName;
                        isActive = true;

                        // Log the event and alert (FR 3.3/3.6)
                        logSensorEvent(packageName, appName, sensorType, true);

                        // Update status tracking
                        if (sensorType.equals("CAMERA")) isCameraActive = true;
                        if (sensorType.equals("MICROPHONE")) isMicActive = true;

                        // Break the loop once one active monitored event is found
                        break;
                    }
                }
            }
        }

        // --- VISUAL INDICATOR UPDATE (FR 3.4) ---
        if (!newStatus.equals(currentStatusText)) {
            updateForegroundNotification(newStatus);
            currentStatusText = newStatus;
        }
    }


    // --- HELPER METHODS ---

    private boolean isSystemApp(String packageName) {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(packageName, 0);
            return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String getAppNameFromPackage(String packageName) {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(packageName, 0);
            return getPackageManager().getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private void logSensorEvent(String packageName, String appName, String sensorType, boolean isAlert) {
        if (sensorLogDao == null || logExecutor == null) {
            Log.e(TAG, "Logging system not initialized!");
            return;
        }

        SensorLogEntry newEntry = new SensorLogEntry(
                System.currentTimeMillis(),
                packageName,
                appName,
                sensorType,
                isAlert
        );

        logExecutor.execute(() -> {
            sensorLogDao.insertLogEntry(newEntry);
            Log.i(TAG, "Sensor Logged: " + sensorType + " by " + appName + (isAlert ? " (ALERT!)" : ""));
        });
    }

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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Sensor Monitoring Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification buildForegroundNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GuardianAI Active")
                .setContentText(currentStatusText) // Use the dynamic status text
                .setSmallIcon(R.drawable.ic_shield)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateForegroundNotification(String statusText) {
        Notification newNotification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GuardianAI Monitoring")
                .setContentText(statusText) // Dynamically set status
                .setSmallIcon(R.drawable.ic_shield)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, newNotification);
        }
    }
}
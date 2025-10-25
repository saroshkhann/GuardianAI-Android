package com.example.guardianai; // Ensure this matches your package

import android.Manifest; // Needed for permission constants
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences; // Needed for perm status saving
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo; // Needed for perm status checking
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle; // Import Bundle
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

// --- Room Database Imports ---
import com.example.guardianai.AppDatabase;
import com.example.guardianai.Recommendation;
import com.example.guardianai.RecommendationDao;
// --- End Room Imports ---

// --- GSON Imports ---
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
// --- End GSON Imports ---

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MonitoringService extends Service implements LocationListener {

    private static final String TAG = "MonitoringService";
    private static final String CHANNEL_ID = "GuardianAI_MonitoringChannel";
    private static final int NOTIFICATION_ID = 1;

    // --- Recommendation Type Constants ---
    private static final String RECOMMENDATION_TYPE_CLIPBOARD = "CLIPBOARD_ACCESS";
    private static final String RECOMMENDATION_TYPE_CAMERA = "CAMERA_ACCESS";
    private static final String RECOMMENDATION_TYPE_LOCATION = "LOCATION_ACCESS";
    private static final String RECOMMENDATION_TYPE_MICROPHONE = "MICROPHONE_ACCESS";
    private static final String RECOMMENDATION_TYPE_PERM_GRANTED = "PERMISSION_GRANTED";
    private static final String RECOMMENDATION_TYPE_PERM_REVOKED = "PERMISSION_REVOKED";


    // --- Service Components ---
    private RecommendationDao recommendationDao;
    private ExecutorService databaseExecutor;
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipChangedListener;
    private CameraManager cameraManager;
    private CameraManager.AvailabilityCallback cameraCallback;
    private LocationManager locationManager;
    private Timer micCheckTimer;
    private boolean isMicInUse = false;
    private static final long MIC_CHECK_INTERVAL_MS = 5000;

    // --- Permission Status Monitoring ---
    private Timer permissionCheckTimer;
    private static final long PERMISSION_CHECK_INTERVAL_MS = 15 * 60 * 1000;
    private SharedPreferences permissionStatusPrefs;
    private Gson gson = new Gson();
    private static final String PERM_STATUS_PREFS_NAME = "GuardianAI_PermStatus";
    private static final String PERM_STATUS_KEY = "permission_statuses";
    private static final String[] KEY_PERMISSIONS_TO_MONITOR = {
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS
    };


    // --- Lifecycle Methods ---

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: Service creating.");
        createNotificationChannel();
        Log.d(TAG, "Notification channel created or already exists.");

        try {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
            recommendationDao = db.recommendationDao();
            databaseExecutor = Executors.newSingleThreadExecutor();
            Log.d(TAG, "Database DAO and Executor initialized.");
        } catch (Exception e) { Log.e(TAG, "Error initializing DB/Executor", e); stopSelf(); return; }

        initializeClipboardMonitoring();
        initializeCameraMonitoring();
        initializeLocationMonitoring();
        startMicMonitoring();
        initializePermissionStatusMonitoring();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand received.");
        Notification notification = createForegroundNotification();
        try {
            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "Service started in foreground.");
        } catch (Exception e) { Log.e(TAG, "Error starting foreground service", e); stopSelf(); return START_NOT_STICKY; }
        return START_STICKY;
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Service stopping.");
        // --- Clean up Listeners & Timers ---
        if (clipboardManager != null && clipChangedListener != null) { try { clipboardManager.removePrimaryClipChangedListener(clipChangedListener); } catch (Exception e) { Log.e(TAG, "Error removing clipboard listener", e); }}
        if (cameraManager != null && cameraCallback != null) { try { cameraManager.unregisterAvailabilityCallback(cameraCallback); } catch (Exception e) { Log.e(TAG, "Error unregistering camera callback", e); }}
        if (locationManager != null) { try { locationManager.removeUpdates(this); } catch (Exception e) { Log.e(TAG, "Error removing location updates", e); }}
        stopMicMonitoring();
        stopPermissionStatusMonitoring();
        // --- Shutdown Executor ---
        if (databaseExecutor != null && !databaseExecutor.isShutdown()) { databaseExecutor.shutdown(); Log.d(TAG, "Database executor shut down requested."); }
        Log.d(TAG,"MonitoringService destroyed.");
    }


    // --- Initialization Helpers ---
    private void initializeClipboardMonitoring() {
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipChangedListener = () -> { Log.i(TAG, "Clipboard content changed!"); handleClipboardChange(); };
            clipboardManager.addPrimaryClipChangedListener(clipChangedListener);
            Log.d(TAG, "Clipboard listener added.");
        } else { Log.e(TAG, "ClipboardManager is null."); }
    }

    private void initializeCameraMonitoring() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager != null) {
            cameraCallback = new CameraManager.AvailabilityCallback() {
                @Override public void onCameraAvailable(@NonNull String id) { super.onCameraAvailable(id); Log.i(TAG, "Cam ["+id+"] available."); }
                @Override public void onCameraUnavailable(@NonNull String id) { super.onCameraUnavailable(id); Log.w(TAG, "Cam ["+id+"] unavailable!"); handleSensorUsage("CAMERA"); }
            };
            try { cameraManager.registerAvailabilityCallback(cameraCallback, new Handler(Looper.getMainLooper())); Log.d(TAG, "Camera callback registered."); }
            catch (Exception e){ Log.e(TAG, "Error registering camera callback", e); }
        } else { Log.e(TAG, "CameraManager is null."); }
    }

    private void initializeLocationMonitoring() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Location permission OK, requesting updates.");
                try {
                    long minTimeMs = 15000; float minDistanceM = 50;
                    boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                    boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                    if (networkEnabled) locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTimeMs, minDistanceM, this);
                    if (gpsEnabled) locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimeMs, minDistanceM, this);
                    if (networkEnabled || gpsEnabled) Log.d(TAG, "Requested location updates.");
                    else Log.w(TAG, "No location providers enabled.");
                } catch (SecurityException e) { Log.e(TAG, "SecurityException requesting location?", e); }
            } else { Log.w(TAG, "Location permission NOT granted."); }
        } else { Log.e(TAG, "LocationManager is null."); }
    }

    private void initializePermissionStatusMonitoring() {
        permissionStatusPrefs = getSharedPreferences(PERM_STATUS_PREFS_NAME, Context.MODE_PRIVATE);
        startPermissionStatusMonitoring();
    }


    // --- Core Logic Methods ---

    /** Handles Clipboard Change Event. */
    private void handleClipboardChange() {
        Log.d(TAG, "Handling clipboard change event...");
        String foregroundAppPackage = getForegroundAppPackageName();
        if (isValidStateForDbOperation(foregroundAppPackage)) {
            Log.i(TAG, "Clipboard accessed likely by: " + foregroundAppPackage);
            saveRecommendationToDb(foregroundAppPackage, "CLIPBOARD");
        } else { logFailureReason("clipboard change", foregroundAppPackage); }
    }

    /** Handles Sensor Usage Event (Camera, Location, Microphone). */
    private void handleSensorUsage(String sensorType) {
        Log.d(TAG, "Handling sensor usage event for: " + sensorType);
        String foregroundAppPackage = getForegroundAppPackageName();
        if (isValidStateForDbOperation(foregroundAppPackage)) {
            Log.i(TAG, sensorType + " accessed likely by: " + foregroundAppPackage);
            // TODO: Implement rate limiting
            saveRecommendationToDb(foregroundAppPackage, sensorType);
        } else { logFailureReason(sensorType + " usage", foregroundAppPackage); }
    }

    /** Saves a recommendation to the database on a background thread. */
    private void saveRecommendationToDb(String packageName, String eventType) {
        final String finalPackageName = packageName;
        final String finalEventType = eventType; // e.g., CAMERA
        final String recommendationType = eventType + "_ACCESS"; // e.g., CAMERA_ACCESS

        databaseExecutor.execute(() -> {
            Log.d(TAG, "BG Task: Saving " + finalEventType + " recommendation for " + finalPackageName);
            String appName = getAppNameFromPackage(finalPackageName);
            String title = finalEventType.substring(0, 1).toUpperCase() + finalEventType.substring(1).toLowerCase() + " Access";
            String description = "'" + appName + "' accessed your " + finalEventType.toLowerCase();
            Recommendation rec = new Recommendation(title, description, recommendationType, finalPackageName);
            try {
                recommendationDao.insertRecommendation(rec);
                Log.d(TAG, "BG Task: Saved " + recommendationType + " recommendation for: " + appName);
            } catch (Exception e) { Log.e(TAG, "BG Task: Error inserting " + recommendationType + " rec for " + finalPackageName, e); }
        });
    }

    /** Checks if DAO, Executor are ready and package name is valid. */
    private boolean isValidStateForDbOperation(String packageName) {
        return packageName != null && recommendationDao != null && databaseExecutor != null && !databaseExecutor.isShutdown();
    }

    /** Logs reasons why a DB operation might fail. */
    private void logFailureReason(String eventDescription, String packageName) {
        if (packageName == null) Log.w(TAG, "Could not determine foreground app during " + eventDescription + ".");
        if (recommendationDao == null) Log.e(TAG, "RecommendationDao is null during " + eventDescription + ".");
        if (databaseExecutor == null || databaseExecutor.isShutdown()) Log.e(TAG, "DB Executor invalid during " + eventDescription + ".");
    }

    // --- LocationListener Implementation ---
    @Override public void onLocationChanged(@NonNull Location location) { Log.w(TAG, "Location changed event."); handleSensorUsage("LOCATION"); }

    // --- FIX FOR ANDROID 7.0 CRASH ---
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // This is required for older Android versions (API 24/25) even though it's deprecated.
        Log.d(TAG, "Location status changed for provider " + provider + ": status=" + status);
    }
    // --- END FIX ---

    @Override public void onProviderEnabled(@NonNull String provider) { Log.d(TAG, "Location provider enabled: " + provider); }
    @Override public void onProviderDisabled(@NonNull String provider) { Log.d(TAG, "Location provider disabled: " + provider); }
    // --- End LocationListener ---

    // --- Microphone Monitoring Methods ---
    private void startMicMonitoring() {
        Log.d(TAG, "Starting mic monitoring timer.");
        if (micCheckTimer != null) micCheckTimer.cancel();
        micCheckTimer = new Timer();
        micCheckTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() { checkMicrophoneStatus(); }
        }, 5000, MIC_CHECK_INTERVAL_MS); // Start after 5 sec, repeat
    }

    private void stopMicMonitoring() {
        Log.d(TAG, "Stopping mic monitoring timer.");
        if (micCheckTimer != null) { micCheckTimer.cancel(); micCheckTimer = null; }
        isMicInUse = false;
    }

    private void checkMicrophoneStatus() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (isMicInUse) isMicInUse = false;
            return; // Cannot check without permission
        }
        boolean micBusy = isMicrophoneInUse();
        if (micBusy && !isMicInUse) {
            Log.w(TAG, "Microphone appears IN USE!"); isMicInUse = true; handleSensorUsage("MICROPHONE");
        } else if (!micBusy && isMicInUse) {
            Log.i(TAG, "Microphone appears FREE."); isMicInUse = false;
        }
    }

    // Includes the permission check fix
    private boolean isMicrophoneInUse() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        AudioRecord audioRecord = null; boolean isInUse = false;
        try {
            int sr=8000; int ch=AudioFormat.CHANNEL_IN_MONO; int fmt=AudioFormat.ENCODING_PCM_16BIT;
            int bufferSize = AudioRecord.getMinBufferSize(sr, ch, fmt);
            if (bufferSize <= 0) { Log.e(TAG, "Mic check: Invalid buffer size: " + bufferSize); return false; }
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sr, ch, fmt, bufferSize);
            audioRecord.startRecording();
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) { isInUse = false; audioRecord.stop(); }
            else { Log.w(TAG, "Mic check: State not RECORDING."); isInUse = true; }
        } catch (Exception e) { Log.w(TAG, "Mic check: Exception, assuming mic in use: " + e.getMessage()); isInUse = true;
        } finally { if (audioRecord != null) { try { audioRecord.release(); } catch (Exception e) { Log.e(TAG, "Mic check: Error releasing", e); }}}
        return isInUse;
    }
    // --- End Microphone Methods ---

    // --- Permission Status Monitoring Methods ---
    private void startPermissionStatusMonitoring() {
        Log.d(TAG, "Starting permission status monitoring timer.");
        if (permissionCheckTimer != null) permissionCheckTimer.cancel();
        permissionCheckTimer = new Timer();
        permissionCheckTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                if (databaseExecutor != null && !databaseExecutor.isShutdown()) {
                    databaseExecutor.execute(MonitoringService.this::checkPermissionStatusChanges);
                } else { Log.w(TAG, "Executor invalid, skipping perm status check."); }
            }
        }, 5 * 60 * 1000, PERMISSION_CHECK_INTERVAL_MS); // Start after 5 mins, repeat
    }

    private void stopPermissionStatusMonitoring() {
        Log.d(TAG, "Stopping permission status monitoring timer.");
        if (permissionCheckTimer != null) { permissionCheckTimer.cancel(); permissionCheckTimer = null; }
    }

    /** Checks grant status of key permissions for user apps, compares against last known status. Runs in background. */
    private void checkPermissionStatusChanges() {
        Log.d(TAG, "BG Task: Checking permission status changes...");
        Context context = getApplicationContext();
        PackageManager pm = context.getPackageManager();
        RecommendationDao recDao = AppDatabase.getDatabase(context).recommendationDao();
        if (recDao == null) { Log.e(TAG, "BG Task: DAO is null, cannot check perm status."); return; }


        Map<String, Map<String, Boolean>> lastStatuses = loadPermissionStatuses();
        Map<String, Map<String, Boolean>> currentStatuses = new HashMap<>();
        List<PackageInfo> installedApps;
        try { installedApps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS); }
        catch (Exception e) { Log.e(TAG, "BG Task: Failed to get packages for status check.", e); return; }

        for (PackageInfo pkgInfo : installedApps) {
            try {
                if (pkgInfo != null && pkgInfo.applicationInfo != null && (pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    String packageName = pkgInfo.packageName;
                    Map<String, Boolean> appCurrentStatus = new HashMap<>();
                    Map<String, Boolean> appLastStatus = lastStatuses.getOrDefault(packageName, new HashMap<>());

                    for (String permission : KEY_PERMISSIONS_TO_MONITOR) {
                        boolean isGranted = (pm.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED);
                        appCurrentStatus.put(permission, isGranted);
                        boolean wasGranted = appLastStatus.getOrDefault(permission, false);

                        if (isGranted != wasGranted) { // If status changed
                            Log.w(TAG, "PERMISSION " + (isGranted ? "GRANTED" : "REVOKED") + ": " + packageName + " -> " + permission);
                            String appName = getAppNameFromPackage(packageName);
                            savePermissionChangeRecommendation(recDao, appName, packageName, permission, isGranted);
                        }
                    }
                    currentStatuses.put(packageName, appCurrentStatus);
                }
            } catch (Exception e) { Log.e(TAG, "BG Task: Error checking perm status for pkg: " + (pkgInfo != null ? pkgInfo.packageName : "null"), e); }
        }
        savePermissionStatuses(currentStatuses);
        Log.d(TAG, "BG Task: Finished checking permission statuses.");
    }

    private void savePermissionChangeRecommendation(RecommendationDao dao, String appName, String packageName, String permission, boolean granted) {
        try {
            String simplePermName = formatPermissionName(permission);
            String title = "Permission " + (granted ? "Granted" : "Revoked");
            String description = "'" + appName + "' " + (granted ? "granted" : "revoked") + ": " + simplePermName;
            String type = granted ? RECOMMENDATION_TYPE_PERM_GRANTED : RECOMMENDATION_TYPE_PERM_REVOKED;
            Recommendation rec = new Recommendation(title, description, type, packageName);
            dao.insertRecommendation(rec);
            Log.d(TAG, "BG Task: Saved perm change rec: " + description);
        } catch (Exception e) { Log.e(TAG, "BG Task: Error saving perm change rec for " + packageName, e); }
    }

    // --- SharedPreferences Helpers for Permission Status ---
    private Map<String, Map<String, Boolean>> loadPermissionStatuses() {
        String json = permissionStatusPrefs.getString(PERM_STATUS_KEY, null);
        if (json != null) {
            try {
                Type type = new TypeToken<HashMap<String, HashMap<String, Boolean>>>(){}.getType();
                return gson.fromJson(json, type);
            } catch (Exception e) { Log.e(TAG, "Error loading perm statuses from JSON", e); }
        }
        return new HashMap<>();
    }

    private void savePermissionStatuses(Map<String, Map<String, Boolean>> statuses) {
        try {
            String json = gson.toJson(statuses);
            permissionStatusPrefs.edit().putString(PERM_STATUS_KEY, json).apply();
        } catch (Exception e) { Log.e(TAG, "Error saving perm statuses to JSON", e); }
    }


    // --- General Helper Methods ---

    /** Checks if Usage Stats permission is granted. */
    private boolean hasUsageStatsPermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
        boolean hasPermission = (mode == AppOpsManager.MODE_ALLOWED);
        return hasPermission;
    }

    /** Gets the likely foreground app package name using UsageStatsManager. */
    private String getForegroundAppPackageName() {
        if (!hasUsageStatsPermission(this)) { return null; }
        UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) { Log.e(TAG, "UsageStatsManager is null."); return null; }
        long currentTime = System.currentTimeMillis();
        List<UsageStats> stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, currentTime - 10 * 1000, currentTime);
        String recentPackage = null;
        if (stats != null && !stats.isEmpty()) {
            try {
                stats.sort(Comparator.comparingLong(UsageStats::getLastTimeUsed).reversed());
                recentPackage = stats.get(0).getPackageName();
            } catch (Exception e) { Log.e(TAG, "Error sorting usage stats.", e); }
        }
        return recentPackage;
    }

    /** Gets the user-friendly application name for a given package name. */
    private String getAppNameFromPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return "Unknown App";
        PackageManager pm = getApplicationContext().getPackageManager();
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            return appInfo.loadLabel(pm).toString();
        } catch (Exception e) { Log.w(TAG, "App name not found for package: " + packageName); return packageName; }
    }

    /** Creates the notification channel for the foreground service. */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "GuardianAI Monitoring"; String description = "GuardianAI active monitoring status";
            int importance = NotificationManager.IMPORTANCE_LOW; NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description); channel.setSound(null, null); channel.enableVibration(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel); else Log.e(TAG, "NotificationManager null.");
        }
    }

    /** Builds the persistent notification for the foreground service. */
    private Notification createForegroundNotification() {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GuardianAI Active")
                .setContentText("Monitoring device activity.")
                .setSmallIcon(R.drawable.ic_security)
                .setContentIntent(pi)
                .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build();
    }

    /** Formats full permission names to shorter versions (e.g., CAMERA). */
    private String formatPermissionName(String fullPermissionName) {
        if (fullPermissionName == null) return "";
        int lastDot = fullPermissionName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fullPermissionName.length() - 1) {
            return fullPermissionName.substring(lastDot + 1);
        }
        return fullPermissionName;
    }

} // End of MonitoringService class
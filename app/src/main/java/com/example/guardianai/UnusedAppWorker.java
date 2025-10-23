package com.example.guardianai;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences; // Import SharedPreferences
import android.content.pm.ApplicationInfo; // Import ApplicationInfo
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList; // Import ArrayList
import java.util.HashSet;   // Import HashSet
import java.util.List;
import java.util.Set;      // Import Set
import java.util.Calendar;   // Import Calendar if needed for different time ranges

public class UnusedAppWorker extends Worker {

    private static final String TAG = "UnusedAppWorker";
    // Define 30 days in milliseconds for checking usage
    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;

    public UnusedAppWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting unused risky app check...");
        Context context = getApplicationContext();

        // Check for necessary permissions before proceeding (Usage Stats)
        // Note: The worker itself cannot request permissions. Permission must be granted beforehand.
        if (!hasUsageStatsPermission(context)) {
            Log.e(TAG, "Usage Stats permission not granted. Worker cannot proceed.");
            // Return failure as the core functionality cannot be performed
            return Result.failure();
        }


        PackageManager pm = context.getPackageManager();
        PermissionAnalyzer analyzer = new PermissionAnalyzer();

        // --- Get Usage Stats ---
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long currentTime = System.currentTimeMillis();
        // Query stats going back 30 days from now
        List<UsageStats> stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, currentTime - THIRTY_DAYS_MS, currentTime);

        if (stats == null) { // Note: queryUsageStats might return null
            Log.w(TAG, "UsageStatsManager returned null stats list.");
            return Result.failure(); // Indicate failure if stats are unavailable
        }

        // --- Get Installed Apps with Permissions ---
        List<PackageInfo> installedApps;
        try {
            installedApps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS | PackageManager.GET_META_DATA);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get installed packages", e);
            return Result.failure(); // Cannot proceed without the app list
        }


        // --- Find Unused Risky Apps ---
        List<String> unusedRiskyApps = new ArrayList<>(); // Store package names
        long thirtyDaysAgoTimestamp = currentTime - THIRTY_DAYS_MS; // Timestamp 30 days ago

        for (PackageInfo pkgInfo : installedApps) {
            try {
                // Check if it's a user-installed app
                if (pkgInfo != null && pkgInfo.applicationInfo != null && (pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    String packageName = pkgInfo.packageName;
                    boolean hasRiskyPermission = false;

                    // Check if it has HIGH or MEDIUM risk permissions
                    String[] permissions = pkgInfo.requestedPermissions;
                    if (permissions != null) {
                        for (String perm : permissions) {
                            PermissionAnalyzer.RiskLevel risk = analyzer.getPermissionRisk(perm);
                            if (risk == PermissionAnalyzer.RiskLevel.HIGH || risk == PermissionAnalyzer.RiskLevel.MEDIUM) {
                                hasRiskyPermission = true;
                                break; // Found one risky permission, no need to check others for this app
                            }
                        }
                    }

                    if (hasRiskyPermission) {
                        // Now check if it's unused by looking through the stats
                        long lastTimeUsed = 0;
                        for (UsageStats usageStat : stats) {
                            if (usageStat.getPackageName().equals(packageName)) {
                                // Find the latest time this app was used within the queried interval
                                lastTimeUsed = Math.max(lastTimeUsed, usageStat.getLastTimeUsed());
                            }
                        }

                        // If last used was over 30 days ago (or never used within the interval, remaining 0)
                        if (lastTimeUsed < thirtyDaysAgoTimestamp) {
                            Log.d(TAG, "Found unused risky app: " + packageName + " (Last used timestamp: " + lastTimeUsed + ")");
                            unusedRiskyApps.add(packageName); // Add package name to the list
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing package in worker: " + (pkgInfo != null ? pkgInfo.packageName : "null package info"), e);
                // Continue to the next app
            }
        } // End of app loop

        Log.d(TAG, "Unused app check finished. Found " + unusedRiskyApps.size() + " apps.");

        // --- SAVE RESULTS TO SHAREDPREFERENCES ---
        try {
            SharedPreferences prefs = context.getSharedPreferences("GuardianAIPrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            // Store the list as a Set of strings (SharedPreferences limitation)
            editor.putStringSet("unused_risky_apps", new HashSet<>(unusedRiskyApps));
            editor.apply(); // Save asynchronously
            Log.d(TAG, "Saved " + unusedRiskyApps.size() + " unused risky apps to SharedPreferences.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save unused apps to SharedPreferences", e);
            return Result.failure(); // Indicate failure if saving failed
        }
        // --- END SAVE ---

        // Indicate that the work finished successfully
        return Result.success();
    }

    // Helper function to check Usage Stats permission (copied from MainActivity for self-containment if needed,
    // but ideally the worker relies on the permission already being granted)
    private boolean hasUsageStatsPermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            return false;
        }
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

} // End of UnusedAppWorker class
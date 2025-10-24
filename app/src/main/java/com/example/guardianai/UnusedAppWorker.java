package com.example.guardianai;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences; // Import SharedPreferences
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.guardianai.AppDatabase;
import com.example.guardianai.Recommendation;
import com.example.guardianai.RecommendationDao;

import java.util.ArrayList;
import java.util.HashSet; // Keep if needed elsewhere, maybe remove later
import java.util.List;
import java.util.Set; // Keep if needed elsewhere, maybe remove later
// import java.util.Calendar; // Remove if not needed

public class UnusedAppWorker extends Worker {

    private static final String TAG = "UnusedAppWorker";
    // Define the type identifier as a constant
    private static final String RECOMMENDATION_TYPE_UNUSED = "UNUSED_APP";

    public UnusedAppWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting unused risky app check worker...");
        Context context = getApplicationContext();

        // --- Permission Check ---
        if (!hasUsageStatsPermission(context)) {
            Log.e(TAG, "Usage Stats permission not granted. Worker cannot proceed.");
            return Result.failure(); // Cannot function without permission
        }

        // --- Setup ---
        PackageManager pm = context.getPackageManager();
        PermissionAnalyzer analyzer = new PermissionAnalyzer();
        RecommendationDao recommendationDao = AppDatabase.getDatabase(context).recommendationDao(); // Get DAO

        // --- Read User Setting for Threshold ---
        SharedPreferences prefs = context.getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
        // Read the saved number of days, defaulting to the value defined in SettingsFragment
        int thresholdDays = prefs.getInt(SettingsFragment.KEY_UNUSED_THRESHOLD_DAYS, SettingsFragment.DEFAULT_UNUSED_THRESHOLD_DAYS);
        // Calculate the threshold in milliseconds based on the user's setting
        long unusedThresholdMs = (long) thresholdDays * 24 * 60 * 60 * 1000;
        Log.d(TAG, "Using unused threshold: " + thresholdDays + " days (" + unusedThresholdMs + " ms)");
        // --- End Reading Setting ---

        // --- Clear Old Unused App Recommendations ---
        try {
            recommendationDao.deleteRecommendationsByType(RECOMMENDATION_TYPE_UNUSED);
            Log.d(TAG, "Cleared old " + RECOMMENDATION_TYPE_UNUSED + " recommendations from database.");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing old recommendations from database", e);
            // Continue even if clearing fails
        }

        // --- Get Usage Stats ---
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long currentTime = System.currentTimeMillis();
        // Query stats going back for the duration of the *user-defined* threshold
        List<UsageStats> stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, currentTime - unusedThresholdMs, currentTime);

        if (stats == null) {
            Log.w(TAG, "UsageStatsManager returned null stats list.");
            return Result.failure(); // Indicate failure if stats are unavailable
        }

        // --- Get Installed Apps ---
        List<PackageInfo> installedApps;
        try {
            installedApps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS | PackageManager.GET_META_DATA);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get installed packages", e);
            return Result.failure(); // Cannot proceed without the app list
        }

        // --- Find Unused Risky Apps & Save Recommendations ---
        int unusedRiskyFoundCount = 0;
        // Calculate the exact timestamp threshold based on the user's setting
        long usageThresholdTimestamp = currentTime - unusedThresholdMs;

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
                                break; // Found one risky permission, no need to check others
                            }
                        }
                    }

                    // Proceed only if the app has at least one risky permission
                    if (hasRiskyPermission) {
                        // Check if it's unused by looking through the usage stats
                        long lastTimeUsed = 0;
                        for (UsageStats usageStat : stats) {
                            if (usageStat.getPackageName().equals(packageName)) {
                                // Find the latest time this app was used within the queried interval
                                lastTimeUsed = Math.max(lastTimeUsed, usageStat.getLastTimeUsed());
                            }
                        }

                        // Compare last used time against the *user-defined* threshold timestamp
                        if (lastTimeUsed < usageThresholdTimestamp) {
                            Log.d(TAG, "Found unused risky app (Threshold: " + thresholdDays + " days): " + packageName + " (Last used: " + lastTimeUsed + ")");

                            // --- SAVE Recommendation to DB ---
                            try {
                                String appName = pkgInfo.applicationInfo.loadLabel(pm).toString();
                                // Create a user-friendly description
                                String description = "Review unused permissions for '" + appName + "'";
                                Recommendation rec = new Recommendation(
                                        "Unused Permissions",      // Consistent title for this type
                                        description,               // Main text shown to user
                                        RECOMMENDATION_TYPE_UNUSED,// Type identifier
                                        packageName                // Associated package name for click action
                                );
                                recommendationDao.insertRecommendation(rec); // Insert into DB
                                unusedRiskyFoundCount++; // Increment count
                            } catch (Exception dbException) {
                                Log.e(TAG, "Failed to insert recommendation for " + packageName, dbException);
                            }
                            // --- END SAVE ---
                        } // End if (unused)
                    } // End if (hasRiskyPermission)
                } // End if (user app)
            } catch (Exception e) {
                Log.e(TAG, "Error processing package in worker: " + (pkgInfo != null ? pkgInfo.packageName : "null package info"), e);
                // Continue processing other apps
            }
        } // End of app loop

        Log.d(TAG, "Unused app check finished. Found and saved " + unusedRiskyFoundCount + " recommendations to database.");

        // Indicate that the work finished successfully
        return Result.success();
    } // End doWork()

    // Helper function to check Usage Stats permission (remains the same)
    private boolean hasUsageStatsPermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            Log.e(TAG, "AppOpsManager service is not available.");
            return false;
        }
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

} // End of UnusedAppWorker class
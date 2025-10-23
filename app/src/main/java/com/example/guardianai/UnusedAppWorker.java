package com.example.guardianai;


import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class UnusedAppWorker extends Worker {

    private static final String TAG = "UnusedAppWorker";
    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000; // 30 days in milliseconds

    public UnusedAppWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting unused app check...");
        Context context = getApplicationContext();
        PackageManager pm = context.getPackageManager();
        PermissionAnalyzer analyzer = new PermissionAnalyzer();

        // --- Get Usage Stats ---
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long currentTime = System.currentTimeMillis();
        // Query stats for the last 30 days (adjust as needed)
        List<UsageStats> stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, currentTime - THIRTY_DAYS_MS, currentTime);

        if (stats == null || stats.isEmpty()) {
            Log.w(TAG, "No usage stats found. Does the app have permission?");
            // You might return failure or retry depending on why stats are missing
            return Result.failure();
        }

        // --- Get Installed Apps with Permissions ---
        List<PackageInfo> installedApps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);

        // --- Find Unused Risky Apps ---
        List<String> unusedRiskyApps = new ArrayList<>(); // Store package names
        long thirtyDaysAgo = currentTime - THIRTY_DAYS_MS;

        for (PackageInfo pkgInfo : installedApps) {
            // Check if user app
            if ((pkgInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
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
                    // Now check if it's unused
                    long lastTimeUsed = 0;
                    for (UsageStats usageStat : stats) {
                        if (usageStat.getPackageName().equals(packageName)) {
                            lastTimeUsed = usageStat.getLastTimeUsed();
                            break;
                        }
                    }

                    // If last used was over 30 days ago (or never used, which is 0)
                    if (lastTimeUsed < thirtyDaysAgo) {
                        Log.d(TAG, "Found unused risky app: " + packageName + " (Last used: " + lastTimeUsed + ")");
                        unusedRiskyApps.add(packageName);
                        // TODO: Add a specific recommendation to your Database here!
                        // Recommendation format: "Review unused permissions for [AppName]"
                    }
                }
            }
        }

        Log.d(TAG, "Unused app check finished. Found " + unusedRiskyApps.size() + " apps.");
        // Store results or generate notifications/recommendations here
        // Example: Save to Room DB, which DashboardFragment reads

        return Result.success();
    }
}
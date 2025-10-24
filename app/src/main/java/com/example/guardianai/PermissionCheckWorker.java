package com.example.guardianai;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent; // Needed for notification action
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings; // Needed for settings intent
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data; // Needed for input/output data
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.guardianai.AppDatabase;
import com.example.guardianai.AppPermissions;
import com.example.guardianai.AppPermissionsDao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PermissionCheckWorker extends Worker {

    private static final String TAG = "PermissionCheckWorker";
    public static final String KEY_PACKAGE_NAME = "PACKAGE_NAME"; // Key for input data
    private static final String CHANNEL_ID = "GuardianAI_Escalation"; // Same channel ID
    private static int notificationId = 1001; // Simple ID incrementer

    public PermissionCheckWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Get the package name passed from the receiver
        String packageName = getInputData().getString(KEY_PACKAGE_NAME);
        if (packageName == null || packageName.isEmpty()) {
            Log.e(TAG, "Package name is missing in input data. Cannot proceed.");
            return Result.failure();
        }
        Log.d(TAG, "Worker started for package: " + packageName);

        Context context = getApplicationContext();
        PackageManager pm = context.getPackageManager();
        PermissionAnalyzer analyzer = new PermissionAnalyzer();
        AppPermissionsDao dao = AppDatabase.getDatabase(context).appPermissionsDao();

        try {
            // --- Get NEW permissions ---
            PackageInfo newInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            String[] newPermissionsArray = newInfo.requestedPermissions;
            Set<String> newPermissions = (newPermissionsArray != null) ? new HashSet<>(Arrays.asList(newPermissionsArray)) : new HashSet<>();
            String newPermissionsString = String.join(",", newPermissions);

            // --- Get OLD permissions from DB ---
            String oldPermissionsString = dao.getPermissionsForApp(packageName);
            Set<String> oldPermissions = new HashSet<>();
            if (oldPermissionsString != null && !oldPermissionsString.isEmpty()) {
                oldPermissions.addAll(Arrays.asList(oldPermissionsString.split(",")));
            }
            Log.d(TAG, "Old permissions for " + packageName + ": " + oldPermissions.size());
            Log.d(TAG, "New permissions for " + packageName + ": " + newPermissions.size());


            // --- Find Added Permissions ---
            Set<String> addedPermissions = new HashSet<>(newPermissions);
            addedPermissions.removeAll(oldPermissions); // Keep only what's new

            if (!addedPermissions.isEmpty()) {
                Log.d(TAG, "Permissions added for " + packageName + ": " + addedPermissions);
                List<String> escalatedRiskyPermissions = new ArrayList<>();

                // Check if any added permissions are risky
                for (String addedPerm : addedPermissions) {
                    PermissionAnalyzer.RiskLevel risk = analyzer.getPermissionRisk(addedPerm);
                    if (risk == PermissionAnalyzer.RiskLevel.HIGH || risk == PermissionAnalyzer.RiskLevel.MEDIUM) {
                        Log.w(TAG, "RISKY PERMISSION ESCALATION: " + packageName + " added " + addedPerm);
                        escalatedRiskyPermissions.add(addedPerm);
                    }
                }

                // If risky permissions were added, send notification
                if (!escalatedRiskyPermissions.isEmpty()) {
                    String appLabel = newInfo.applicationInfo.loadLabel(pm).toString();
                    sendEscalationNotification(context, appLabel, packageName, escalatedRiskyPermissions);
                }
            } else {
                Log.d(TAG, "No permissions added for " + packageName + " during update.");
            }

            // --- Update the database with the new permissions list ---
            AppPermissions updatedEntry = new AppPermissions(packageName, newPermissionsString);
            dao.insertOrUpdateAppPermissions(updatedEntry);
            Log.d(TAG, "Updated permissions in DB for " + packageName);

            return Result.success(); // Work completed successfully

        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not get package info for updated app: " + packageName, e);
            // App might have been uninstalled between update broadcast and worker running
            // Consider removing from DB if appropriate
            // dao.deleteAppPermissions(packageName);
            return Result.failure(); // Indicate failure
        } catch (Exception e) {
            Log.e(TAG, "Error during permission comparison worker for " + packageName, e);
            return Result.failure(); // Indicate failure
        }
    }

    // --- Notification Logic (Moved from Receiver) ---
    private void sendEscalationNotification(Context context, String appName, String packageName, List<String> riskyPermissions) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Create Notification Channel (Required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Permission Escalation Alerts";
            String description = "Alerts when apps request new risky permissions after updating";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Ensure notification manager is not null before creating channel
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created or already exists.");
            } else {
                Log.e(TAG, "NotificationManager is null, cannot create channel.");
                return; // Cannot proceed without manager
            }
        }

        // Build the Notification
        String title = "Permission Alert: " + appName;
        String content = "Added risky permissions: " + String.join(", ", riskyPermissions);

        // --- Create Intent to open App Settings when notification is tapped ---
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", packageName, null);
        intent.setData(uri);
        // Create PendingIntent
        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                packageName.hashCode(), // Use package name hashcode as request code for uniqueness
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); // Use IMMUTABLE flag


        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_security) // Ensure you have this icon
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent) // Set the PendingIntent
                .setAutoCancel(true);

        // Show the Notification
        if (notificationManager != null) {
            notificationManager.notify(notificationId++, builder.build()); // Use unique ID
            Log.d(TAG, "Sent notification for " + packageName);
        } else {
            Log.e(TAG, "NotificationManager is null, cannot send notification.");
        }
    }
}
package com.example.guardianai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri; // Needed for package URI
import android.util.Log;

// --- WorkManager Imports ---
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
// --- End WorkManager Imports ---

public class AppUpdateReceiver extends BroadcastReceiver {

    private static final String TAG = "AppUpdateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            Log.w(TAG, "Received null intent or action.");
            return;
        }

        // Check if the intent action is for package replacement (update)
        if (Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data == null) {
                Log.w(TAG, "Intent data is null for package update.");
                return;
            }
            // Extract the package name from the intent data (e.g., "package:com.example.app")
            String updatedPackageName = data.getEncodedSchemeSpecificPart();
            if (updatedPackageName == null || updatedPackageName.isEmpty()) {
                Log.w(TAG, "Could not get package name from update intent URI.");
                return;
            }

            Log.d(TAG, "App updated: " + updatedPackageName + ". Enqueuing PermissionCheckWorker.");

            // --- Create Input Data containing the package name for the Worker ---
            Data inputData = new Data.Builder()
                    .putString(PermissionCheckWorker.KEY_PACKAGE_NAME, updatedPackageName)
                    .build();

            // --- Create a One-Time Work Request to run the PermissionCheckWorker ---
            OneTimeWorkRequest checkWorkRequest =
                    new OneTimeWorkRequest.Builder(PermissionCheckWorker.class)
                            .setInputData(inputData)
                            // Optionally add constraints here (e.g., require network, battery not low)
                            // .setConstraints(new Constraints.Builder()... .build())
                            .build();

            // --- Enqueue the Work Request with WorkManager ---
            try {
                WorkManager.getInstance(context).enqueue(checkWorkRequest);
                Log.d(TAG, "Successfully enqueued PermissionCheckWorker for " + updatedPackageName);
            } catch (Exception e) {
                Log.e(TAG, "Error enqueuing PermissionCheckWorker for " + updatedPackageName, e);
                // Handle potential exceptions during enqueueing
            }

        }
        // TODO: You might want to handle ACTION_PACKAGE_REMOVED here as well
        // else if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
        //     Uri data = intent.getData();
        //     String removedPackageName = data.getEncodedSchemeSpecificPart();
        //     Log.d(TAG, "App uninstalled: " + removedPackageName);
        //     // Schedule a worker or directly remove the app's permissions from your database
        // }
    }

    // Note: The comparePermissions and sendEscalationNotification methods
    // have been moved to PermissionCheckWorker.java
}
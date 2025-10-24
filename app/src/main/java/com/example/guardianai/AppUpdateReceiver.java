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

// --- Database Imports ---
import com.example.guardianai.AppDatabase;
// --- End Database Imports ---


public class AppUpdateReceiver extends BroadcastReceiver {

    private static final String TAG = "AppUpdateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Basic safety checks
        if (context == null || intent == null || intent.getAction() == null) {
            Log.w(TAG, "Received null context, intent, or action in onReceive.");
            return;
        }

        Uri data = intent.getData();
        if (data == null) {
            Log.w(TAG, "Intent data (URI) is null for package event.");
            return;
        }
        // Extract the package name from the URI (e.g., "package:com.example.app")
        String packageName = data.getEncodedSchemeSpecificPart();
        if (packageName == null || packageName.isEmpty()) {
            Log.w(TAG, "Could not get package name from intent URI.");
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action + " for package: " + packageName);

        // --- Handle Different Package-Related Actions ---

        if (Intent.ACTION_PACKAGE_ADDED.equals(action) || Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            // --- App Installed or Updated ---
            // We need to check/update its permissions record in the database.
            // Schedule a background worker to handle this.
            Log.d(TAG, "App added or replaced: " + packageName + ". Enqueuing PermissionCheckWorker.");

            // Create Input Data containing the package name for the Worker
            Data inputData = new Data.Builder()
                    .putString(PermissionCheckWorker.KEY_PACKAGE_NAME, packageName)
                    .build();

            // Create a One-Time Work Request to run the PermissionCheckWorker
            OneTimeWorkRequest checkWorkRequest =
                    new OneTimeWorkRequest.Builder(PermissionCheckWorker.class)
                            .setInputData(inputData)
                            // Optionally add constraints (e.g., require network) if needed
                            // .setConstraints(new Constraints.Builder()... .build())
                            .build();

            // Enqueue the Work Request with WorkManager
            try {
                // Use application context to avoid potential leaks if receiver context is short-lived
                WorkManager.getInstance(context.getApplicationContext()).enqueue(checkWorkRequest);
                Log.d(TAG, "Successfully enqueued PermissionCheckWorker for " + packageName);
            } catch (Exception e) {
                Log.e(TAG, "Error enqueuing PermissionCheckWorker for " + packageName, e);
                // Consider how to handle enqueueing errors (e.g., retry logic?)
            }

        } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            // --- App Uninstalled ---
            // We need to clean up any stored data related to this app.
            Log.d(TAG, "App removed: " + packageName + ". Cleaning up database entries.");

            // --- Clean up DB in a background thread ---
            // Using a simple thread here. For robustness, WorkManager could also be used.
            final Context appContext = context.getApplicationContext(); // Use application context
            new Thread(() -> {
                Log.d(TAG, "Background thread started for DB cleanup: " + packageName);
                try {
                    AppDatabase db = AppDatabase.getDatabase(appContext);
                    // Delete the app's permission record
                    db.appPermissionsDao().deleteAppPermissions(packageName);
                    // Delete any recommendations specifically associated with this app
                    db.recommendationDao().deleteRecommendationsByPackage(packageName);
                    Log.d(TAG, "Database cleaned up successfully for removed package: " + packageName);
                } catch (Exception e) {
                    Log.e(TAG, "Error cleaning up database for removed package: " + packageName, e);
                }
            }).start();
            // --- End DB Cleanup ---
        } else {
            // Log if we receive an unexpected action for a package URI
            Log.w(TAG, "Received unexpected action: " + action + " for package: " + packageName);
        }
    } // End onReceive

} // End AppUpdateReceiver class
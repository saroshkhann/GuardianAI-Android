package com.example.guardianai;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.work.ExistingPeriodicWorkPolicy; // Import WorkManager classes
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import android.app.AppOpsManager; // Import AppOpsManager
import android.content.Context; // Import Context
import android.content.Intent; // Import Intent
import android.net.Uri; // Import Uri
import android.os.Bundle;
import android.os.Process; // Import Process
import android.provider.Settings; // Import Settings
import android.util.Log; // Import Log
import android.widget.Toast; // Import Toast

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.concurrent.TimeUnit; // Import TimeUnit

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity"; // Tag for logging

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate started.");

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        // --- Load the default fragment (DashboardFragment) ---
        if (savedInstanceState == null) {
            Log.d(TAG, "Loading initial DashboardFragment.");
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new DashboardFragment())
                    .commit();
        }

        // --- Set up the bottom navigation listener ---
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();
            Log.d(TAG, "BottomNav item selected: " + item.getTitle());

            if (itemId == R.id.nav_home) {
                selectedFragment = new DashboardFragment();
            } else if (itemId == R.id.nav_permissions) {
                // TODO: Create and assign PermissionsFragment
                // selectedFragment = new PermissionsFragment();
                Toast.makeText(this, "Permissions Clicked (Not Implemented)", Toast.LENGTH_SHORT).show(); // Placeholder
            } else if (itemId == R.id.nav_controls) {
                // TODO: Create and assign ControlsFragment
                // selectedFragment = new ControlsFragment();
                Toast.makeText(this, "Controls Clicked (Not Implemented)", Toast.LENGTH_SHORT).show(); // Placeholder
            } else if (itemId == R.id.nav_settings) {
                // TODO: Create and assign SettingsFragment
                // selectedFragment = new SettingsFragment();
                Toast.makeText(this, "Settings Clicked (Not Implemented)", Toast.LENGTH_SHORT).show(); // Placeholder
            }

            // Replace the fragment container with the selected fragment
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }
            return true; // Return true to display the item as the selected item
        });

        // --- Check for Usage Stats Permission and Schedule Background Worker ---
        checkAndScheduleUsageWorker();

        Log.d(TAG, "onCreate finished.");
    }

    // --- Method to Check Permission and Schedule Worker ---
    private void checkAndScheduleUsageWorker() {
        Log.d(TAG, "Checking Usage Stats permission...");
        if (!hasUsageStatsPermission()) {
            Log.d(TAG, "Usage Stats permission NOT granted. Requesting...");
            requestUsageStatsPermission();
            // Note: The worker won't run effectively without this permission.
            // You might want to disable features or show a prompt until permission is granted.
        } else {
            Log.d(TAG, "Usage Stats permission already granted. Scheduling worker.");
            scheduleUnusedAppWorker(); // Permission granted, schedule the background task
        }
    }


    // --- Helper Function to Check Usage Stats Permission ---
    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            Log.e(TAG, "AppOpsManager is null. Cannot check permission.");
            return false; // Cannot check, assume false
        }
        // Check the specific operation permission for our app's user ID and package name
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
        // Return true only if the mode is explicitly allowed
        boolean hasPermission = (mode == AppOpsManager.MODE_ALLOWED);
        Log.d(TAG, "Usage Stats Permission Status: " + hasPermission);
        return hasPermission;
    }

    // --- Helper Function to Request Usage Stats Permission ---
    private void requestUsageStatsPermission() {
        // Explain to the user why you need the permission
        Toast.makeText(this, "GuardianAI needs Usage Access to find unused apps. Please enable it in the next screen.", Toast.LENGTH_LONG).show();
        try {
            // Create an Intent to open the system settings page for Usage Access
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open Usage Access Settings activity", e);
            Toast.makeText(this, "Could not open Usage Access settings automatically. Please grant manually.", Toast.LENGTH_LONG).show();
            // Optionally, provide manual instructions here
        }
    }

    // --- Helper Function to Schedule the Background Worker ---
    private void scheduleUnusedAppWorker() {
        try {
            // Schedule the check to run roughly once a day
            PeriodicWorkRequest unusedCheckRequest =
                    new PeriodicWorkRequest.Builder(UnusedAppWorker.class, 1, TimeUnit.DAYS)
                            // Add constraints if needed (e.g., run only on Wi-Fi, while charging)
                            // .setConstraints(constraints)
                            .build();

            // Enqueue the work, keeping the existing work if it's already scheduled
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "UnusedAppCheck", // Unique name for this task
                    ExistingPeriodicWorkPolicy.KEEP, // KEEP: If work exists, do nothing. REPLACE: Replace existing work.
                    unusedCheckRequest);

            Log.d(TAG, "Scheduled UnusedAppWorker successfully.");

        } catch (Exception e) {
            Log.e(TAG, "Error scheduling UnusedAppWorker", e);
            // Handle error, maybe inform user
        }
    }

    // --- Optional: Check Permission Again When User Returns ---
    @Override
    protected void onResume() {
        super.onResume();
        // This log helps check if the user granted the permission after being sent to settings.
        Log.d(TAG, "onResume: Current Usage Stats Permission Status: " + hasUsageStatsPermission());
        // You could potentially trigger the worker scheduling again here if it wasn't granted initially,
        // but the KEEP policy in scheduleUnusedAppWorker usually handles this well enough.
    }
} // End of MainActivity class
package com.example.guardianai; // Ensure this matches your package

import androidx.annotation.NonNull; // Import NonNull
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat; // Import ActivityCompat
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.work.ExistingPeriodicWorkPolicy; // Import WorkManager classes
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import android.Manifest; // Import Manifest for permission constants
import android.app.AlertDialog; // Import AlertDialog for rationale
import android.app.AppOpsManager; // Import AppOpsManager
import android.content.Context; // Import Context
import android.content.Intent; // Import Intent
import android.content.pm.PackageManager; // Import PackageManager
import android.net.Uri; // Import Uri
import android.os.Bundle;
import android.os.Process; // Import Process
import android.provider.Settings; // Import Settings
import android.util.Log; // Import Log
import android.widget.Toast; // Import Toast

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList; // Needed for checking multiple permissions
import java.util.List; // Needed for checking multiple permissions
import java.util.concurrent.TimeUnit; // Import TimeUnit

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity"; // Tag for logging
    // Unique request codes for permissions
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;
    private static final int MICROPHONE_PERMISSION_REQUEST_CODE = 102;
    // Request code for multiple permissions at once (if needed)
    private static final int ALL_PERMISSIONS_REQUEST_CODE = 100;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate started.");

        // --- Check ALL required dangerous permissions ---
        checkAndRequestNeededPermissions(); // Single function to handle multiple permissions

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

            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

            if (itemId == R.id.nav_home) {
                if (!(currentFragment instanceof DashboardFragment)) {
                    selectedFragment = new DashboardFragment();
                }
            } else if (itemId == R.id.nav_permissions) {
                // --- THIS IS THE FIX ---
                if (!(currentFragment instanceof PermissionFragment)) {
                    selectedFragment = new PermissionFragment();
                }
                // --- END OF FIX ---
            } else if (itemId == R.id.nav_controls) {
                // TODO: Create and assign ControlsFragment
                Toast.makeText(this, "Controls Clicked (Not Implemented)", Toast.LENGTH_SHORT).show();
            } else if (itemId == R.id.nav_settings) {
                if (!(currentFragment instanceof SettingsFragment)) {
                    selectedFragment = new SettingsFragment();
                }
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }
            return true;
        });

        // --- Check for Usage Stats Permission and Schedule Worker ---
        // This runs independently as it uses a different permission mechanism
        checkAndScheduleUsageWorker();

        Log.d(TAG, "onCreate finished.");
    } // End onCreate

    // --- Method to Check ALL Necessary Dangerous Permissions ---
    private void checkAndRequestNeededPermissions() {
        Log.d(TAG, "Checking dangerous permissions...");
        List<String> permissionsToRequest = new ArrayList<>();

        // Check Location
        boolean fineLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fineLocationGranted && !coarseLocationGranted) {
            Log.d(TAG, "Location permission needed.");
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        } else {
            Log.d(TAG, "Location permission already granted.");
            // If location is granted, we can try starting the service now
            startMonitoringServiceIfNeeded();
        }


        // Check Microphone
        boolean micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (!micGranted) {
            Log.d(TAG, "Microphone permission needed.");
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
        } else {
            Log.d(TAG, "Microphone permission already granted.");
        }

        // --- ADD THIS CRITICAL CAMERA CHECK ---
        boolean cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        if (!cameraGranted) {
            Log.d(TAG, "Camera permission needed.");
            permissionsToRequest.add(Manifest.permission.CAMERA);
        }

        // TODO: Check other permissions if needed (e.g., Camera, Storage)

        // If any permissions are missing, request them
        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "Requesting missing permissions: " + permissionsToRequest);
            // Consider showing rationale if needed (ActivityCompat.shouldShowRequestPermissionRationale)
            // For simplicity here, we request directly. Add rationale dialog if required.
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]), // Convert list to array
                    ALL_PERMISSIONS_REQUEST_CODE); // Use a single request code
        } else {
            Log.d(TAG, "All necessary dangerous permissions seem to be granted.");
            // Ensure service is started if all permissions were already granted on startup
            startMonitoringServiceIfNeeded();
        }
    }

    // --- Handle the result of the permission request(s) ---
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult called for request code: " + requestCode);

        if (requestCode == ALL_PERMISSIONS_REQUEST_CODE) {

            // --- INITIALIZE MISSING VARIABLE ---
            boolean locationGranted = false;
            boolean micGranted = false;
            boolean cameraGranted = false; // <-- CRITICAL FIX: Initialize here
            // --- END INITIALIZATION ---

            // Check results for each requested permission
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                int grantResult = grantResults[i];

                if (permission.equals(Manifest.permission.ACCESS_FINE_LOCATION) || permission.equals(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        locationGranted = true;
                    }
                } else if (permission.equals(Manifest.permission.RECORD_AUDIO)) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        micGranted = true;
                    }
                } else if (permission.equals(Manifest.permission.CAMERA)) { // <-- ADDED CAMERA CHECK
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        cameraGranted = true;
                    }
                }
                // Add checks for other permissions if requested
            }

            // Handle Location Result
            if (locationGranted) {
                Log.d(TAG, "Location permission GRANTED by user in bulk request.");
                startMonitoringServiceIfNeeded(); // Attempt to start service now
            } else {
                boolean locationRequested = false;
                for (String p : permissions) if (p.equals(Manifest.permission.ACCESS_FINE_LOCATION)) { locationRequested = true; break;}
                if(locationRequested){
                    Log.w(TAG, "Location permission DENIED by user in bulk request.");
                    Toast.makeText(this, "Location permission denied. Location monitoring disabled.", Toast.LENGTH_LONG).show();
                }
            }

            // Handle Microphone Result
            if (micGranted) {
                Log.d(TAG, "Microphone permission GRANTED by user in bulk request.");
            } else {
                boolean micRequested = false;
                for (String p : permissions) if (p.equals(Manifest.permission.RECORD_AUDIO)) { micRequested = true; break;}
                if(micRequested){
                    Log.w(TAG, "Microphone permission DENIED by user in bulk request.");
                    Toast.makeText(this, "Microphone permission denied. Microphone monitoring disabled.", Toast.LENGTH_LONG).show();
                }
            }

            // --- HANDLE CAMERA RESULT (New Logic) ---
            if (cameraGranted) {
                Log.d(TAG, "Camera permission GRANTED by user in bulk request.");
            } else {
                boolean cameraRequested = false;
                for (String p : permissions) if (p.equals(Manifest.permission.CAMERA)) { cameraRequested = true; break;}
                if(cameraRequested){
                    Log.w(TAG, "Camera permission DENIED by user in bulk request.");
                    Toast.makeText(this, "Camera permission denied. Camera monitoring disabled.", Toast.LENGTH_LONG).show();
                }
            }
            // --- END CAMERA RESULT HANDLER ---
        }
        // Handle other specific request codes if needed
    }

    // --- Method to Start Monitoring Service (Checks Location Permission) ---
    private void startMonitoringServiceIfNeeded() {
        Log.d(TAG, "Checking if MonitoringService should start...");
        // Service needs at least location permission to start its location listener part
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        {
            Log.d(TAG, "Required permission (Location) granted. Starting MonitoringService...");
            Intent serviceIntent = new Intent(this, MonitoringService.class);
            try {
                ContextCompat.startForegroundService(this, serviceIntent);
                Log.d(TAG, "MonitoringService started successfully via startMonitoringServiceIfNeeded.");
            } catch (Exception e) {
                Log.e(TAG, "Error starting MonitoringService via startMonitoringServiceIfNeeded", e);
            }
        } else {
            Log.w(TAG, "startMonitoringServiceIfNeeded called, but location permission is NOT granted. Service not started yet.");
            // The service won't start until location is granted via onRequestPermissionsResult
        }
    }


    // --- Method to Check Usage Stats Permission and Schedule Worker ---
    private void checkAndScheduleUsageWorker() {
        Log.d(TAG, "Checking Usage Stats permission...");
        if (!hasUsageStatsPermission()) {
            Log.d(TAG, "Usage Stats permission NOT granted. Requesting...");
            requestUsageStatsPermission();
        } else {
            Log.d(TAG, "Usage Stats permission already granted. Scheduling worker.");
            scheduleUnusedAppWorker(); // Permission granted, schedule the background task
        }
    }


    // --- Helper Function to Check Usage Stats Permission ---
    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) { Log.e(TAG, "AppOpsManager is null."); return false; }
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
        boolean hasPermission = (mode == AppOpsManager.MODE_ALLOWED);
        Log.d(TAG, "Usage Stats Permission Status: " + hasPermission);
        return hasPermission;
    }

    // --- Helper Function to Request Usage Stats Permission ---
    private void requestUsageStatsPermission() {
        Toast.makeText(this, "GuardianAI needs Usage Access to find unused apps. Please enable it in the next screen.", Toast.LENGTH_LONG).show();
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Log.e(TAG, "Usage Access Settings activity not found.");
                Toast.makeText(this, "Could not open Usage Access settings automatically.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open Usage Access Settings activity", e);
            Toast.makeText(this, "Could not open Usage Access settings automatically.", Toast.LENGTH_LONG).show();
        }
    }

    // --- Helper Function to Schedule the Background Worker ---
    private void scheduleUnusedAppWorker() {
        try {
            PeriodicWorkRequest unusedCheckRequest =
                    new PeriodicWorkRequest.Builder(UnusedAppWorker.class, 1, TimeUnit.DAYS)
                            .build();
            WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(
                    "UnusedAppCheck",
                    ExistingPeriodicWorkPolicy.KEEP,
                    unusedCheckRequest);
            Log.d(TAG, "Scheduled UnusedAppWorker successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling UnusedAppWorker", e);
        }
    }

    // --- Lifecycle Method: Check Permissions Again When User Returns ---
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Checking permissions again...");
        // Re-check dangerous permissions and potentially start service if granted now
        checkAndRequestNeededPermissions();
        // Log usage stats status again
        Log.d(TAG, "onResume: Current Usage Stats Permission Status: " + hasUsageStatsPermission());
    }
} // End of MainActivity class
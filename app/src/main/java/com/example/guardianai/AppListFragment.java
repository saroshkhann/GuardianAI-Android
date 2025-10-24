package com.example.guardianai;

import android.app.AlertDialog; // Import AlertDialog
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo; // Import ApplicationInfo
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button; // Import Button
import android.widget.CheckBox; // Import CheckBox
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

// Implement the listener interface from the adapter
public class AppListFragment extends Fragment implements AppListAdapter.OnSelectionChangedListener {

    private static final String TAG = "AppListFragment"; // Tag for logging

    // --- UI Elements ---
    private RecyclerView recyclerView;
    private TextView titleTextView;
    private Button reviewSelectedButton;
    private CheckBox selectAllCheckbox; // Checkbox for Select All

    // --- Data ---
    private AppListAdapter adapter;
    private PermissionAnalyzer.RiskLevel currentRiskLevel;
    private ArrayList<String> currentAppPackages;

    // --- Lifecycle Method ---
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_app_list, container, false);
        Log.d(TAG, "onCreateView called");

        // --- Initialize UI components ---
        recyclerView = view.findViewById(R.id.app_recycler_view);
        titleTextView = view.findViewById(R.id.list_title);
        reviewSelectedButton = view.findViewById(R.id.button_review_selected);
        selectAllCheckbox = view.findViewById(R.id.checkbox_select_all); // Initialize checkbox

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // --- Retrieve data passed from the previous screen ---
        Bundle args = getArguments();
        if (args != null) {
            currentRiskLevel = (PermissionAnalyzer.RiskLevel) args.getSerializable("RISK_LEVEL");
            currentAppPackages = args.getStringArrayList("APP_PACKAGES");

            // Ensure data is valid before proceeding
            if (currentRiskLevel != null && currentAppPackages != null) {
                Log.d(TAG, "Received data for " + currentRiskLevel + " with " + currentAppPackages.size() + " apps.");
                // Set the title
                titleTextView.setText(currentRiskLevel.name() + " Risk Apps (" + currentAppPackages.size() + ")");

                // Create and set the adapter, passing 'this' as the selection listener
                adapter = new AppListAdapter(getContext(), currentAppPackages, currentRiskLevel, this);
                recyclerView.setAdapter(adapter);

                // Show Select All checkbox only if the list is not empty
                selectAllCheckbox.setVisibility(currentAppPackages.isEmpty() ? View.GONE : View.VISIBLE);

            } else {
                Log.e(TAG, "Failed to retrieve valid RISK_LEVEL or APP_PACKAGES from arguments.");
                titleTextView.setText("Error Loading Apps");
                reviewSelectedButton.setVisibility(View.GONE);
                selectAllCheckbox.setVisibility(View.GONE);
            }
        } else {
            Log.e(TAG, "Arguments bundle is null.");
            titleTextView.setText("Error Loading Apps");
            reviewSelectedButton.setVisibility(View.GONE);
            selectAllCheckbox.setVisibility(View.GONE);
        }

        // --- Setup Select All Checkbox Listener ---
        selectAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Check if the listener is triggered by user interaction (important!)
            // Also check adapter is not null
            if (buttonView.isPressed() && adapter != null) {
                if (isChecked) {
                    Log.d(TAG, "'Select All' checkbox checked by user.");
                    adapter.selectAll();
                    // Text is updated in onSelectionChanged callback
                } else {
                    Log.d(TAG, "'Select All' checkbox unchecked by user.");
                    adapter.deselectAll();
                    // Text is updated in onSelectionChanged callback
                }
            } else if (adapter == null) {
                Log.w(TAG, "Select All checkbox changed, but adapter is null.");
            }
            // Do nothing if the change wasn't from a direct user press (e.g., programmatic change)
        });


        // --- Setup Button Click Listener for Bulk Review ---
        reviewSelectedButton.setOnClickListener(v -> {
            if (adapter == null) { // Safety check
                Log.e(TAG, "Adapter is null, cannot review selected apps.");
                return;
            }
            List<String> selectedPackages = adapter.getSelectedPackageNames();
            if (selectedPackages != null && !selectedPackages.isEmpty()) {
                Log.d(TAG, "Starting review process for " + selectedPackages.size() + " selected apps.");
                // Start the guided review process beginning with the first app in the list
                reviewNextApp(selectedPackages, 0);
            } else {
                Log.d(TAG, "'Review Selected' clicked, but no apps are selected.");
                Toast.makeText(getContext(), "No apps selected.", Toast.LENGTH_SHORT).show();
            }
        });

        // Initial state update for buttons based on adapter state (if any selection persisted)
        // Check adapter before calling methods on it
        if (adapter != null) {
            onSelectionChanged(adapter.getSelectedPackageNames().size());
        } else {
            onSelectionChanged(0); // If adapter failed, assume 0 selected
        }


        return view;
    } // End onCreateView

    // --- Implementation of the Adapter's OnSelectionChangedListener ---
    @Override
    public void onSelectionChanged(int count) {
        Log.d(TAG, "onSelectionChanged callback received with count: " + count);
        // Ensure UI elements are not null and fragment is attached
        if (reviewSelectedButton == null || selectAllCheckbox == null || adapter == null || getContext() == null) {
            Log.w(TAG, "UI element or adapter/context is null in onSelectionChanged. Cannot update UI state.");
            return;
        }

        // --- Update Review Button ---
        if (count > 0) {
            reviewSelectedButton.setVisibility(View.VISIBLE);
            reviewSelectedButton.setText("Review Selected Apps (" + count + ")");
        } else {
            reviewSelectedButton.setVisibility(View.GONE); // Hide if nothing selected
        }

        // --- Update Select All Checkbox State ---
        boolean allSelected = (adapter.getItemCount() > 0 && count == adapter.getItemCount());

        // Temporarily remove the listener to prevent triggering it when setting checked state programmatically
        selectAllCheckbox.setOnCheckedChangeListener(null);
        selectAllCheckbox.setChecked(allSelected);
        selectAllCheckbox.setText(allSelected ? "Deselect All" : "Select All");
        // Re-attach the listener
        selectAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed() && adapter != null) {
                if (isChecked) {
                    adapter.selectAll();
                } else {
                    adapter.deselectAll();
                }
                // Text will be updated via the onSelectionChanged callback triggered by selectAll/deselectAll
            }
        });

    } // End onSelectionChanged

    // --- Helper Method to guide user through reviewing apps one by one ---
    private void reviewNextApp(List<String> packageList, int currentIndex) {
        Context context = getContext(); // Get context once
        if (context == null) {
            Log.w(TAG, "Context is null in reviewNextApp. Aborting review.");
            return;
        }

        // Base case: If we've gone past the last app in the list, finish the process.
        if (currentIndex >= packageList.size()) {
            Log.d(TAG, "Finished reviewing all selected apps.");
            Toast.makeText(context, "Finished reviewing selected apps.", Toast.LENGTH_SHORT).show();
            // Optional: Clear selection after review
            if (adapter != null) adapter.clearSelection();
            // onSelectionChanged will be called by clearSelection via the adapter listener
            return;
        }

        String currentPackageName = packageList.get(currentIndex);
        String currentAppName = getAppName(currentPackageName); // Get user-friendly name
        Log.d(TAG, "Showing prompt for app " + (currentIndex + 1) + "/" + packageList.size() + ": " + currentPackageName);

        // --- Use AlertDialog to prompt the user ---
        new AlertDialog.Builder(context)
                .setTitle("Review Permissions (" + (currentIndex + 1) + "/" + packageList.size() + ")")
                .setMessage("Next app: " + currentAppName + "\n\nClick OK to open its system settings and review its permissions.")
                .setPositiveButton("OK", (dialog, which) -> {
                    openAppSettings(currentPackageName); // Open settings for the current app

                    // Inform user they need to click Review again to continue
                    Toast.makeText(context, "After reviewing, click 'Review Selected Apps' again to continue.", Toast.LENGTH_LONG).show();
                    // NOTE: Doesn't automatically proceed. Requires user interaction.
                })
                .setNegativeButton("Skip This App", (dialog, which) -> {
                    // User chose to skip this app, proceed to the next one in the list
                    Log.d(TAG, "Skipping app: " + currentPackageName);
                    reviewNextApp(packageList, currentIndex + 1); // Recursive call for the next index
                })
                .setNeutralButton("Stop Review", (dialog, which) -> {
                    // User chose to stop the review process altogether
                    Log.d(TAG, "User stopped the review process.");
                    Toast.makeText(context, "Review stopped.", Toast.LENGTH_SHORT).show();
                    // Optionally clear selection here too
                    // if (adapter != null) adapter.clearSelection();
                    // onSelectionChanged(0);
                })
                .setCancelable(false) // Prevent dismissing the dialog by tapping outside
                .show();
    } // End reviewNextApp

    // --- Helper to get user-friendly app name from package name ---
    private String getAppName(String packageName) {
        Context context = getContext(); // Get context safely
        if (context == null || packageName == null || packageName.isEmpty()) {
            return packageName != null ? packageName : "Unknown App"; // Fallback
        }
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            return appInfo.loadLabel(pm).toString();
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "App name not found for package: " + packageName);
            return packageName; // Fallback to package name
        } catch (Exception e) {
            Log.e(TAG, "Error getting app name for: " + packageName, e);
            return packageName; // Fallback
        }
    } // End getAppName

    // --- Helper to open the system settings page for a specific app ---
    private void openAppSettings(String packageName) {
        Log.d(TAG, "Attempting to open settings for: " + packageName);
        Context context = getContext();
        if (packageName == null || packageName.isEmpty() || context == null) {
            Log.e(TAG, "Cannot open app settings - invalid package name or context is null");
            if (context != null) {
                Toast.makeText(context, "Could not open settings for this app.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        try {
            // Create an Intent to go directly to the Application Details settings screen
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", packageName, null);
            intent.setData(uri);
            startActivity(intent); // Launch the system settings activity
        } catch (Exception e) {
            // Handle cases where the settings activity might not be found or other errors
            Log.e(TAG, "Failed to open app settings activity for " + packageName, e);
            Toast.makeText(context, "Could not open settings activity for this app.", Toast.LENGTH_SHORT).show();
        }
    } // End openAppSettings

} // End AppListFragment class
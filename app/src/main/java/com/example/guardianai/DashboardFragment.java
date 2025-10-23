package com.example.guardianai;

import android.content.pm.ApplicationInfo; // Needed for app info
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log; // For debugging
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast; // For user messages

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView; // Needed for CardView
import androidx.fragment.app.Fragment;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList; // Needed for ArrayList
import java.util.HashMap;   // Needed for HashMap
import java.util.List;
import java.util.Map;       // Needed for Map

public class DashboardFragment extends Fragment {

    // --- UI Elements ---
    private TextView progressText;
    private CircularProgressIndicator progressBar;
    private TextView highRiskText;
    private TextView mediumRiskText;
    private TextView lowRiskText;
    private TextView noRiskText;
    private CardView highRiskCard;
    private CardView mediumRiskCard;
    private CardView lowRiskCard;
    private CardView noRiskCard;
    // We will update the recommendations list later

    // --- Logic Components ---
    private PermissionAnalyzer analyzer;
    private Map<PermissionAnalyzer.RiskLevel, List<String>> categorizedApps; // Stores categorized app package names

    // --- Lifecycle Method ---
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        // Initialize UI elements by finding them in the layout
        initializeViews(view);

        // Initialize our permission analysis engine
        analyzer = new PermissionAnalyzer();

        // Run the permission scan (consider running this in a background thread for real apps)
        scanDevicePermissions();

        return view;
    }

    // --- Helper Method to Initialize Views ---
    private void initializeViews(View view) {
        progressText = view.findViewById(R.id.progress_text);
        progressBar = view.findViewById(R.id.progress_bar);
        highRiskText = view.findViewById(R.id.text_high_risk);
        mediumRiskText = view.findViewById(R.id.text_medium_risk);
        lowRiskText = view.findViewById(R.id.text_low_risk);
        noRiskText = view.findViewById(R.id.text_no_risk);
        highRiskCard = view.findViewById(R.id.card_high_risk);
        mediumRiskCard = view.findViewById(R.id.card_medium_risk);
        lowRiskCard = view.findViewById(R.id.card_low_risk);
        noRiskCard = view.findViewById(R.id.card_no_risk);
    }

    // --- Core Logic: Scan Device Permissions ---
    private void scanDevicePermissions() {
        Log.d("DashboardFragment", "Starting permission scan...");

        // Initialize the map to store categorized apps
        categorizedApps = new HashMap<>();
        categorizedApps.put(PermissionAnalyzer.RiskLevel.HIGH, new ArrayList<>());
        categorizedApps.put(PermissionAnalyzer.RiskLevel.MEDIUM, new ArrayList<>());
        categorizedApps.put(PermissionAnalyzer.RiskLevel.LOW, new ArrayList<>());
        categorizedApps.put(PermissionAnalyzer.RiskLevel.NO_RISK, new ArrayList<>());

        // Counters for each risk level
        int highRiskCount = 0;
        int mediumRiskCount = 0;
        int lowRiskCount = 0;
        int noRiskCount = 0;
        int totalUserAppsScanned = 0;

        // Get Android's package manager service
        PackageManager pm = getActivity().getPackageManager();
        // Get list of all installed packages and their requested permissions
        List<PackageInfo> installedApps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS | PackageManager.GET_META_DATA);

        // Loop through each installed app
        for (PackageInfo pkgInfo : installedApps) {
            // Check if it's a user-installed app (not part of the Android system)
            if ((pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                totalUserAppsScanned++;
                String packageName = pkgInfo.packageName; // Get the unique app identifier
                boolean appHasHighRisk = false;
                boolean appHasMediumRisk = false;
                boolean appHasLowRisk = false;

                String[] permissions = pkgInfo.requestedPermissions; // Get the list of permissions the app asks for

                // Check if the app actually requested any permissions
                if (permissions != null && permissions.length > 0) {
                    // Loop through each permission requested by this app
                    for (String permission : permissions) {
                        // Use our analyzer to determine the risk level of this permission
                        PermissionAnalyzer.RiskLevel risk = analyzer.getPermissionRisk(permission);
                        // Track the highest risk level found for this specific app
                        switch (risk) {
                            case HIGH:   appHasHighRisk = true; break;
                            case MEDIUM: appHasMediumRisk = true; break;
                            case LOW:    appHasLowRisk = true; break;
                            // NO_RISK doesn't affect categorization priority
                        }
                    }

                    // Categorize the app based on its highest risk permission found
                    if (appHasHighRisk) {
                        categorizedApps.get(PermissionAnalyzer.RiskLevel.HIGH).add(packageName);
                    } else if (appHasMediumRisk) {
                        categorizedApps.get(PermissionAnalyzer.RiskLevel.MEDIUM).add(packageName);
                    } else if (appHasLowRisk) {
                        categorizedApps.get(PermissionAnalyzer.RiskLevel.LOW).add(packageName);
                    } else {
                        // If it had permissions but none were High/Medium/Low, it's No Risk
                        categorizedApps.get(PermissionAnalyzer.RiskLevel.NO_RISK).add(packageName);
                    }
                } else {
                    // App requested zero permissions - definitely No Risk
                    categorizedApps.get(PermissionAnalyzer.RiskLevel.NO_RISK).add(packageName);
                }
            }
        }

        // Get the final counts from the categorized lists
        highRiskCount = categorizedApps.get(PermissionAnalyzer.RiskLevel.HIGH).size();
        mediumRiskCount = categorizedApps.get(PermissionAnalyzer.RiskLevel.MEDIUM).size();
        lowRiskCount = categorizedApps.get(PermissionAnalyzer.RiskLevel.LOW).size();
        noRiskCount = categorizedApps.get(PermissionAnalyzer.RiskLevel.NO_RISK).size();

        Log.d("DashboardFragment", "Scan complete. User apps scanned: " + totalUserAppsScanned);
        Log.d("DashboardFragment", "Counts - High: " + highRiskCount + ", Medium: " + mediumRiskCount + ", Low: " + lowRiskCount + ", No Risk: " + noRiskCount);

        // --- Calculate Privacy Score ---
        int score = 100;
        if (totalUserAppsScanned > 0) { // Avoid division by zero
            // Simple penalty system: Lose points for apps with risky permissions
            // Adjust weights as needed for your desired scoring sensitivity
            double highRiskPenalty = (double) highRiskCount / totalUserAppsScanned * 50; // Max 50 points penalty
            double mediumRiskPenalty = (double) mediumRiskCount / totalUserAppsScanned * 25; // Max 25 points penalty
            score = (int) (100 - highRiskPenalty - mediumRiskPenalty);

            if (score < 0) score = 0; // Ensure score doesn't go below 0
        }
        Log.d("DashboardFragment", "Calculated score: " + score);

        // Update the dashboard UI elements with the real data
        updateDashboardUI(score, highRiskCount, mediumRiskCount, lowRiskCount, noRiskCount);

        // Make the risk cards clickable now that the data is ready
        setupCardClickListeners();
    }

    // --- Helper Method to Update the UI Elements ---
    private void updateDashboardUI(int score, int high, int medium, int low, int no) {
        // Ensure UI elements are not null before updating
        if (progressText != null) progressText.setText(String.valueOf(score));
        if (progressBar != null) progressBar.setProgress(score, true); // Animate progress change

        if (highRiskText != null) highRiskText.setText("High Risk (" + high + ")");
        if (mediumRiskText != null) mediumRiskText.setText("Medium Risk (" + medium + ")");
        if (lowRiskText != null) lowRiskText.setText("Low Risk (" + low + ")");
        if (noRiskText != null) noRiskText.setText("No Risk (" + no + ")");

        // TODO: Update AI recommendations list based on scan results (next step)
    }

    // --- Helper Method to Set Up Click Listeners for Risk Cards ---
    private void setupCardClickListeners() {
        // Set onClickListeners - they call navigateToAppList with the correct risk level
        if (highRiskCard != null) highRiskCard.setOnClickListener(v -> navigateToAppList(PermissionAnalyzer.RiskLevel.HIGH));
        if (mediumRiskCard != null) mediumRiskCard.setOnClickListener(v -> navigateToAppList(PermissionAnalyzer.RiskLevel.MEDIUM));
        if (lowRiskCard != null) lowRiskCard.setOnClickListener(v -> navigateToAppList(PermissionAnalyzer.RiskLevel.LOW));
        if (noRiskCard != null) noRiskCard.setOnClickListener(v -> navigateToAppList(PermissionAnalyzer.RiskLevel.NO_RISK));
    }

    // --- Helper Method to Navigate to the App List Screen ---
    private void navigateToAppList(PermissionAnalyzer.RiskLevel riskLevel) {
        if (categorizedApps == null || categorizedApps.get(riskLevel) == null) {
            Log.e("DashboardFragment", "App data not ready or risk level not found for: " + riskLevel);
            Toast.makeText(getActivity(), "Error: App data not ready.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> appPackageNames = categorizedApps.get(riskLevel);

        if (!appPackageNames.isEmpty()) {
            Log.d("DashboardFragment", "Navigating to AppList for " + riskLevel + " with " + appPackageNames.size() + " apps.");

            // Create the new fragment instance
            AppListFragment appListFragment = new AppListFragment();

            // Create a Bundle to pass data (risk level and package names)
            Bundle args = new Bundle();
            args.putSerializable("RISK_LEVEL", riskLevel);
            args.putStringArrayList("APP_PACKAGES", new ArrayList<>(appPackageNames)); // Pass a copy
            appListFragment.setArguments(args);

            // Use FragmentManager to replace the current dashboard with the app list screen
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, appListFragment) // Replace content in MainActivity's container
                    .addToBackStack(null) // Allows the user to press 'Back' to return to the dashboard
                    .commit();
        } else {
            Log.d("DashboardFragment", "No apps found for risk level: " + riskLevel);
            Toast.makeText(getActivity(), "No apps found for this risk level.", Toast.LENGTH_SHORT).show();
        }
    }
}
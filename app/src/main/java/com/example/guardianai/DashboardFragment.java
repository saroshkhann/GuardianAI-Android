package com.example.guardianai;

import android.content.Context; // Needed for Context
import android.content.Intent; // Needed for Intent
import android.content.SharedPreferences; // Needed for SharedPreferences
import android.content.pm.ApplicationInfo; // Needed for app info
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri; // Needed for Uri
import android.os.Bundle;
import android.provider.Settings; // Needed for Settings
import android.util.Log; // For debugging
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView; // Needed for ImageView
import android.widget.LinearLayout; // Needed for LinearLayout
import android.widget.TextView;
import android.widget.Toast; // For user messages

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView; // Needed for CardView
import androidx.core.content.ContextCompat; // Needed for getDrawable and getColor
import androidx.fragment.app.Fragment;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList; // Needed for ArrayList
import java.util.HashMap;   // Needed for HashMap
import java.util.HashSet;   // Needed for HashSet
import java.util.List;
import java.util.Map;       // Needed for Map
import java.util.Set;      // Needed for Set

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
    private LinearLayout recommendationsLayout; // Container for recommendations

    // --- Logic Components ---
    private PermissionAnalyzer analyzer;
    private Map<PermissionAnalyzer.RiskLevel, List<String>> categorizedApps; // Stores categorized app package names
    private List<String> recommendations; // List to hold recommendation text strings

    // --- Lifecycle Method: Create the View ---
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        Log.d("DashboardFragment", "onCreateView called");

        // Initialize UI elements by finding them in the layout
        initializeViews(view);

        // Initialize our permission analysis engine
        analyzer = new PermissionAnalyzer();

        return view; // Return the created view
    }

    // --- Lifecycle Method: View is Created and Ready ---
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d("DashboardFragment", "onViewCreated called");

        // --- Start the Scan ---
        // Now that the view is definitely created and ready, start the scan.
        scanDevicePermissions();
    }


    // --- Helper Method to Initialize Views ---
    private void initializeViews(View view) {
        Log.d("DashboardFragment", "Initializing views...");
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
        recommendationsLayout = view.findViewById(R.id.recommendations_list); // Initialize the layout
        Log.d("DashboardFragment", "Views initialized.");
    }

    // --- Core Logic: Scan Device Permissions ---
    private void scanDevicePermissions() {
        Log.d("DashboardFragment", "Starting permission scan...");

        // Safety check for context
        Context context = getContext(); // Get context once
        if (context == null || context.getPackageManager() == null) {
            Log.e("DashboardFragment", "Context or PackageManager is null during scan.");
            return;
        }
        PackageManager pm = context.getPackageManager();

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

        // Get list of all installed packages and their requested permissions
        List<PackageInfo> installedApps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS | PackageManager.GET_META_DATA);

        // Loop through each installed app
        for (PackageInfo pkgInfo : installedApps) {
            try {
                if (pkgInfo != null && pkgInfo.applicationInfo != null && (pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    totalUserAppsScanned++;
                    String packageName = pkgInfo.packageName;
                    boolean appHasHighRisk = false;
                    boolean appHasMediumRisk = false;
                    boolean appHasLowRisk = false;

                    String[] permissions = pkgInfo.requestedPermissions;

                    if (permissions != null && permissions.length > 0) {
                        for (String permission : permissions) {
                            PermissionAnalyzer.RiskLevel risk = analyzer.getPermissionRisk(permission);
                            switch (risk) {
                                case HIGH:   appHasHighRisk = true; break;
                                case MEDIUM: appHasMediumRisk = true; break;
                                case LOW:    appHasLowRisk = true; break;
                            }
                        }

                        if (appHasHighRisk) {
                            categorizedApps.get(PermissionAnalyzer.RiskLevel.HIGH).add(packageName);
                        } else if (appHasMediumRisk) {
                            categorizedApps.get(PermissionAnalyzer.RiskLevel.MEDIUM).add(packageName);
                        } else if (appHasLowRisk) {
                            categorizedApps.get(PermissionAnalyzer.RiskLevel.LOW).add(packageName);
                        } else {
                            categorizedApps.get(PermissionAnalyzer.RiskLevel.NO_RISK).add(packageName);
                        }
                    } else {
                        categorizedApps.get(PermissionAnalyzer.RiskLevel.NO_RISK).add(packageName);
                    }
                }
            } catch (Exception e) {
                Log.e("DashboardFragment", "Error processing package: " + (pkgInfo != null ? pkgInfo.packageName : "null package info"), e);
            }
        } // End of app loop

        // Get the final counts
        highRiskCount = (categorizedApps.get(PermissionAnalyzer.RiskLevel.HIGH) != null) ? categorizedApps.get(PermissionAnalyzer.RiskLevel.HIGH).size() : 0;
        mediumRiskCount = (categorizedApps.get(PermissionAnalyzer.RiskLevel.MEDIUM) != null) ? categorizedApps.get(PermissionAnalyzer.RiskLevel.MEDIUM).size() : 0;
        lowRiskCount = (categorizedApps.get(PermissionAnalyzer.RiskLevel.LOW) != null) ? categorizedApps.get(PermissionAnalyzer.RiskLevel.LOW).size() : 0;
        noRiskCount = (categorizedApps.get(PermissionAnalyzer.RiskLevel.NO_RISK) != null) ? categorizedApps.get(PermissionAnalyzer.RiskLevel.NO_RISK).size() : 0;

        // --- Generate Recommendations ---
        recommendations = new ArrayList<>();

        // Rule 1: High Risk Apps
        if (highRiskCount > 0) {
            recommendations.add("Review " + highRiskCount + " high-risk apps");
        }
        // Rule 2: Medium Risk Apps
        if (mediumRiskCount > 0) {
            recommendations.add("Check permissions for " + mediumRiskCount + " medium-risk apps");
        }

        // Rule 3: Unused Risky Apps (Read from SharedPreferences)
        SharedPreferences prefs = context.getSharedPreferences("GuardianAIPrefs", Context.MODE_PRIVATE);
        // Default to an empty set if not found
        Set<String> unusedPackages = prefs.getStringSet("unused_risky_apps", new HashSet<>());

        if (unusedPackages != null && !unusedPackages.isEmpty()) {
            Log.d("DashboardFragment", "Found " + unusedPackages.size() + " unused risky apps from SharedPreferences.");
            for (String packageName : unusedPackages) {
                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                    String appName = appInfo.loadLabel(pm).toString();
                    // Add recommendation text including the package name for later extraction
                    recommendations.add("Review unused permissions for '" + appName + "' (" + packageName + ")");
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w("DashboardFragment", "Could not get app name for unused package: " + packageName);
                    // Add recommendation with just package name as fallback
                    recommendations.add("Review unused permissions for package: (" + packageName + ")");
                } catch (Exception e) {
                    Log.e("DashboardFragment", "Error getting app info for unused package: " + packageName, e);
                }
            }
        } else {
            Log.d("DashboardFragment", "No unused risky apps found in SharedPreferences.");
        }
        // --- End Unused App Recommendations ---


        // --- Calculate Privacy Score ---
        int score = 100;
        if (totalUserAppsScanned > 0) {
            double highRiskPenalty = (double) highRiskCount / totalUserAppsScanned * 50;
            double mediumRiskPenalty = (double) mediumRiskCount / totalUserAppsScanned * 25;
            score = (int) (100 - highRiskPenalty - mediumRiskPenalty);
            if (score < 0) score = 0;
        }

        // --- Log Final Results Before UI Update ---
        Log.d("DashboardFragment", "Scan complete. User apps scanned: " + totalUserAppsScanned);
        Log.d("DashboardFragment", "Final Counts - High: " + highRiskCount + ", Medium: " + mediumRiskCount + ", Low: " + lowRiskCount + ", No Risk: " + noRiskCount);
        Log.d("DashboardFragment", "Calculated Score: " + score);
        Log.d("DashboardFragment", "Generated Recommendations: " + (recommendations == null ? "null" : recommendations.toString()));
        // --- End Logging ---

        // Update the dashboard UI elements with the real data
        updateDashboardUI(score, highRiskCount, mediumRiskCount, lowRiskCount, noRiskCount, recommendations);

        // Make the risk cards clickable now that the data is ready
        setupCardClickListeners();
    }

    // --- Helper Method to Update the UI Elements ---
    private void updateDashboardUI(int score, int high, int medium, int low, int no, List<String> currentRecommendations) {
        Log.d("DashboardFragment", "updateDashboardUI called with Score: " + score + ", High: " + high + ", Medium: " + medium);

        // Ensure UI elements are not null AND fragment is still attached before updating
        View fragmentView = getView();
        Context context = getContext();
        if (fragmentView == null || context == null) {
            Log.e("DashboardFragment", "View or Context is null in updateDashboardUI - Cannot update UI.");
            return;
        }

        // Update Score and Progress Bar
        if (progressText != null) progressText.setText(String.valueOf(score));
        if (progressBar != null) progressBar.setProgress(score, true);

        // Update Risk Card Text
        if (highRiskText != null) highRiskText.setText("High Risk (" + high + ")");
        if (mediumRiskText != null) mediumRiskText.setText("Medium Risk (" + medium + ")");
        if (lowRiskText != null) lowRiskText.setText("Low Risk (" + low + ")");
        if (noRiskText != null) noRiskText.setText("No Risk (" + no + ")");

        // --- Update Recommendations List ---
        if (recommendationsLayout == null) {
            Log.e("DashboardFragment", "Cannot update recommendations, recommendationsLayout is null.");
            return;
        }
        recommendationsLayout.removeAllViews(); // Clear previous items

        LayoutInflater inflater = LayoutInflater.from(context);

        if (currentRecommendations == null || currentRecommendations.isEmpty()) {
            Log.d("DashboardFragment", "No recommendations to display.");
            TextView noRecsTextView = new TextView(context);
            noRecsTextView.setText("No specific recommendations right now. Stay vigilant!");
            noRecsTextView.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
            noRecsTextView.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray));
            recommendationsLayout.addView(noRecsTextView);
        } else {
            Log.d("DashboardFragment", "Displaying " + currentRecommendations.size() + " recommendations.");
            for (String recText : currentRecommendations) {
                try {
                    View itemView = inflater.inflate(R.layout.list_item_recommendation, recommendationsLayout, false);
                    TextView recTextView = itemView.findViewById(R.id.recommendation_text);
                    ImageView recIcon = itemView.findViewById(R.id.recommendation_icon);
                    // ImageView recChevron = itemView.findViewById(R.id.recommendation_chevron); // Find if needed

                    if (recTextView != null) {
                        recTextView.setText(recText);
                        Log.d("DashboardFragment", "Added recommendation item: " + recText);
                    } else {
                        Log.e("DashboardFragment", "TextView recommendation_text not found in list_item_recommendation.xml");
                    }

                    // Set appropriate icon based on recommendation type
                    if (recIcon != null) {
                        if (recText.contains("high-risk")) {
                            recIcon.setImageResource(R.drawable.ic_risk_high);
                            recIcon.setBackgroundResource(R.drawable.risk_card_high_bg);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.high_risk_color));
                        } else if (recText.contains("medium-risk")) {
                            recIcon.setImageResource(R.drawable.ic_risk_medium);
                            recIcon.setBackgroundResource(R.drawable.risk_card_medium_bg);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.medium_risk_color));
                        } else if (recText.contains("unused")) { // Check for unused app recommendation
                            recIcon.setImageResource(R.drawable.ic_history); // Use history icon
                            recIcon.setBackgroundResource(R.drawable.notice_background); // Use generic background
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.default_recommendation_color));
                        } else {
                            // Default icon/color for other types
                            recIcon.setImageResource(R.drawable.ic_lightbulb);
                            recIcon.setBackgroundResource(R.drawable.notice_background);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.default_recommendation_color));
                        }
                    } else {
                        Log.e("DashboardFragment", "ImageView recommendation_icon not found in list_item_recommendation.xml");
                    }

                    recommendationsLayout.addView(itemView);

                    // --- ADD CLICK LISTENER TO THE ITEM VIEW ---
                    itemView.setOnClickListener(v -> {
                        Log.d("DashboardFragment", "Recommendation clicked: " + recText);
                        if (recText.contains("high-risk")) {
                            navigateToAppList(PermissionAnalyzer.RiskLevel.HIGH);
                        } else if (recText.contains("medium-risk")) {
                            navigateToAppList(PermissionAnalyzer.RiskLevel.MEDIUM);
                        } else if (recText.contains("unused")) {
                            // --- Handle unused app click ---
                            String packageName = extractPackageNameFromRecommendation(recText); // Get package name
                            if (packageName != null) {
                                openAppSettings(packageName); // Open settings for that specific app
                            } else {
                                Toast.makeText(getActivity(), "Could not identify app for this recommendation.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // For other recommendations
                            Toast.makeText(getActivity(), "More info for: " + recText, Toast.LENGTH_SHORT).show();
                        }
                    });
                    // --- END OF ADDED CLICK LISTENER ---
                } catch (Exception e) {
                    Log.e("DashboardFragment", "Error inflating/adding recommendation item for '" + recText + "'", e);
                }
            } // End of for loop
        } // End of else block
        Log.d("DashboardFragment", "updateDashboardUI finished.");
    } // End of updateDashboardUI


    // --- Helper Method to Set Up Click Listeners for Risk Cards ---
    private void setupCardClickListeners() {
        Log.d("DashboardFragment", "Setting up card click listeners...");
        if (highRiskCard != null) highRiskCard.setOnClickListener(v -> navigateToAppList(PermissionAnalyzer.RiskLevel.HIGH));
        if (mediumRiskCard != null) mediumRiskCard.setOnClickListener(v -> navigateToAppList(PermissionAnalyzer.RiskLevel.MEDIUM));
        if (lowRiskCard != null) lowRiskCard.setOnClickListener(v -> navigateToAppList(PermissionAnalyzer.RiskLevel.LOW));
        if (noRiskCard != null) noRiskCard.setOnClickListener(v -> navigateToAppList(PermissionAnalyzer.RiskLevel.NO_RISK));
        Log.d("DashboardFragment", "Card click listeners set up.");
    }

    // --- Helper Method to Navigate to the App List Screen ---
    private void navigateToAppList(PermissionAnalyzer.RiskLevel riskLevel) {
        Log.d("DashboardFragment", "navigateToAppList called for: " + riskLevel);
        if (getActivity() == null || categorizedApps == null || categorizedApps.get(riskLevel) == null) {
            Log.e("DashboardFragment", "App data not ready or risk level not found for: " + riskLevel);
            if (getActivity() != null) {
                Toast.makeText(getActivity(), "App data is still loading or unavailable.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        List<String> appPackageNames = categorizedApps.get(riskLevel);

        if (appPackageNames != null && !appPackageNames.isEmpty()) {
            Log.d("DashboardFragment", "Navigating to AppList for " + riskLevel + " with " + appPackageNames.size() + " apps.");
            AppListFragment appListFragment = new AppListFragment();
            Bundle args = new Bundle();
            args.putSerializable("RISK_LEVEL", riskLevel);
            args.putStringArrayList("APP_PACKAGES", new ArrayList<>(appPackageNames));
            appListFragment.setArguments(args);

            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, appListFragment)
                    .addToBackStack("dashboard")
                    .commit();
        } else {
            Log.d("DashboardFragment", "No apps found for risk level: " + riskLevel);
            if (getActivity() != null) {
                Toast.makeText(getActivity(), "No apps found for this risk level.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // --- Helper to extract package name from recommendation text ---
    private String extractPackageNameFromRecommendation(String recommendationText) {
        // Assumes format "... ('AppName') (packageName)" or "... package: (packageName)"
        int startIndex = recommendationText.lastIndexOf('(');
        int endIndex = recommendationText.lastIndexOf(')');

        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            String potentialPackageName = recommendationText.substring(startIndex + 1, endIndex);
            Log.d("DashboardFragment", "Extracted potential package name: " + potentialPackageName);
            // Basic validation: check if it contains at least one dot
            if (potentialPackageName.contains(".")) {
                return potentialPackageName;
            } else {
                Log.w("DashboardFragment", "Extracted text doesn't look like a package name: " + potentialPackageName);
            }
        } else {
            Log.w("DashboardFragment", "Could not find package name in parentheses format in: " + recommendationText);
        }
        return null; // Return null if extraction fails
    }

    // --- Helper to open the system settings page for a specific app ---
    private void openAppSettings(String packageName) {
        Log.d("DashboardFragment", "Attempting to open settings for: " + packageName);
        Context context = getContext();
        if (packageName == null || packageName.isEmpty() || context == null) {
            Log.e("DashboardFragment", "Cannot open app settings - invalid package name or context is null");
            if (context != null) {
                Toast.makeText(context, "Could not open settings for this app.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", packageName, null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("DashboardFragment", "Failed to open app settings activity for " + packageName, e);
            Toast.makeText(context, "Could not open settings activity for this app.", Toast.LENGTH_SHORT).show();
        }
    }

    // --- Helper to convert dp to pixels ---
    private int dpToPx(int dp) {
        Context context = getContext();
        if (context == null || context.getResources() == null || context.getResources().getDisplayMetrics() == null) {
            return dp; // Basic fallback
        }
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }
} // End of DashboardFragment class
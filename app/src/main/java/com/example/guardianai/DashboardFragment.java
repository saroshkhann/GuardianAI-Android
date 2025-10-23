package com.example.guardianai;

import android.content.Intent; // Needed for Intent
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
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView; // Needed for CardView
import androidx.core.content.ContextCompat; // Needed for getDrawable and getColor
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

        // **IMPORTANT:** Scan is MOVED from here to onViewCreated

        return view; // Return the created view
    }

    // --- Lifecycle Method: View is Created and Ready ---
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d("DashboardFragment", "onViewCreated called");

        // --- CALL THE SCAN HERE ---
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
        if (getActivity() == null || getActivity().getPackageManager() == null) {
            Log.e("DashboardFragment", "Activity or PackageManager is null during scan.");
            // Maybe show an error to the user or retry later
            return;
        }
        PackageManager pm = getActivity().getPackageManager();

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
            // Check if it's a user-installed app (not part of the Android system)
            // Use try-catch for safety, ApplicationInfo might be null
            try {
                if (pkgInfo != null && pkgInfo.applicationInfo != null && (pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
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
            } catch (Exception e) {
                Log.e("DashboardFragment", "Error processing package: " + (pkgInfo != null ? pkgInfo.packageName : "null package info"), e);
            }
        } // End of app loop

        // Get the final counts from the categorized lists (check for null safety)
        highRiskCount = (categorizedApps.get(PermissionAnalyzer.RiskLevel.HIGH) != null) ? categorizedApps.get(PermissionAnalyzer.RiskLevel.HIGH).size() : 0;
        mediumRiskCount = (categorizedApps.get(PermissionAnalyzer.RiskLevel.MEDIUM) != null) ? categorizedApps.get(PermissionAnalyzer.RiskLevel.MEDIUM).size() : 0;
        lowRiskCount = (categorizedApps.get(PermissionAnalyzer.RiskLevel.LOW) != null) ? categorizedApps.get(PermissionAnalyzer.RiskLevel.LOW).size() : 0;
        noRiskCount = (categorizedApps.get(PermissionAnalyzer.RiskLevel.NO_RISK) != null) ? categorizedApps.get(PermissionAnalyzer.RiskLevel.NO_RISK).size() : 0;

        // --- Generate Recommendations ---
        recommendations = new ArrayList<>(); // Create the empty list

        // Apply Rule 1
        if (highRiskCount > 0) {
            recommendations.add("Review " + highRiskCount + " high-risk apps"); // Add message to list
        }
        // Apply Rule 2
        if (mediumRiskCount > 0) {
            recommendations.add("Check permissions for " + mediumRiskCount + " medium-risk apps"); // Add message to list
        }
        // TODO: Add rules/logic here to fetch "unused app" recommendations from the database later

        // --- Calculate Privacy Score ---
        int score = 100;
        if (totalUserAppsScanned > 0) { // Avoid division by zero
            double highRiskPenalty = (double) highRiskCount / totalUserAppsScanned * 50; // Max 50 points penalty
            double mediumRiskPenalty = (double) mediumRiskCount / totalUserAppsScanned * 25; // Max 25 points penalty
            score = (int) (100 - highRiskPenalty - mediumRiskPenalty);
            if (score < 0) score = 0; // Ensure score doesn't go below 0
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
        Log.d("DashboardFragment", "updateDashboardUI called with Score: " + score + ", High: " + high + ", Medium: " + medium); // Log entry

        // Ensure UI elements are not null AND fragment is still attached before updating
        // Use getView() and getContext() inside this method for safety
        View fragmentView = getView();
        Context context = getContext();
        if (fragmentView == null || context == null) {
            Log.e("DashboardFragment", "View or Context is null in updateDashboardUI - Cannot update UI.");
            return; // Exit if fragment is not attached properly
        }

        // Update Score and Progress Bar
        if (progressText != null) progressText.setText(String.valueOf(score));
        if (progressBar != null) progressBar.setProgress(score, true); // Animate progress change

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

        // IMPORTANT: Clear out any old views that might be inside it
        recommendationsLayout.removeAllViews();

        // Get a tool to create views from XML layouts
        LayoutInflater inflater = LayoutInflater.from(context);

        // Check if the recommendations list is empty
        if (currentRecommendations == null || currentRecommendations.isEmpty()) {
            Log.d("DashboardFragment", "No recommendations to display."); // Log empty case
            // If empty, show a default message
            TextView noRecsTextView = new TextView(context);
            noRecsTextView.setText("No specific recommendations right now. Stay vigilant!");
            // Add some styling
            noRecsTextView.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16)); // Use helper for dp
            noRecsTextView.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray)); // Use ContextCompat
            recommendationsLayout.addView(noRecsTextView); // Add the message view to the layout
        } else {
            Log.d("DashboardFragment", "Displaying " + currentRecommendations.size() + " recommendations."); // Log how many items
            // If there are recommendations, loop through each message string
            for (String recText : currentRecommendations) {
                try { // Add try-catch for safety during inflation/view finding
                    // Create a new row View from the list_item_recommendation.xml layout
                    View itemView = inflater.inflate(R.layout.list_item_recommendation, recommendationsLayout, false);

                    // Find elements inside the row layout
                    TextView recTextView = itemView.findViewById(R.id.recommendation_text);
                    ImageView recIcon = itemView.findViewById(R.id.recommendation_icon);
                    ImageView recChevron = itemView.findViewById(R.id.recommendation_chevron); // Assuming you added this ID

                    if (recTextView != null) {
                        recTextView.setText(recText);
                        Log.d("DashboardFragment", "Added recommendation item: " + recText); // Log successful add
                    } else {
                        Log.e("DashboardFragment", "TextView recommendation_text not found in list_item_recommendation.xml");
                    }

                    // Set appropriate icon based on recommendation type (Example Logic)
                    if (recIcon != null) { // Check if icon exists
                        if (recText.contains("high-risk")) {
                            recIcon.setImageResource(R.drawable.ic_risk_high);
                            recIcon.setBackgroundResource(R.drawable.risk_card_high_bg);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.high_risk_color));
                        } else if (recText.contains("medium-risk")) {
                            recIcon.setImageResource(R.drawable.ic_risk_medium);
                            recIcon.setBackgroundResource(R.drawable.risk_card_medium_bg);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.medium_risk_color));
                        } else if (recText.contains("unused")) { // Example for unused apps
                            recIcon.setImageResource(R.drawable.ic_history); // Make sure you have this icon
                            recIcon.setBackgroundResource(R.drawable.notice_background);
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

                    // Add the newly created row View to the main recommendations layout
                    recommendationsLayout.addView(itemView);

                    // --- ADD CLICK LISTENER TO THE ITEM VIEW ---
                    itemView.setOnClickListener(v -> {
                        Log.d("DashboardFragment", "Recommendation clicked: " + recText);
                        // Determine action based on the recommendation text
                        if (recText.contains("high-risk")) {
                            // If the text mentions high-risk, navigate to the high-risk app list
                            navigateToAppList(PermissionAnalyzer.RiskLevel.HIGH);
                        } else if (recText.contains("medium-risk")) {
                            // If the text mentions medium-risk, navigate to the medium-risk app list
                            navigateToAppList(PermissionAnalyzer.RiskLevel.MEDIUM);
                        } else if (recText.contains("unused")) {
                            // --- NEW: Handle unused app click ---
                            String packageName = extractPackageNameFromRecommendation(recText); // Get package name
                            if (packageName != null) {
                                openAppSettings(packageName); // Open settings for that specific app
                            } else {
                                Toast.makeText(getActivity(), "Could not identify app for this recommendation.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // For any other recommendations (like clipboard - added later)
                            Toast.makeText(getActivity(), "More info for: " + recText, Toast.LENGTH_SHORT).show();
                        }
                    });
                    // --- END OF ADDED CLICK LISTENER ---
                } catch (Exception e) {
                    Log.e("DashboardFragment", "Error inflating/adding recommendation item for '" + recText + "'", e); // Log any errors
                }
            } // End of for loop
        } // End of else block
        Log.d("DashboardFragment", "updateDashboardUI finished.");
    } // End of updateDashboardUI


    // --- Helper Method to Set Up Click Listeners for Risk Cards ---
    private void setupCardClickListeners() {
        Log.d("DashboardFragment", "Setting up card click listeners...");
        // Set onClickListeners - they call navigateToAppList with the correct risk level
        if (highRiskCard != null) highRiskCard.setOnClickListener(v -> navigateToAppList(PermissionAnalyzer.RiskLevel.HIGH));
        if (mediumRiskCard != null) mediumRiskCard.setOnClickListener(v -> navigateToAppList(PermissionAnalyzer.RiskLevel.MEDIUM));
        if (lowRiskCard != null) lowRiskCard.setOnClickListener(v -> navigateToAppList(PermissionAnalyzer.RiskLevel.LOW));
        if (noRiskCard != null) noRiskCard.setOnClickListener(v -> navigateToAppList(PermissionAnalyzer.RiskLevel.NO_RISK));
        Log.d("DashboardFragment", "Card click listeners set up.");
    }

    // --- Helper Method to Navigate to the App List Screen ---
    private void navigateToAppList(PermissionAnalyzer.RiskLevel riskLevel) {
        Log.d("DashboardFragment", "navigateToAppList called for: " + riskLevel);
        // Safety check before accessing map
        if (getActivity() == null || categorizedApps == null || categorizedApps.get(riskLevel) == null) {
            Log.e("DashboardFragment", "App data not ready or risk level not found for: " + riskLevel);
            // Provide feedback to the user
            if (getActivity() != null) {
                Toast.makeText(getActivity(), "App data is still loading or unavailable.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        List<String> appPackageNames = categorizedApps.get(riskLevel);

        if (appPackageNames != null && !appPackageNames.isEmpty()) {
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
                    .addToBackStack("dashboard") // Allows the user to press 'Back' to return to the dashboard
                    .commit();
        } else {
            Log.d("DashboardFragment", "No apps found for risk level: " + riskLevel);
            if (getActivity() != null) {
                Toast.makeText(getActivity(), "No apps found for this risk level.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // --- PASTE THE NEW HELPER METHODS HERE ---

    // Example helper to extract package name (needs refinement)
    private String extractPackageNameFromRecommendation(String recommendationText) {
        // This is a basic example, assumes format "Review unused permissions for 'AppName'"
        // For this to work, you MUST store the package name when the recommendation is created by UnusedAppWorker.
        // For now, it returns null. You need to implement the logic to retrieve the package name associated with this text.
        Log.w("DashboardFragment", "extractPackageNameFromRecommendation needs proper implementation! Cannot get package name from: " + recommendationText);
        // Example (IF you store the package name in the recommendation text like "Review... for 'App' (com.example.app)"):
        // int start = recommendationText.indexOf('(');
        // int end = recommendationText.indexOf(')');
        // if (start != -1 && end != -1 && start < end) {
        //     return recommendationText.substring(start + 1, end);
        // }
        return null; // Placeholder - Requires implementation
    }

    // Opens the system settings page for a specific app
    private void openAppSettings(String packageName) {
        Log.d("DashboardFragment", "Attempting to open settings for: " + packageName);
        // Safety check
        if (packageName == null || packageName.isEmpty() || getActivity() == null) {
            Log.e("DashboardFragment", "Cannot open app settings - invalid package name or activity is null");
            if (getActivity() != null) {
                Toast.makeText(getActivity(), "Could not open settings for this app.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", packageName, null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("DashboardFragment", "Failed to open app settings for " + packageName, e);
            if (getActivity() != null) {
                Toast.makeText(getActivity(), "Could not open settings for this app.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // --- Helper to convert dp to pixels ---
    private int dpToPx(int dp) {
        // Safety check for context
        if (getContext() == null || getResources() == null || getResources().getDisplayMetrics() == null) {
            return dp; // Basic fallback
        }
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }
} // End of DashboardFragment class
package com.example.guardianai;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider; // Import ViewModelProvider

// --- ROOM DATABASE IMPORTS ---
import com.example.guardianai.AppDatabase;
import com.example.guardianai.AppPermissionsDao;
import com.example.guardianai.AppPermissions;
import com.example.guardianai.Recommendation;
// --- END ROOM IMPORTS ---

import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardFragment extends Fragment {

    private static final String TAG = "DashboardFragment";

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
    private LinearLayout recommendationsLayout;

    // --- Logic Components ---
    private PermissionAnalyzer analyzer;
    private Map<PermissionAnalyzer.RiskLevel, List<String>> categorizedApps; // Holds results from last scan
    private RecommendationViewModel recommendationViewModel; // ViewModel instance
    private AppPermissionsDao appPermissionsDao; // DAO for AppPermissions table

    // --- Threading Components ---
    private ExecutorService executorService; // Executor for background tasks
    private Handler mainThreadHandler;      // Handler to post results to UI thread

    // --- Lifecycle Method: Create the View ---
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        Log.d(TAG, "onCreateView called");

        // --- Initialize ViewModel ---
        recommendationViewModel = new ViewModelProvider(this).get(RecommendationViewModel.class);
        Log.d(TAG, "RecommendationViewModel initialized.");
        // --- End ViewModel Init ---

        initializeViews(view);
        analyzer = new PermissionAnalyzer();

        // Initialize Database Access (AppPermissionsDao needed for saving the scan)
        Context appContext = getContext() != null ? getContext().getApplicationContext() : null;
        if (appContext != null) {
            AppDatabase db = AppDatabase.getDatabase(appContext);
            appPermissionsDao = db.appPermissionsDao();
            Log.d(TAG, "AppPermissions DAO initialized.");
        } else {
            Log.e(TAG, "Context was null during DAO initialization!");
        }

        // Initialize Threading Components
        executorService = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());

        return view;
    }

    // --- Lifecycle Method: View is Created and Ready ---
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated called");

        // --- Observe LiveData for Recommendations ---
        recommendationViewModel.getAllRecommendations().observe(getViewLifecycleOwner(), this::onRecommendationsLiveDataUpdate);
        // --- End Observe LiveData ---

        // Start the initial permission scan (calculates score, counts, and categories)
        startPermissionScan();
    }

    // --- Lifecycle Method: Refresh data when fragment becomes visible again ---
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called, refreshing data...");
        // Re-run scan to ensure score/counts are fresh
        startPermissionScan();
    }

    // --- Lifecycle Method: Clean up Executor ---
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            Log.d(TAG, "ExecutorService shut down.");
        }
        Log.d(TAG,"onDestroyView called.");
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
        recommendationsLayout = view.findViewById(R.id.recommendations_list);
        Log.d(TAG, "Views initialized.");
    }

    // --- Method to Start the Background Scan ---
    private void startPermissionScan() {
        if (executorService == null || executorService.isShutdown() || appPermissionsDao == null) {
            Log.e(TAG, "Cannot start scan: Executor or AppPermissionsDao is not ready.");
            if(getActivity() != null) Toast.makeText(getActivity(), "Error initializing components.", Toast.LENGTH_LONG).show();
            updateDashboardBaseUI(-1, 0,0,0,0);
            updateRecommendationsUI(new ArrayList<>());
            return;
        }

        Log.d(TAG, "Submitting permission scan task to executor...");
        executorService.execute(() -> {
            Log.d(TAG, "Background scan task started.");
            Context context = getContext();
            if (context == null || appPermissionsDao == null) {
                Log.e(TAG, "Context or AppPermissionsDao became null. Aborting.");
                return;
            }
            PackageManager pm = context.getPackageManager();

            // Perform the scan, categorization, and DB saving of app permissions
            final ScanResult result = performScanAndCategorization(context, pm);

            // --- Post Results back to Main Thread ---
            mainThreadHandler.post(() -> {
                Log.d(TAG, "Received scan results on main thread.");
                if (result != null && isAdded() && getActivity() != null) {
                    // Update the categorizedApps map (used for card clicks)
                    categorizedApps = result.categorizedApps;
                    // Update the BASE UI (score and risk cards) - recommendations updated by LiveData
                    updateDashboardBaseUI(result.score, result.highRiskCount, result.mediumRiskCount, result.lowRiskCount, result.noRiskCount);
                    // Setup click listeners for cards
                    setupCardClickListeners();
                    Log.d(TAG, "Base UI update complete after background scan.");
                } else if (!isAdded() || getActivity() == null) {
                    Log.w(TAG, "Fragment not attached or Activity is null after scan. UI not updated.");
                } else { // result == null
                    Log.e(TAG, "Scan result was null. UI not updated.");
                    if(getActivity() != null) Toast.makeText(getActivity(), "Failed to load app permissions.", Toast.LENGTH_SHORT).show();
                    updateDashboardBaseUI(-1, 0,0,0,0);
                }
            });
        });
    }

    // --- Class to Hold Scan Results ---
    private static class ScanResult {
        int score = -1;
        int highRiskCount = 0;
        int mediumRiskCount = 0;
        int lowRiskCount = 0;
        int noRiskCount = 0;
        Map<PermissionAnalyzer.RiskLevel, List<String>> categorizedApps;
        List<String> recommendations; // Temporary list for summary recommendations
    }

    // --- Core Scan Logic (Runs in Background) ---
    private ScanResult performScanAndCategorization(Context context, PackageManager pm) {
        ScanResult result = new ScanResult();

        try {
            result.categorizedApps = new HashMap<>();
            result.categorizedApps.put(PermissionAnalyzer.RiskLevel.HIGH, new ArrayList<>());
            result.categorizedApps.put(PermissionAnalyzer.RiskLevel.MEDIUM, new ArrayList<>());
            result.categorizedApps.put(PermissionAnalyzer.RiskLevel.LOW, new ArrayList<>());
            result.categorizedApps.put(PermissionAnalyzer.RiskLevel.NO_RISK, new ArrayList<>());
            int totalUserAppsScanned = 0;

            List<PackageInfo> installedApps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS | PackageManager.GET_META_DATA);

            for (PackageInfo pkgInfo : installedApps) {
                try {
                    if (pkgInfo != null && pkgInfo.applicationInfo != null && (pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                        totalUserAppsScanned++;
                        String packageName = pkgInfo.packageName;
                        boolean appHasHighRisk = false;
                        boolean appHasMediumRisk = false;
                        boolean appHasLowRisk = false;
                        String[] permissions = pkgInfo.requestedPermissions;
                        String permissionsString = "";

                        if (permissions != null && permissions.length > 0) {
                            permissionsString = String.join(",", permissions);
                            for (String permission : permissions) {
                                PermissionAnalyzer.RiskLevel risk = analyzer.getPermissionRisk(permission);
                                switch (risk) {
                                    case HIGH:   appHasHighRisk = true; break;
                                    case MEDIUM: appHasMediumRisk = true; break;
                                    case LOW:    appHasLowRisk = true; break;
                                }
                            }
                            // Categorize based on highest risk found
                            if (appHasHighRisk) result.categorizedApps.get(PermissionAnalyzer.RiskLevel.HIGH).add(packageName);
                            else if (appHasMediumRisk) result.categorizedApps.get(PermissionAnalyzer.RiskLevel.MEDIUM).add(packageName);
                            else if (appHasLowRisk) result.categorizedApps.get(PermissionAnalyzer.RiskLevel.LOW).add(packageName);
                            else result.categorizedApps.get(PermissionAnalyzer.RiskLevel.NO_RISK).add(packageName);
                        } else {
                            result.categorizedApps.get(PermissionAnalyzer.RiskLevel.NO_RISK).add(packageName);
                        }
                        // Save/Update App Permissions record in DB
                        AppPermissions appPerms = new AppPermissions(packageName, permissionsString);
                        appPermissionsDao.insertOrUpdateAppPermissions(appPerms);
                    }
                } catch (Exception e) {
                    Log.e("DashboardFragment BG", "Error processing/saving package: " + (pkgInfo != null ? pkgInfo.packageName : "null"), e);
                }
            } // End loop

            // Get counts
            result.highRiskCount = result.categorizedApps.get(PermissionAnalyzer.RiskLevel.HIGH).size();
            result.mediumRiskCount = result.categorizedApps.get(PermissionAnalyzer.RiskLevel.MEDIUM).size();
            result.lowRiskCount = result.categorizedApps.get(PermissionAnalyzer.RiskLevel.LOW).size();
            result.noRiskCount = result.categorizedApps.get(PermissionAnalyzer.RiskLevel.NO_RISK).size();

            // --- Generate ONLY Summary Recommendations Here ---
            result.recommendations = new ArrayList<>();
            if (result.highRiskCount > 0) result.recommendations.add("Review " + result.highRiskCount + " high-risk apps");
            if (result.mediumRiskCount > 0) result.recommendations.add("Check permissions for " + result.mediumRiskCount + " medium-risk apps");
            // --- End Summary Recommendations ---

            // Calculate Score
            result.score = calculateScore(result.highRiskCount, result.mediumRiskCount, totalUserAppsScanned);

        } catch (Exception e) {
            Log.e("DashboardFragment BG", "Major error during background scan", e);
            return null; // Indicate failure
        }
        return result; // Return scan results (score, counts, categories)
    }


    // Helper function for score calculation
    private int calculateScore(int high, int medium, int totalApps) {
        int score = 100;
        if (totalApps > 0) {
            double highPenalty = (double) high / totalApps * 50;
            double mediumPenalty = (double) medium / totalApps * 25;
            score = (int) (100 - highPenalty - mediumPenalty);
            if (score < 0) score = 0;
        }
        return score;
    }


    // --- LiveData Observer Callback (Updates Recommendations UI) ---
    private void onRecommendationsLiveDataUpdate(List<Recommendation> dbRecommendations) {
        Log.d(TAG, "LiveData observer received update with " + (dbRecommendations != null ? dbRecommendations.size() : "null") + " items.");

        // Check if categorizedApps is ready (it's updated by startPermissionScan)
        if (categorizedApps == null) {
            Log.w(TAG, "Categorized map is null. Waiting for full scan to complete before updating recommendations.");
            return;
        }

        // 1. Format the database recommendations into display strings
        List<String> displayStrings = new ArrayList<>();

        // 2. Add Summary Recommendations (Generated from the latest scan counts)
        int highCount = (categorizedApps.get(PermissionAnalyzer.RiskLevel.HIGH) != null) ? categorizedApps.get(PermissionAnalyzer.RiskLevel.HIGH).size() : 0;
        int mediumCount = (categorizedApps.get(PermissionAnalyzer.RiskLevel.MEDIUM) != null) ? categorizedApps.get(PermissionAnalyzer.RiskLevel.MEDIUM).size() : 0;
        if (highCount > 0) displayStrings.add("Review " + highCount + " high-risk apps");
        if (mediumCount > 0) displayStrings.add("Check permissions for " + mediumCount + " medium-risk apps");


        // 3. Add Formatted Strings from Real-time/Periodic Database Recommendations
        if (dbRecommendations != null) {
            for (Recommendation rec : dbRecommendations) {
                String displayText = rec.description; // Base text

                // Append package name in parentheses for click handling
                if (rec.associatedPackageName != null && !rec.associatedPackageName.isEmpty()) {
                    // This is done for UNUSED_APP, CLIPBOARD_ACCESS, etc.
                    displayText += " (" + rec.associatedPackageName + ")";
                }
                displayStrings.add(displayText);
            }
        }
        // 4. Update the UI
        updateRecommendationsUI(displayStrings);
    }


    // --- Method to Update ONLY the BASE Dashboard UI (Score and Risk Cards) ---
    private void updateDashboardBaseUI(int score, int high, int medium, int low, int no) {
        Log.d(TAG, "updateDashboardBaseUI called with Score: " + score);
        View fragmentView = getView();
        Context context = getContext();
        if (!isAdded() || context == null || fragmentView == null) {
            Log.e(TAG, "Fragment not attached or context/view is null. Cannot update base UI.");
            return;
        }

        // Update Score and Progress Bar
        if (progressText != null) progressText.setText(score == -1 ? "--" : String.valueOf(score));
        if (progressBar != null) progressBar.setProgress(score == -1 ? 0 : score, true);

        // Update Risk Card Text
        if (highRiskText != null) highRiskText.setText("High Risk (" + high + ")");
        if (mediumRiskText != null) mediumRiskText.setText("Medium Risk (" + medium + ")");
        if (lowRiskText != null) lowRiskText.setText("Low Risk (" + low + ")");
        if (noRiskText != null) noRiskText.setText("No Risk (" + no + ")");
        Log.d(TAG, "Base dashboard UI updated.");
    }


    // --- Method to Update ONLY the Recommendations List UI ---
    private void updateRecommendationsUI(List<String> currentDisplayRecommendations) {
        Log.d(TAG, "updateRecommendationsUI called with " + (currentDisplayRecommendations != null ? currentDisplayRecommendations.size() : "null") + " items.");
        Context context = getContext();
        if (recommendationsLayout == null || context == null || !isAdded()) {
            Log.e(TAG, "Cannot update recommendations UI: Layout, context, or fragment state invalid.");
            return;
        }

        recommendationsLayout.removeAllViews(); // Clear previous items
        LayoutInflater inflater = LayoutInflater.from(context);

        if (currentDisplayRecommendations == null || currentDisplayRecommendations.isEmpty()) {
            Log.d(TAG, "No recommendations to display.");
            TextView noRecsTextView = new TextView(context);
            noRecsTextView.setText("No specific recommendations found.");
            noRecsTextView.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
            noRecsTextView.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray));
            recommendationsLayout.addView(noRecsTextView);
        } else {
            Log.d(TAG, "Displaying " + currentDisplayRecommendations.size() + " recommendations.");
            for (String recText : currentDisplayRecommendations) {
                try {
                    View itemView = inflater.inflate(R.layout.list_item_recommendation, recommendationsLayout, false);
                    TextView recTextView = itemView.findViewById(R.id.recommendation_text);
                    ImageView recIcon = itemView.findViewById(R.id.recommendation_icon);

                    if (recTextView != null) recTextView.setText(recText);
                    else Log.e(TAG, "TextView recommendation_text not found");

                    // Set appropriate icon based on recommendation type (deduced from text)
                    if (recIcon != null) {
                        String lowerRecText = recText.toLowerCase();
                        if (lowerRecText.contains("high-risk")) {
                            recIcon.setImageResource(R.drawable.ic_risk_high);
                            recIcon.setBackgroundResource(R.drawable.risk_card_high_bg);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.high_risk_color));
                        } else if (lowerRecText.contains("medium-risk")) {
                            recIcon.setImageResource(R.drawable.ic_risk_medium);
                            recIcon.setBackgroundResource(R.drawable.risk_card_medium_bg);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.medium_risk_color));
                        } else if (lowerRecText.contains("unused")) {
                            recIcon.setImageResource(R.drawable.ic_history);
                            recIcon.setBackgroundResource(R.drawable.notice_background);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.default_recommendation_color));
                        } else if (lowerRecText.contains("clipboard")) {
                            recIcon.setImageResource(R.drawable.ic_clipboard);
                            recIcon.setBackgroundResource(R.drawable.risk_card_medium_bg);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.medium_risk_color));
                        } else if (lowerRecText.contains("camera")) {
                            recIcon.setImageResource(R.drawable.ic_camera);
                            recIcon.setBackgroundResource(R.drawable.risk_card_high_bg);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.high_risk_color));
                        } else if (lowerRecText.contains("location") || lowerRecText.contains("mic")) { // Added mic check for robustness
                            recIcon.setImageResource(R.drawable.ic_location); // Assuming location icon is sufficient for location/mic
                            recIcon.setBackgroundResource(R.drawable.risk_card_medium_bg);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.medium_risk_color));
                        } else {
                            // Default icon for unclassified or permission grant/revoke events
                            recIcon.setImageResource(R.drawable.ic_lightbulb);
                            recIcon.setBackgroundResource(R.drawable.notice_background);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.default_recommendation_color));
                        }
                    } else { Log.e(TAG, "ImageView recommendation_icon not found"); }

                    recommendationsLayout.addView(itemView);

                    // Click listener
                    itemView.setOnClickListener(v -> {
                        Log.d(TAG, "Recommendation clicked: " + recText);
                        String packageName = extractPackageNameFromRecommendation(recText);
                        if (recText.contains("high-risk")) navigateToAppList(PermissionAnalyzer.RiskLevel.HIGH);
                        else if (recText.contains("medium-risk")) navigateToAppList(PermissionAnalyzer.RiskLevel.MEDIUM);
                        else if (recText.contains("unused") || recText.contains("clipboard") || recText.contains("camera") || recText.contains("location") || recText.contains("permission")) {
                            // Handle specific app recommendations or permission changes by opening settings
                            if (packageName != null) openAppSettings(packageName);
                            else Toast.makeText(getActivity(), "Could not identify app.", Toast.LENGTH_SHORT).show();
                        } else Toast.makeText(getActivity(), "More info for: " + recText, Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) { Log.e(TAG, "Error inflating item for '" + recText + "'", e); }
            }
        }
        Log.d(TAG, "updateRecommendationsUI finished.");
    }


    // Helper Method to Set Up Click Listeners for Risk Cards
    private void setupCardClickListeners() {
        Log.d(TAG, "Setting up card click listeners...");
        View v = getView(); if (v == null) {Log.e(TAG, "View null in setupCardClickListeners"); return;}
        if (highRiskCard != null) highRiskCard.setOnClickListener(view -> navigateToAppList(PermissionAnalyzer.RiskLevel.HIGH));
        if (mediumRiskCard != null) mediumRiskCard.setOnClickListener(view -> navigateToAppList(PermissionAnalyzer.RiskLevel.MEDIUM));
        if (lowRiskCard != null) lowRiskCard.setOnClickListener(view -> navigateToAppList(PermissionAnalyzer.RiskLevel.LOW));
        if (noRiskCard != null) noRiskCard.setOnClickListener(view -> navigateToAppList(PermissionAnalyzer.RiskLevel.NO_RISK));
        Log.d(TAG, "Card click listeners set up.");
    }

    // Helper Method to Navigate to the App List Screen
    private void navigateToAppList(PermissionAnalyzer.RiskLevel riskLevel) {
        Log.d(TAG, "navigateToAppList called for: " + riskLevel);
        if (getActivity() == null || categorizedApps == null || categorizedApps.get(riskLevel) == null) {
            Log.e(TAG, "Cannot navigate: Activity, category map, or risk list is null for: " + riskLevel);
            if (getActivity() != null) Toast.makeText(getActivity(), "App data is still loading or unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> appPackageNames = categorizedApps.get(riskLevel);
        if (appPackageNames != null && !appPackageNames.isEmpty()) {
            AppListFragment appListFragment = new AppListFragment(); Bundle args = new Bundle();
            args.putSerializable("RISK_LEVEL", riskLevel); args.putStringArrayList("APP_PACKAGES", new ArrayList<>(appPackageNames));
            appListFragment.setArguments(args);
            getParentFragmentManager().beginTransaction().replace(R.id.fragment_container, appListFragment).addToBackStack("dashboard").commit();
        } else {
            Log.d(TAG, "No apps found for risk level: " + riskLevel);
            if (getActivity() != null) Toast.makeText(getActivity(), "No apps found for this risk level.", Toast.LENGTH_SHORT).show();
        }
    }

    // Helper to extract package name from recommendation text
    private String extractPackageNameFromRecommendation(String recommendationText) {
        if (recommendationText == null) return null; int startIndex = recommendationText.lastIndexOf('('); int endIndex = recommendationText.lastIndexOf(')');
        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            String potentialPackageName = recommendationText.substring(startIndex + 1, endIndex);
            if (potentialPackageName.contains(".")) return potentialPackageName;
            else Log.w(TAG, "Extracted text doesn't look like pkg name: " + potentialPackageName);
        } else Log.w(TAG, "Could not find pkg name format in: " + recommendationText); return null;
    }

    // Helper to open the system settings page for a specific app
    private void openAppSettings(String packageName) {
        Log.d(TAG, "Attempting to open settings for: " + packageName);
        Context context = getContext();
        if (packageName == null || packageName.isEmpty() || context == null) {
            Log.e(TAG, "Cannot open app settings - invalid pkg name or context is null");
            if (context != null) Toast.makeText(context, "Could not open settings.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", packageName, null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open app settings activity for " + packageName, e);
            Toast.makeText(context, "Could not open settings activity.", Toast.LENGTH_SHORT).show();
        }
    }

    // Helper to convert dp to pixels
    private int dpToPx(int dp) {
        Context context = getContext();
        if (context == null || context.getResources() == null || context.getResources().getDisplayMetrics() == null) return dp;
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }
} // End of DashboardFragment class
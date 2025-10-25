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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider; // Keep ViewModel for LiveData setup

// --- ROOM DATABASE IMPORTS ---
import com.example.guardianai.AppDatabase;
import com.example.guardianai.AppPermissions;
import com.example.guardianai.AppPermissionsDao;
import com.example.guardianai.Recommendation;
import com.example.guardianai.RecommendationDao; // Re-import DAO for background access
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
    private ProgressBar loadingSpinner;
    private ConstraintLayout mainContentGroup;

    // --- Logic Components ---
    private PermissionAnalyzer analyzer;
    private Map<PermissionAnalyzer.RiskLevel, List<String>> categorizedApps; // Holds results from last scan
    private RecommendationViewModel recommendationViewModel; // ViewModel instance
    private AppPermissionsDao appPermissionsDao; // DAO for AppPermissions table
    private RecommendationDao recommendationDao; // ADD THIS LINE ⬅️
// ...
// ...

    // --- Threading Components ---
    private ExecutorService executorService;
    private Handler mainThreadHandler;

    // --- Lifecycle Method: Create the View ---
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        Log.d(TAG, "onCreateView called");

        // Initialize ViewModel and DAOs
        recommendationViewModel = new ViewModelProvider(this).get(RecommendationViewModel.class);
        initializeViews(view);
        analyzer = new PermissionAnalyzer();

        Context appContext = getContext() != null ? getContext().getApplicationContext() : null;
        if (appContext != null) {
            AppDatabase db = AppDatabase.getDatabase(appContext);
            appPermissionsDao = db.appPermissionsDao();
            recommendationDao = db.recommendationDao(); // Re-initialize Recommendation DAO
            Log.d(TAG, "Database DAOs initialized.");
        } else {
            Log.e(TAG, "Context was null during DAO initialization!");
        }

        executorService = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());

        return view;
    }

    // --- Lifecycle Method: View is Created and Ready ---
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated called");

        // --- OBSERVE LIVE DATA (Only used for real-time updates after initial load) ---
        // This is kept, but it will now update the UI with a full list of *all* recommendations
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

        loadingSpinner = view.findViewById(R.id.loading_spinner);
        mainContentGroup = view.findViewById(R.id.main_content_group);

        Log.d(TAG, "Views initialized.");
    }

    // --- Method to Start the Background Scan ---
    private void startPermissionScan() {
        if (executorService == null || executorService.isShutdown() || appPermissionsDao == null || recommendationDao == null) {
            Log.e(TAG, "Cannot start scan: Executor or DAOs are not ready.");
            if(getActivity() != null) Toast.makeText(getActivity(), "Error initializing components.", Toast.LENGTH_LONG).show();
            updateDashboardBaseUI(-1, 0,0,0,0);
            updateRecommendationsUI(new ArrayList<>());
            return;
        }

        // --- SHOW SPINNER ---
        // Only show spinner if the UI isn't already visible (i.e., first load or fresh start)
        if (mainContentGroup.getVisibility() != View.VISIBLE) {
            if (loadingSpinner != null) loadingSpinner.setVisibility(View.VISIBLE);
            if (mainContentGroup != null) mainContentGroup.setVisibility(View.GONE);
        }
        // --- END SHOW SPINNER ---

        Log.d(TAG, "Submitting permission scan task to executor...");
        executorService.execute(() -> { // Submit task to run on the background thread
            Log.d(TAG, "Background scan task started.");
            Context context = getContext();
            if (context == null) { Log.e(TAG, "Context became null. Aborting."); return; }

            // Perform the scan, categorization, DB saving, AND recommendation fetching in one go
            final ScanResult result = performScanAndCategorization(context, context.getPackageManager());

            // --- Post Results back to Main Thread ---
            mainThreadHandler.post(() -> { // Use the handler to run code on the UI thread
                Log.d(TAG, "Received scan results on main thread.");
                if (result != null && isAdded() && getActivity() != null) {
                    // 1. Update the categorizedApps map (used for card clicks)
                    categorizedApps = result.categorizedApps;

                    // 2. Update the BASE UI (score and risk cards)
                    updateDashboardBaseUI(result.score, result.highRiskCount, result.mediumRiskCount, result.lowRiskCount, result.noRiskCount);

                    // 3. Update the Recommendations UI (using the list fetched in background)
                    // We call the observer handler manually here to display the initial data
                    onRecommendationsLiveDataUpdate(result.recommendations);

                    // 4. Setup click listeners for cards
                    setupCardClickListeners();

                    // --- HIDE SPINNER AND SHOW CONTENT ---
                    if (loadingSpinner != null) loadingSpinner.setVisibility(View.GONE);
                    if (mainContentGroup != null) mainContentGroup.setVisibility(View.VISIBLE);
                    // --- END HIDE SPINNER ---

                    Log.d(TAG, "Full UI update complete.");
                } else { // Handle failure
                    Log.e(TAG, "Scan result was null or fragment detached. UI not updated.");
                    if(getActivity() != null) Toast.makeText(getActivity(), "Failed to load app permissions.", Toast.LENGTH_SHORT).show();
                    if (loadingSpinner != null) loadingSpinner.setVisibility(View.GONE);
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
        List<Recommendation> recommendations; // ADDED: Now holds Recommendation objects fetched from DB
    }

    // --- Core Scan Logic (Runs in Background) ---
    private ScanResult performScanAndCategorization(Context context, PackageManager pm) {
        ScanResult result = new ScanResult();

        try { // Wrap major logic
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
                        String packageName = pkgInfo.packageName;

                        // --- CRITICAL FILTER: EXCLUDE SELF APP ---
                        if (packageName.equals(context.getPackageName())) { continue; }
                        // ----------------------------------------

                        totalUserAppsScanned++;
                        boolean appHasHighRisk = false;
                        boolean appHasMediumRisk = false;
                        boolean appHasLowRisk = false;
                        String[] permissions = pkgInfo.requestedPermissions;
                        String permissionsString = "";

                        if (permissions != null && permissions.length > 0) {
                            permissionsString = String.join(",", permissions);
                            for (String permission : permissions) {
                                PermissionAnalyzer.RiskLevel risk = analyzer.getPermissionRisk(permission);

                                // --- HYBRID SCORING LOGIC ---
                                int grantStatus = pm.checkPermission(permission, packageName);
                                boolean isGranted = (grantStatus == PackageManager.PERMISSION_GRANTED);

                                // Only count risk if the permission is currently GRANTED.
                                if (isGranted) {
                                    switch (risk) {
                                        case HIGH:   appHasHighRisk = true; break;
                                        case MEDIUM: appHasMediumRisk = true; break;
                                        case LOW:    appHasLowRisk = true; break;
                                    }
                                }
                            }
                            // Categorize based on highest GRANTED risk found
                            if (appHasHighRisk) result.categorizedApps.get(PermissionAnalyzer.RiskLevel.HIGH).add(packageName);
                            else if (appHasMediumRisk) result.categorizedApps.get(PermissionAnalyzer.RiskLevel.MEDIUM).add(packageName);
                                // --- FIX: Ensure Low Risk box counts apps with GRANTED Low Risk perms ---
                            else if (appHasLowRisk) result.categorizedApps.get(PermissionAnalyzer.RiskLevel.LOW).add(packageName);
                                // --- END FIX ---
                            else {
                                // App requested permissions, but ZERO granted H/M/L
                                result.categorizedApps.get(PermissionAnalyzer.RiskLevel.NO_RISK).add(packageName);
                            }
                        } else {
                            // No permissions requested at all
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

            // Calculate Score
            result.score = calculateScore(result.highRiskCount, result.mediumRiskCount, totalUserAppsScanned);

            // --- NEW: Load ALL Recommendations from DB (Blocking read) ---
            RecommendationDao recDao = AppDatabase.getDatabase(context).recommendationDao();
            result.recommendations = recDao.getAllRecommendations(); // Blocking read done on background thread
            Log.d(TAG, "Background scan finished. Fetched " + result.recommendations.size() + " recs from DB.");
            // --- END NEW ---


        } catch (Exception e) {
            Log.e("DashboardFragment BG", "Major error during background scan", e);
            return null; // Indicate failure
        }
        return result; // Return scan results and fully loaded recommendations
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
        // This observer will now only handle updates *after* the initial full load.
        Log.d(TAG, "LiveData observer received update with " + (dbRecommendations != null ? dbRecommendations.size() : "null") + " items.");

        // Check if categorizedApps is ready (it's updated by startPermissionScan)
        if (categorizedApps == null) {
            Log.w(TAG, "Categorized map is null. Skipping live update.");
            return;
        }

        // 1. Format the database recommendations into display strings
        List<String> displayStrings = new ArrayList<>();

        // 2. Add Summary Recommendations (The crucial part that relies on the main scan)
        int highCount = (categorizedApps.get(PermissionAnalyzer.RiskLevel.HIGH) != null) ? categorizedApps.get(PermissionAnalyzer.RiskLevel.HIGH).size() : 0;
        int mediumCount = (categorizedApps.get(PermissionAnalyzer.RiskLevel.MEDIUM) != null) ? categorizedApps.get(PermissionAnalyzer.RiskLevel.MEDIUM).size() : 0;

        // Add summary recs to the front of the list
        if (highCount > 0) displayStrings.add("Review " + highCount + " high-risk apps");
        if (mediumCount > 0) displayStrings.add("Check permissions for " + mediumCount + " medium-risk apps");


        // 3. Add Formatted Strings from Real-time/Periodic Database Recommendations
        if (dbRecommendations != null) {
            for (Recommendation rec : dbRecommendations) {
                String displayText = rec.description; // Base text

                // Append package name in parentheses for click handling
                if (rec.associatedPackageName != null && !rec.associatedPackageName.isEmpty())
                {
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
                        if (lowerRecText.contains("high-risk") || lowerRecText.contains("camera")) {
                            recIcon.setImageResource(R.drawable.ic_risk_high);
                            recIcon.setBackgroundResource(R.drawable.risk_card_high_bg);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.high_risk_color));
                        } else if (lowerRecText.contains("medium-risk") || lowerRecText.contains("clipboard") || lowerRecText.contains("location") || lowerRecText.contains("mic")) {
                            recIcon.setImageResource(R.drawable.ic_risk_medium);
                            recIcon.setBackgroundResource(R.drawable.risk_card_medium_bg);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.medium_risk_color));
                        } else if (lowerRecText.contains("unused")) {
                            recIcon.setImageResource(R.drawable.ic_history);
                            recIcon.setBackgroundResource(R.drawable.notice_background);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.default_recommendation_color));
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
                            if (packageName != null) openAppSettings(packageName);
                            else Toast.makeText(getActivity(), "Could not identify app.", Toast.LENGTH_SHORT).show();
                        } else Toast.makeText(getActivity(), "More info for: " + recText, Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) { Log.e(TAG, "Error inflating item for '" + recText + "'", e); }
            }
        }
        Log.d(TAG, "updateRecommendationsUI finished.");
    }

    // --- Other Helper Methods ---

    // Setup Click Listeners for Risk Cards
    private void setupCardClickListeners() {
        Log.d(TAG, "Setting up card click listeners...");
        View v = getView(); if (v == null) {Log.e(TAG, "View null in setupCardClickListeners"); return;}
        if (highRiskCard != null) highRiskCard.setOnClickListener(view -> navigateToAppList(PermissionAnalyzer.RiskLevel.HIGH));
        if (mediumRiskCard != null) mediumRiskCard.setOnClickListener(view -> navigateToAppList(PermissionAnalyzer.RiskLevel.MEDIUM));
        if (lowRiskCard != null) lowRiskCard.setOnClickListener(view -> navigateToAppList(PermissionAnalyzer.RiskLevel.LOW));
        if (noRiskCard != null) noRiskCard.setOnClickListener(view -> navigateToAppList(PermissionAnalyzer.RiskLevel.NO_RISK));
        Log.d(TAG, "Card click listeners set up.");
    }

    // Navigate to App List Screen
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

    // Helper to extract package name
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
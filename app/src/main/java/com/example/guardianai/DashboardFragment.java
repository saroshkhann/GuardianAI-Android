package com.example.guardianai;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler; // Import Handler
import android.os.Looper; // Import Looper
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

import com.example.guardianai.db.AppDatabase;
import com.example.guardianai.db.AppPermissions;
import com.example.guardianai.db.AppPermissionsDao;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService; // Import ExecutorService
import java.util.concurrent.Executors;     // Import Executors

public class DashboardFragment extends Fragment {

    // --- UI Elements ---
    // (Same as before)
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
    private Map<PermissionAnalyzer.RiskLevel, List<String>> categorizedApps;
    private List<String> recommendations;
    private AppPermissionsDao appPermissionsDao;

    // --- Threading Components ---
    private ExecutorService executorService; // Executor for background tasks
    private Handler mainThreadHandler;      // Handler to post results to UI thread

    // --- Lifecycle Method: Create the View ---
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        Log.d("DashboardFragment", "onCreateView called");

        initializeViews(view);
        analyzer = new PermissionAnalyzer();

        // Initialize Database Access
        AppDatabase db = AppDatabase.getDatabase(getContext().getApplicationContext());
        appPermissionsDao = db.appPermissionsDao();
        Log.d("DashboardFragment", "Database DAO initialized.");

        // Initialize Threading Components
        executorService = Executors.newSingleThreadExecutor(); // Creates a single background thread
        mainThreadHandler = new Handler(Looper.getMainLooper()); // Handler associated with the main UI thread

        return view;
    }

    // --- Lifecycle Method: View is Created and Ready ---
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d("DashboardFragment", "onViewCreated called");
        startPermissionScan(); // Start scan using the executor
    }

    // --- Lifecycle Method: Clean up Executor ---
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Shutdown the executor when the fragment's view is destroyed to prevent leaks
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            Log.d("DashboardFragment", "ExecutorService shut down.");
        }
    }


    // --- Helper Method to Initialize Views ---
    private void initializeViews(View view) {
        // ... (same as before) ...
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
        Log.d("DashboardFragment", "Views initialized.");
    }

    // --- Method to Start the Background Scan ---
    private void startPermissionScan() {
        Log.d("DashboardFragment", "Submitting permission scan task to executor...");
        executorService.execute(() -> { // Submit a task to run on the background thread
            Log.d("DashboardFragment BG", "Background scan task started.");
            Context context = getContext(); // Get context within the background thread if needed
            if (context == null || appPermissionsDao == null) {
                Log.e("DashboardFragment BG", "Context or DAO is null in background task. Aborting.");
                return; // Cannot proceed
            }
            PackageManager pm = context.getPackageManager();

            // Perform the scan and DB operations
            ScanResult result = performScanAndCategorization(context, pm);

            // --- Post Results back to Main Thread ---
            mainThreadHandler.post(() -> { // Use the handler to run code on the UI thread
                Log.d("DashboardFragment", "Received scan results on main thread.");
                if (result != null) {
                    // Update the main variables with results from the background thread
                    categorizedApps = result.categorizedApps;
                    recommendations = result.recommendations;
                    // Update the UI
                    updateDashboardUI(result.score, result.highRiskCount, result.mediumRiskCount, result.lowRiskCount, result.noRiskCount, result.recommendations);
                    // Setup click listeners now that data is ready and UI is updated
                    setupCardClickListeners();
                    Log.d("DashboardFragment", "UI update complete after background scan.");
                } else {
                    Log.e("DashboardFragment", "Scan result was null. UI not updated.");
                    // Optionally show an error message to the user
                    if(getActivity() != null) {
                        Toast.makeText(getActivity(), "Failed to load app permissions.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });
    }

    // --- New Class to Hold Scan Results ---
    private static class ScanResult {
        int score;
        int highRiskCount;
        int mediumRiskCount;
        int lowRiskCount;
        int noRiskCount;
        Map<PermissionAnalyzer.RiskLevel, List<String>> categorizedApps;
        List<String> recommendations;
    }

    // --- Core Scan Logic (Runs in Background) ---
    private ScanResult performScanAndCategorization(Context context, PackageManager pm) {
        ScanResult result = new ScanResult(); // Object to hold all results

        // Initialize maps/counts
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
                        if (appHasHighRisk) result.categorizedApps.get(PermissionAnalyzer.RiskLevel.HIGH).add(packageName);
                        else if (appHasMediumRisk) result.categorizedApps.get(PermissionAnalyzer.RiskLevel.MEDIUM).add(packageName);
                        else if (appHasLowRisk) result.categorizedApps.get(PermissionAnalyzer.RiskLevel.LOW).add(packageName);
                        else result.categorizedApps.get(PermissionAnalyzer.RiskLevel.NO_RISK).add(packageName);
                    } else {
                        result.categorizedApps.get(PermissionAnalyzer.RiskLevel.NO_RISK).add(packageName);
                    }
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

        // Generate Recommendations
        result.recommendations = new ArrayList<>();
        if (result.highRiskCount > 0) result.recommendations.add("Review " + result.highRiskCount + " high-risk apps");
        if (result.mediumRiskCount > 0) result.recommendations.add("Check permissions for " + result.mediumRiskCount + " medium-risk apps");
        // Load Unused App Recs
        SharedPreferences prefs = context.getSharedPreferences("GuardianAIPrefs", Context.MODE_PRIVATE);
        Set<String> unusedPackages = prefs.getStringSet("unused_risky_apps", new HashSet<>());
        if (unusedPackages != null && !unusedPackages.isEmpty()) {
            for (String packageName : unusedPackages) {
                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                    String appName = appInfo.loadLabel(pm).toString();
                    result.recommendations.add("Review unused permissions for '" + appName + "' (" + packageName + ")");
                } catch (Exception e) { result.recommendations.add("Review unused permissions for package: (" + packageName + ")");}
            }
        }

        // Calculate Score
        result.score = calculateScore(result.highRiskCount, result.mediumRiskCount, totalUserAppsScanned);

        // Log results
        Log.d("DashboardFragment BG", "Scan finished. Score: " + result.score + ", Counts - H:" + result.highRiskCount + " M:" + result.mediumRiskCount + " L:" + result.lowRiskCount + " N:" + result.noRiskCount);
        Log.d("DashboardFragment BG", "Generated Recs: " + result.recommendations.toString());

        return result; // Return all calculated data
    }


    // Helper function for score calculation (remains the same)
    private int calculateScore(int high, int medium, int totalApps) {
        // ... (same as before) ...
        int score = 100;
        if (totalApps > 0) {
            double highPenalty = (double) high / totalApps * 50;
            double mediumPenalty = (double) medium / totalApps * 25;
            score = (int) (100 - highPenalty - mediumPenalty);
            if (score < 0) score = 0;
        }
        return score;
    }


    // Helper Method to Update the UI Elements (remains the same as previous complete code)
    private void updateDashboardUI(int score, int high, int medium, int low, int no, List<String> currentRecommendations) {
        // ... (same as the previous version you had, including logging and adding recommendation views + click listeners) ...
        Log.d("DashboardFragment", "updateDashboardUI called with Score: " + score + ", High: " + high + ", Medium: " + medium);
        View fragmentView = getView();
        Context context = getContext();
        if (fragmentView == null || context == null) { Log.e("DashboardFragment", "View or Context is null in updateDashboardUI"); return; }
        if (progressText != null) progressText.setText(String.valueOf(score));
        if (progressBar != null) progressBar.setProgress(score, true);
        if (highRiskText != null) highRiskText.setText("High Risk (" + high + ")");
        if (mediumRiskText != null) mediumRiskText.setText("Medium Risk (" + medium + ")");
        if (lowRiskText != null) lowRiskText.setText("Low Risk (" + low + ")");
        if (noRiskText != null) noRiskText.setText("No Risk (" + no + ")");
        if (recommendationsLayout == null) { Log.e("DashboardFragment", "recommendationsLayout is null."); return; }
        recommendationsLayout.removeAllViews();
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
                    if (recTextView != null) recTextView.setText(recText);
                    else Log.e("DashboardFragment", "TextView recommendation_text not found");
                    if (recIcon != null) { /* Set icon based on recText */
                        if (recText.contains("high-risk")) {
                            recIcon.setImageResource(R.drawable.ic_risk_high);
                            recIcon.setBackgroundResource(R.drawable.risk_card_high_bg);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.high_risk_color));
                        } else if (recText.contains("medium-risk")) {
                            recIcon.setImageResource(R.drawable.ic_risk_medium);
                            recIcon.setBackgroundResource(R.drawable.risk_card_medium_bg);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.medium_risk_color));
                        } else if (recText.contains("unused")) {
                            recIcon.setImageResource(R.drawable.ic_history);
                            recIcon.setBackgroundResource(R.drawable.notice_background);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.default_recommendation_color));
                        } else {
                            recIcon.setImageResource(R.drawable.ic_lightbulb);
                            recIcon.setBackgroundResource(R.drawable.notice_background);
                            recIcon.setColorFilter(ContextCompat.getColor(context, R.color.default_recommendation_color));
                        }
                    } else Log.e("DashboardFragment", "ImageView recommendation_icon not found");
                    recommendationsLayout.addView(itemView);
                    itemView.setOnClickListener(v -> { /* Click listener logic */
                        Log.d("DashboardFragment", "Recommendation clicked: " + recText);
                        if (recText.contains("high-risk")) navigateToAppList(PermissionAnalyzer.RiskLevel.HIGH);
                        else if (recText.contains("medium-risk")) navigateToAppList(PermissionAnalyzer.RiskLevel.MEDIUM);
                        else if (recText.contains("unused")) {
                            String packageName = extractPackageNameFromRecommendation(recText);
                            if (packageName != null) openAppSettings(packageName);
                            else Toast.makeText(getActivity(), "Could not identify app.", Toast.LENGTH_SHORT).show();
                        } else Toast.makeText(getActivity(), "More info for: " + recText, Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) { Log.e("DashboardFragment", "Error inflating item for '" + recText + "'", e); }
            }
        }
        Log.d("DashboardFragment", "updateDashboardUI finished.");
    }


    // Helper Method to Set Up Click Listeners for Risk Cards (remains the same)
    private void setupCardClickListeners() {
        // ... (same as before) ...
        Log.d("DashboardFragment", "Setting up card click listeners...");
        if (highRiskCard != null) highRiskCard.setOnClickListener(v -> navigateToAppList(PermissionAnalyzer.RiskLevel.HIGH));
        if (mediumRiskCard != null) mediumRiskCard.setOnClickListener(v -> navigateToAppList(PermissionAnalyzer.RiskLevel.MEDIUM));
        if (lowRiskCard != null) lowRiskCard.setOnClickListener(v -> navigateToAppList(PermissionAnalyzer.RiskLevel.LOW));
        if (noRiskCard != null) noRiskCard.setOnClickListener(v -> navigateToAppList(PermissionAnalyzer.RiskLevel.NO_RISK));
        Log.d("DashboardFragment", "Card click listeners set up.");
    }

    // Helper Method to Navigate to the App List Screen (remains the same)
    private void navigateToAppList(PermissionAnalyzer.RiskLevel riskLevel) {
        // ... (same as before) ...
        Log.d("DashboardFragment", "navigateToAppList called for: " + riskLevel);
        if (getActivity() == null || categorizedApps == null || categorizedApps.get(riskLevel) == null) {
            Log.e("DashboardFragment", "App data not ready or risk level not found for: " + riskLevel);
            if (getActivity() != null) Toast.makeText(getActivity(), "App data is still loading or unavailable.", Toast.LENGTH_SHORT).show();
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
            if (getActivity() != null) Toast.makeText(getActivity(), "No apps found for this risk level.", Toast.LENGTH_SHORT).show();
        }
    }

    // Helper to extract package name (remains the same)
    private String extractPackageNameFromRecommendation(String recommendationText) {
        // ... (same as before) ...
        int startIndex = recommendationText.lastIndexOf('(');
        int endIndex = recommendationText.lastIndexOf(')');
        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            String potentialPackageName = recommendationText.substring(startIndex + 1, endIndex);
            Log.d("DashboardFragment", "Extracted potential package name: " + potentialPackageName);
            if (potentialPackageName.contains(".")) return potentialPackageName;
            else Log.w("DashboardFragment", "Extracted text doesn't look like pkg name: " + potentialPackageName);
        } else Log.w("DashboardFragment", "Could not find pkg name in format in: " + recommendationText);
        return null;
    }

    // Helper to open app settings (remains the same)
    private void openAppSettings(String packageName) {
        // ... (same as before) ...
        Log.d("DashboardFragment", "Attempting to open settings for: " + packageName);
        Context context = getContext();
        if (packageName == null || packageName.isEmpty() || context == null) {
            Log.e("DashboardFragment", "Cannot open app settings - invalid pkg name or context is null");
            if (context != null) Toast.makeText(context, "Could not open settings.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", packageName, null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("DashboardFragment", "Failed to open app settings activity for " + packageName, e);
            Toast.makeText(context, "Could not open settings activity.", Toast.LENGTH_SHORT).show();
        }
    }

    // Helper to convert dp to pixels (remains the same)
    private int dpToPx(int dp) {
        // ... (same as before) ...
        Context context = getContext();
        if (context == null || context.getResources() == null || context.getResources().getDisplayMetrics() == null) return dp;
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }
} // End of DashboardFragment class
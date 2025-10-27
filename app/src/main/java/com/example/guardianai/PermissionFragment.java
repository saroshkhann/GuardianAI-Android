package com.example.guardianai;

// --- STANDARD ANDROID IMPORTS ---
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView; // <-- Import for SearchView
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// --- YOUR CUSTOM CLASS IMPORTS ---
import com.example.guardianai.AppModel;
import com.example.guardianai.AppRiskAdapter;
import com.example.guardianai.PermissionCategory;
import com.example.guardianai.PermissionGridAdapter;
import com.example.guardianai.PermissionAnalyzer;


public class PermissionFragment extends Fragment {

    private static final String TAG = "PermissionFragment";

    // --- UI Components ---
    private RecyclerView gridRecyclerView;
    private RecyclerView appListRecyclerView;
    private TextView tvUnusedSubtitle;
    private Button btnReviewUnused;
    private SearchView searchView; // <-- ADDED

    // --- Adapters ---
    private PermissionGridAdapter gridAdapter;
    private AppRiskAdapter appListAdapter;

    // --- Data Holders ---
    private Map<String, List<PackageInfo>> permissionGroupMap = new HashMap<>();
    private List<AppModel> allAppsList = new ArrayList<>();
    private List<PermissionCategory> categoryList = new ArrayList<>();
    private List<String> unusedAppPackages = new ArrayList<>();
    private int totalAppCount = 0;

    // Analyzer (from your Dashboard)
    private PermissionAnalyzer analyzer;

    // --- Threading Components ---
    private ExecutorService executorService;
    private Handler mainThreadHandler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_permission, container, false);

        // --- Find all UI elements ---
        gridRecyclerView = view.findViewById(R.id.recycler_view_permission_grid);
        appListRecyclerView = view.findViewById(R.id.recycler_view_app_list);
        tvUnusedSubtitle = view.findViewById(R.id.tv_unused_subtitle);
        btnReviewUnused = view.findViewById(R.id.btn_review_unused);
        searchView = view.findViewById(R.id.app_search_view); // <-- ADDED

        analyzer = new PermissionAnalyzer();

        // --- Setup adapters, listeners, and threads ---
        setupGrid();
        setupAppList();
        setupSearch(); // <-- ADDED
        btnReviewUnused.setOnClickListener(v -> onReviewUnusedClicked());

        executorService = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());

        loadData();

        return view;
    }

    private void setupGrid() {
        gridRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        gridAdapter = new PermissionGridAdapter(new ArrayList<>());
        gridRecyclerView.setAdapter(gridAdapter);

        // Handle grid clicks
        gridAdapter.setOnCategoryClickListener(category -> {
            Log.d(TAG, "Clicked on category: " + category.getName());

            List<PackageInfo> appsWithPermission = permissionGroupMap.get(category.getPermissionConstant());

            if (appsWithPermission == null || appsWithPermission.isEmpty()) {
                Toast.makeText(getContext(), "No apps found with this permission.", Toast.LENGTH_SHORT).show();
                return;
            }

            ArrayList<String> packageNames = new ArrayList<>();
            for (PackageInfo pkg : appsWithPermission) {
                packageNames.add(pkg.packageName);
            }

            PermissionAnalyzer.RiskLevel riskLevel = analyzer.getPermissionRisk(category.getPermissionConstant());
            navigateToAppList(packageNames, riskLevel);
        });
    }

    private void setupAppList() {
        appListRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        appListAdapter = new AppRiskAdapter(getContext(), new ArrayList<>());
        appListRecyclerView.setAdapter(appListAdapter);
        appListRecyclerView.setNestedScrollingEnabled(false);

        // Handle app list clicks
        appListAdapter.setOnAppClickListener(app -> {
            Log.d(TAG, "Clicked on app: " + app.getPackageName());
            openAppSettings(app.getPackageName());
        });
    }

    /**
     * NEW METHOD
     * Sets up the listener for the SearchView.
     */
    private void setupSearch() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterAppList(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterAppList(newText);
                return false;
            }
        });
    }

    /**
     * NEW METHOD
     * Filters the 'All Installed Apps' list based on a search query.
     */
    private void filterAppList(String query) {
        if (query == null || query.isEmpty()) {
            appListAdapter.updateData(allAppsList);
            return;
        }

        List<AppModel> filteredList = new ArrayList<>();
        for (AppModel app : allAppsList) {
            if (app.getAppName().toLowerCase().contains(query.toLowerCase())) {
                filteredList.add(app);
            }
        }
        appListAdapter.updateData(filteredList);
    }


    private void loadData() {
        Log.d(TAG, "Submitting scan task to background thread...");

        executorService.execute(() -> {
            if (getContext() == null) return;

            scanAllPermissions();
            processGridCategories();
            findUnusedApps();
            Log.d(TAG, "Background scan complete.");

            mainThreadHandler.post(() -> {
                if (getContext() == null || !isAdded()) {
                    return;
                }

                gridAdapter.updateData(categoryList);
                appListAdapter.updateData(allAppsList);
                Log.d(TAG, "UI adapters updated on main thread.");

                if (btnReviewUnused.getText().toString().equals("Grant Permission")) {
                    return;
                }

                int unusedCount = unusedAppPackages.size();
                if (unusedCount > 0) {
                    tvUnusedSubtitle.setText(unusedCount + " apps can be safely revoked.");
                } else {
                    tvUnusedSubtitle.setText("No unused apps found.");
                }
                btnReviewUnused.setEnabled(unusedCount > 0);
            });
        });
    }

    private void scanAllPermissions() {
        if (getContext() == null) return;

        permissionGroupMap.clear();
        allAppsList.clear();
        totalAppCount = 0;

        PackageManager pm = getContext().getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS | PackageManager.GET_META_DATA);

        for (PackageInfo packageInfo : packages) {
            try {
                if ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    continue;
                }
                if (packageInfo.packageName.equals(getContext().getPackageName())) {
                    continue;
                }

                totalAppCount++;
                String packageName = packageInfo.packageName;
                String appName = packageInfo.applicationInfo.loadLabel(pm).toString();
                Drawable appIcon = packageInfo.applicationInfo.loadIcon(pm);
                String risk = "Low";

                String[] requestedPermissions = packageInfo.requestedPermissions;
                if (requestedPermissions != null) {
                    boolean hasHigh = false;
                    boolean hasMedium = false;

                    for (int i = 0; i < requestedPermissions.length; i++) {
                        if ((packageInfo.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                            String permission = requestedPermissions[i];

                            List<PackageInfo> appList = permissionGroupMap.get(permission);
                            if (appList == null) {
                                appList = new ArrayList<>();
                                permissionGroupMap.put(permission, appList);
                            }
                            appList.add(packageInfo);

                            PermissionAnalyzer.RiskLevel level = analyzer.getPermissionRisk(permission);
                            if (level == PermissionAnalyzer.RiskLevel.HIGH) hasHigh = true;
                            if (level == PermissionAnalyzer.RiskLevel.MEDIUM) hasMedium = true;
                        }
                    }

                    if (hasHigh) risk = "High";
                    else if (hasMedium) risk = "Medium";
                }

                allAppsList.add(new AppModel(appName, packageName, appIcon, risk));

            } catch (Exception e) {
                Log.e(TAG, "Failed to process package: " + (packageInfo != null ? packageInfo.packageName : "NULL"), e);
            }
        }
        Log.d(TAG, "Scan complete. Found " + totalAppCount + " user apps.");
    }

    private void processGridCategories() {
        categoryList.clear();

        categoryList.add(new PermissionCategory("Camera", "android.permission.CAMERA", R.drawable.ic_camera));
        categoryList.add(new PermissionCategory("Location", "android.permission.ACCESS_FINE_LOCATION", R.drawable.ic_location));
        categoryList.add(new PermissionCategory("Contacts", "android.permission.READ_CONTACTS", R.drawable.ic_contacts));
        categoryList.add(new PermissionCategory("Microphone", "android.permission.RECORD_AUDIO", R.drawable.ic_microphone));
        categoryList.add(new PermissionCategory("Call Logs", "android.permission.READ_CALL_LOG", R.drawable.ic_call));
        categoryList.add(new PermissionCategory("Files", "android.permission.READ_EXTERNAL_STORAGE", R.drawable.ic_folder));

        for (PermissionCategory category : categoryList) {
            List<PackageInfo> apps = permissionGroupMap.get(category.getPermissionConstant());
            category.setAppCount(apps != null ? apps.size() : 0);
            category.setTotalAppCount(totalAppCount);
        }
    }

    // --- METHODS FOR NAVIGATION & PERMISSIONS ---

    private void onReviewUnusedClicked() {
        if (unusedAppPackages != null && !unusedAppPackages.isEmpty()) {
            navigateToAppList(new ArrayList<>(unusedAppPackages), PermissionAnalyzer.RiskLevel.LOW);
        } else {
            Toast.makeText(getContext(), "No unused apps to review.", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToAppList(ArrayList<String> appPackageNames, PermissionAnalyzer.RiskLevel riskLevel) {
        if (getContext() == null || !isAdded()) return;

        Log.d(TAG, "Navigating to AppListFragment for risk " + riskLevel + " with " + appPackageNames.size() + " apps.");

        AppListFragment appListFragment = new AppListFragment();
        Bundle args = new Bundle();

        args.putSerializable("RISK_LEVEL", riskLevel);
        args.putStringArrayList("APP_PACKAGES", appPackageNames);

        appListFragment.setArguments(args);

        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, appListFragment)
                .addToBackStack(null)
                .commit();
    }

    private void findUnusedApps() {
        if (getContext() == null) return;

        if (!hasUsageStatsPermission()) {
            Log.w(TAG, "Usage stats permission not granted. Cannot find unused apps.");

            mainThreadHandler.post(() -> {
                if (getContext() == null || !isAdded()) return;
                if (tvUnusedSubtitle != null) {
                    tvUnusedSubtitle.setText("Grant Usage Access to find unused apps.");
                    btnReviewUnused.setText("Grant Permission");
                    btnReviewUnused.setEnabled(true);
                    btnReviewUnused.setOnClickListener(v -> requestUsageStatsPermission());
                }
            });
            return;
        }

        UsageStatsManager usm = (UsageStatsManager) getContext().getSystemService(Context.USAGE_STATS_SERVICE);
        Calendar calendar = Calendar.getInstance();
        long endTime = calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_YEAR, -30);
        long startTime = calendar.getTimeInMillis();

        List<UsageStats> usageStatsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

        HashMap<String, Long> appUsageMap = new HashMap<>();
        if (usageStatsList != null) {
            for (UsageStats stats : usageStatsList) {
                long lastTimeUsed = stats.getLastTimeUsed();
                String packageName = stats.getPackageName();
                if (lastTimeUsed > appUsageMap.getOrDefault(packageName, 0L)) {
                    appUsageMap.put(packageName, lastTimeUsed);
                }
            }
        }

        long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
        unusedAppPackages.clear();

        for (AppModel app : allAppsList) {
            String pkgName = app.getPackageName();
            Long lastTimeUsed = appUsageMap.get(pkgName);

            if (lastTimeUsed == null || lastTimeUsed == 0) {
                unusedAppPackages.add(pkgName);
            } else if (lastTimeUsed < thirtyDaysAgo) {
                unusedAppPackages.add(pkgName);
            }
        }
        Log.d(TAG, "Found " + unusedAppPackages.size() + " unused apps.");
    }

    private boolean hasUsageStatsPermission() {
        if (getContext() == null) return false;
        AppOpsManager appOps = (AppOpsManager) getContext().getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) { Log.e(TAG, "AppOpsManager is null."); return false; }
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getContext().getPackageName());
        return (mode == AppOpsManager.MODE_ALLOWED);
    }

    private void requestUsageStatsPermission() {
        if (getContext() == null) return;
        Toast.makeText(getContext(), "GuardianAI needs Usage Access to find unused apps. Please enable it in the next screen.", Toast.LENGTH_LONG).show();
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open Usage Access Settings activity", e);
        }
    }

    private void openAppSettings(String packageName) {
        if (getContext() == null || !isAdded()) return;

        Log.d(TAG, "Attempting to open settings for: " + packageName);
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", packageName, null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open app settings activity for " + packageName, e);
            Toast.makeText(getContext(), "Could not open settings activity.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            Log.d(TAG, "ExecutorService shut down.");
        }
    }
}
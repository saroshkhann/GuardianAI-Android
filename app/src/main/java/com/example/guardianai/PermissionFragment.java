package com.example.guardianai;

import androidx.recyclerview.widget.GridLayoutManager; // IMPORTANT
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.fragment.app.Fragment;
// Add this line with your other imports
import com.example.guardianai.adapters.AppRiskAdapter;

public class PermissionFragment extends Fragment {

    // --- UI Components ---
    private RecyclerView gridRecyclerView;
    private RecyclerView appListRecyclerView;
    private PermissionGridAdapter gridAdapter;
    private AppRiskAdapter appListAdapter; // Reuse your existing adapter from Dashboard

    // --- Data ---
    // You'll get this from your scan
    private List<AppRiskModel> allAppsList = new ArrayList<>();
    private List<PermissionCategory> categoryList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_permission, container, false);

        // 1. Find BOTH RecyclerViews
        gridRecyclerView = view.findViewById(R.id.recycler_view_permission_grid);
        appListRecyclerView = view.findViewById(R.id.recycler_view_app_list);

        // 2. Setup the Grid RecyclerView
        setupGrid();

        // 3. Setup the App List RecyclerView
        setupAppList();

        // 4. Load ALL data (ideally from a ViewModel)
        loadData();

        return view;
    }

    private void setupGrid() {
        // Use a GridLayoutManager with 3 columns
        gridRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        gridAdapter = new PermissionGridAdapter(new ArrayList<>());
        gridRecyclerView.setAdapter(gridAdapter);
        // Clicks on the grid will navigate to AppListFragment, filtered by that permission
    }

    private void setupAppList() {
        // Use your existing adapter (I'm guessing the name)
        appListRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        appListAdapter = new AppRiskAdapter(new ArrayList<>()); // Use your adapter
        appListRecyclerView.setAdapter(appListAdapter);

        // **CRITICAL** for scrolling inside a NestedScrollView
        appListRecyclerView.setNestedScrollingEnabled(false);
        // Clicks on this list will navigate to App Info settings
    }

    private void loadData() {
        // --- This is the main part ---
        // 1. Run your FULL app scan (the one from DashboardFragment)
        //    This scan should produce:
        //    - The list of all apps with their risk (High, Medium, Low)
        //    - The permission map (Map<String, List<App>>)
        //    - The unused app list

        // 2. Populate the App List
        // allAppsList = ... (from scan)
        // appListAdapter.updateData(allAppsList);

        // 3. Populate the Permission Grid
        // categoryList = ... (process the permission map)
        // You'll need to add "Call Logs" and "Files and Media"
        // e.g., categoryList.add(new PermissionCategory("Call Logs", "android.permission.READ_CALL_LOG", R.drawable.ic_call));
        // gridAdapter.updateData(categoryList);

        // 4. Populate the Top Banner
        // int unusedAppCount = ... (from scan)
        // tvUnusedSubtitle.setText(unusedAppCount + " apps can be safely revoked.");
    }
}
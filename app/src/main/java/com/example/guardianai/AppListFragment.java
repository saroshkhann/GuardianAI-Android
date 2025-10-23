package com.example.guardianai;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager; // Import LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class AppListFragment extends Fragment {

    private RecyclerView recyclerView;
    private AppListAdapter adapter;
    private TextView titleTextView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_app_list, container, false);

        recyclerView = view.findViewById(R.id.app_recycler_view);
        titleTextView = view.findViewById(R.id.list_title);

        // Set the layout manager for the RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Get data passed from DashboardFragment
        Bundle args = getArguments();
        if (args != null) {
            PermissionAnalyzer.RiskLevel riskLevel = (PermissionAnalyzer.RiskLevel) args.getSerializable("RISK_LEVEL");
            ArrayList<String> appPackages = args.getStringArrayList("APP_PACKAGES");

            if (riskLevel != null && appPackages != null) {
                Log.d("AppListFragment", "Received data for " + riskLevel + " with " + appPackages.size() + " apps.");
                // Set title based on risk level
                titleTextView.setText(riskLevel.name() + " Risk Apps (" + appPackages.size() + ")");

                // Setup RecyclerView Adapter
                adapter = new AppListAdapter(getContext(), appPackages);
                recyclerView.setAdapter(adapter);
            } else {
                Log.e("AppListFragment", "Failed to retrieve data from arguments.");
                titleTextView.setText("Error Loading Apps");
            }
        } else {
            Log.e("AppListFragment", "Arguments bundle is null.");
            titleTextView.setText("Error Loading Apps");
        }

        return view;
    }
}
package com.example.guardianai;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors; // For Java 8 stream operations, if used

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

    private static final String TAG = "AppListAdapter"; // Tag for logging

    private List<String> packageNames;
    private Context context;
    private PackageManager packageManager;
    private PermissionAnalyzer analyzer; // To check permission risk
    private PermissionAnalyzer.RiskLevel filterRiskLevel; // Which risk level are we showing?

    // Keep track of selected items using their package names
    private Set<String> selectedPackageNames = new HashSet<>();
    private OnSelectionChangedListener selectionListener; // Interface for callbacks

    // Interface for notifying the Fragment about selection changes
    public interface OnSelectionChangedListener {
        void onSelectionChanged(int count); // Pass the current number of selected items
    }


    // Constructor updated to receive risk level and listener
    public AppListAdapter(Context context, List<String> packageNames, PermissionAnalyzer.RiskLevel riskLevel, OnSelectionChangedListener listener) {
        this.context = context;
        // Ensure packageNames list is never null
        this.packageNames = packageNames != null ? packageNames : new ArrayList<>();
        this.filterRiskLevel = riskLevel;
        this.selectionListener = listener;
        if (context != null) {
            this.packageManager = context.getPackageManager();
            this.analyzer = new PermissionAnalyzer(); // Initialize analyzer
        } else {
            Log.e(TAG, "Context is null in constructor!");
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the layout for each row item
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_app_detail, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Safety check
        if (packageManager == null || analyzer == null || packageNames == null || position >= packageNames.size()) {
            Log.e(TAG, "Adapter component is null or position is out of bounds in onBindViewHolder.");
            return;
        }

        String packageName = packageNames.get(position);
        // Check if this item is currently in our selected set
        boolean isSelected = selectedPackageNames.contains(packageName);

        try {
            // Get application info (for name and icon)
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            // Get package info (for permissions)
            PackageInfo pkgInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);

            // Set app name and icon
            holder.appName.setText(appInfo.loadLabel(packageManager));
            Drawable icon = appInfo.loadIcon(packageManager);
            holder.appIcon.setImageDrawable(icon);

            // Set the checkbox state
            holder.checkBox.setChecked(isSelected);

            // --- Show Relevant Permissions ---
            List<String> riskyPermissions = getRelevantPermissions(pkgInfo.requestedPermissions);
            if (!riskyPermissions.isEmpty()) {
                // Join the permission names with commas and set the text
                holder.permissionsText.setText("Permissions: " + String.join(", ", riskyPermissions));
                holder.permissionsText.setVisibility(View.VISIBLE); // Show the TextView
            } else {
                holder.permissionsText.setVisibility(View.GONE); // Hide if no relevant permissions
            }
            // --- End Show Permissions ---


            // --- Handle Clicks for Selection ---
            // Set the click listener on the *entire row* (itemView)
            holder.itemView.setOnClickListener(v -> {
                // Figure out the NEW selection state (toggle the current state)
                boolean nowSelected = !holder.checkBox.isChecked(); // Read current checkbox state and invert it

                // Update the internal selection state first
                if (nowSelected) {
                    selectedPackageNames.add(packageName);
                    Log.d(TAG, "Selected: " + packageName);
                } else {
                    selectedPackageNames.remove(packageName);
                    Log.d(TAG, "Deselected: " + packageName);
                }

                // Update the checkbox UI *after* updating the internal state
                // This prevents issues if notifyDataSetChanged is called elsewhere rapidly
                holder.checkBox.setChecked(nowSelected);


                // Notify the fragment (AppListFragment) that the selection count has changed
                if (selectionListener != null) {
                    selectionListener.onSelectionChanged(selectedPackageNames.size());
                }
            });

            // Prevent checkbox itself from consuming clicks, let itemView handle it
            holder.checkBox.setClickable(false);


        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "App info or package info not found for: " + packageName, e);
            // Fallback display if app info cannot be retrieved
            holder.appName.setText(packageName); // Show package name as fallback
            holder.appIcon.setImageResource(R.mipmap.ic_launcher); // Default icon
            holder.permissionsText.setVisibility(View.GONE);
            holder.checkBox.setChecked(false); // Ensure checkbox is unchecked
            holder.itemView.setOnClickListener(null); // Disable click if info is missing
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error binding view for: " + packageName, e);
            // Handle other potential errors gracefully
            holder.appName.setText("Error loading app");
            holder.appIcon.setImageResource(R.mipmap.ic_launcher);
            holder.permissionsText.setVisibility(View.GONE);
            holder.checkBox.setChecked(false);
            holder.itemView.setOnClickListener(null);
        }
    }

    // Helper to get only High/Medium risk permissions for display
    private List<String> getRelevantPermissions(String[] allPermissions) {
        List<String> relevant = new ArrayList<>();
        if (allPermissions == null || analyzer == null) return relevant;

        for (String perm : allPermissions) {
            PermissionAnalyzer.RiskLevel risk = analyzer.getPermissionRisk(perm);
            // --- Only add HIGH or MEDIUM risk permissions to the display list ---
            if (risk == PermissionAnalyzer.RiskLevel.HIGH || risk == PermissionAnalyzer.RiskLevel.MEDIUM) {
                relevant.add(formatPermissionName(perm)); // Add the formatted (short) name
            }
        }

        // Limit the number displayed if the list is too long (optional enhancement)
        final int MAX_PERMISSIONS_TO_SHOW = 2; // Show max 2 permissions + "..."
        if (relevant.size() > MAX_PERMISSIONS_TO_SHOW) {
            List<String> limitedList = relevant.subList(0, MAX_PERMISSIONS_TO_SHOW);
            limitedList.add("..."); // Indicate there are more permissions not shown
            return limitedList;
        }
        return relevant; // Return the full list if it's short enough
    }

    // Helper to make permission names shorter (e.g., "CAMERA" instead of "android.permission.CAMERA")
    private String formatPermissionName(String fullPermissionName) {
        if (fullPermissionName == null) return "";
        int lastDot = fullPermissionName.lastIndexOf('.');
        // Check if the dot exists and is not the last character
        if (lastDot >= 0 && lastDot < fullPermissionName.length() - 1) {
            // Return the part after the last dot
            return fullPermissionName.substring(lastDot + 1);
        }
        return fullPermissionName; // Return full name if format is unexpected
    }


    @Override
    public int getItemCount() {
        // Return the size of the package names list
        return packageNames.size();
    }

    // Method for Fragment to get the list of selected package names
    public List<String> getSelectedPackageNames() {
        return new ArrayList<>(selectedPackageNames); // Return a copy
    }

    // --- Methods for Select All / Deselect All ---
    public void selectAll() {
        if (packageNames == null) return;
        selectedPackageNames.clear();
        selectedPackageNames.addAll(packageNames); // Add all package names to the set
        notifyDataSetChanged(); // Redraw the entire list to update checkboxes
        // Notify the fragment about the change in selection count
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selectedPackageNames.size());
        }
        Log.d(TAG, "Selected all " + selectedPackageNames.size() + " items.");
    }

    public void deselectAll() {
        selectedPackageNames.clear(); // Clear the selection set
        notifyDataSetChanged(); // Redraw the entire list
        // Notify the fragment
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(0);
        }
        Log.d(TAG, "Deselected all items.");
    }

    // Method to clear selection (useful after review is complete)
    public void clearSelection() {
        deselectAll(); // Reuse deselectAll logic
    }

    // --- ViewHolder Class ---
    // Holds references to the views within each list item layout
    public static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        ImageView appIcon;
        TextView appName;
        TextView permissionsText; // TextView for displaying permissions
        ImageView infoIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Find the views by their IDs in the list_item_app_detail.xml layout
            checkBox = itemView.findViewById(R.id.app_checkbox);
            appIcon = itemView.findViewById(R.id.app_icon);
            appName = itemView.findViewById(R.id.app_name);
            permissionsText = itemView.findViewById(R.id.app_permissions); // Initialize permissions TextView
            infoIcon = itemView.findViewById(R.id.info_icon);
        }
    }
} // End AppListAdapter class
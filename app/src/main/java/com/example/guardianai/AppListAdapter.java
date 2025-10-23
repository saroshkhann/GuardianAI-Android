package com.example.guardianai;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable; // Import Drawable
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast; // For clicks
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

    private List<String> packageNames;
    private Context context;
    private PackageManager packageManager;

    public AppListAdapter(Context context, List<String> packageNames) {
        this.context = context;
        this.packageNames = packageNames;
        if (context != null) {
            this.packageManager = context.getPackageManager();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_app_detail, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (packageManager == null) return; // Safety check

        String packageName = packageNames.get(position);
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            holder.appName.setText(appInfo.loadLabel(packageManager));

            // Load icon safely
            Drawable icon = appInfo.loadIcon(packageManager);
            holder.appIcon.setImageDrawable(icon);

            // Add click listener to the whole item
            holder.itemView.setOnClickListener(v -> {
                // TODO: Implement action when an app is clicked (e.g., show details or open settings)
                Toast.makeText(context, "Clicked: " + holder.appName.getText(), Toast.LENGTH_SHORT).show();
            });

        } catch (PackageManager.NameNotFoundException e) {
            Log.e("AppListAdapter", "App not found: " + packageName, e);
            holder.appName.setText(packageName); // Fallback to package name
            holder.appIcon.setImageResource(R.mipmap.ic_launcher); // Fallback icon
        }
    }

    @Override
    public int getItemCount() {
        return packageNames == null ? 0 : packageNames.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName;
        ImageView infoIcon; // Add info icon if needed

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.app_icon);
            appName = itemView.findViewById(R.id.app_name);
            infoIcon = itemView.findViewById(R.id.info_icon); // Initialize info icon
        }
    }
}
package com.example.guardianai;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class AppRiskAdapter extends RecyclerView.Adapter<AppRiskAdapter.AppViewHolder> {

    private Context context;
    private List<AppModel> appList;

    // --- 1. Click Listener Interface ---
    public interface OnAppClickListener {
        void onAppClick(AppModel app);
    }

    private OnAppClickListener clickListener;

    public void setOnAppClickListener(OnAppClickListener listener) {
        this.clickListener = listener;
    }
    // --- End Click Listener ---

    public AppRiskAdapter(Context context, List<AppModel> appList) {
        this.context = context;
        this.appList = appList;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // This links to your "item_app_list.xml" layout
        View view = LayoutInflater.from(context).inflate(R.layout.item_app_list, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppModel app = appList.get(position);

        holder.appName.setText(app.getAppName());
        holder.appIcon.setImageDrawable(app.getAppIcon());
        holder.appRisk.setText(app.getRiskLevel() + " Risk");

        // Set color based on risk
        switch (app.getRiskLevel()) {
            case "High":
                holder.appRisk.setTextColor(Color.parseColor("#D9534F")); // Red
                break;
            case "Medium":
                holder.appRisk.setTextColor(Color.parseColor("#F0AD4E")); // Orange
                break;
            case "Low":
                holder.appRisk.setTextColor(Color.parseColor("#5CB85C")); // Green
                break;
            default:
                holder.appRisk.setTextColor(Color.GRAY);
                break;
        }

        // --- 2. Set the click listener on the item view ---
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onAppClick(app);
            }
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    // Helper method to update the data
    public void updateData(List<AppModel> newAppList) {
        this.appList = newAppList;
        notifyDataSetChanged();
    }

    // --- ViewHolder Class ---
    public static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName;
        TextView appRisk; // The "High Risk" text

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            // These IDs must match your "item_app_list.xml" file
            appIcon = itemView.findViewById(R.id.iv_app_icon);
            appName = itemView.findViewById(R.id.tv_app_name);
            appRisk = itemView.findViewById(R.id.tv_app_risk);
        }
    }
}
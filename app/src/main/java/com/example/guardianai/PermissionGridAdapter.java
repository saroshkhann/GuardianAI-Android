package com.example.guardianai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class PermissionGridAdapter extends RecyclerView.Adapter<PermissionGridAdapter.GridViewHolder> {

    private List<PermissionCategory> categoryList;

    // --- 1. Click Listener Interface ---
    public interface OnCategoryClickListener {
        void onCategoryClick(PermissionCategory category);
    }

    private OnCategoryClickListener clickListener;

    public void setOnCategoryClickListener(OnCategoryClickListener listener) {
        this.clickListener = listener;
    }
    // --- End Click Listener ---


    public PermissionGridAdapter(List<PermissionCategory> categoryList) {
        this.categoryList = categoryList;
    }

    @NonNull
    @Override
    public GridViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.grid_item_permission, parent, false);
        return new GridViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GridViewHolder holder, int position) {
        PermissionCategory category = categoryList.get(position);

        holder.permissionIcon.setImageResource(category.getIconResId());
        holder.permissionName.setText(category.getName());

        String countText = category.getAppCount() + "/" + category.getTotalAppCount();
        holder.permissionAppCount.setText(countText);

        // --- 2. Set the click listener on the item view ---
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onCategoryClick(category);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    public void updateData(List<PermissionCategory> newList) {
        this.categoryList = newList;
        notifyDataSetChanged();
    }

    public static class GridViewHolder extends RecyclerView.ViewHolder {
        ImageView permissionIcon;
        TextView permissionName;
        TextView permissionAppCount;

        public GridViewHolder(@NonNull View itemView) {
            super(itemView);
            permissionIcon = itemView.findViewById(R.id.permission_icon);
            permissionName = itemView.findViewById(R.id.permission_name);
            permissionAppCount = itemView.findViewById(R.id.permission_app_count);
        }
    }
}
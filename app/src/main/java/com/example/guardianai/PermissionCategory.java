package com.example.guardianai;

public class PermissionCategory {
    String name;
    String permissionConstant; // e.g., "android.permission.CAMERA"
    int iconResId; // e.g., R.drawable.ic_camera
    int appCount;
    int totalAppCount;

    // --- Constructor ---
    public PermissionCategory(String name, String permissionConstant, int iconResId) {
        this.name = name;
        this.permissionConstant = permissionConstant;
        this.iconResId = iconResId;
        this.appCount = 0; // Default to 0
        this.totalAppCount = 0; // Default to 0
    }

    // --- Getters ---
    public String getName() {
        return name;
    }

    public String getPermissionConstant() {
        return permissionConstant;
    }

    public int getIconResId() {
        return iconResId;
    }

    public int getAppCount() {
        return appCount;
    }

    public int getTotalAppCount() {
        return totalAppCount;
    }

    // --- Setters ---
    public void setAppCount(int appCount) {
        this.appCount = appCount;
    }

    public void setTotalAppCount(int totalAppCount) {
        this.totalAppCount = appCount;
    }
}
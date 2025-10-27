package com.example.guardianai;

import android.graphics.drawable.Drawable;

public class AppModel {
    private String appName;
    private String packageName;
    private Drawable appIcon;
    private String riskLevel; // "High", "Medium", "Low"

    // Constructor
    public AppModel(String appName, String packageName, Drawable appIcon, String riskLevel) {
        this.appName = appName;
        this.packageName = packageName;
        this.appIcon = appIcon;
        this.riskLevel = riskLevel;
    }

    // Getters
    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public Drawable getAppIcon() {
        return appIcon;
    }

    public String getRiskLevel() {
        return riskLevel;
    }
}
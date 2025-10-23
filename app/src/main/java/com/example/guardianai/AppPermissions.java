package com.example.guardianai.db; // Create a 'db' sub-package if you like

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "app_permissions")
public class AppPermissions {

    @PrimaryKey
    @NonNull
    public String packageName; // Unique identifier for the app

    // Store permissions as a single comma-separated string
    public String permissionsList;

    public AppPermissions(@NonNull String packageName, String permissionsList) {
        this.packageName = packageName;
        this.permissionsList = permissionsList;
    }
}
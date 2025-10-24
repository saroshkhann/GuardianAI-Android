package com.example.guardianai;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "recommendations")
public class Recommendation {

    @PrimaryKey(autoGenerate = true)
    public int id; // Unique ID for each recommendation

    @NonNull
    public String title; // e.g., "Unused Permissions"

    @NonNull
    public String description; // The main text, e.g., "Review unused permissions for 'AppName' (pkg.name)"

    @NonNull
    public String type; // Category, e.g., "UNUSED_APP", "HIGH_RISK_SUMMARY", "MEDIUM_RISK_SUMMARY"

    public String associatedPackageName; // Store package name if applicable (for unused apps)

    public long timestamp; // When the recommendation was generated

    // Constructor
    public Recommendation(@NonNull String title, @NonNull String description, @NonNull String type, String associatedPackageName) {
        this.title = title;
        this.description = description;
        this.type = type;
        this.associatedPackageName = associatedPackageName;
        this.timestamp = System.currentTimeMillis(); // Set timestamp on creation
    }
}
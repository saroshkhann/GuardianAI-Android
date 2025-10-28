package com.example.guardianai;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sensor_logs")
public class SensorLogEntry {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "timestamp")
    public long timestamp; // When the event occurred (System.currentTimeMillis())

    @ColumnInfo(name = "package_name")
    public String packageName; // The app that accessed the sensor

    @ColumnInfo(name = "app_name")
    public String appName; // User-friendly name of the app

    @ColumnInfo(name = "sensor_type")
    public String sensorType; // E.g., "CAMERA", "MICROPHONE", "LOCATION", "CLIPBOARD"

    @ColumnInfo(name = "is_alert")
    public boolean isAlert; // True if this event triggered a user alert (e.g., background mic use)

    // --- Constructor ---
    public SensorLogEntry(long timestamp, String packageName, String appName, String sensorType, boolean isAlert) {
        this.timestamp = timestamp;
        this.packageName = packageName;
        this.appName = appName;
        this.sensorType = sensorType;
        this.isAlert = isAlert;
    }

    // Room automatically generates setters and getters, but we'll add them for good practice
    // (You can omit these if you use public fields, but using the full class structure is safer)

    public int getId() { return id; }
    public long getTimestamp() { return timestamp; }
    public String getPackageName() { return packageName; }
    public String getAppName() { return appName; }
    public String getSensorType() { return sensorType; }
    public boolean isAlert() { return isAlert; }
}
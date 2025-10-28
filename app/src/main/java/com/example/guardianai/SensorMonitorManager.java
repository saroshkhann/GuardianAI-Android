package com.example.guardianai;

import android.content.Context;
import android.content.SharedPreferences;

public class SensorMonitorManager {

    private static final String PREFS_NAME = "SensorPrefs";
    private static final String KEY_MIC = "isMicMonitoringEnabled";
    private static final String KEY_CAMERA = "isCameraMonitoringEnabled";
    private static final String KEY_LOCATION = "isLocationMonitoringEnabled";
    private static final String KEY_CLIPBOARD = "isClipboardMonitoringEnabled";

    private SharedPreferences sharedPrefs;

    public SensorMonitorManager(Context context) {
        sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- MIC MONITORING ---
    public void setMicMonitoring(boolean isEnabled) {
        sharedPrefs.edit().putBoolean(KEY_MIC, isEnabled).apply();
    }
    public boolean isMicMonitoringEnabled() {
        return sharedPrefs.getBoolean(KEY_MIC, true);
    }

    // --- CAMERA MONITORING ---
    public void setCameraMonitoring(boolean isEnabled) {
        sharedPrefs.edit().putBoolean(KEY_CAMERA, isEnabled).apply();
    }
    public boolean isCameraMonitoringEnabled() {
        return sharedPrefs.getBoolean(KEY_CAMERA, true);
    }

    // --- LOCATION MONITORING ---
    public void setLocationMonitoring(boolean isEnabled) {
        sharedPrefs.edit().putBoolean(KEY_LOCATION, isEnabled).apply();
    }
    public boolean isLocationMonitoringEnabled() {
        return sharedPrefs.getBoolean(KEY_LOCATION, true);
    }

    // --- CLIPBOARD MONITORING ---
    public void setClipboardMonitoring(boolean isEnabled) {
        sharedPrefs.edit().putBoolean(KEY_CLIPBOARD, isEnabled).apply();
    }
    public boolean isClipboardMonitoringEnabled() {
        return sharedPrefs.getBoolean(KEY_CLIPBOARD, true);
    }
}
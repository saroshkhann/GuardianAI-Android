package com.example.guardianai;


public class PermissionAnalyzer {

    // This enum defines our risk levels
    public enum RiskLevel implements java.io.Serializable{
        HIGH,
        MEDIUM,
        LOW,
        NO_RISK
    }

    // This is the "engine." It takes a permission and returns its risk level.
    public RiskLevel getPermissionRisk(String permission) {
        if (permission == null) {
            return RiskLevel.NO_RISK;
        }

        switch (permission) {
            // --- HIGH RISK PERMISSIONS ---
            case "android.permission.READ_CONTACTS":
            case "android.permission.WRITE_CONTACTS":
            case "android.permission.READ_SMS":
            case "android.permission.SEND_SMS":
            case "android.permission.RECEIVE_SMS":
            case "android.permission.CAMERA":
            case "android.permission.RECORD_AUDIO":
            case "android.permission.READ_CALENDAR":
            case "android.permission.WRITE_CALENDAR":
            case "android.permission.BIND_ACCESSIBILITY_SERVICE":
            case "android.permission.READ_CALL_LOG":
            case "android.permission.PROCESS_OUTGOING_CALLS":
            case "android.permission.REQUEST_INSTALL_PACKAGES":
                return RiskLevel.HIGH;

            // --- MEDIUM RISK PERMISSIONS ---
            case "android.permission.ACCESS_FINE_LOCATION":
            case "android.permission.ACCESS_COARSE_LOCATION":
            case "android.permission.READ_EXTERNAL_STORAGE":
            case "android.permission.WRITE_EXTERNAL_STORAGE":
            case "android.permission.GET_ACCOUNTS":
            case "android.permission.READ_PHONE_STATE":
                return RiskLevel.MEDIUM;

            // --- LOW RISK PERMISSIONS ---
            case "android.permission.INTERNET":
            case "android.permission.ACCESS_NETWORK_STATE":
            case "android.permission.BLUETOOTH":
            case "android.permission.VIBRATE":
            case "android.permission.WAKE_LOCK":
                return RiskLevel.LOW;

            // --- NO RISK (Considered safe) ---
            default:
                return RiskLevel.NO_RISK;
        }
    }
}
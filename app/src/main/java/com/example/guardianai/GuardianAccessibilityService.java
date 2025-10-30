package com.example.guardianai;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.util.Log;

public class GuardianAccessibilityService extends AccessibilityService {

    public static String currentForegroundApp = "Unknown";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null) {
                currentForegroundApp = pkg.toString();
                Log.d("GuardianAI", "Foreground app: " + currentForegroundApp);
            }
        }
    }

    @Override
    public void onInterrupt() { }
}

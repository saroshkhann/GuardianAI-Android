package com.example.guardianai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";
    public static final String PREFS_NAME = "GuardianAIPrefs"; // Consistent prefs name
    public static final String KEY_UNUSED_THRESHOLD_DAYS = "unused_app_threshold_days";
    public static final int DEFAULT_UNUSED_THRESHOLD_DAYS = 30; // Default value

    private RadioGroup radioGroupThreshold;
    private RadioButton radio30Days, radio60Days, radio90Days;
    private SharedPreferences sharedPreferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // Initialize views
        radioGroupThreshold = view.findViewById(R.id.radio_group_unused_threshold);
        radio30Days = view.findViewById(R.id.radio_30_days);
        radio60Days = view.findViewById(R.id.radio_60_days);
        radio90Days = view.findViewById(R.id.radio_90_days);

        // Get SharedPreferences instance
        sharedPreferences = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Load the currently saved setting and check the corresponding radio button
        loadCurrentSetting();

        // Set listener to save the new setting when a radio button is selected
        radioGroupThreshold.setOnCheckedChangeListener((group, checkedId) -> {
            int selectedDays = DEFAULT_UNUSED_THRESHOLD_DAYS; // Default
            if (checkedId == R.id.radio_30_days) {
                selectedDays = 30;
            } else if (checkedId == R.id.radio_60_days) {
                selectedDays = 60;
            } else if (checkedId == R.id.radio_90_days) {
                selectedDays = 90;
            }
            saveSetting(selectedDays);
        });

        return view;
    }

    private void loadCurrentSetting() {
        int currentThresholdDays = sharedPreferences.getInt(KEY_UNUSED_THRESHOLD_DAYS, DEFAULT_UNUSED_THRESHOLD_DAYS);
        Log.d(TAG, "Loading threshold setting: " + currentThresholdDays + " days");

        if (currentThresholdDays == 60) {
            radio60Days.setChecked(true);
        } else if (currentThresholdDays == 90) {
            radio90Days.setChecked(true);
        } else { // Default to 30 if value is invalid or 30
            radio30Days.setChecked(true);
        }
    }

    private void saveSetting(int days) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_UNUSED_THRESHOLD_DAYS, days);
        editor.apply(); // Save asynchronously
        Log.d(TAG, "Saved threshold setting: " + days + " days");
        Toast.makeText(getContext(), "Unused threshold set to " + days + " days.", Toast.LENGTH_SHORT).show();
        // Optional: Immediately trigger or reschedule the worker if needed,
        // though the periodic worker will pick up the new value on its next run.
    }
}
package com.example.guardianai;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class OnboardingAdapter extends FragmentStateAdapter {

    public OnboardingAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        // Return a NEW fragment instance for the given position.
        switch (position) {
            case 0:
                return new OnboardingPurposeFragment();
            case 1:
                return new OnboardingRiskFragment();
            // Add more cases for more screens
            case 2: // New page
                return new OnboardingFeaturesFragment();
            case 3: // Your new page
                return new OnboardingTipsFragment();
            default:
                return new OnboardingPurposeFragment();
        }
    }

    @Override
    public int getItemCount() {
        // The number of pages
        return 4;
    }
}
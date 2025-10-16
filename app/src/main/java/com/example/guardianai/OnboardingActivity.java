package com.example.guardianai;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;
import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private OnboardingAdapter adapter;
    private LinearLayout layoutDots;
    private Button nextButton;
    private TextView backButton;

    private List<ImageView> dots;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        viewPager = findViewById(R.id.viewPager);
        layoutDots = findViewById(R.id.layout_dots);
        nextButton = findViewById(R.id.button_next);
        backButton = findViewById(R.id.button_back);

        adapter = new OnboardingAdapter(this);
        viewPager.setAdapter(adapter);

        setupDots();
        updateDots(0);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateDots(position);
                updateNavigationButtons(position);
            }
        });

        nextButton.setOnClickListener(v -> {
            int currentItem = viewPager.getCurrentItem();
            if (currentItem < adapter.getItemCount() - 1) {
                viewPager.setCurrentItem(currentItem + 1);
            } else {
                // Last page: navigate to main app or finish
                Toast.makeText(this, "Onboarding Complete!", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        backButton.setOnClickListener(v -> {
            int currentItem = viewPager.getCurrentItem();
            if (currentItem > 0) {
                viewPager.setCurrentItem(currentItem - 1);
            }
        });
    }

    private void setupDots() {
        dots = new ArrayList<>();
        layoutDots.removeAllViews();
        for (int i = 0; i < adapter.getItemCount(); i++) {
            ImageView dot = new ImageView(this);
            // Add margins to the dot
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0);
            dot.setLayoutParams(params);

            dots.add(dot);
            layoutDots.addView(dot);
        }
    }

    private void updateDots(int currentPage) {
        for (int i = 0; i < dots.size(); i++) {
            if (i == currentPage) {
                dots.get(i).setImageDrawable(ContextCompat.getDrawable(this, R.drawable.dot_active));
            } else {
                dots.get(i).setImageDrawable(ContextCompat.getDrawable(this, R.drawable.dot_inactive));
            }
        }
    }

    private void updateNavigationButtons(int position) {
        if (position > 0) {
            backButton.setVisibility(View.VISIBLE);
        } else {
            backButton.setVisibility(View.INVISIBLE);
        }

        if (position == adapter.getItemCount() - 1) {
            nextButton.setText("Finish");
        } else {
            nextButton.setText("Next");
        }
    }
}
package com.example.guardianai;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class OnboardingActivity extends AppCompatActivity {

    private Button nextButton;
    private TextView backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        nextButton = findViewById(R.id.button_next);
        backButton = findViewById(R.id.button_back);

        nextButton.setOnClickListener(v -> {
            // Later, this will go to the next onboarding screen
            Toast.makeText(this, "Next Clicked!", Toast.LENGTH_SHORT).show();
        });

        backButton.setOnClickListener(v -> {
            // This will go back to the WelcomeActivity
            finish(); // Closes the current activity
        });
    }
}
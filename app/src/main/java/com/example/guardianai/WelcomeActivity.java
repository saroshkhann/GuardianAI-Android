package com.example.guardianai;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class WelcomeActivity extends AppCompatActivity {

    private Button getStartedButton;
    private TextView skipButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // This links the Java file to the XML layout file
        setContentView(R.layout.activity_welcome);

        // Find the buttons from the layout by their ID
        getStartedButton = findViewById(R.id.button_get_started);
        skipButton = findViewById(R.id.button_skip);

        // Set a click listener for the "Get Started" button
        // ... inside onCreate method
        getStartedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start the OnboardingActivity
                Intent intent = new Intent(WelcomeActivity.this, OnboardingActivity.class);
                startActivity(intent);
            }
        });

        // Set a click listener for the "Skip" button
        skipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // This should skip the onboarding and go to the main dashboard
                // For now, it shows a temporary message.
                Toast.makeText(WelcomeActivity.this, "Skip Clicked!", Toast.LENGTH_SHORT).show();

                // Example: How to start your main activity
                // Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
                // startActivity(intent);
                // finish(); // Call finish() to prevent the user from coming back to this screen
            }
        });
    }
}
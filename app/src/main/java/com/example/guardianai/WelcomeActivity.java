package com.example.guardianai;

import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
// import android.widget.TextView; // No longer needed
// import android.widget.Toast; // No longer needed

public class WelcomeActivity extends AppCompatActivity {

    private Button getStartedButton;
    // private TextView skipButton; // <-- REMOVED

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // This links the Java file to the XML layout file
        setContentView(R.layout.activity_welcome);

        // Find the "Get Started" button
        getStartedButton = findViewById(R.id.button_get_started);

        // Set a click listener for the "Get Started" button
        getStartedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start the OnboardingActivity
                Intent intent = new Intent(WelcomeActivity.this, OnboardingActivity.class);
                startActivity(intent);
            }
        });

        // --- All code for the skipButton has been removed ---
    }
}
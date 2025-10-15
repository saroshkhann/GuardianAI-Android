package com.example.guardianai;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class SplashActivity extends AppCompatActivity {

    // Time in milliseconds that the splash screen will be displayed
    private static final int SPLASH_SCREEN_TIMEOUT = 2500; // 2.5 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // This Handler will run a task after a delay
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                // Create an Intent to start the WelcomeActivity
                Intent intent = new Intent(SplashActivity.this, WelcomeActivity.class);
                startActivity(intent);

                // Close this activity so the user can't go back to it
                finish();
            }
        }, SPLASH_SCREEN_TIMEOUT);
    }
}
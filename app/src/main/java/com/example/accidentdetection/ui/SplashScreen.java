package com.example.accidentdetection.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AlphaAnimation;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.accidentdetection.R;


public class SplashScreen extends AppCompatActivity {

    private static final int SPLASH_DURATION = 3000;
    private static final int STEP_DELAY = SPLASH_DURATION / 100;

    private ProgressBar progressBar;
    private TextView tvLoading;
    private int progress = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final String[] messages = {
            "Loading...",
            "Checking sensors...",
            "Preparing safety features...",
            "Almost ready..."
    };

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (progress <= 100) {
                progressBar.setProgress(progress);
                tvLoading.setText(messages[Math.min(progress / 26, messages.length - 1)]);
                progress++;
                handler.postDelayed(this, STEP_DELAY);
            } else {
                goToNextScreen();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash_screen);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        TextView tvAppName = findViewById(R.id.tvAppName);
        progressBar = findViewById(R.id.progressBar);
        tvLoading = findViewById(R.id.tvLoading);

        tvAppName.startAnimation(createFadeIn(1000));

        handler.post(progressRunnable);
    }

    private AlphaAnimation createFadeIn(int duration) {
        AlphaAnimation anim = new AlphaAnimation(0f, 1f);
        anim.setDuration(duration);
        anim.setStartOffset(500);
        anim.setFillAfter(true);
        return anim;
    }

    private void goToNextScreen() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean firstLaunch = prefs.getBoolean("first_launch", true);
        Intent intent;
        if(firstLaunch){
            intent = new Intent(this, LoginPage.class);
        }else{
            intent=new Intent(this, MainActivity.class);
        }
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(progressRunnable);
    }
}
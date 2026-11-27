package com.example.accidentdetection.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.accidentdetection.R;
import com.example.accidentdetection.utils.GpsFilter;
import com.example.accidentdetection.utils.SosHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;

public class AlertActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "type";        // SosHelper.TYPE_*
    public static final String EXTRA_SUMMARY = "summary";  // detector summary (optional)

    /** True while the alert is on screen. The service must not open a second one. */
    public static volatile boolean isShowing = false;

    private static final int MANUAL_COUNTDOWN_SECONDS = 10;
    private static final float RESUME_KMH = 15f;
    private static final int RESUME_FIXES = 3;              // 3 trusted fixes in a row

    private String type;
    private boolean finished = false;                       // cancelled, sent or tested
    private CountDownTimer timer;
    private MediaPlayer player;
    private Vibrator vibrator;
    private AudioManager audio;
    private int savedAlarmVolume = -1;
    private FusedLocationProviderClient fused;
    private LocationCallback locationCallback;
    private int fastFixes = 0;
    private OnBackPressedCallback backCallback;

    private View groupCountdown, groupSent;
    private TextView tvSeconds, tvCountdownText, tvSentTitle, tvSentInfo;
    private MaterialButton btnOk, btnSendNow, btnSafe, btnCall112;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showOverLockScreen();
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_alert);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });

        isShowing = true;
        type = getIntent().getStringExtra(EXTRA_TYPE);
        if (type == null) type = SosHelper.TYPE_AUTO;

        groupCountdown = findViewById(R.id.groupCountdown);
        groupSent = findViewById(R.id.groupSent);
        tvSeconds = findViewById(R.id.tvSeconds);
        tvCountdownText = findViewById(R.id.tvCountdownText);
        tvSentTitle = findViewById(R.id.tvSentTitle);
        tvSentInfo = findViewById(R.id.tvSentInfo);
        btnOk = findViewById(R.id.btnOk);
        btnSendNow = findViewById(R.id.btnSendNow);
        btnSafe = findViewById(R.id.btnSafe);
        btnCall112 = findViewById(R.id.btnCall112);
        TextView tvDetail = findViewById(R.id.tvDetail);
        TextView tvTestBanner = findViewById(R.id.tvTestBanner);

        boolean isTest = SosHelper.TYPE_TEST.equals(type);
        tvTestBanner.setVisibility(isTest ? View.VISIBLE : View.GONE);

        String summary = getIntent().getStringExtra(EXTRA_SUMMARY);
        tvDetail.setText(summary == null ? "" : summary);

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int seconds = SosHelper.TYPE_MANUAL.equals(type)
                ? MANUAL_COUNTDOWN_SECONDS
                : prefs.getInt(SettingsActivity.KEY_COUNTDOWN, 30);

        btnOk.setOnClickListener(v -> cancel("Cancelled by user"));
        btnSendNow.setOnClickListener(v -> triggerSend());
        btnCall112.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))));
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        btnSafe.setOnClickListener(v -> sendSafe());

        // Back must not dismiss a live alert (it could be pressed by accident)
        backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Toast.makeText(AlertActivity.this,
                        "Tap \"I'M OK\" to cancel the alert", Toast.LENGTH_SHORT).show();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);

        startAlarm();
        startCountdown(seconds);
        if (SosHelper.TYPE_AUTO.equals(type)) startMotionWatch();
    }

    // ------------------------------------------------------------------
    // Countdown and decisions
    // ------------------------------------------------------------------

    private void startCountdown(int seconds) {
        final long total = seconds * 1000L;
        timer = new CountDownTimer(total, 250) {
            @Override
            public void onTick(long left) {
                tvSeconds.setText(String.valueOf((int) Math.ceil(left / 1000.0)));
                float progress = 1f - (float) left / total;
                setAlarmVolume(0.3f + 0.7f * progress);          // gets louder every second
            }

            @Override
            public void onFinish() {
                tvSeconds.setText("0");
                triggerSend();
            }
        }.start();
    }

    private void triggerSend() {
        if (finished) return;
        if (SosHelper.TYPE_TEST.equals(type)) finishTest();
        else sendNow();
    }

    private void sendNow() {
        finished = true;
        stopEverything();
        tvSeconds.setText("...");
        tvCountdownText.setText("Sending your alert...");
        btnOk.setEnabled(false);
        btnSendNow.setEnabled(false);

        SosHelper.sendSos(this, type, reached -> {
            if (isFinishing() || isDestroyed()) return;
            showSent(reached);
        });
    }

    private void cancel(String status) {
        if (finished) return;
        finished = true;
        stopEverything();
        SosHelper.logHistory(this, type, status, "");
        Toast.makeText(this, "Alert cancelled. Stay safe!", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void finishTest() {
        finished = true;
        stopEverything();
        SosHelper.logHistory(this, type, "Test completed - no message", "");
        groupCountdown.setVisibility(View.GONE);
        groupSent.setVisibility(View.VISIBLE);
        tvSentTitle.setText("TEST COMPLETE");
        tvSentInfo.setText("No message was sent.\nIn a real alert your contacts would be texted now.");
        btnSafe.setVisibility(View.GONE);
        btnCall112.setVisibility(View.GONE);
        backCallback.setEnabled(false);
    }

    private void showSent(int reached) {
        groupCountdown.setVisibility(View.GONE);
        groupSent.setVisibility(View.VISIBLE);
        backCallback.setEnabled(false);

        if (reached > 0) {
            tvSentTitle.setText("SOS SENT");
            tvSentInfo.setText("Your location and medical details were sent to " + reached
                    + (reached == 1 ? " contact." : " contacts.")
                    + "\nLive location updates continue for 30 minutes.");
            btnSafe.setVisibility(View.VISIBLE);
        } else {
            tvSentTitle.setText("COULD NOT SEND");
            tvSentInfo.setText("No SMS could be sent (no SIM, no signal or permission missing)."
                    + "\nCall for help now.");
            btnSafe.setVisibility(View.GONE);
        }
        btnCall112.setVisibility(View.VISIBLE);
    }

    private void sendSafe() {
        btnSafe.setEnabled(false);
        btnSafe.setText("Notifying...");
        SosHelper.sendSafeMessage(this, reached -> {
            Toast.makeText(getApplicationContext(),
                    reached > 0 ? "Your contacts know you are safe."
                            : "Could not send the message.",
                    Toast.LENGTH_LONG).show();
            if (!isFinishing()) finish();
        });
    }

    // ------------------------------------------------------------------
    // Layer 7: auto-cancel if the vehicle starts moving again
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private void startMotionWatch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fused = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                if (finished) return;
                float kmh = GpsFilter.speedKmh(result.getLastLocation());
                if (kmh < 0) return;                            // untrusted fix: ignore
                fastFixes = kmh >= RESUME_KMH ? fastFixes + 1 : 0;
                if (fastFixes >= RESUME_FIXES) cancel("Cancelled (movement resumed)");
            }
        };
        try {
            fused.requestLocationUpdates(
                    new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).build(),
                    locationCallback, Looper.getMainLooper());
        } catch (SecurityException ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Alarm, vibration, screen
    // ------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    private void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void startAlarm() {
        try {
            audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM);
            audio.setStreamVolume(AudioManager.STREAM_ALARM,
                    audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);

            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.setVolume(0.3f, 0.3f);
            player.prepare();
            player.start();
        } catch (Exception e) {
            // The sound is optional: the vibration and the red screen still work.
        }
        startVibration();
    }

    private void setAlarmVolume(float v) {
        if (player == null) return;
        try {
            player.setVolume(v, v);
        } catch (IllegalStateException ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private void startVibration() {
        try {
            vibrator = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? ((VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE)).getDefaultVibrator()
                    : (Vibrator) getSystemService(VIBRATOR_SERVICE);
            long[] pattern = {0, 700, 400};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void stopEverything() {
        if (timer != null) timer.cancel();

        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
            }
            player.release();
            player = null;
        }
        if (audio != null && savedAlarmVolume >= 0) {
            try {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0);
            } catch (Exception ignored) {
            }
            savedAlarmVolume = -1;
        }
        if (vibrator != null) vibrator.cancel();
        if (fused != null && locationCallback != null) {
            fused.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        // Fail-safe: if the screen is destroyed mid-countdown (not a rotation),
        // send the alert. Staying silent could cost a life.
        boolean aborted = !finished && !isChangingConfigurations();
        stopEverything();
        isShowing = false;
        if (aborted && !SosHelper.TYPE_TEST.equals(type)) {
            SosHelper.sendSos(getApplicationContext(), type, null);
        }
        super.onDestroy();
    }
}
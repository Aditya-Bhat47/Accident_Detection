package com.example.accidentdetection.ui;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.accidentdetection.MonitoringService;
import com.example.accidentdetection.R;
import com.example.accidentdetection.database.AppDatabase;
import com.example.accidentdetection.utils.BottomNavHelper;
import com.example.accidentdetection.utils.SessionManager;
import com.example.accidentdetection.utils.SosHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int GREEN = Color.parseColor("#2E7D32");
    private static final int GREY = Color.parseColor("#9E9E9E");

    private MaterialCardView cardStatus;
    private TextView tvStatusTitle, tvStatusSub, tvSpeed, tvLocation, tvContactCount;
    private SwitchMaterial switchMonitoring;
    private BottomNavigationView bottomNav;
    private SharedPreferences prefs;
    private SessionManager session;
    private int contactCount = 0;

    // Switch listener kept as a field so it can be detached while syncing the UI
    private final CompoundButton.OnCheckedChangeListener monitoringListener =
            (button, isChecked) -> {
                if (isChecked && contactCount == 0) {
                    button.setChecked(false);   // re-enters this listener with false
                    Toast.makeText(this, "Add an emergency contact first",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                updateStatusUi(isChecked);
                setMonitoring(isChecked);       // saves the pref and starts/stops the service
            };

    // Live speed and location from MonitoringService
    private final BroadcastReceiver liveReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            double s = i.getDoubleExtra(MonitoringService.EXTRA_SPEED, -1);
            tvSpeed.setText(s < 0 ? "-- km/h" : String.format(Locale.US, "%.0f km/h", s));
            tvLocation.setText(String.format(Locale.US, "%.5f, %.5f",
                    i.getDoubleExtra(MonitoringService.EXTRA_LAT, 0),
                    i.getDoubleExtra(MonitoringService.EXTRA_LNG, 0)));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        session = new SessionManager(this);

        TextView tvGreeting = findViewById(R.id.tvGreeting);
        TextView tvLogout = findViewById(R.id.tvLogout);
        cardStatus = findViewById(R.id.cardStatus);
        tvStatusTitle = findViewById(R.id.tvStatusTitle);
        tvStatusSub = findViewById(R.id.tvStatusSub);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvLocation = findViewById(R.id.tvLocation);
        tvContactCount = findViewById(R.id.tvContactCount);
        switchMonitoring = findViewById(R.id.switchMonitoring);
        MaterialButton btnSos = findViewById(R.id.btnSos);
        bottomNav = findViewById(R.id.bottomNav);

        tvGreeting.setText("Hello, " + session.getUserName());

        // Set the initial state BEFORE attaching the listener
        boolean enabled = MonitoringService.running;
        switchMonitoring.setChecked(enabled);
        updateStatusUi(enabled);
        switchMonitoring.setOnCheckedChangeListener(monitoringListener);

        AppDatabase.get(this).contactDao().count().observe(this, count -> {
            contactCount = count;
            tvContactCount.setText(count + " saved");

            if (count == 0 && switchMonitoring.isChecked()) {
                switchMonitoring.setChecked(false);   // listener stops the service
                Toast.makeText(this, "Monitoring turned off: no emergency contacts",
                        Toast.LENGTH_LONG).show();
            }
        });

        setupHoldToSos(btnSos);
        tvLogout.setOnClickListener(v -> confirmLogout());
        findViewById(R.id.btnNearby).setOnClickListener(v ->
                startActivity(new Intent(this, NearbyHelpActivity.class)));
        BottomNavHelper.setup(this, bottomNav, R.id.nav_home);
    }

    private void updateStatusUi(boolean on) {
        cardStatus.setCardBackgroundColor(on ? GREEN : GREY);
        tvStatusTitle.setText(on ? "Monitoring is ON" : "Monitoring is OFF");
        tvStatusSub.setText(on ? "You are protected" : "Turn on to detect accidents");
        if (!on) {
            tvSpeed.setText("-- km/h");
            tvLocation.setText("--");
        }
    }

    // ---------- Monitoring service control ----------

    private void setMonitoring(boolean on) {
        prefs.edit().putBoolean("monitoring_enabled", on).apply();
        Intent i = new Intent(this, MonitoringService.class);
        if (on) {
            i.setAction(MonitoringService.ACTION_START);
            ContextCompat.startForegroundService(this, i);
            checkFullScreenIntent();
            askBatteryOptimization();
        } else {
            i.setAction(MonitoringService.ACTION_STOP);
            startService(i);
        }
    }

    private void checkFullScreenIntent() {
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (!nm.canUseFullScreenIntent()) {
                new AlertDialog.Builder(this)
                        .setTitle("Allow full-screen alerts")
                        .setMessage("Needed so the alert shows over the lock screen.")
                        .setPositiveButton("Open settings", (d, w) -> startActivity(
                                new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                        Uri.parse("package:" + getPackageName()))))
                        .setNegativeButton("Later", null)
                        .show();
            }
        }
    }

    private void askBatteryOptimization() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    // ---------- Hold-to-SOS ----------

    @SuppressLint("ClickableViewAccessibility")
    private void setupHoldToSos(MaterialButton btn) {
        final long holdMs = 3000;
        btn.setOnTouchListener(new View.OnTouchListener() {
            private CountDownTimer timer;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        timer = new CountDownTimer(holdMs, 100) {
                            @Override
                            public void onTick(long left) {
                                btn.setText(String.valueOf((left + 999) / 1000));   // 3, 2, 1
                            }

                            @Override
                            public void onFinish() {
                                btn.setText("SOS");
                                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                if (contactCount == 0) {
                                    Toast.makeText(MainActivity.this,
                                            "Add an emergency contact first, or call 112",
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }
                                Intent i = new Intent(MainActivity.this, AlertActivity.class);
                                i.putExtra(AlertActivity.EXTRA_TYPE, SosHelper.TYPE_MANUAL);
                                startActivity(i);
                            }
                        }.start();
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (timer != null) {
                            timer.cancel();
                            timer = null;
                        }
                        btn.setText("SOS");
                        return true;
                }
                return false;
            }
        });
    }

    // ---------- Logout ----------

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Log out?")
                .setMessage("Monitoring will keep running until you turn it off, "
                        + "but you will need to log in again.")
                .setPositiveButton("Log out", (d, w) -> {
                    session.logout();
                    Intent intent = new Intent(this, LoginPage.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- Lifecycle ----------

    @Override
    protected void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(this, liveReceiver,
                new IntentFilter(MonitoringService.ACTION_UPDATE),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(liveReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bottomNav.setSelectedItemId(R.id.nav_home);

        // Match the switch to the real service state without firing the listener
        boolean running = MonitoringService.running;
        switchMonitoring.setOnCheckedChangeListener(null);
        switchMonitoring.setChecked(running);
        switchMonitoring.setOnCheckedChangeListener(monitoringListener);
        updateStatusUi(running);
    }
}
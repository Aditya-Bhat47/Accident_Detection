package com.example.accidentdetection.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.accidentdetection.R;
import com.example.accidentdetection.database.AppDatabase;
import com.example.accidentdetection.utils.BottomNavHelper;
import com.example.accidentdetection.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

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

        boolean enabled = prefs.getBoolean("monitoring_enabled", false);
        switchMonitoring.setChecked(enabled);
        updateStatusUi(enabled);

        AppDatabase.get(this).contactDao().count().observe(this, count -> {
            contactCount = count;
            tvContactCount.setText(count + " saved");

            if (count == 0 && switchMonitoring.isChecked()) {
                switchMonitoring.setChecked(false);
                Toast.makeText(this, "Monitoring turned off: no emergency contacts",
                        Toast.LENGTH_LONG).show();
            }
        });

        switchMonitoring.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked && contactCount == 0) {
                button.setChecked(false);
                Toast.makeText(this, "Add an emergency contact first", Toast.LENGTH_LONG).show();
                return;
            }
            prefs.edit().putBoolean("monitoring_enabled", isChecked).apply();
            updateStatusUi(isChecked);
            // TODO (next step): start or stop MonitoringService here
        });

        btnSos.setOnClickListener(v -> confirmSos());
        tvLogout.setOnClickListener(v -> confirmLogout());
        findViewById(R.id.btnNearby).setOnClickListener(v ->
                startActivity(new Intent(this, NearbyHelpActivity.class)));

        BottomNavHelper.setup(this, bottomNav, R.id.nav_home);
    }

    private void updateStatusUi(boolean on) {
        cardStatus.setCardBackgroundColor(on ? GREEN : GREY);
        tvStatusTitle.setText(on ? "Monitoring is ON" : "Monitoring is OFF");
        tvStatusSub.setText(on ? "You are protected" : "Turn on to detect accidents");
    }

    private void confirmSos() {
        new AlertDialog.Builder(this)
                .setTitle("Send emergency alert?")
                .setMessage("Your emergency contacts will receive your location.")
                .setPositiveButton("SEND SOS", (d, w) -> {
                    // TODO (later): send SMS with location to saved contacts
                    Toast.makeText(this, "SOS triggered (demo)", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Log out?")
                .setMessage("Monitoring will keep its saved setting, but you will need to log in again.")
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

    @Override
    protected void onResume() {
        super.onResume();
        bottomNav.setSelectedItemId(R.id.nav_home);
    }
}
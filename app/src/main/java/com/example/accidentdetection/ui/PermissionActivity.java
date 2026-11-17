package com.example.accidentdetection.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.accidentdetection.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class PermissionActivity extends AppCompatActivity {

    private static final int GREEN = Color.parseColor("#2E7D32");
    private static final int RED = Color.parseColor("#D32F2F");

    private TextView tvLocation, tvSms, tvCall, tvNotif, tvBg;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        updateStatus();
                        checkPermanentlyDenied();
                    });
    private final ActivityResultLauncher<String> backgroundLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> updateStatus());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_permission);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvLocation = findViewById(R.id.tvLocationStatus);
        tvSms = findViewById(R.id.tvSmsStatus);
        tvCall = findViewById(R.id.tvCallStatus);
        tvNotif = findViewById(R.id.tvNotifStatus);
        tvBg = findViewById(R.id.tvBgStatus);

        MaterialButton btnGrant = findViewById(R.id.btnGrant);
        MaterialButton btnContinue = findViewById(R.id.btnContinue);

        btnGrant.setOnClickListener(v -> requestNext());
        btnContinue.setOnClickListener(v -> onContinue());

        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }


    private void requestNext() {
        List<String> missing = new ArrayList<>();

        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (!has(Manifest.permission.SEND_SMS)) {
            missing.add(Manifest.permission.SEND_SMS);
        }
        if (!has(Manifest.permission.CALL_PHONE)) {
            missing.add(Manifest.permission.CALL_PHONE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !has(Manifest.permission.POST_NOTIFICATIONS)) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!missing.isEmpty()) {
            permissionLauncher.launch(missing.toArray(new String[0]));
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && !has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            new AlertDialog.Builder(this)
                    .setTitle("Allow location all the time")
                    .setMessage("On the next screen, choose \"Allow all the time\" so accidents "
                            + "can be detected when the app is closed.")
                    .setPositiveButton("OK", (d, w) ->
                            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                    .setNegativeButton("Not now", null)
                    .show();
            return;
        }

        Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
    }

    private void checkPermanentlyDenied() {
        boolean blocked = false;
        String[] required = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.SEND_SMS
        };
        for (String p : required) {
            if (!has(p) && !ActivityCompat.shouldShowRequestPermissionRationale(this, p)) {
                blocked = true;
            }
        }
        if (blocked && hasAskedBefore()) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission needed")
                    .setMessage("Location and SMS are required. Please enable them in Settings.")
                    .setPositiveButton("Open Settings", (d, w) -> openAppSettings())
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private boolean hasAskedBefore() {
        return true;
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }


    private void updateStatus() {
        setStatus(tvLocation, has(Manifest.permission.ACCESS_FINE_LOCATION));
        setStatus(tvSms, has(Manifest.permission.SEND_SMS));
        setStatus(tvCall, has(Manifest.permission.CALL_PHONE));

        boolean notif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || has(Manifest.permission.POST_NOTIFICATIONS);
        setStatus(tvNotif, notif);

        boolean bg = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || has(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        setStatus(tvBg, bg);
    }

    private void setStatus(TextView tv, boolean granted) {
        tv.setText(granted ? "Granted" : "Not granted");
        tv.setTextColor(granted ? GREEN : RED);
    }

    private boolean has(String permission) {
        return ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED;
    }


    private void onContinue() {
        boolean locationOk = has(Manifest.permission.ACCESS_FINE_LOCATION);
        boolean smsOk = has(Manifest.permission.SEND_SMS);

        if (!locationOk || !smsOk) {
            Toast.makeText(this,
                    "Location and SMS permissions are required to continue.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        prefs.edit().putBoolean("first_launch", false).apply();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
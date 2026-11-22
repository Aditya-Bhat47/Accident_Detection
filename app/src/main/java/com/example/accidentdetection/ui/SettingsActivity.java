package com.example.accidentdetection.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.accidentdetection.R;
import com.example.accidentdetection.database.AppDatabase;
import com.example.accidentdetection.database.HistoryEntry;
import com.example.accidentdetection.utils.BottomNavHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    // Keys shared with the detection service later
    public static final String KEY_SENSITIVITY = "sensitivity";       // 0 low, 1 medium, 2 high
    public static final String KEY_COUNTDOWN = "countdown_seconds";
    public static final String KEY_AUTO_CALL = "auto_call";
    public static final String KEY_SOS_MESSAGE = "sos_message";
    public static final String DEFAULT_MESSAGE = "I may have been in an accident. Please help!";

    private SharedPreferences prefs;
    private TextView tvCountdown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        RadioGroup rg = findViewById(R.id.rgSensitivity);
        Slider slider = findViewById(R.id.sliderCountdown);
        tvCountdown = findViewById(R.id.tvCountdown);
        SwitchMaterial switchAutoCall = findViewById(R.id.switchAutoCall);
        TextInputEditText etMessage = findViewById(R.id.etSosMessage);
        BottomNavigationView nav = findViewById(R.id.bottomNav);

        // ----- load saved values -----
        int sens = prefs.getInt(KEY_SENSITIVITY, 1);
        rg.check(sens == 0 ? R.id.rbLow : sens == 2 ? R.id.rbHigh : R.id.rbMedium);

        int seconds = prefs.getInt(KEY_COUNTDOWN, 30);
        slider.setValue(seconds);
        updateCountdownLabel(seconds);

        switchAutoCall.setChecked(prefs.getBoolean(KEY_AUTO_CALL, false));
        etMessage.setText(prefs.getString(KEY_SOS_MESSAGE, DEFAULT_MESSAGE));

        // ----- save on change -----
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            int value = checkedId == R.id.rbLow ? 0 : checkedId == R.id.rbHigh ? 2 : 1;
            prefs.edit().putInt(KEY_SENSITIVITY, value).apply();
        });

        slider.addOnChangeListener((s, value, fromUser) -> {
            int v = (int) value;
            updateCountdownLabel(v);
            prefs.edit().putInt(KEY_COUNTDOWN, v).apply();
        });

        switchAutoCall.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(KEY_AUTO_CALL, checked).apply());

        findViewById(R.id.btnSaveMessage).setOnClickListener(v -> {
            String msg = etMessage.getText().toString().trim();
            if (msg.isEmpty()) msg = DEFAULT_MESSAGE;
            prefs.edit().putString(KEY_SOS_MESSAGE, msg).apply();
            etMessage.setText(msg);
            Toast.makeText(this, "Message saved", Toast.LENGTH_SHORT).show();
        });

        // Adds a demo entry so you can check the History screen.
        // This will open the real alert screen once we build it.
        findViewById(R.id.btnTest).setOnClickListener(v -> {
            HistoryEntry e = new HistoryEntry();
            e.timestamp = System.currentTimeMillis();
            e.type = "Test alert";
            e.status = "Test";
            e.mapLink = "";
            AppDatabase.executor.execute(() -> AppDatabase.get(this).historyDao().insert(e));
            Toast.makeText(this, "Test entry added to History", Toast.LENGTH_SHORT).show();
        });

        BottomNavHelper.setup(this, nav, R.id.nav_settings);
    }

    private void updateCountdownLabel(int seconds) {
        tvCountdown.setText("Alert countdown: " + seconds + " seconds");
    }
}

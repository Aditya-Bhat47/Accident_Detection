package com.example.accidentdetection.ui;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.accidentdetection.R;
import com.example.accidentdetection.utils.BottomNavHelper;
import com.example.accidentdetection.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ProfileActivity extends AppCompatActivity {

    private static final String[] BLOOD_GROUPS =
            {"Select", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};

    private TextInputEditText etName, etAllergies, etConditions, etDoctor;
    private TextInputLayout tilDoctor;
    private Spinner spBlood;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        etName = findViewById(R.id.etName);
        etAllergies = findViewById(R.id.etAllergies);
        etConditions = findViewById(R.id.etConditions);
        etDoctor = findViewById(R.id.etDoctor);
        tilDoctor = findViewById(R.id.tilDoctor);
        spBlood = findViewById(R.id.spBlood);
        BottomNavigationView nav = findViewById(R.id.bottomNav);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, BLOOD_GROUPS);
        spBlood.setAdapter(adapter);

        loadProfile();

        findViewById(R.id.btnSave).setOnClickListener(v -> saveProfile());
        BottomNavHelper.setup(this, nav, R.id.nav_profile);
    }

    private void loadProfile() {
        String defaultName = new SessionManager(this).getUserName();
        etName.setText(prefs.getString("med_name", defaultName));
        etAllergies.setText(prefs.getString("med_allergies", ""));
        etConditions.setText(prefs.getString("med_conditions", ""));
        etDoctor.setText(prefs.getString("med_doctor", ""));

        String blood = prefs.getString("med_blood", "Select");
        for (int i = 0; i < BLOOD_GROUPS.length; i++) {
            if (BLOOD_GROUPS[i].equals(blood)) spBlood.setSelection(i);
        }
    }

    private void saveProfile() {
        tilDoctor.setError(null);
        String doctor = etDoctor.getText().toString().trim();
        if (!doctor.isEmpty() && !doctor.matches("\\d{10}")) {
            tilDoctor.setError("Enter a 10-digit number or leave empty");
            return;
        }

        prefs.edit()
                .putString("med_name", etName.getText().toString().trim())
                .putString("med_blood", spBlood.getSelectedItem().toString())
                .putString("med_allergies", etAllergies.getText().toString().trim())
                .putString("med_conditions", etConditions.getText().toString().trim())
                .putString("med_doctor", doctor)
                .apply();

        Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();
    }
}

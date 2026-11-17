package com.example.accidentdetection.ui;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.accidentdetection.R;
import com.example.accidentdetection.model.RegisterRequest;
import com.example.accidentdetection.network.ApiClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterPage extends AppCompatActivity {
    private TextInputLayout tilName, tilEmail, tilPhone, tilPassword, tilConfirm;
    private TextInputEditText etName, etEmail, etPhone, etPassword, etConfirm;
    private MaterialButton btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tilName = findViewById(R.id.tilName);
        tilEmail = findViewById(R.id.tilEmail);
        tilPhone = findViewById(R.id.tilPhone);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirm = findViewById(R.id.tilConfirm);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);
        etConfirm = findViewById(R.id.etConfirm);

        btnRegister = findViewById(R.id.btnRegister);
        TextView tvLogin = findViewById(R.id.tvLogin);

        btnRegister.setOnClickListener(v -> attemptRegister());
        tvLogin.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        tilName.setError(null);
        tilEmail.setError(null);
        tilPhone.setError(null);
        tilPassword.setError(null);
        tilConfirm.setError(null);

        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirm = etConfirm.getText().toString();

        if (name.length() < 2) {
            tilName.setError("Enter your name");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid email");
            return;
        }
        if (phone.length() != 10) {
            tilPhone.setError("Enter a 10-digit phone number");
            return;
        }
        if (password.length() < 6) {
            tilPassword.setError("Minimum 6 characters");
            return;
        }
        if (!password.equals(confirm)) {
            tilConfirm.setError("Passwords do not match");
            return;
        }

        setLoading(true);

        ApiClient.get(this)
                .register(new RegisterRequest(name, email, phone, password))
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> res) {
                        if (isFinishing() || isDestroyed()) return;
                        setLoading(false);

                        if (res.isSuccessful()) {
                            Toast.makeText(RegisterPage.this,
                                    "Account created. Please log in.",
                                    Toast.LENGTH_LONG).show();
                            finish();
                        } else if (res.code() == 409) {
                            tilEmail.setError("This email is already registered");
                        } else {
                            Toast.makeText(RegisterPage.this,
                                    "Registration failed (" + res.code() + ")",
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        setLoading(false);
                        Toast.makeText(RegisterPage.this,
                                "Cannot reach server. Check your internet connection.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        btnRegister.setEnabled(!loading);
        btnRegister.setText(loading ? "CREATING ACCOUNT..." : "REGISTER");
    }
}
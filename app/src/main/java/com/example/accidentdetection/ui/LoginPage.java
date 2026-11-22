package com.example.accidentdetection.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.accidentdetection.R;
import com.example.accidentdetection.model.AuthResponse;
import com.example.accidentdetection.model.LoginRequest;
import com.example.accidentdetection.network.ApiClient;
import com.example.accidentdetection.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginPage extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login_page);   // this file must contain btnSkip
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        session = new SessionManager(this);

        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> attemptLogin());
        findViewById(R.id.tvRegister).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterPage.class)));

        setupSkipButton();
    }


    private void setupSkipButton() {
        MaterialButton btnSkip = findViewById(R.id.btnSkip);
        if (btnSkip == null) {
            Toast.makeText(this,
                    "btnSkip is missing from activity_login_page.xml",
                    Toast.LENGTH_LONG).show();
            return;
        }
        btnSkip.setVisibility(View.VISIBLE);
        btnSkip.setOnClickListener(v -> {
            session.saveSession("debug-token", "Test User");
            goToNextScreen();
        });
    }

    private void attemptLogin() {
        tilEmail.setError(null);
        tilPassword.setError(null);

        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid email");
            return;
        }
        if (password.isEmpty()) {
            tilPassword.setError("Enter your password");
            return;
        }

        setLoading(true);

        ApiClient.get(this)
                .login(new LoginRequest(email, password))
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthResponse> call, Response<AuthResponse> res) {
                        if (isFinishing() || isDestroyed()) return;
                        setLoading(false);

                        if (res.isSuccessful() && res.body() != null
                                && res.body().getToken() != null) {
                            session.saveSession(res.body().getToken(), res.body().getName());
                            Toast.makeText(LoginPage.this,
                                    "Welcome, " + session.getUserName(),
                                    Toast.LENGTH_SHORT).show();
                            goToNextScreen();
                        } else if (res.code() == 401 || res.code() == 403) {
                            // Don't reveal which one was wrong
                            tilPassword.setError("Incorrect email or password");
                        } else {
                            Toast.makeText(LoginPage.this,
                                    "Server error (" + res.code() + "). Try again.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<AuthResponse> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        setLoading(false);
                        Toast.makeText(LoginPage.this,
                                "Cannot reach server. Check your internet connection.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "LOGGING IN..." : "LOG IN");
    }

    private void goToNextScreen() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean firstLaunch = prefs.getBoolean("first_launch", true);

        Intent intent = new Intent(this,
                firstLaunch ? PermissionActivity.class : MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
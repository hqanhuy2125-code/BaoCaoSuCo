package com.example.baocaosuco.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.baocaosuco.MainActivity;
import com.example.baocaosuco.R;
import com.example.baocaosuco.data.model.User;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail;
    private EditText etPassword;
    private Button   btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.auth_login);

        etEmail    = findViewById(R.id.auth_login_email);
        etPassword = findViewById(R.id.auth_login_password);
        btnLogin   = findViewById(R.id.auth_login_btn);

        btnLogin.setOnClickListener(v -> attemptLogin());

        TextView linkRegister = findViewById(R.id.auth_login_link_register);
        linkRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }

    private void attemptLogin() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        // Kiem tra o trong
        if (email.isEmpty()) {
            etEmail.setError(getString(R.string.auth_err_email_empty));
            etEmail.requestFocus();
            return;
        }
        if (password.length() < 6) {
            etPassword.setError(getString(R.string.auth_err_password_short));
            etPassword.requestFocus();
            return;
        }

        setLoading(true);

        AuthRepositories.get(this).signIn(email, password, new AuthRepository.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                // Kiem tra Activity con song truoc khi dung UI
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                goToMain();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Khoa / mo khoa nut trong khi dang xu ly. */
    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? getString(R.string.auth_loading)
                                 : getString(R.string.auth_btn_login));
    }

    /** Sang MainActivity de no tu dieu huong theo role. */
    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

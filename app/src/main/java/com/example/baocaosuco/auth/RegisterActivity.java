package com.example.baocaosuco.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.baocaosuco.MainActivity;
import com.example.baocaosuco.R;
import com.example.baocaosuco.data.model.User;

public class RegisterActivity extends AppCompatActivity {

    private EditText etName;
    private EditText etEmail;
    private EditText etPassword;
    private Button   btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etName     = findViewById(R.id.et_name);
        etEmail    = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnRegister = findViewById(R.id.btn_action);

        // Dung lai man hinh cua Phuc o che do dang ky: hien ten, an so phong va Google (chua co BE)
        findViewById(R.id.til_name).setVisibility(View.VISIBLE);
        findViewById(R.id.til_room).setVisibility(View.GONE);
        findViewById(R.id.btn_google_auth).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.tv_form_title)).setText(R.string.auth_title_register);
        btnRegister.setText(R.string.auth_btn_register);

        btnRegister.setOnClickListener(v -> attemptRegister());

        TextView linkLogin = findViewById(R.id.tv_switch_mode);
        linkLogin.setText(R.string.auth_link_go_login);
        linkLogin.setOnClickListener(v -> finish()); // Quay lai LoginActivity
    }

    private void attemptRegister() {
        String name     = etName.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        // Kiem tra o trong theo thu tu
        if (name.isEmpty()) {
            etName.setError(getString(R.string.auth_err_name_empty));
            etName.requestFocus();
            return;
        }
        if (email.isEmpty()) {
            etEmail.setError(getString(R.string.auth_err_email_empty));
            etEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.auth_err_email_invalid));
            etEmail.requestFocus();
            return;
        }
        if (password.length() < 6) {
            etPassword.setError(getString(R.string.auth_err_password_short));
            etPassword.requestFocus();
            return;
        }

        setLoading(true);

        AuthRepositories.get(this).register(name, email, password, new AuthRepository.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                Toast.makeText(RegisterActivity.this, getString(R.string.auth_register_success), Toast.LENGTH_SHORT).show();
                goToMain();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        btnRegister.setEnabled(!loading);
        btnRegister.setText(loading ? getString(R.string.auth_loading)
                                    : getString(R.string.auth_btn_register));
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
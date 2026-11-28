package com.example.baocaosuco.auth;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.baocaosuco.MainActivity;
import com.example.baocaosuco.R;
import com.example.baocaosuco.data.model.User;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail;
    private EditText etPassword;
    static final String SEND_VERIFICATION = "sendVerification";

    private Button btnLogin;
    private View formPanel;
    private View verificationPanel;
    private View progress;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_login);

        // Keep the end of the scrollable form above the IME and navigation bar.
        View loginForm = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(loginForm, (view, insets) -> {
            Insets safeArea = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom);
            return insets;
        });

        etEmail    = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin   = findViewById(R.id.btn_action);
        formPanel = findViewById(R.id.auth_credentials);
        verificationPanel = findViewById(R.id.auth_verification_panel);
        progress = findViewById(R.id.pb_login_loading);
        initThemeAndBack(this, () -> loading);
        findViewById(R.id.auth_check_verification).setOnClickListener(v -> updateVerification(false, true));
        findViewById(R.id.auth_resend_verification).setOnClickListener(v -> updateVerification(true, false));
        findViewById(R.id.auth_switch_account).setOnClickListener(v -> {
            AuthRepositories.get(this).signOut();
            etPassword.setText("");
            showVerification(false);
        });

        // UI Phuc gop dang nhap/dang ky: che phan chi dung cho dang ky va Google (chua co BE)
        findViewById(R.id.til_name).setVisibility(View.GONE);
        findViewById(R.id.til_room).setVisibility(View.GONE);
        findViewById(R.id.btn_google_auth).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.tv_form_title)).setText(R.string.auth_title_login);
        btnLogin.setText(R.string.auth_btn_login);

        btnLogin.setOnClickListener(v -> attemptLogin());

        TextView linkRegister = findViewById(R.id.tv_switch_mode);
        linkRegister.setText(R.string.auth_link_go_register);
        linkRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
        FirebaseAuthRepository repository = firebaseRepository();
        if (repository != null && repository.hasSession()) {
            boolean send = getIntent().getBooleanExtra(SEND_VERIFICATION, false);
            getIntent().removeExtra(SEND_VERIFICATION);
            if (send || savedInstanceState != null && savedInstanceState.getBoolean("emailVerificationPanel")) {
                showVerification(true);
            }
            updateVerification(send, !send);
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("emailVerificationPanel", verificationPanel.getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(state);
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
                FirebaseAuthRepository repository = firebaseRepository();
                if (repository != null && repository.hasSession()) updateVerification(false, false);
            }
        });
    }

    private FirebaseAuthRepository firebaseRepository() {
        AuthRepository repository = AuthRepositories.get(this);
        return repository instanceof FirebaseAuthRepository ? (FirebaseAuthRepository) repository : null;
    }

    private void updateVerification(boolean send, boolean continueAfterCheck) {
        FirebaseAuthRepository repository = firebaseRepository();
        if (loading || repository == null) return;
        setLoading(true);
        repository.verifyEmail(send, new AuthRepository.Callback<Boolean>() {
            @Override public void onSuccess(Boolean pending) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                showVerification(pending);
                if (pending && send) {
                    Toast.makeText(LoginActivity.this, R.string.auth_verification_sent, Toast.LENGTH_LONG).show();
                } else if (!pending && continueAfterCheck) {
                    goToMain();
                }
            }

            @Override public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showVerification(boolean pending) {
        formPanel.setVisibility(pending ? View.GONE : View.VISIBLE);
        verificationPanel.setVisibility(pending ? View.VISIBLE : View.GONE);
        findViewById(R.id.tv_switch_mode).setVisibility(pending ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.tv_form_title)).setText(
                pending ? R.string.auth_verification_title : R.string.auth_title_login);
        if (pending) etPassword.setText("");
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        setControlsEnabled(this, !loading, R.id.btn_action, R.id.tv_switch_mode, R.id.et_email,
                R.id.et_password, R.id.btn_theme_toggle, R.id.auth_check_verification,
                R.id.auth_resend_verification, R.id.auth_switch_account);
        boolean verify = verificationPanel.getVisibility() == View.VISIBLE;
        progress.setVisibility(loading && !verify ? View.VISIBLE : View.GONE);
        findViewById(R.id.auth_verification_progress).setVisibility(
                loading && verify ? View.VISIBLE : View.GONE);
        btnLogin.setText(loading && !verify ? "" : getString(R.string.auth_btn_login));
    }

    static void setControlsEnabled(AppCompatActivity activity, boolean enabled, int... controls) {
        for (int id : controls) activity.findViewById(id).setEnabled(enabled);
    }

    static void initThemeAndBack(AppCompatActivity activity, java.util.function.BooleanSupplier busy) {
        activity.findViewById(R.id.btn_theme_toggle).setOnClickListener(v -> {
            int night = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            AppCompatDelegate.setDefaultNightMode(night == Configuration.UI_MODE_NIGHT_YES
                    ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
        });
        activity.getOnBackPressedDispatcher().addCallback(activity, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (!busy.getAsBoolean()) {
                    setEnabled(false);
                    try { activity.getOnBackPressedDispatcher().onBackPressed(); }
                    finally { setEnabled(true); }
                }
            }
        });
    }

    /** Sang MainActivity de no tu dieu huong theo role. */
    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
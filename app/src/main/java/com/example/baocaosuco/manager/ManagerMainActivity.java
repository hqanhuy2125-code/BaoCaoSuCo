package com.example.baocaosuco.manager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.baocaosuco.MainActivity;
import com.example.baocaosuco.R;
import com.example.baocaosuco.auth.AuthRepositories;
import com.example.baocaosuco.auth.AuthRepository;
import com.example.baocaosuco.auth.LoginActivity;
import com.example.baocaosuco.data.model.User;

public class ManagerMainActivity extends AppCompatActivity {

    private TextView tvGreeting;
    private TextView tvName;
    private TextView tvEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ui_manager_main);

        tvGreeting = findViewById(R.id.welcome_text);
        tvName = findViewById(R.id.tv_header_resident_name);
        tvEmail = findViewById(R.id.tv_header_resident_email);

        String initialName = getIntent().getStringExtra("displayName");
        setGreetingText(initialName);

        AuthRepositories.get(this).getCurrentUser(new AuthRepository.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                if (isFinishing() || isDestroyed()) return;
                if (user == null || !User.ROLE_MANAGER.equals(user.getRole())) {
                    redirectToMain();
                    return;
                }
                if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                    setGreetingText(user.getDisplayName());
                    tvName.setText(user.getDisplayName());
                }
                if (user.getEmail() != null) {
                    tvEmail.setText(user.getEmail());
                }
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                redirectToMain();
            }
        });

        ImageButton btnLogout = findViewById(R.id.logout_button);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> logout());
        }

        View navHome = findViewById(R.id.nav_item_home);
        if (navHome != null) {
            navHome.setOnClickListener(v -> switchToHome());
        }

        if (savedInstanceState == null) {
            switchToHome();
        }
    }

    private void setGreetingText(String name) {
        if (tvGreeting == null) return;
        String safeName = (name != null) ? name.trim() : "";
        tvGreeting.setText(getString(R.string.manager_welcome) + " " + safeName);
    }

    public void switchToHome() {
        loadFragment(new ManagerListFragment());
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void redirectToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void logout() {
        AuthRepositories.get(this).signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

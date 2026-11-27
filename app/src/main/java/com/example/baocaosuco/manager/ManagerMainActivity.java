package com.example.baocaosuco.manager;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.baocaosuco.integration.ui.IncidentUiText;


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

        IncidentUiText.applyInsets(this, findViewById(R.id.main));
        if (getResources().getConfiguration().screenHeightDp < 600
                || getResources().getConfiguration().fontScale >= 1.3f) {
            View avatar = findViewById(R.id.iv_header_avatar);
            ((View) avatar.getParent()).setVisibility(View.GONE);
            // The welcome line already identifies the dashboard on compact screens.
            findViewById(R.id.title_text).setVisibility(View.GONE);
            View header = findViewById(R.id.container_merged_header);
            int vertical = Math.round(12 * getResources().getDisplayMetrics().density);
            header.setPaddingRelative(header.getPaddingStart(), vertical, header.getPaddingEnd(), vertical);
        }
        View theme = findViewById(R.id.theme_toggle_button);
        theme.setOnClickListener(v -> {
            int mask = getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            AppCompatDelegate.setDefaultNightMode(mask == Configuration.UI_MODE_NIGHT_YES
                    ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
        });
        int[] unsupported = {R.id.nav_item_explore, R.id.nav_item_projects, R.id.nav_item_profile};
        for (int id : unsupported) {
            View tab = findViewById(id);
            if (tab != null) tab.setVisibility(View.GONE);
        }
        int[] running = {R.id.nav_item_home, R.id.nav_item_saved};
        for (int id : running) {
            View tab = findViewById(id);
            if (tab != null) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) tab.getLayoutParams();
                params.width = 0;
                params.weight = 1f;
                tab.setLayoutParams(params);
            }
        }
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

        findViewById(R.id.nav_item_saved).setOnClickListener(v -> {
            Fragment content = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (content instanceof ManagerListFragment) ((ManagerListFragment) content).showOverdue();
        });
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
        Fragment content = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (content instanceof ManagerListFragment) ((ManagerListFragment) content).showAll();
        else loadFragment(new ManagerListFragment());
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

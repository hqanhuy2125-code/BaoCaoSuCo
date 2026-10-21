package com.example.baocaosuco.manager;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.baocaosuco.R;
import com.example.baocaosuco.auth.AuthRepositories;
import com.example.baocaosuco.auth.LoginActivity;

/**
 * Man hinh chinh cua Quanly.
 * TODO (Nguoi 3): them danh sach xu ly su co, thong ke, v.v.
 */
public class ManagerMainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manager_main);

        String displayName = getIntent().getStringExtra("displayName");
        if (displayName == null) displayName = "";

        TextView tvGreeting = findViewById(R.id.manager_main_greeting);
        tvGreeting.setText(getString(R.string.manager_main_greeting, displayName));

        Button btnLogout = findViewById(R.id.manager_main_btn_logout);
        btnLogout.setOnClickListener(v -> logout());
    }

    private void logout() {
        AuthRepositories.get(this).signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

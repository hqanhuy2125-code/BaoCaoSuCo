package com.example.baocaosuco.resident;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.baocaosuco.R;
import com.example.baocaosuco.auth.AuthRepositories;
import com.example.baocaosuco.auth.LoginActivity;

/**
 * Man hinh chinh cua Cudan.
 * TODO (Nguoi 2): them danh sach bao cao su co, form gui bao cao, v.v.
 */
public class ResidentMainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.resident_main);

        // Lay ten hien thi tu Intent (MainActivity truyen vao)
        String displayName = getIntent().getStringExtra("displayName");
        if (displayName == null) displayName = "";

        TextView tvGreeting = findViewById(R.id.resident_main_greeting);
        tvGreeting.setText(getString(R.string.resident_main_greeting, displayName));

        Button btnLogout = findViewById(R.id.resident_main_btn_logout);
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

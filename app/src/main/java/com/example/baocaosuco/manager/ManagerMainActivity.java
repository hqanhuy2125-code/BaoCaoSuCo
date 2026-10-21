package com.example.baocaosuco.manager;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.baocaosuco.MainActivity;
import com.example.baocaosuco.R;
import com.example.baocaosuco.auth.AuthRepositories;
import com.example.baocaosuco.auth.AuthRepository;
import com.example.baocaosuco.auth.LoginActivity;
import com.example.baocaosuco.data.model.User;

/**
 * Man hinh chinh cua Quanly.
 * TODO (Nguoi 3): them danh sach xu ly su co, thong ke, v.v.
 */
public class ManagerMainActivity extends AppCompatActivity {

    private TextView tvGreeting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manager_main);

        tvGreeting = findViewById(R.id.manager_main_greeting);

        // Lay ten hien thi ban dau tu Intent (neu co)
        String initialName = getIntent().getStringExtra("displayName");
        setGreetingText(initialName);

        // Kiem tra phan quyen: Chi cho phep nguoi dung co vai tro Quan ly da dang nhap o lai.
        // Neu chua dang nhap hoac sai vai tro thi chuyen ve MainActivity de dieu huong lai.
        AuthRepositories.get(this).getCurrentUser(new AuthRepository.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                if (isFinishing() || isDestroyed()) return;
                if (user == null || !User.ROLE_MANAGER.equals(user.getRole())) {
                    redirectToMain();
                    return;
                }
                // Cap nhat ten hien thi tu User neu co (phong truong hop Intent khong truyen hoac rong)
                if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                    setGreetingText(user.getDisplayName());
                }
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                redirectToMain();
            }
        });

        Button btnLogout = findViewById(R.id.manager_main_btn_logout);
        btnLogout.setOnClickListener(v -> logout());
    }

    /**
     * Hien thi loi chao, xu ly an toan truong hop null va chuoi rong.
     */
    private void setGreetingText(String name) {
        if (tvGreeting == null) return;
        String safeName = (name != null) ? name.trim() : "";
        tvGreeting.setText(getString(R.string.manager_main_greeting, safeName));
    }

    /**
     * Chuyen ve MainActivity de dieu huong lai khi chua dang nhap hoac sai vai tro,
     * dong thoi xoa sach back stack de tranh nguoi dung an Back quay lai man hinh nay.
     */
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
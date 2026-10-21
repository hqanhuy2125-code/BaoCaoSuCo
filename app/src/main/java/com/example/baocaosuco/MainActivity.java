package com.example.baocaosuco;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.baocaosuco.auth.AuthRepositories;
import com.example.baocaosuco.auth.AuthRepository;
import com.example.baocaosuco.auth.LoginActivity;
import com.example.baocaosuco.data.model.User;
import com.example.baocaosuco.manager.ManagerMainActivity;
import com.example.baocaosuco.resident.ResidentMainActivity;

/**
 * Bo dieu huong trung tam.
 * Khong hien thi giao dien, chi kiem tra phien va chuyen man hinh.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Khong setContentView - Activity nay chi dieu huong

        AuthRepositories.get(this).getCurrentUser(new AuthRepository.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                if (isFinishing() || isDestroyed()) return;

                if (user == null) {
                    // Chua dang nhap
                    goTo(LoginActivity.class, null);
                    return;
                }

                String role = user.getRole();
                if (User.ROLE_RESIDENT.equals(role)) {
                    goTo(ResidentMainActivity.class, user.getDisplayName());
                } else if (User.ROLE_MANAGER.equals(role)) {
                    goTo(ManagerMainActivity.class, user.getDisplayName());
                } else {
                    // Role la, dong phien va ve Dang nhap
                    AuthRepositories.get(MainActivity.this).signOut();
                    Toast.makeText(MainActivity.this,
                            getString(R.string.auth_err_unknown_role),
                            Toast.LENGTH_LONG).show();
                    goTo(LoginActivity.class, null);
                }
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                // Loi khong xac dinh: ve man Dang nhap cho an toan
                goTo(LoginActivity.class, null);
            }
        });
    }

    /**
     * Mo Activity moi va dong MainActivity.
     * @param displayName null neu khong can truyen ten.
     */
    private void goTo(Class<?> target, String displayName) {
        Intent intent = new Intent(this, target);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (displayName != null) {
            intent.putExtra("displayName", displayName);
        }
        startActivity(intent);
        finish();
    }
}

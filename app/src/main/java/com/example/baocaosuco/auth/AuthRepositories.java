package com.example.baocaosuco.auth;

import android.content.Context;

/**
 * Factory singleton tra ve implementation hien tai cua AuthRepository.
 * Ho tro chuyen doi linh hoat giua FirebaseAuthRepository va MockAuthRepository.
 */
public class AuthRepositories {

    /**
     * Co bat/tat Firebase Authentication:
     * - true (mac dinh): Su dung Firebase Authentication + Cloud Firestore thuc te.
     * - false: Su dung MockAuthRepository (SharedPreferences) cho che do kiem thu offline.
     */
    public static final boolean USE_FIREBASE = true;

    private static AuthRepository instance;

    public static synchronized AuthRepository get(Context context) {
        if (instance == null) {
            Context appContext = context.getApplicationContext();
            if (USE_FIREBASE) {
                instance = new FirebaseAuthRepository(appContext);
            } else {
                instance = new MockAuthRepository(appContext);
            }
        }
        return instance;
    }

    /**
     * Cho phep inject repository tuy bien (phuc vu kiem thu).
     */
    public static synchronized void setInstance(AuthRepository repository) {
        instance = repository;
    }

    private AuthRepositories() {}
}
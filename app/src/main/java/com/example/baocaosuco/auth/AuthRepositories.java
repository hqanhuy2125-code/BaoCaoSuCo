package com.example.baocaosuco.auth;

import android.content.Context;

/**
 * Factory singleton tra ve implementation hien tai cua AuthRepository.
 * Khi noi Firebase: doi dong duy nhat trong get() tu MockAuthRepository sang FirebaseAuthRepository.
 */
public class AuthRepositories {

    private static AuthRepository instance;

    public static synchronized AuthRepository get(Context context) {
        if (instance == null) {
            // Chi giu applicationContext, khong giu Activity
            instance = new MockAuthRepository(context.getApplicationContext());
        }
        return instance;
    }

    private AuthRepositories() {}
}

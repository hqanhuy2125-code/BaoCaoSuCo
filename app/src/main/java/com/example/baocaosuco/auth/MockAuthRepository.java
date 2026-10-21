package com.example.baocaosuco.auth;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.baocaosuco.data.model.User;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * KHONG DUNG TRONG PRODUCTION.
 * Chi danh cho demo / phase 1 truoc khi noi Firebase.
 * Du lieu luu trong SharedPreferences duoi dang JSON; khong ma hoa.
 */
class MockAuthRepository implements AuthRepository {

    private static final String PREFS_ACCOUNTS = "mock_accounts";
    private static final String PREFS_SESSION  = "mock_session";
    private static final String KEY_ACCOUNTS   = "accounts";
    private static final String KEY_SESSION_EMAIL = "session_email";

    // Cau truc JSON moi tai khoan: {"email":"...","password":"...","displayName":"...","role":"..."}

    private final SharedPreferences prefsAccounts;
    private final SharedPreferences prefsSession;

    MockAuthRepository(Context appContext) {
        prefsAccounts = appContext.getSharedPreferences(PREFS_ACCOUNTS, Context.MODE_PRIVATE);
        prefsSession  = appContext.getSharedPreferences(PREFS_SESSION,  Context.MODE_PRIVATE);
        seedIfEmpty();
    }

    /**
     * Dam bao 2 tai khoan mau (cudan@bcsc.vn va quanly@bcsc.vn) luon ton tai.
     * Neu may da co du lieu cu (vi du seed tu ban cu) hoac da co tai khoan tu dang ky,
     * chi bo sung tai khoan mau neu con thieu ma khong xoa hay ghi de tai khoan da co.
     */
    private void seedIfEmpty() {
        try {
            JSONArray arr = loadAccounts();
            boolean hasResident = false;
            boolean hasManager = false;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String email = o.optString("email");
                if ("cudan@bcsc.vn".equalsIgnoreCase(email)) {
                    hasResident = true;
                }
                if ("quanly@bcsc.vn".equalsIgnoreCase(email)) {
                    hasManager = true;
                }
            }

            boolean changed = false;
            if (!hasResident) {
                arr.put(buildAccount("Cư dân", "cudan@bcsc.vn", "123456", User.ROLE_RESIDENT));
                changed = true;
            }
            if (!hasManager) {
                arr.put(buildAccount("Quản lý", "quanly@bcsc.vn", "123456", User.ROLE_MANAGER));
                changed = true;
            }

            if (changed || !prefsAccounts.contains(KEY_ACCOUNTS)) {
                saveAccounts(arr);
            }
        } catch (Exception ignored) {}
    }

    private JSONObject buildAccount(String name, String email, String password, String role)
            throws Exception {
        JSONObject o = new JSONObject();
        o.put("displayName", name);
        o.put("email",       email.trim().toLowerCase());
        o.put("password",    password);
        o.put("role",        role);
        return o;
    }

    // Doc toan bo danh sach tai khoan
    private JSONArray loadAccounts() {
        try {
            return new JSONArray(prefsAccounts.getString(KEY_ACCOUNTS, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    // Luu danh sach tai khoan
    private void saveAccounts(JSONArray arr) {
        prefsAccounts.edit().putString(KEY_ACCOUNTS, arr.toString()).apply();
    }

    // Tim tai khoan theo email (da chuan hoa: trim va lowercase)
    private JSONObject findByEmail(String normalizedEmail) {
        try {
            JSONArray arr = loadAccounts();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (normalizedEmail.equalsIgnoreCase(o.optString("email").trim())) return o;
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void register(String displayName, String email, String password,
                         Callback<User> callback) {
        String normalizedEmail = email.trim().toLowerCase();

        if (findByEmail(normalizedEmail) != null) {
            callback.onError("Email này đã được đăng ký.");
            return;
        }

        try {
            JSONObject account = buildAccount(displayName, normalizedEmail, password, User.ROLE_RESIDENT);
            JSONArray arr = loadAccounts();
            arr.put(account);
            saveAccounts(arr);

            // Dang nhap luon sau khi dang ky
            prefsSession.edit().putString(KEY_SESSION_EMAIL, normalizedEmail).apply();
            User user = new User(displayName, normalizedEmail, User.ROLE_RESIDENT);
            callback.onSuccess(user);
        } catch (Exception e) {
            callback.onError("Đăng ký thất bại. Vui lòng thử lại.");
        }
    }

    @Override
    public void signIn(String email, String password, Callback<User> callback) {
        String normalizedEmail = email.trim().toLowerCase();
        JSONObject account = findByEmail(normalizedEmail);

        // Bao loi chung, khong tiet lo email co ton tai hay khong
        if (account == null || !password.equals(account.optString("password"))) {
            callback.onError("Email hoặc mật khẩu không đúng.");
            return;
        }

        prefsSession.edit().putString(KEY_SESSION_EMAIL, normalizedEmail).apply();
        User user = new User(
                account.optString("displayName"),
                normalizedEmail,
                account.optString("role")
        );
        callback.onSuccess(user);
    }

    @Override
    public void getCurrentUser(Callback<User> callback) {
        String sessionEmail = prefsSession.getString(KEY_SESSION_EMAIL, null);
        if (sessionEmail == null) {
            // Chua dang nhap
            callback.onSuccess(null);
            return;
        }
        JSONObject account = findByEmail(sessionEmail.trim().toLowerCase());
        if (account == null) {
            // Phien con nhung tai khoan bi xoa (khong xay ra trong demo)
            callback.onSuccess(null);
            return;
        }
        User user = new User(
                account.optString("displayName"),
                sessionEmail,
                account.optString("role")
        );
        callback.onSuccess(user);
    }

    @Override
    public void signOut() {
        prefsSession.edit().remove(KEY_SESSION_EMAIL).apply();
    }
}
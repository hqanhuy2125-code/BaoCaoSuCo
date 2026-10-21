package com.example.baocaosuco.auth;

import com.example.baocaosuco.data.model.User;

/**
 * Giao dien truy cap tang xac thuc.
 * MockAuthRepository thuc thi cho demo; Firebase sau nay thay the.
 */
public interface AuthRepository {

    /** Callback chung, co the goi tren bat ky thread nao. */
    interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    /** Dang ky tai khoan moi. Role mac dinh la RESIDENT. */
    void register(String displayName, String email, String password,
                  Callback<User> callback);

    /**
     * Dang nhap.
     * Bao loi chung neu sai, khong tiet lo email co ton tai hay khong.
     */
    void signIn(String email, String password, Callback<User> callback);

    /**
     * Lay nguoi dung hien tai.
     * onSuccess(null) = chua dang nhap.
     */
    void getCurrentUser(Callback<User> callback);

    /** Dang xuat, xoa phien hien tai. */
    void signOut();
}

package com.example.baocaosuco.auth;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.baocaosuco.data.model.User;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.HashMap;
import java.util.Map;

/**
 * Trien khai thuc te cua AuthRepository su dung Firebase Authentication va Cloud Firestore.
 * - Luu tai khoan Email/Password tren Firebase Auth (mat khau duoc ma hoa an toan boi Google).
 * - Luu ho so nguoi dung (uid, displayName, email, role, createdAt) trong collection "users/{uid}".
 * - Mac dinh role khi tu dang ky luon la RESIDENT.
 */
public class FirebaseAuthRepository implements AuthRepository {

    private static final String COLLECTION_USERS = "users";

    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;
    private final Handler mainHandler;

    public FirebaseAuthRepository(Context context) {
        this(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance());
    }

    /** Constructor ho tro inject mock / test instance. */
    public FirebaseAuthRepository(FirebaseAuth auth, FirebaseFirestore firestore) {
        this.firebaseAuth = auth;
        this.firestore = firestore;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void register(String displayName, String email, String password, Callback<User> callback) {
        String normalizedEmail = email != null ? email.trim().toLowerCase() : "";
        String safeName = displayName != null ? displayName.trim() : "";

        firebaseAuth.createUserWithEmailAndPassword(normalizedEmail, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        postError(callback, getAuthErrorMessage(task.getException(), "Đăng ký thất bại."));
                        return;
                    }

                    FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                    if (firebaseUser == null) {
                        postError(callback, "Không thể xác định tài khoản sau khi đăng ký.");
                        return;
                    }

                    String uid = firebaseUser.getUid();

                    // Dong bo displayName len Auth profile
                    if (!safeName.isEmpty()) {
                        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                .setDisplayName(safeName)
                                .build();
                        firebaseUser.updateProfile(profileUpdates);
                    }

                    // Khoi tao ho so tren Cloud Firestore
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("uid", uid);
                    userData.put("displayName", safeName);
                    userData.put("email", normalizedEmail);
                    userData.put("role", User.ROLE_RESIDENT);
                    userData.put("createdAt", FieldValue.serverTimestamp());

                    firestore.collection(COLLECTION_USERS)
                            .document(uid)
                            .set(userData)
                            .addOnSuccessListener(aVoid -> {
                                User newUser = new User(uid, safeName, normalizedEmail, User.ROLE_RESIDENT, Timestamp.now());
                                postSuccess(callback, newUser);
                            })
                            .addOnFailureListener(e -> {
                                // Ghi Firestore that bai: xu ly callback don dep an toan de tranh tai khoan Auth "mo coi"
                                String firestoreErrorMsg = getFirestoreErrorMessage(e, "Lỗi tạo hồ sơ người dùng trên Firestore");
                                firebaseUser.delete().addOnCompleteListener(deleteTask -> {
                                    firebaseAuth.signOut();
                                    if (!deleteTask.isSuccessful()) {
                                        postError(callback, firestoreErrorMsg + " (Không thể hủy phiên Auth: " + 
                                                (deleteTask.getException() != null ? deleteTask.getException().getMessage() : "lỗi không rõ") + ")");
                                    } else {
                                        postError(callback, firestoreErrorMsg + ". Đã hủy phiên đăng ký để đảm bảo an toàn.");
                                    }
                                });
                            });
                });
    }

    @Override
    public void signIn(String email, String password, Callback<User> callback) {
        String normalizedEmail = email != null ? email.trim().toLowerCase() : "";

        firebaseAuth.signInWithEmailAndPassword(normalizedEmail, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        postError(callback, getAuthErrorMessage(task.getException(), "Email hoặc mật khẩu không đúng."));
                        return;
                    }

                    FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                    if (firebaseUser == null) {
                        postError(callback, "Không thể xác định phiên người dùng sau khi đăng nhập.");
                        return;
                    }

                    // Doc ho so tu Cloud Firestore de lay role va thong tin
                    firestore.collection(COLLECTION_USERS)
                            .document(firebaseUser.getUid())
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                User user = parseUserDocument(firebaseUser, documentSnapshot);
                                if (user == null) {
                                    // Ho so thieu hoac vai tro khong hop le -> dang xuat an toan
                                    firebaseAuth.signOut();
                                    postError(callback, "Hồ sơ người dùng không tồn tại hoặc vai trò không hợp lệ trên Firestore.");
                                    return;
                                }
                                postSuccess(callback, user);
                            })
                            .addOnFailureListener(e -> {
                                firebaseAuth.signOut();
                                postError(callback, getFirestoreErrorMessage(e, "Không thể tải hồ sơ người dùng từ Firestore"));
                            });
                });
    }

    @Override
    public void getCurrentUser(Callback<User> callback) {
        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        if (firebaseUser == null) {
            postSuccess(callback, null);
            return;
        }

        firestore.collection(COLLECTION_USERS)
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User user = parseUserDocument(firebaseUser, documentSnapshot);
                    if (user == null) {
                        // Phien co tren Auth nhung ho so Firestore bi thieu hoac sai role -> huy phien
                        firebaseAuth.signOut();
                        postSuccess(callback, null);
                        return;
                    }
                    postSuccess(callback, user);
                })
                .addOnFailureListener(e -> {
                    // Loi mang hoac quyen truy cap Firestore khi kiem tra phien
                    postError(callback, getFirestoreErrorMessage(e, "Không thể kiểm tra phiên đăng nhập từ Firestore"));
                });
    }

    @Override
    public void signOut() {
        firebaseAuth.signOut();
    }

    /**
     * Parse document tu Firestore sang User model an toan.
     * Tra ve null neu document khong ton tai hoac role khong hop le.
     */
    private User parseUserDocument(FirebaseUser firebaseUser, DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return null;
        }

        String role = doc.getString("role");
        if (role == null || (!User.ROLE_RESIDENT.equals(role) && !User.ROLE_MANAGER.equals(role))) {
            return null; // Khong chap nhan role la hoac null
        }

        String displayName = doc.getString("displayName");
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = firebaseUser.getDisplayName();
        }
        if (displayName == null) {
            displayName = "";
        }

        String email = doc.getString("email");
        if (email == null || email.trim().isEmpty()) {
            email = firebaseUser.getEmail();
        }

        Timestamp createdAt = doc.getTimestamp("createdAt");
        return new User(firebaseUser.getUid(), displayName, email, role, createdAt);
    }

    /**
     * Chuyen doi Exception cua Firebase sang thong bao tieng Viet than thien.
     */
    private String getAuthErrorMessage(Exception exception, String defaultMsg) {
        if (exception == null) return defaultMsg;
        if (exception instanceof FirebaseAuthUserCollisionException) {
            return "Email này đã được đăng ký tài khoản.";
        }
        if (exception instanceof FirebaseAuthWeakPasswordException) {
            return "Mật khẩu quá yếu. Vui lòng đặt mật khẩu dài hơn.";
        }
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            return "Email hoặc mật khẩu không đúng định dạng/thông tin.";
        }
        if (exception instanceof FirebaseAuthInvalidUserException) {
            return "Tài khoản không tồn tại hoặc đã bị vô hiệu hóa.";
        }
        if (exception instanceof FirebaseNetworkException) {
            return "Lỗi kết nối mạng. Vui lòng kiểm tra lại kết nối Internet.";
        }
        return defaultMsg;
    }

    private String getFirestoreErrorMessage(Exception exception, String defaultPrefix) {
        if (exception == null) return defaultPrefix;
        if (exception instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException fe = (FirebaseFirestoreException) exception;
            if (fe.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return defaultPrefix + ": Bị từ chối quyền truy cập (Permission Denied). Vui lòng kiểm tra Firestore Security Rules.";
            }
            if (fe.getCode() == FirebaseFirestoreException.Code.UNAVAILABLE) {
                return defaultPrefix + ": Máy chủ Firestore hiện không khả dụng hoặc mất mạng.";
            }
        }
        return defaultPrefix + ": " + exception.getLocalizedMessage();
    }

    private <T> void postSuccess(Callback<T> callback, T result) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onSuccess(result));
    }

    private <T> void postError(Callback<T> callback, String message) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onError(message));
    }
}
package com.example.baocaosuco.auth;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.baocaosuco.R;
import com.example.baocaosuco.data.model.User;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseUserMetadata;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

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
    private final Context context;
    private final long cutoffMillis;

    public FirebaseAuthRepository(Context context) {
        this(context.getApplicationContext(), FirebaseAuth.getInstance(), FirebaseFirestore.getInstance(),
                Long.parseLong(context.getString(R.string.auth_verification_cutoff_millis)));
    }

    /** Constructor ho tro inject mock / test instance. */
    public FirebaseAuthRepository(FirebaseAuth auth, FirebaseFirestore firestore) {
        this(null, auth, firestore, 0);
    }

    private FirebaseAuthRepository(Context context, FirebaseAuth auth, FirebaseFirestore firestore,
                                   long cutoffMillis) {
        this.context = context;
        this.cutoffMillis = cutoffMillis;
        this.firebaseAuth = auth;
        this.firestore = firestore;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void register(String displayName, String email, String password, Callback<User> callback) {
        if (context != null && !registrationOpen(cutoffMillis, System.currentTimeMillis())) {
            postError(callback, text(R.string.auth_verification_not_configured));
            return;
        }
        String normalizedEmail = email != null ? email.trim().toLowerCase(Locale.ROOT) : "";
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
                    FirebaseUserMetadata metadata = firebaseUser.getMetadata();
                    if (context != null && (metadata == null
                            || !registrationOpen(cutoffMillis, metadata.getCreationTimestamp()))) {
                        // No profile exists yet. The SDK/server creation time defines the cutover.
                        firebaseUser.delete().addOnCompleteListener(ignored -> {
                            signOutIfCurrent(uid);
                            postError(callback, text(R.string.auth_verification_not_configured));
                        });
                        return;
                    }

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
                                    signOutIfCurrent(uid);
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
        String normalizedEmail = email != null ? email.trim().toLowerCase(Locale.ROOT) : "";

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

                    readProfile(firebaseUser, false, true, callback);
                });
    }

    @Override
    public void getCurrentUser(Callback<User> callback) {
        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        if (firebaseUser == null) {
            postSuccess(callback, null);
            return;
        }
        readProfile(firebaseUser, false, false, callback);
    }

    boolean hasSession() {
        return firebaseAuth.getCurrentUser() != null;
    }

    /** Only a session with a server-read own Resident profile can send another email. */
    void verifyEmail(boolean send, Callback<Boolean> callback) {
        FirebaseUser current = firebaseAuth.getCurrentUser();
        if (current == null) {
            postError(callback, text(R.string.auth_verification_sign_in));
            return;
        }
        readProfile(current, true, false, new Callback<User>() {
            @Override public void onSuccess(User profile) {
                if (!isCurrent(current.getUid())) {
                    postError(callback, text(R.string.auth_verification_sign_in));
                    return;
                }
                if (!requiresVerification(profile, current)) {
                    postSuccess(callback, false);
                    return;
                }
                // Refresh after opening the email link on this or another device.
                current.reload().addOnCompleteListener(reload -> {
                    if (!isCurrent(current.getUid())) {
                        postError(callback, text(R.string.auth_verification_sign_in));
                    } else if (!reload.isSuccessful()) {
                        postError(callback, getAuthErrorMessage(reload.getException(),
                                text(R.string.auth_verification_refresh_failed)));
                    } else if (!requiresVerification(profile, firebaseAuth.getCurrentUser())) {
                        postSuccess(callback, false);
                    } else if (send) {
                        current.sendEmailVerification().addOnCompleteListener(sent -> {
                            if (!isCurrent(current.getUid())) {
                                postError(callback, text(R.string.auth_verification_sign_in));
                            } else if (sent.isSuccessful()) {
                                postSuccess(callback, true);
                            } else {
                                postError(callback, getAuthErrorMessage(sent.getException(),
                                        text(R.string.auth_verification_send_failed)));
                            }
                        });
                    } else {
                        postSuccess(callback, true);
                    }
                });
            }

            @Override public void onError(String message) { postError(callback, message); }
        });
    }

    private void readProfile(FirebaseUser authenticated, boolean allowPending, boolean signingIn,
                             Callback<User> callback) {
        String uid = authenticated.getUid();
        firestore.collection(COLLECTION_USERS).document(uid).get(Source.SERVER)
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isCurrent(uid)) {
                        postError(callback, text(R.string.auth_verification_sign_in));
                        return;
                    }
                    FirebaseUser current = firebaseAuth.getCurrentUser();
                    User profile = parseUserDocument(current, documentSnapshot);
                    if (profile == null) {
                        signOutIfCurrent(uid);
                        if (signingIn || allowPending) {
                            postError(callback, "Hồ sơ người dùng không tồn tại hoặc vai trò không hợp lệ.");
                        } else {
                            postSuccess(callback, null);
                        }
                    } else if (!allowPending && requiresVerification(profile, current)) {
                        // Preserve the owning Auth session for resend/reload on the login screen.
                        if (signingIn) {
                            postError(callback, text(R.string.auth_verification_required));
                        } else {
                            postSuccess(callback, null);
                        }
                    } else {
                        postSuccess(callback, profile);
                    }
                }).addOnFailureListener(e -> {
                    if (signingIn) signOutIfCurrent(uid);
                    postError(callback, getFirestoreErrorMessage(e,
                            "Không thể tải hồ sơ người dùng từ Firestore"));
                });
    }

    private boolean requiresVerification(User profile, FirebaseUser current) {
        FirebaseUserMetadata metadata = current.getMetadata();
        return requiresVerification(profile.getRole(), cutoffMillis,
                metadata == null ? 0 : metadata.getCreationTimestamp(), current.isEmailVerified());
    }

    static boolean requiresVerification(String role, long cutoffMillis, long creationMillis,
                                        boolean verified) {
        return User.ROLE_RESIDENT.equals(role) && !verified && cutoffMillis > 0
                && (creationMillis <= 0 || creationMillis >= cutoffMillis);
    }

    static boolean registrationOpen(long cutoffMillis, long nowMillis) {
        return cutoffMillis > 0 && nowMillis >= cutoffMillis;
    }

    private String text(int resource) {
        // Test constructor retains the historical unconfigured adapter for existing smoke fixtures.
        return context == null ? "Không thể xác minh phiên đăng nhập." : context.getString(resource);
    }

    private boolean isCurrent(String uid) {
        FirebaseUser current = firebaseAuth.getCurrentUser();
        return current != null && uid.equals(current.getUid());
    }

    private void signOutIfCurrent(String uid) {
        if (isCurrent(uid)) firebaseAuth.signOut();
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
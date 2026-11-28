package com.example.baocaosuco.resident;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.baocaosuco.integration.ui.IncidentUiText;
import androidx.activity.OnBackPressedCallback;
import com.example.baocaosuco.resident.photo.IncidentPhotoController;
import com.example.baocaosuco.resident.photo.IncidentPhotoProcessor;
import com.example.baocaosuco.resident.photo.IncidentPhotoUiBridge;

import com.example.baocaosuco.MainActivity;
import com.example.baocaosuco.R;
import com.example.baocaosuco.auth.AuthRepositories;
import com.example.baocaosuco.auth.AuthRepository;
import com.example.baocaosuco.auth.LoginActivity;
import com.example.baocaosuco.data.model.User;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ResidentMainActivity extends AppCompatActivity {

    private IncidentPhotoUiBridge photoBridge;
    private IncidentPhotoUiBridge.Listener photoListener;
    private boolean residentVerified;
    private OnBackPressedCallback formBack;
    private TextView tvGreeting;
    private TextView tvName;
    private TextView tvEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        photoBridge = new IncidentPhotoUiBridge(this, new IncidentPhotoUiBridge.Listener() {
            @Override public void onBusy(boolean busy) {
                if (photoListener != null) photoListener.onBusy(busy);
            }

            @Override public void onPhotoReady(IncidentPhotoProcessor.PreparedPhoto photo) {
                if (photoListener != null) photoListener.onPhotoReady(photo);
                else photoBridge.clearSelected();
            }

            @Override public void onPhotoCleared() {
                if (photoListener != null) photoListener.onPhotoCleared();
            }

            @Override public void onCancelled(IncidentPhotoController.Source source) {
                if (photoListener != null) photoListener.onCancelled(source);
            }

            @Override public void onPickerError(IncidentPhotoController.Source source,
                                               IncidentPhotoController.Error error) {
                if (photoListener != null) photoListener.onPickerError(source, error);
            }

            @Override public void onProcessingError(IncidentPhotoProcessor.Failure error) {
                if (photoListener != null) photoListener.onProcessingError(error);
            }
        });
        setContentView(R.layout.ui_resident_main);
        formBack = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { switchToHome(); }
        };
        getOnBackPressedDispatcher().addCallback(this, formBack);
        syncFormChrome(getSupportFragmentManager().findFragmentById(R.id.fragment_container)
                instanceof CreateIncidentFragment);

        IncidentUiText.applyInsets(this, findViewById(R.id.main));
        if (getResources().getConfiguration().screenHeightDp < 600
                || getResources().getConfiguration().fontScale >= 1.3f) {
            View avatar = findViewById(R.id.iv_header_avatar);
            ((View) avatar.getParent()).setVisibility(View.GONE);
            // The welcome line already identifies the dashboard on compact screens.
            findViewById(R.id.title_text).setVisibility(View.GONE);
            View header = findViewById(R.id.container_merged_header);
            int vertical = Math.round(12 * getResources().getDisplayMetrics().density);
            header.setPaddingRelative(header.getPaddingStart(), vertical, header.getPaddingEnd(), vertical);
        }
        View theme = findViewById(R.id.theme_toggle_button);
        theme.setOnClickListener(v -> {
            int mask = getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            AppCompatDelegate.setDefaultNightMode(mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
        });
        tvGreeting = findViewById(R.id.welcome_text);
        tvName = findViewById(R.id.tv_header_resident_name);
        tvEmail = findViewById(R.id.tv_header_resident_email);

        String initialName = getIntent().getStringExtra("displayName");
        setGreetingText(initialName);

        AuthRepositories.get(this).getCurrentUser(new AuthRepository.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                if (isFinishing() || isDestroyed()) return;
                if (user == null || !User.ROLE_RESIDENT.equals(user.getRole())) {
                    redirectToMain();
                    return;
                }
                residentVerified = true;
                Fragment content = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                if (content instanceof CreateIncidentFragment) {
                    ((CreateIncidentFragment) content).refreshPhotoAccess();
                }
                if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                    setGreetingText(user.getDisplayName());
                    tvName.setText(user.getDisplayName());
                }
                if (user.getEmail() != null) {
                    tvEmail.setText(user.getEmail());
                }
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                redirectToMain();
            }
        });

        ImageButton btnLogout = findViewById(R.id.logout_button);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> logout());
        }

        View navHome = findViewById(R.id.nav_item_home);
        View navCreate = findViewById(R.id.nav_item_projects);
        View btnCameraFab = findViewById(R.id.btn_camera_fab);

        if (navHome != null) {
            navHome.setOnClickListener(v -> switchToHome());
        }
        navCreate.setOnClickListener(v -> switchToCreateIncident());
        findViewById(R.id.nav_item_profile).setOnClickListener(v -> {
            if (residentVerified) AccountProfileDialog.open(this, User.ROLE_RESIDENT);
        });
        if (btnCameraFab != null) {
            btnCameraFab.setOnClickListener(v -> switchToCreateIncident());
        }

        if (savedInstanceState == null) {
            switchToHome();
        }
    }

    private void setGreetingText(String name) {
        if (tvGreeting == null) return;
        String safeName = (name != null) ? name.trim() : "";
        tvGreeting.setText(getString(R.string.resident_welcome) + " " + safeName);
    }

    public void switchToHome() {
        Fragment content = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (content instanceof CreateIncidentFragment
                && (((CreateIncidentFragment) content).isSubmitting() || photoBridge.isBusy())) return;
        if (content instanceof ResidentListFragment) return;
        photoBridge.clearSelected();
        syncFormChrome(false);
        loadFragment(new ResidentListFragment());
    }

    public void switchToCreateIncident() {
        if (!residentVerified || formBack.isEnabled()) return;
        syncFormChrome(true);
        loadFragment(new CreateIncidentFragment());
    }

    private void syncFormChrome(boolean form) {
        formBack.setEnabled(form);
        findViewById(R.id.nav_item_home).setSelected(!form);
        findViewById(R.id.nav_item_projects).setSelected(form);
        int color = ContextCompat.getColor(this, form ? R.color.f7_nav : R.color.f7_nav_active);
        ((ImageView) findViewById(R.id.iv_nav_home)).setColorFilter(color);
        ((TextView) findViewById(R.id.tv_nav_home)).setTextColor(color);
        findViewById(R.id.dot_nav_home).setVisibility(form ? View.GONE : View.VISIBLE);
        findViewById(R.id.dot_nav_projects).setVisibility(form ? View.VISIBLE : View.GONE);
        findViewById(R.id.container_merged_header).setVisibility(form ? View.GONE : View.VISIBLE);
    }

    IncidentPhotoUiBridge photoBridge() { return photoBridge; }

    boolean isResidentVerified() { return residentVerified; }

    void setPhotoListener(IncidentPhotoUiBridge.Listener listener) { photoListener = listener; }

    @Override
    protected void onDestroy() {
        photoListener = null;
        photoBridge.close();
        super.onDestroy();
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

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

    /** A read-only destination shared by the two role shells. */
    public static final class AccountProfileDialog extends DialogFragment {
        private static final String TAG = "own-account-profile";
        private static final String ROLE = "role";
        private int generation;

        public static void open(AppCompatActivity activity, String role) {
            FragmentManager fragments = activity.getSupportFragmentManager();
            if (fragments.isStateSaved() || fragments.findFragmentByTag(TAG) != null) return;
            Bundle arguments = new Bundle();
            arguments.putString(ROLE, role);
            AccountProfileDialog profile = new AccountProfileDialog();
            profile.setArguments(arguments);
            profile.showNow(fragments, TAG);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle state) {
            return new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.account_profile_title)
                    .setMessage(R.string.account_profile_loading)
                    .setNegativeButton(R.string.account_profile_close, null)
                    .setNeutralButton(R.string.account_profile_retry, null)
                    .create();
        }

        @Override
        public void onStart() {
            super.onStart();
            AlertDialog dialog = (AlertDialog) requireDialog();
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> load(dialog));
            load(dialog);
        }

        @Override
        public void onStop() {
            generation++;
            ((AlertDialog) requireDialog()).setMessage(getText(R.string.account_profile_loading));
            super.onStop();
        }

        private void load(AlertDialog dialog) {
            int request = ++generation;
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
            dialog.setMessage(getText(R.string.account_profile_loading));

            String uid = activeUid();
            if (uid == null) {
                showError(dialog);
                return;
            }

            String role = requireArguments().getString(ROLE);
            AuthRepositories.get(requireContext()).getCurrentUser(new AuthRepository.Callback<User>() {
                @Override
                public void onSuccess(User user) {
                    if (request != generation || !isAdded() || !dialog.isShowing()) return;
                    if (user == null || !uid.equals(activeUid()) || !uid.equals(user.getUid())
                            || role == null || !role.equals(user.getRole())) {
                        showError(dialog);
                        return;
                    }
                    int roleLabel = User.ROLE_MANAGER.equals(role) ? R.string.account_profile_manager
                            : R.string.account_profile_resident;
                    dialog.setMessage(getString(R.string.account_profile_details,
                            valueOrDash(user.getDisplayName()), valueOrDash(user.getEmail()),
                            getString(roleLabel)));
                }

                @Override
                public void onError(String message) {
                    if (request == generation && isAdded() && dialog.isShowing()) showError(dialog);
                }
            });
        }

        private void showError(AlertDialog dialog) {
            dialog.setMessage(getText(R.string.account_profile_error));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
        }

        private static String activeUid() {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            return user == null ? null : user.getUid();
        }

        private static String valueOrDash(String value) {
            return value == null || value.trim().isEmpty() ? "—" : value;
        }
    }
}

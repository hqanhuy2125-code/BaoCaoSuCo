package com.example.baocaosuco.resident;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.baocaosuco.R;
import com.example.baocaosuco.resident.photo.EvidenceUiText;
import com.example.baocaosuco.resident.photo.SupabaseEvidenceClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.repository.FirebaseIncidentRepository;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.resident.photo.IncidentPhotoController;
import com.example.baocaosuco.resident.photo.IncidentPhotoProcessor;
import com.example.baocaosuco.resident.photo.IncidentPhotoUiBridge;
import com.example.baocaosuco.resident.photo.IncidentPhotoViewBinder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import com.example.baocaosuco.integration.ui.IncidentUiText;

public class CreateIncidentFragment extends Fragment implements ResidentIncidentCoordinator.View, IncidentPhotoViewBinder.ErrorHandler {

    private TextInputEditText etTitle, etLocation, etDesc;
    private RadioGroup rgPriority;
    private MaterialButton btnSubmit;
    private ProgressBar pbLoading;
    private MaterialCardView cvPhotoPreview;
    private ImageView ivCapturedPhoto;
    private View btnRemovePhoto;

    private ResidentIncidentCoordinator coordinator;
    private IncidentPhotoUiBridge photoBridge;
    private IncidentPhotoViewBinder photoViewBinder;
    private boolean submitting;
    private boolean uploading;
    private boolean retryPhoto = true;
    private String savedIncident;
    private String savedOwner;
    private String draftOwner;
    private SupabaseEvidenceClient evidence;
    private FirebaseAuth.AuthStateListener authListener;
    private TextView message;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_create_incident, container, false);

        etTitle = view.findViewById(R.id.et_incident_title);
        etLocation = view.findViewById(R.id.et_incident_location);
        etDesc = view.findViewById(R.id.et_incident_desc);
        rgPriority = view.findViewById(R.id.rg_priority);
        btnSubmit = view.findViewById(R.id.btn_submit_incident);
        pbLoading = view.findViewById(R.id.pb_submit_loading);
        cvPhotoPreview = view.findViewById(R.id.cv_photo_preview);
        ivCapturedPhoto = view.findViewById(R.id.iv_captured_photo);
        btnRemovePhoto = view.findViewById(R.id.btn_remove_photo);

        if (savedInstanceState != null) {
            savedIncident = savedInstanceState.getString("evidence_incident");
            savedOwner = savedInstanceState.getString("evidence_owner");
            retryPhoto = savedInstanceState.getBoolean("evidence_retry", true);
        }
        draftOwner = savedInstanceState == null ? currentUid()
                : savedInstanceState.getString("evidence_draft_owner", currentUid());
        authListener = auth -> {
            if (draftOwner != null && !draftOwner.equals(currentUid())) {
                if (evidence != null) evidence.close();
                uploading = false;
                retryPhoto = false;
                photoBridge.clearSelected();
                showMessage(savedIncident == null ? "Phiên đăng nhập đã thay đổi."
                        : "Phiên đăng nhập đã thay đổi. Báo cáo đã lưu.");
                renderBusy();
            }
        };
        coordinator = new ResidentIncidentCoordinator(new FirebaseIncidentRepository().resident(), this);

        LinearLayout formContainer = (LinearLayout) ((ViewGroup) cvPhotoPreview.getParent());
        int index = formContainer.indexOfChild(cvPhotoPreview);

        MaterialButton btnCaptureDynamic = new MaterialButton(requireContext());
        btnCaptureDynamic.setId(R.id.photo_capture);
        btnCaptureDynamic.setText("Chụp ảnh");
        formContainer.addView(btnCaptureDynamic, index);

        MaterialButton btnGalleryDynamic = new MaterialButton(requireContext());
        btnGalleryDynamic.setId(R.id.photo_gallery);
        btnGalleryDynamic.setText("Chọn ảnh từ thư viện");
        formContainer.addView(btnGalleryDynamic, index + 1);

        for (MaterialButton button : new MaterialButton[]{btnCaptureDynamic, btnGalleryDynamic}) {
            button.setBackgroundTintList(androidx.appcompat.content.res.AppCompatResources.getColorStateList(
                    button.getContext(), R.color.f7_photo_button_bg));
            button.setTextColor(androidx.appcompat.content.res.AppCompatResources.getColorStateList(
                    button.getContext(), R.color.f7_photo_button_fg));
        }

        message = new TextView(requireContext());
        message.setId(R.id.incident_form_message);
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        formContainer.addView(message, index + 2);
        showMessage("Ảnh xem trước được giữ trên thiết bị.");
        ResidentMainActivity host = (ResidentMainActivity) requireActivity();
        photoBridge = host.photoBridge();
        host.setPhotoListener(new IncidentPhotoUiBridge.Listener() {
            @Override public void onBusy(boolean busy) {
                photoViewBinder.onBusy(busy);
                renderBusy();
            }
            @Override public void onPhotoReady(IncidentPhotoProcessor.PreparedPhoto photo) {
                cvPhotoPreview.setVisibility(View.VISIBLE);
                photoViewBinder.onPhotoReady(photo);
                if (savedIncident == null) showMessage("Ảnh xem trước được giữ trên thiết bị.");
            }
            @Override public void onPhotoCleared() {
                cvPhotoPreview.setVisibility(View.GONE);
                photoViewBinder.onPhotoCleared();
                if (savedIncident == null) showMessage("Ảnh xem trước được giữ trên thiết bị.");
            }
            @Override public void onCancelled(IncidentPhotoController.Source source) {
                if (photoViewBinder != null) photoViewBinder.onCancelled(source);
            }
            @Override public void onPickerError(IncidentPhotoController.Source source, IncidentPhotoController.Error error) {
                CreateIncidentFragment.this.onPickerError(source, error);
            }
            @Override public void onProcessingError(IncidentPhotoProcessor.Failure error) {
                CreateIncidentFragment.this.onProcessingError(error);
            }
        });

        photoViewBinder = new IncidentPhotoViewBinder(
                btnCaptureDynamic, btnGalleryDynamic, btnRemovePhoto, ivCapturedPhoto, null, this
        );
        photoViewBinder.attach(photoBridge);
        cvPhotoPreview.setVisibility(photoBridge.currentPhoto() == null ? View.GONE : View.VISIBLE);
        if (rgPriority.getCheckedRadioButtonId() == View.NO_ID) rgPriority.check(R.id.rb_priority_normal);
        refreshPhotoAccess();

        if (savedIncident != null) {
            showMessage(savedInstanceState.getString("evidence_message", "Báo cáo đã lưu. Có thể gửi lại ảnh."));
        }
        btnSubmit.setOnClickListener(v -> {
            if (savedIncident != null) uploadEvidence();
            else submitIncident();
        });
        FirebaseAuth.getInstance().addAuthStateListener(authListener);

        return view;
    }

    private void submitIncident() {
        if (savedIncident != null || submitting || photoBridge.isBusy() || !btnSubmit.isEnabled()) return;
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String location = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        String desc = etDesc.getText() != null ? etDesc.getText().toString().trim() : "";
        String priority = "NORMAL";
        int checkedId = rgPriority.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_priority_urgent) {
            priority = "URGENT";
        } else if (checkedId == R.id.rb_priority_low) {
            priority = "LOW";
        }

        coordinator.submit(title, desc, location, priority);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("evidence_incident", savedIncident);
        state.putString("evidence_owner", savedOwner);
        state.putString("evidence_draft_owner", draftOwner);
        state.putBoolean("evidence_retry", retryPhoto);
        if (message != null && savedIncident != null) state.putString("evidence_message",
                uploading ? "Báo cáo đã lưu. Chưa xác nhận ảnh; có thể thử lại." : message.getText().toString());
    }

    @Override
    public void onDestroyView() {
        FirebaseAuth.getInstance().removeAuthStateListener(authListener);
        if (evidence != null) evidence.close();
        evidence = null;
        uploading = false;
        super.onDestroyView();
        if (coordinator != null) coordinator.close();
        ((ResidentMainActivity) requireActivity()).setPhotoListener(null);
        photoViewBinder = null;
        photoBridge = null;
    }

    @Override
    public void onBusy(boolean busy) {
        submitting = busy;
        if (busy) showMessage("Đang gửi báo cáo…");
        renderBusy();
    }

    boolean isSubmitting() { return submitting || uploading; }

    void refreshPhotoAccess() {
        if (photoViewBinder != null) renderBusy();
    }

    private void renderBusy() {
        boolean busy = submitting || uploading || photoBridge.isBusy();
        boolean verified = ((ResidentMainActivity) requireActivity()).isResidentVerified()
                && draftOwner != null && draftOwner.equals(currentUid());
        boolean persisted = savedIncident != null;
        photoViewBinder.setResidentVerified(verified && !busy && !persisted);
        etTitle.setEnabled(!busy && !persisted);
        etLocation.setEnabled(!busy && !persisted);
        etDesc.setEnabled(!busy && !persisted);
        for (int i = 0; i < rgPriority.getChildCount(); i++) {
            rgPriority.getChildAt(i).setEnabled(!busy && !persisted);
        }
        pbLoading.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (persisted) btnSubmit.setText("Thử gửi lại ảnh");
        btnSubmit.setEnabled(verified && !busy && (!persisted
                || (retryPhoto && savedOwner != null && savedOwner.equals(currentUid())
                    && photoBridge.currentUri() != null)));
    }

    private void showMessage(String text) { message.setText(text); }

    @Override
    public void onList(List<IncidentSnapshot> incidents) {}

    @Override
    public void onDetail(IncidentSnapshot incident, List<IncidentHistorySnapshot> history) {}

    @Override
    public void onSaved(IncidentSnapshot incident) {
        savedIncident = incident.getId();
        savedOwner = incident.getResidentId();
        if (photoBridge.currentUri() == null) {
            Toast.makeText(requireContext(), "Đã lưu báo cáo.", Toast.LENGTH_SHORT).show();
            ((ResidentMainActivity) requireActivity()).switchToHome();
            return;
        }
        uploadEvidence();
    }

    private String currentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : user.getUid();
    }

    private void uploadEvidence() {
        if (uploading || savedIncident == null || !retryPhoto
                || savedOwner == null || !savedOwner.equals(currentUid())) return;
        android.net.Uri uri = photoBridge.currentUri();
        if (uri == null) {
            showMessage("Báo cáo đã lưu. Ảnh xem trước không còn trên thiết bị.");
            renderBusy();
            return;
        }
        if (evidence == null) evidence = new SupabaseEvidenceClient(requireContext());
        uploading = true;
        showMessage("Báo cáo đã lưu. Đang gửi ảnh…");
        renderBusy();
        evidence.upload(savedIncident, SupabaseEvidenceClient.Kind.REPORTED, uri,
                new SupabaseEvidenceClient.Callback<SupabaseEvidenceClient.Evidence>() {
            @Override public void onSuccess(SupabaseEvidenceClient.Evidence data) {
                uploading = false;
                retryPhoto = false;
                Toast.makeText(requireContext(), "Đã lưu báo cáo và ảnh.", Toast.LENGTH_SHORT).show();
                ((ResidentMainActivity) requireActivity()).switchToHome();
            }

            @Override public void onError(SupabaseEvidenceClient.Error error) {
                uploading = false;
                retryPhoto = EvidenceUiText.canRetry(error);
                showMessage("Báo cáo đã lưu. " + EvidenceUiText.error(error));
                renderBusy();
            }
        });
    }

    @Override
    public void onError(IncidentRepository.Error error) {
        showMessage(IncidentUiText.error(error));
    }

    @Override
    public void onPickerError(IncidentPhotoController.Source source, IncidentPhotoController.Error error) {
        showMessage("Không thể mở hoặc nhận ảnh. Vui lòng thử lại.");
    }

    @Override
    public void onProcessingError(IncidentPhotoProcessor.Failure error) {
        showMessage("Ảnh không hợp lệ hoặc không đọc được. Hãy chọn ảnh khác.");
    }
}

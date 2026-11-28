package com.example.baocaosuco.resident;

import android.os.Bundle;
import android.util.AtomicFile;
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
import com.example.baocaosuco.data.incident.NewIncidentInput;
import com.example.baocaosuco.data.repository.FirebaseIncidentRepository;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.resident.photo.IncidentPhotoController;
import com.example.baocaosuco.resident.photo.IncidentPhotoProcessor;
import com.example.baocaosuco.resident.photo.IncidentPhotoUiBridge;
import com.example.baocaosuco.resident.photo.IncidentPhotoViewBinder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;
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
    private String submission;
    private NewIncidentInput submittedInput;
    private boolean draftUnreadable;
    private AtomicFile draftFile;
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
        if (draftOwner != null && draftOwner.equals(currentUid()) && savedIncident == null) {
            File privateDir = requireContext().getNoBackupFilesDir();
            draftFile = new AtomicFile(new File(privateDir, "resident-submit-" + android.net.Uri.encode(draftOwner)));
            restoreSubmission();
        }

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
        } else if (draftUnreadable) {
            showMessage("Không thể đọc báo cáo đang chờ trên thiết bị. Hãy mở lại màn hình để thử lại.");
        } else if (submission != null) {
            showMessage("Có báo cáo chưa xác nhận. Nhấn gửi lại để tiếp tục cùng báo cáo.");
        }
        btnSubmit.setOnClickListener(v -> {
            if (savedIncident != null) uploadEvidence();
            else submitIncident();
        });
        FirebaseAuth.getInstance().addAuthStateListener(authListener);

        return view;
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle state) {
        super.onViewStateRestored(state);
        if (submittedInput == null || !draftOwner.equals(currentUid())) return;
        etTitle.setText(submittedInput.getTitle());
        etLocation.setText(submittedInput.getLocation());
        etDesc.setText(submittedInput.getDescription());
        int priority = R.id.rb_priority_normal;
        if ("URGENT".equals(submittedInput.getPriority())) priority = R.id.rb_priority_urgent;
        else if ("LOW".equals(submittedInput.getPriority())) priority = R.id.rb_priority_low;
        rgPriority.check(priority);
    }

    private void submitIncident() {
        if (savedIncident != null || submitting || photoBridge.isBusy() || !btnSubmit.isEnabled()) return;
        if (draftFile == null || draftUnreadable || !draftOwner.equals(currentUid())) return;
        if (submittedInput != null) {
            coordinator.submit(submittedInput, submission);
            return;
        }
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

        final NewIncidentInput input;
        try {
            input = new NewIncidentInput(title, desc, location, priority);
        } catch (IllegalArgumentException invalid) {
            onError(IncidentRepository.Error.VALIDATION);
            return;
        }
        String id = UUID.randomUUID().toString();
        if (!persistSubmission(id, input)) {
            showMessage("Không thể lưu báo cáo đang chờ trên thiết bị. Hãy thử lại.");
            return;
        }
        submission = id;
        submittedInput = input;
        coordinator.submit(input, id);
    }

    private void restoreSubmission() {
        try {
            JSONObject draft = new JSONObject(new String(draftFile.readFully(), StandardCharsets.UTF_8));
            String id = UUID.fromString(draft.getString("id")).toString();
            NewIncidentInput input = new NewIncidentInput(draft.getString("title"), draft.getString("description"),
                    draft.getString("location"), draft.getString("priority"));
            submission = id;
            submittedInput = input;
        } catch (FileNotFoundException absent) {
            draftUnreadable = draftFile.getBaseFile().exists();
        } catch (IOException | JSONException | IllegalArgumentException invalid) {
            draftUnreadable = true;
        }
    }

    private boolean persistSubmission(String id, NewIncidentInput input) {
        FileOutputStream stream = null;
        try {
            JSONObject draft = new JSONObject().put("id", id).put("title", input.getTitle())
                    .put("description", input.getDescription()).put("location", input.getLocation())
                    .put("priority", input.getPriority());
            byte[] expected = draft.toString().getBytes(StandardCharsets.UTF_8);
            stream = draftFile.startWrite();
            stream.write(expected);
            draftFile.finishWrite(stream);
            stream = null;
            return Arrays.equals(expected, draftFile.readFully());
        } catch (IOException | JSONException failure) {
            if (stream != null) draftFile.failWrite(stream);
            return false;
        }
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
        boolean editable = verified && !busy && !persisted && submission == null && !draftUnreadable;
        photoViewBinder.setResidentVerified(verified && !busy && !persisted);
        etTitle.setEnabled(editable);
        etLocation.setEnabled(editable);
        etDesc.setEnabled(editable);
        for (int i = 0; i < rgPriority.getChildCount(); i++) {
            rgPriority.getChildAt(i).setEnabled(editable);
        }
        pbLoading.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (persisted) btnSubmit.setText("Thử gửi lại ảnh");
        else if (submission != null) btnSubmit.setText("Thử gửi lại báo cáo");
        btnSubmit.setEnabled(verified && !busy && !draftUnreadable && (!persisted
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
        if (submission != null && submission.equals(savedIncident) && draftOwner.equals(savedOwner)) {
            draftFile.delete();
        }
        submission = null;
        submittedInput = null;
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
        showMessage(IncidentUiText.error(error)
                + (submission == null ? "" : " Báo cáo chưa được xác nhận. Hãy thử lại với cùng nội dung."));
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

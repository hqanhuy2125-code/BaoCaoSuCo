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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.baocaosuco.R;
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

        coordinator = new ResidentIncidentCoordinator(new FirebaseIncidentRepository().resident(), this);

        LinearLayout formContainer = (LinearLayout) ((ViewGroup) cvPhotoPreview.getParent());
        int index = formContainer.indexOfChild(cvPhotoPreview);

        MaterialButton btnCaptureDynamic = new MaterialButton(requireContext());
        btnCaptureDynamic.setText("📷 Chụp Ảnh Sự Cố");
        formContainer.addView(btnCaptureDynamic, index);

        MaterialButton btnGalleryDynamic = new MaterialButton(requireContext());
        btnGalleryDynamic.setText("🖼 Chọn Ảnh Từ Thư Viện");
        formContainer.addView(btnGalleryDynamic, index + 1);

        photoBridge = new IncidentPhotoUiBridge(requireActivity(), new IncidentPhotoUiBridge.Listener() {
            @Override public void onBusy(boolean busy) { pbLoading.setVisibility(busy ? View.VISIBLE : View.GONE); }
            @Override public void onPhotoReady(IncidentPhotoProcessor.PreparedPhoto photo) {
                if (photoViewBinder != null) photoViewBinder.onPhotoReady(photo);
            }
            @Override public void onPhotoCleared() {
                if (photoViewBinder != null) photoViewBinder.onPhotoCleared();
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
                btnCaptureDynamic, btnGalleryDynamic, btnRemovePhoto, ivCapturedPhoto, pbLoading, this
        );
        photoViewBinder.attach(photoBridge);
        photoViewBinder.setResidentVerified(true);

        btnSubmit.setOnClickListener(v -> submitIncident());

        return view;
    }

    private void submitIncident() {
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
    public void onDestroyView() {
        super.onDestroyView();
        if (coordinator != null) coordinator.close();
        if (photoBridge != null) photoBridge.close();
    }

    @Override
    public void onBusy(boolean busy) {
        pbLoading.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(!busy);
    }

    @Override
    public void onList(List<IncidentSnapshot> incidents) {}

    @Override
    public void onDetail(IncidentSnapshot incident, List<IncidentHistorySnapshot> history) {}

    @Override
    public void onSaved(IncidentSnapshot incident) {
        Toast.makeText(requireContext(), "Gửi báo cáo thành công!", Toast.LENGTH_SHORT).show();
        if (requireActivity() instanceof ResidentMainActivity) {
            ((ResidentMainActivity) requireActivity()).switchToHome();
        }
    }

    @Override
    public void onError(IncidentRepository.Error error) {
        Toast.makeText(requireContext(), "Lỗi gửi báo cáo: " + error, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPickerError(IncidentPhotoController.Source source, IncidentPhotoController.Error error) {
        Toast.makeText(requireContext(), "Lỗi chọn ảnh: " + error, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProcessingError(IncidentPhotoProcessor.Failure error) {
        Toast.makeText(requireContext(), "Lỗi xử lý ảnh: " + error, Toast.LENGTH_SHORT).show();
    }
}

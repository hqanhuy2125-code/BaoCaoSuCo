package com.example.baocaosuco.resident.photo;

import android.view.View;
import android.widget.ImageView;

/**
 * G3: Java-only view binder for existing Phuc B01 XML; NO R.id assumptions.
 * The caller passes actual Views from an approved layout after setContentView().
 * Buttons remain disabled until the existing Resident Auth role gate passes.
 */
public final class IncidentPhotoViewBinder
        implements IncidentPhotoUiBridge.Listener {

    public interface ErrorHandler {
        void onPickerError(IncidentPhotoController.Source source,
                           IncidentPhotoController.Error error);
        void onProcessingError(IncidentPhotoProcessor.Failure error);
    }

    private final View captureButton;
    private final View galleryButton;
    private final View removeButton;
    private final ImageView previewImage;
    private final View busyIndicator;
    private final ErrorHandler errors;

    private IncidentPhotoUiBridge bridge;
    private boolean residentVerified;
    private boolean busy;

    /**
     * busyIndicator and errors may be null when B01 already owns those UI states.
     * All controls except busyIndicator must exist in the supplied approved XML.
     */
    public IncidentPhotoViewBinder(View captureButton, View galleryButton,
            View removeButton, ImageView previewImage,
            View busyIndicator, ErrorHandler errors) {
        if (captureButton == null || galleryButton == null
                || removeButton == null || previewImage == null) {
            throw new IllegalArgumentException("B01 photo views must be provided");
        }
        this.captureButton = captureButton;
        this.galleryButton = galleryButton;
        this.removeButton = removeButton;
        this.previewImage = previewImage;
        this.busyIndicator = busyIndicator;
        this.errors = errors;
        this.captureButton.setOnClickListener(v -> {
            if (canInteract()) bridge.capture();
        });
        this.galleryButton.setOnClickListener(v -> {
            if (canInteract()) bridge.pickFromGallery();
        });
        this.removeButton.setOnClickListener(v -> {
            if (canInteract() && bridge.clearSelected()) redraw(null);
        });
        redraw(null);
        refreshEnabled();
    }

    /** Call once after constructing bridge in Activity.onCreate(). */
    public void attach(IncidentPhotoUiBridge bridge) {
        if (bridge == null || this.bridge != null) {
            throw new IllegalArgumentException("Photo bridge must be attached once");
        }
        this.bridge = bridge;
        busy = bridge.isBusy();
        redraw(bridge.currentPhoto());
        refreshEnabled();
    }

    /** Invoke only when the existing repository/Auth role check confirms RESIDENT. */
    public void setResidentVerified(boolean verified) {
        residentVerified = verified;
        refreshEnabled();
    }

    private boolean canInteract() {
        return bridge != null && residentVerified && !busy;
    }

    private void redraw(IncidentPhotoProcessor.PreparedPhoto photo) {
        if (photo == null) {
            previewImage.setImageDrawable(null);
            previewImage.setVisibility(View.GONE);
            removeButton.setVisibility(View.GONE);
        } else {
            previewImage.setImageURI(photo.getUri());
            previewImage.setVisibility(View.VISIBLE);
            removeButton.setVisibility(View.VISIBLE);
        }
        refreshEnabled();
    }

    private void refreshEnabled() {
        boolean enabled = canInteract();
        captureButton.setEnabled(enabled);
        galleryButton.setEnabled(enabled);
        removeButton.setEnabled(enabled && bridge.currentUri() != null);
        if (busyIndicator != null) {
            busyIndicator.setVisibility(busy ? View.VISIBLE : View.GONE);
        }
    }

    @Override public void onBusy(boolean busy) {
        this.busy = busy;
        refreshEnabled();
    }

    @Override public void onPhotoReady(
            IncidentPhotoProcessor.PreparedPhoto photo) {
        redraw(photo);
    }

    @Override public void onPhotoCleared() {
        redraw(null);
    }

    @Override public void onCancelled(IncidentPhotoController.Source source) {
        // Huy B01: Cancel must preserve previous preview.
    }

    @Override public void onPickerError(IncidentPhotoController.Source source,
                                        IncidentPhotoController.Error error) {
        if (errors != null) errors.onPickerError(source, error);
    }

    @Override public void onProcessingError(IncidentPhotoProcessor.Failure error) {
        if (errors != null) errors.onProcessingError(error);
    }
}

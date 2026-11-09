package com.example.baocaosuco.resident.photo;

import android.content.ActivityNotFoundException;
import android.net.Uri;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

/**
 * C01 G1 - reusable Resident Camera/Gallery launcher, Java only.
 *
 * Register unconditionally from Activity.onCreate() BEFORE the Activity is STARTED,
 * in a stable order on every recreation. Launch only after the existing role gate
 * has confirmed RESIDENT. No UI/XML, Firebase or new runtime permission is owned here.
 *
 * IMPORTANT: full-size Camera requires a reviewed FileProvider declaration:
 * authority `<applicationId>.incidentphoto.fileprovider`, with cache-path
 * `incident_camera/`. Without that approved Manifest/XML, capture() reports
 * PROVIDER_NOT_CONFIGURED; pickFromGallery() is independent of this requirement.
 */
public final class IncidentPhotoController {
    public enum Source { CAMERA, GALLERY }

    public enum Error {
        PROVIDER_NOT_CONFIGURED,
        STORAGE_ERROR,
        CAMERA_UNAVAILABLE,
        GALLERY_UNAVAILABLE,
        EMPTY_CAPTURE,
        INVALID_URI
    }

    public interface Listener {
        /** URI may be used for immediate preview. Gallery URI must be copied in G2. */
        void onSelected(Uri uri, Source source);
        void onCancelled(Source source);
        void onError(Source source, Error reason);
    }

    private static final String CAMERA_CACHE_FOLDER = "incident_camera";
    private static final String PROVIDER_SUFFIX = ".incidentphoto.fileprovider";

    private final ComponentActivity activity;
    private final Listener listener;
    private final IncidentPhotoRequestState state = new IncidentPhotoRequestState();
    private final ActivityResultLauncher<Uri> cameraLauncher;
    private final ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher;

    private File pendingCameraFile;
    private Uri pendingCameraUri;

    /**
     * Call once in onCreate() at a stable point; registerForActivityResult must
     * not be delayed until a Firebase asynchronous role callback returns.
     */
    public IncidentPhotoController(ComponentActivity activity, Listener listener) {
        if (activity == null || listener == null) {
            throw new IllegalArgumentException("Activity and Listener are required");
        }
        this.activity = activity;
        this.listener = listener;

        cameraLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                result -> onCameraResult(Boolean.TRUE.equals(result))
        );
        galleryLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                this::onGalleryResult
        );
    }

    /** Starts full-resolution capture; requires approved FileProvider app config. */
    public boolean capture() {
        if (!state.begin(IncidentPhotoRequestState.Operation.CAMERA)) return false;

        try {
            File directory = new File(activity.getCacheDir(), CAMERA_CACHE_FOLDER);
            if (!directory.isDirectory() && !directory.mkdirs()) {
                deliverError(IncidentPhotoRequestState.Operation.CAMERA, Source.CAMERA,
                        Error.STORAGE_ERROR);
                return false;
            }
            pendingCameraFile = File.createTempFile("incident_", ".jpg", directory);
        } catch (IOException | SecurityException error) {
            deletePendingCamera();
            deliverError(IncidentPhotoRequestState.Operation.CAMERA, Source.CAMERA,
                    Error.STORAGE_ERROR);
            return false;
        }

        try {
            pendingCameraUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + PROVIDER_SUFFIX,
                    pendingCameraFile
            );
        } catch (IllegalArgumentException | IllegalStateException error) {
            deletePendingCamera();
            deliverError(IncidentPhotoRequestState.Operation.CAMERA, Source.CAMERA,
                    Error.PROVIDER_NOT_CONFIGURED);
            return false;
        }

        try {
            cameraLauncher.launch(pendingCameraUri);
            return true;
        } catch (ActivityNotFoundException | SecurityException error) {
            deletePendingCamera();
            deliverError(IncidentPhotoRequestState.Operation.CAMERA, Source.CAMERA,
                    Error.CAMERA_UNAVAILABLE);
            return false;
        } catch (RuntimeException error) {
            deletePendingCamera();
            deliverError(IncidentPhotoRequestState.Operation.CAMERA, Source.CAMERA,
                    Error.CAMERA_UNAVAILABLE);
            return false;
        }
    }

    /** Opens system Photo Picker for ONE image, with AndroidX fallback on old devices. */
    public boolean pickFromGallery() {
        if (!state.begin(IncidentPhotoRequestState.Operation.GALLERY)) return false;
        try {
            PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build();
            galleryLauncher.launch(request);
            return true;
        } catch (RuntimeException error) {
            deliverError(IncidentPhotoRequestState.Operation.GALLERY, Source.GALLERY,
                    Error.GALLERY_UNAVAILABLE);
            return false;
        }
    }

    private void onCameraResult(boolean success) {
        if (!state.finish(IncidentPhotoRequestState.Operation.CAMERA)) return;

        File file = pendingCameraFile;
        Uri uri = pendingCameraUri;
        pendingCameraFile = null;
        pendingCameraUri = null;

        if (!success) {
            deleteOwnedFile(file);
            listener.onCancelled(Source.CAMERA);
            return;
        }
        if (file == null || uri == null || !file.isFile() || file.length() <= 0L) {
            deleteOwnedFile(file);
            listener.onError(Source.CAMERA, Error.EMPTY_CAPTURE);
            return;
        }
        // Ownership of this successful temporary file transfers to the G2 store/consumer.
        listener.onSelected(uri, Source.CAMERA);
    }

    private void onGalleryResult(Uri uri) {
        if (!state.finish(IncidentPhotoRequestState.Operation.GALLERY)) return;
        if (uri == null) {
            listener.onCancelled(Source.GALLERY);
        } else if (!"content".equalsIgnoreCase(uri.getScheme())) {
            listener.onError(Source.GALLERY, Error.INVALID_URI);
        } else {
            // Do NOT persist the URI: copy its contents to app-private storage in G2.
            listener.onSelected(uri, Source.GALLERY);
        }
    }

    private void deliverError(IncidentPhotoRequestState.Operation operation,
                              Source source, Error error) {
        if (state.finish(operation)) listener.onError(source, error);
    }

    private void deletePendingCamera() {
        deleteOwnedFile(pendingCameraFile);
        pendingCameraFile = null;
        pendingCameraUri = null;
    }

    private void deleteOwnedFile(File file) {
        if (file == null) return;
        // Only this module's pending temp files are deleted here.
        File dir = new File(activity.getCacheDir(), CAMERA_CACHE_FOLDER);
        if (dir.equals(file.getParentFile()) && file.exists()) {
            // Failed deletion is a cache cleanup issue; never delete outside this folder.
            file.delete();
        }
    }

    public boolean isBusy() {
        return state.isBusy();
    }

    /**
     * Must be called from Activity.onDestroy()/logout. Callbacks after close are ignored.
     * Rotation/process-death restoration of an in-flight capture is NOT part of G1;
     * G3 will verify and close that lifecycle gate before app-level acceptance.
     */
    public void close() {
        if (state.isClosed()) return;
        state.close();
        cameraLauncher.unregister();
        galleryLauncher.unregister();
        deletePendingCamera();
    }
}

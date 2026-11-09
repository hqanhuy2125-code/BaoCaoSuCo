package com.example.baocaosuco.resident.photo;

import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.savedstate.SavedStateRegistry;

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
    private static final String STATE_KEY = "c01.photo.camera.pending";
    private static final String SAVED_OPERATION = "operation";
    private static final String SAVED_URI = "uri";

    private final ComponentActivity activity;
    private final Listener listener;
    private final IncidentPhotoStore store;
    private final SavedStateRegistry savedStateRegistry;
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
        this.store = new IncidentPhotoStore(activity);
        this.savedStateRegistry = activity.getSavedStateRegistry();

        // Goi sau super.onCreate(), truoc STARTED. URI pending duoc giu khi xoay man hinh.
        restorePending(savedStateRegistry.consumeRestoredStateForKey(STATE_KEY));
        savedStateRegistry.registerSavedStateProvider(STATE_KEY, this::savePending);

        cameraLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                result -> onCameraResult(Boolean.TRUE.equals(result))
        );
        galleryLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                this::onGalleryResult
        );
    }

    private void restorePending(Bundle saved) {
        if (saved == null) return;
        String operation = saved.getString(SAVED_OPERATION);
        if ("CAMERA".equals(operation)) {
            state.begin(IncidentPhotoRequestState.Operation.CAMERA);
            String raw = saved.getString(SAVED_URI);
            if (raw != null) {
                try {
                    Uri uri = Uri.parse(raw);
                    File owned = store.resolveCaptured(uri);
                    if (owned != null) {
                        pendingCameraFile = owned;
                        pendingCameraUri = uri;
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid restoration yields EMPTY_CAPTURE when Camera returns.
                }
            }
        } else if ("GALLERY".equals(operation)) {
            state.begin(IncidentPhotoRequestState.Operation.GALLERY);
        }
    }

    private Bundle savePending() {
        Bundle saved = new Bundle();
        if (state.isPending(IncidentPhotoRequestState.Operation.CAMERA)) {
            saved.putString(SAVED_OPERATION, "CAMERA");
            if (pendingCameraUri != null) {
                saved.putString(SAVED_URI, pendingCameraUri.toString());
            }
        } else if (state.isPending(IncidentPhotoRequestState.Operation.GALLERY)) {
            saved.putString(SAVED_OPERATION, "GALLERY");
        }
        return saved;
    }

    /** Starts full-resolution capture; requires the scoped FileProvider app config. */
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
     * Call from Activity.onDestroy()/logout. On configuration changes the captured
     * file survives for the next ActivityResult launcher; on real exit it is removed.
     * Device/process-death behavior still requires G3 Android verification.
     */
    public void close() {
        if (state.isClosed()) return;
        boolean keepCameraFile = activity.isChangingConfigurations()
                && state.isPending(IncidentPhotoRequestState.Operation.CAMERA);
        state.close();
        cameraLauncher.unregister();
        galleryLauncher.unregister();
        savedStateRegistry.unregisterSavedStateProvider(STATE_KEY);
        if (keepCameraFile) {
            pendingCameraFile = null;
            pendingCameraUri = null;
        } else {
            deletePendingCamera();
        }
    }
}

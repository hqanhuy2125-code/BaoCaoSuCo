package com.example.baocaosuco.resident.photo;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

/**
 * C01 local-only cache. Files are not incident attachments and may be evicted
 * by Android. The provider exposes ONLY the incident_camera/ cache subfolder.
 */
public final class IncidentPhotoStore {
    private static final String FOLDER = "incident_camera";
    private static final String AUTHORITY_SUFFIX = ".incidentphoto.fileprovider";
    private final Context appContext;

    public IncidentPhotoStore(Context context) {
        if (context == null) throw new IllegalArgumentException("Context required");
        appContext = context.getApplicationContext();
    }

    File newStagingFile() throws IOException {
        return File.createTempFile("incident_processing_", ".tmp", ensureDirectory());
    }

    File newPreparedFile() throws IOException {
        return File.createTempFile("incident_ready_", ".jpg", ensureDirectory());
    }

    Uri uriFor(File file) {
        return FileProvider.getUriForFile(
                appContext, appContext.getPackageName() + AUTHORITY_SUFFIX, file);
    }

    /**
     * Delete only an output created by this module. A foreign content URI,
     * or a path outside the approved cache folder, can never be deleted.
     */
    public boolean deletePrepared(Uri uri) {
        File file = resolveOwnedFile(uri, "incident_ready_", ".jpg");
        return file != null && (!file.exists() || file.delete());
    }

    /** Remove temporary full-size Camera source AFTER G2 has consumed it. */
    boolean deleteCaptured(Uri uri) {
        File file = resolveOwnedFile(uri, "incident_", ".jpg");
        if (file == null || file.getName().startsWith("incident_ready_")) return false;
        return !file.exists() || file.delete();
    }

    void deleteInternal(File file) {
        if (file == null) return;
        try {
            if (ensureDirectory().getCanonicalFile()
                    .equals(file.getCanonicalFile().getParentFile())) {
                file.delete();
            }
        } catch (IOException ignored) {
            // Cache cleanup failure is not permission to delete elsewhere.
        }
    }

    private File resolveOwnedFile(Uri uri, String prefix, String suffix) {
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())
                || !(appContext.getPackageName() + AUTHORITY_SUFFIX)
                .equals(uri.getAuthority())) return null;
        String name = uri.getLastPathSegment();
        if (name == null || !name.startsWith(prefix) || !name.endsWith(suffix)
                || name.contains("/") || name.contains("\\")
                || name.contains("..")) return null;
        try {
            File file = new File(ensureDirectory(), name).getCanonicalFile();
            if (!ensureDirectory().getCanonicalFile().equals(file.getParentFile())) {
                return null;
            }
            return uri.equals(uriFor(file)) ? file : null;
        } catch (IOException | IllegalArgumentException | IllegalStateException error) {
            return null;
        }
    }

    private File ensureDirectory() throws IOException {
        File directory = new File(appContext.getCacheDir(), FOLDER);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Unable to create incident photo cache");
        }
        return directory;
    }
}

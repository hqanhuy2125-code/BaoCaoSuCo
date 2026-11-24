package com.example.baocaosuco.resident.photo;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import javax.net.ssl.HttpsURLConnection;

/**
 * C01 shared-evidence network adapter. Does not replace Firestore IncidentRepository.
 * Client obtains the CURRENT Firebase ID token for each request. Supabase secret keys,
 * trusted role checks and the Firestore authorization belong only to the server.
 *
 * Construct from an Android Activity/Fragment owner (app context is retained).
 * Call close() on logout / screen destruction; UI callbacks run on the main thread.
 * Until the independent L3 security gate is approved, the server returns
 * EVIDENCE_GATE_NOT_APPROVED (503). Never fake upload completion in the UI.
 */
public final class SupabaseEvidenceClient {
    public enum Kind { REPORTED, RESOLUTION }

    public enum Error {
        AUTH_REQUIRED, CONFIG_MISMATCH, VALIDATION, TOO_LARGE,
        PERMISSION_DENIED, NOT_FOUND, CONFLICT, GATE_DISABLED,
        NETWORK, SERVER_ERROR
    }

    public interface Callback<T> {
        void onSuccess(T data);
        void onError(Error error);
    }

    public static final class Evidence {
        private final String id;
        private final String incidentId;
        private final Kind kind;
        private final String uploadedBy;
        private final int bytes;
        private final String createdAt;

        private Evidence(String id, String incidentId, Kind kind,
                         String uploadedBy, int bytes, String createdAt) {
            this.id = id;
            this.incidentId = incidentId;
            this.kind = kind;
            this.uploadedBy = uploadedBy;
            this.bytes = bytes;
            this.createdAt = createdAt;
        }

        public String getId() { return id; }
        public String getIncidentId() { return incidentId; }
        public Kind getKind() { return kind; }
        public String getUploadedBy() { return uploadedBy; }
        public int getBytes() { return bytes; }
        public String getCreatedAt() { return createdAt; }
    }

    private static final String FIREBASE_PROJECT_ID = "baocaosuco-c4ccc";
    private static final String GATEWAY =
            "https://xnmlnqaetcaawvsbmhjy.supabase.co/functions/v1/evidence-gateway";
    private static final String PHOTO_AUTHORITY = ".incidentphoto.fileprovider";
    private static final String PHOTO_PREFIX = "/incident_camera/incident_ready_";
    private static final int NETWORK_TIMEOUT_MS = 15000;
    private static final int MAX_JSON_BYTES = 65536;

    private final Context appContext;
    private final FirebaseAuth auth;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;

    public SupabaseEvidenceClient(Context context) {
        this(context, FirebaseAuth.getInstance());
    }

    public SupabaseEvidenceClient(Context context, FirebaseAuth auth) {
        if (context == null || auth == null) {
            throw new IllegalArgumentException("Context and FirebaseAuth required");
        }
        this.appContext = context.getApplicationContext();
        this.auth = auth;
    }

    private interface Work<T> {
        T run(String token, String uid) throws Exception;
    }

    /**
     * Callback is asynchronous; a return value of true means queued, not uploaded.
     * Use the incident ID returned from the successfully persisted Firestore report.
     * Only accepts processed JPEGs from C01's own FileProvider, never arbitrary
     * gallery URIs or user-controlled URL paths.
     */
    public boolean upload(String incidentId, Kind kind, Uri processedJpeg,
                          Callback<Evidence> callback) {
        if (kind == null || processedJpeg == null || !isOwnPreparedUri(processedJpeg)) {
            fail(callback, Error.VALIDATION);
            return false;
        }
        return submit(incidentId, callback, (token, uid) -> {
            byte[] jpeg = readProcessedJpeg(processedJpeg);
            JSONObject root = requestJson("POST",
                    "?incidentId=" + incidentId + "&kind=" + kind.name(),
                    token, jpeg);
            return parseEvidence(root.getJSONObject("item"), incidentId);
        });
    }

    /** Reads only metadata. The images remain private until separately downloaded. */
    public boolean list(String incidentId, Callback<List<Evidence>> callback) {
        return submit(incidentId, callback, (token, uid) -> {
            JSONObject root = requestJson("GET", "?incidentId=" + incidentId, token, null);
            JSONArray items = root.getJSONArray("items");
            List<Evidence> list = new ArrayList<>();
            if (items.length() > 2) throw new IOException("Unexpected evidence count");
            for (int i = 0; i < items.length(); i++) {
                list.add(parseEvidence(items.getJSONObject(i), incidentId));
            }
            return Collections.unmodifiableList(list);
        });
    }

    /**
     * Downloads one image after the server checks the CURRENT Firebase user.
     * The resulting file:// URI is private to this app; it is not a shareable URL.
     */
    public boolean download(String incidentId, String evidenceId, Callback<Uri> callback) {
        if (!IncidentEvidenceRequestPolicy.validEvidenceId(evidenceId)) {
            fail(callback, Error.VALIDATION);
            return false;
        }
        return submit(incidentId, callback, (token, uid) -> {
            byte[] jpeg = requestBytes("?incidentId=" + incidentId +
                    "&photoId=" + evidenceId, token);
            ensureSession(uid);
            File dir = ensureDownloadDir();
            File output = File.createTempFile("evidence_", ".jpg", dir);
            boolean complete = false;
            try {
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    stream.write(jpeg);
                }
                ensureSession(uid);
                complete = true;
                return Uri.fromFile(output);
            } finally {
                if (!complete) output.delete();
            }
        });
    }

    private boolean isOwnPreparedUri(Uri uri) {
        return "content".equalsIgnoreCase(uri.getScheme())
                && (appContext.getPackageName() + PHOTO_AUTHORITY).equals(uri.getAuthority())
                && uri.getPath() != null
                && uri.getPath().startsWith(PHOTO_PREFIX)
                && uri.getLastPathSegment() != null
                && uri.getLastPathSegment().startsWith("incident_ready_")
                && uri.getLastPathSegment().endsWith(".jpg");
    }

    private <T> boolean submit(String incidentId, Callback<T> callback, Work<T> work) {
        if (callback == null) throw new IllegalArgumentException("Callback required");
        if (closed) return false;
        if (!IncidentEvidenceRequestPolicy.validIncidentId(incidentId)) {
            fail(callback, Error.VALIDATION);
            return false;
        }
        String actualProject;
        try {
            actualProject = FirebaseApp.getInstance().getOptions().getProjectId();
        } catch (IllegalStateException failure) {
            fail(callback, Error.CONFIG_MISMATCH);
            return false;
        }
        if (!FIREBASE_PROJECT_ID.equals(actualProject)) {
            fail(callback, Error.CONFIG_MISMATCH);
            return false;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            fail(callback, Error.AUTH_REQUIRED);
            return false;
        }
        final String actor = user.getUid();
        user.getIdToken(false).addOnCompleteListener(task -> {
            if (closed || !sameSession(actor)) return;
            String token = task.isSuccessful() && task.getResult() != null
                    ? task.getResult().getToken() : null;
            if (token == null || token.isEmpty()) {
                fail(callback, Error.AUTH_REQUIRED);
                return;
            }
            try {
                worker.execute(() -> {
                    T result = null;
                    Error failure = null;
                    try {
                        ensureSession(actor);
                        result = work.run(token, actor);
                        ensureSession(actor);
                    } catch (GatewayFailure error) {
                        failure = error.error;
                    } catch (IOException error) {
                        failure = Error.NETWORK;
                    } catch (Exception error) {
                        failure = Error.SERVER_ERROR;
                    }
                    final T delivered = result;
                    final Error reported = failure;
                    main.post(() -> {
                        if (!closed && sameSession(actor)) {
                            if (reported == null) callback.onSuccess(delivered);
                            else callback.onError(reported);
                        }
                    });
                });
            } catch (RejectedExecutionException ignored) {
                fail(callback, Error.NETWORK);
            }
        });
        return true;
    }

    private boolean sameSession(String uid) {
        FirebaseUser active = auth.getCurrentUser();
        return active != null && uid.equals(active.getUid());
    }

    private void ensureSession(String uid) throws IOException {
        if (closed || !sameSession(uid) || Thread.currentThread().isInterrupted()) {
            throw new IOException("Session no longer active");
        }
    }

    private void fail(Callback<?> callback, Error error) {
        if (callback != null && !closed) {
            main.post(() -> { if (!closed) callback.onError(error); });
        }
    }

    private static final class GatewayFailure extends Exception {
        final Error error;
        GatewayFailure(Error error) { this.error = error; }
    }

    private byte[] readProcessedJpeg(Uri uri) throws IOException, GatewayFailure {
        try (InputStream input = appContext.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Missing local photo");
            byte[] bytes = readLimited(input, IncidentEvidenceRequestPolicy.MAX_JPEG_BYTES);
            if (!IncidentEvidenceRequestPolicy.validJpeg(bytes)) {
                throw new GatewayFailure(Error.VALIDATION);
            }
            return bytes;
        } catch (SecurityException error) {
            throw new GatewayFailure(Error.VALIDATION);
        }
    }

    private static byte[] readLimited(InputStream in, int max)
            throws IOException, GatewayFailure {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Interrupted");
            if (out.size() + read > max) throw new GatewayFailure(Error.TOO_LARGE);
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private HttpsURLConnection connection(String query, String token, String method)
            throws IOException {
        URL url = new URL(GATEWAY + query);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod(method);
        conn.setConnectTimeout(NETWORK_TIMEOUT_MS);
        conn.setReadTimeout(NETWORK_TIMEOUT_MS);
        conn.setUseCaches(false);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json, image/jpeg");
        conn.setRequestProperty("Cache-Control", "no-store");
        return conn;
    }

    private JSONObject requestJson(String method, String query,
                                   String token, byte[] jpeg)
            throws Exception {
        HttpsURLConnection conn = connection(query, token, method);
        try {
            if (jpeg != null) {
                conn.setRequestProperty("Content-Type", "image/jpeg");
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(jpeg.length);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(jpeg);
                }
            }
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) throw mappedError(conn, status);
            try (InputStream stream = conn.getInputStream()) {
                byte[] json = readLimited(stream, MAX_JSON_BYTES);
                return new JSONObject(new String(json, StandardCharsets.UTF_8));
            }
        } finally {
            conn.disconnect();
        }
    }

    private byte[] requestBytes(String query, String token) throws Exception {
        HttpsURLConnection conn = connection(query, token, "GET");
        try {
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) throw mappedError(conn, status);
            String mime = conn.getContentType();
            if (mime == null || !mime.toLowerCase(java.util.Locale.ROOT)
                    .startsWith("image/jpeg")) throw new GatewayFailure(Error.SERVER_ERROR);
            try (InputStream in = conn.getInputStream()) {
                byte[] jpeg = readLimited(in, IncidentEvidenceRequestPolicy.MAX_JPEG_BYTES);
                if (!IncidentEvidenceRequestPolicy.validJpeg(jpeg)) {
                    throw new GatewayFailure(Error.SERVER_ERROR);
                }
                return jpeg;
            }
        } finally {
            conn.disconnect();
        }
    }

    private GatewayFailure mappedError(HttpURLConnection conn, int status) {
        if (status == 401) return new GatewayFailure(Error.AUTH_REQUIRED);
        if (status == 403) return new GatewayFailure(Error.PERMISSION_DENIED);
        if (status == 404) return new GatewayFailure(Error.NOT_FOUND);
        if (status == 409) return new GatewayFailure(Error.CONFLICT);
        if (status == 413) return new GatewayFailure(Error.TOO_LARGE);
        if (status == 400 || status == 415) return new GatewayFailure(Error.VALIDATION);
        if (status == 503) {
            // Gateway deliberately returns 503 while security signoff is pending.
            try (InputStream errorBody = conn.getErrorStream()) {
                if (errorBody != null) {
                    byte[] json = readLimited(errorBody, MAX_JSON_BYTES);
                    JSONObject body = new JSONObject(new String(json, StandardCharsets.UTF_8));
                    if ("EVIDENCE_GATE_NOT_APPROVED".equals(body.optString("error"))) {
                        return new GatewayFailure(Error.GATE_DISABLED);
                    }
                }
            } catch (Exception ignored) { }
        }
        return new GatewayFailure(status >= 500 ? Error.SERVER_ERROR : Error.VALIDATION);
    }

    private static Evidence parseEvidence(JSONObject item, String expectedIncident)
            throws JSONException, IOException {
        String incidentId = item.getString("incidentId");
        String id = item.getString("id");
        Kind kind;
        try {
            kind = Kind.valueOf(item.getString("kind"));
        } catch (IllegalArgumentException failure) {
            throw new IOException("Unexpected kind");
        }
        int size = item.getInt("bytes");
        if (!expectedIncident.equals(incidentId)
                || !IncidentEvidenceRequestPolicy.validEvidenceId(id)
                || size <= 0 || size > IncidentEvidenceRequestPolicy.MAX_JPEG_BYTES) {
            throw new IOException("Unexpected server evidence");
        }
        return new Evidence(id, incidentId, kind,
                item.getString("uploadedBy"), size, item.getString("createdAt"));
    }

    private File ensureDownloadDir() throws IOException {
        File dir = new File(appContext.getCacheDir(), "c01_evidence_remote");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cache unavailable");
        return dir;
    }

    /** Remove app-private downloaded previews at logout/Activity exit. */
    public void clearDownloaded() {
        File dir = new File(appContext.getCacheDir(), "c01_evidence_remote");
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (file.isFile() && name.startsWith("evidence_") && name.endsWith(".jpg")
                    && name.indexOf("..") < 0) {
                file.delete();
            }
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        worker.shutdownNow();
        clearDownloaded();
    }
}

package com.example.baocaosuco.resident.photo;

import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.baocaosuco.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.List;

/** Detail-only evidence UI. The gateway still authorizes each list and download. */
public final class EvidencePanel {
    private final View panel;
    private final TextView message;
    private final View retry;
    private final LinearLayout items;
    private final ImageView preview;
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseAuth.AuthStateListener listener;
    private SupabaseEvidenceClient client;
    private String incident;
    private String uid;
    private int generation;
    private boolean busy;

    public EvidencePanel(View panel) {
        this.panel = panel;
        message = panel.findViewById(R.id.evidence_message);
        retry = panel.findViewById(R.id.evidence_retry);
        items = panel.findViewById(R.id.evidence_items);
        preview = panel.findViewById(R.id.evidence_preview);
        retry.setOnClickListener(v -> load());
        listener = source -> {
            if (uid != null && !uid.equals(currentUid())) hide();
        };
        auth.addAuthStateListener(listener);
    }

    public void showIncident(String id) {
        String active = currentUid();
        if (active == null) { hide(); return; }
        if (id.equals(incident) && active.equals(uid)) return;
        hide();
        uid = active;
        incident = id;
        client = new SupabaseEvidenceClient(panel.getContext(), auth);
        panel.setVisibility(View.VISIBLE);
        load();
    }

    private boolean active(int request) {
        return generation == request && uid != null && uid.equals(currentUid()) && client != null;
    }

    private String currentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    private void load() {
        if (client == null || busy) return;
        busy = true;
        items.removeAllViews();
        clearPreview();
        retry.setVisibility(View.GONE);
        message.setText("Đang tải danh sách ảnh…");
        int request = ++generation;
        client.list(incident, new SupabaseEvidenceClient.Callback<List<SupabaseEvidenceClient.Evidence>>() {
            @Override public void onSuccess(List<SupabaseEvidenceClient.Evidence> photos) {
                if (!active(request)) return;
                busy = false;
                message.setText(photos.isEmpty() ? "Chưa có ảnh bằng chứng." : "");
                for (SupabaseEvidenceClient.Evidence photo : photos) {
                    MaterialButton button = new MaterialButton(panel.getContext(), null,
                            com.google.android.material.R.attr.materialButtonOutlinedStyle);
                    String title = photo.getKind() == SupabaseEvidenceClient.Kind.REPORTED
                            ? "Ảnh lúc báo sự cố" : "Ảnh kết quả xử lý";
                    button.setText(title);
                    button.setOnClickListener(v -> download(photo, title));
                    items.addView(button);
                }
            }

            @Override public void onError(SupabaseEvidenceClient.Error error) {
                showError(request, error);
            }
        });
    }

    private void download(SupabaseEvidenceClient.Evidence photo, String title) {
        if (busy || client == null) return;
        busy = true;
        retry.setVisibility(View.GONE);
        clearPreview();
        message.setText("Đang tải ảnh…");
        int request = ++generation;
        client.download(incident, photo.getId(), new SupabaseEvidenceClient.Callback<Uri>() {
            @Override public void onSuccess(Uri uri) {
                if (!active(request)) return;
                busy = false;
                preview.setImageURI(uri);
                if (preview.getDrawable() == null) {
                    showError(request, SupabaseEvidenceClient.Error.SERVER_ERROR);
                    return;
                }
                preview.setContentDescription(title);
                preview.setVisibility(View.VISIBLE);
                message.setText(title);
            }

            @Override public void onError(SupabaseEvidenceClient.Error error) {
                showError(request, error);
            }
        });
    }

    private void showError(int request, SupabaseEvidenceClient.Error error) {
        if (!active(request)) return;
        busy = false;
        message.setText(EvidenceUiText.error(error));
        retry.setVisibility(EvidenceUiText.canRetry(error) ? View.VISIBLE : View.GONE);
    }

    private void clearPreview() {
        preview.setImageDrawable(null);
        preview.setVisibility(View.GONE);
        if (client != null) client.clearDownloaded();
    }

    public void hide() {
        generation++;
        busy = false;
        uid = null;
        incident = null;
        items.removeAllViews();
        clearPreview();
        if (client != null) client.close();
        client = null;
        panel.setVisibility(View.GONE);
    }

    public void close() {
        auth.removeAuthStateListener(listener);
        hide();
    }
}

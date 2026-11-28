package com.example.baocaosuco.resident;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import androidx.appcompat.app.AppCompatActivity;

import com.example.baocaosuco.R;
import com.example.baocaosuco.resident.photo.EvidencePanel;
import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.repository.FirebaseIncidentRepository;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.logic.IncidentStatus;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import com.example.baocaosuco.integration.ui.IncidentUiText;
import java.util.List;

public class ResidentDetailActivity extends AppCompatActivity implements ResidentIncidentCoordinator.View {

    private ImageButton btnBack;
    private TextView tvDetailPriority, tvDetailStatus, tvDetailTitle, tvDetailLocation, tvDetailCreatedAt, tvDetailDesc, tvDetailHandler;
    private MaterialCardView cardResidentConfirm;
    private MaterialButton btnConfirmDone, btnConfirmNotDone;
    private TextView message;
    private RecyclerView timeline;
    private View retry;
    private boolean busy;
    private boolean needsReload;

    private ResidentIncidentCoordinator coordinator;
    private String incidentId;
    private EvidencePanel evidence;
    private IncidentSnapshot currentIncident;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_detail);

        IncidentUiText.applyInsets(this, findViewById(R.id.main));
        evidence = new EvidencePanel(findViewById(R.id.incident_evidence_panel));
        incidentId = getIntent().getStringExtra("incident_id");

        btnBack = findViewById(R.id.btn_back);
        tvDetailPriority = findViewById(R.id.tv_detail_priority);
        tvDetailStatus = findViewById(R.id.tv_detail_status);
        tvDetailTitle = findViewById(R.id.tv_detail_title);
        tvDetailLocation = findViewById(R.id.tv_detail_location);
        tvDetailCreatedAt = findViewById(R.id.tv_detail_created_at);
        tvDetailDesc = findViewById(R.id.tv_detail_desc);
        tvDetailHandler = findViewById(R.id.tv_detail_handler);
        cardResidentConfirm = findViewById(R.id.card_resident_confirm);
        btnConfirmDone = findViewById(R.id.btn_confirm_done);
        btnConfirmNotDone = findViewById(R.id.btn_confirm_not_done);
        message = findViewById(R.id.incident_message);
        retry = findViewById(R.id.incident_retry);
        timeline = findViewById(R.id.rv_timeline_events);
        timeline.setLayoutManager(new LinearLayoutManager(this));
        // Let the timeline receive vertical gestures when its surrounding ScrollView reaches the end.
        timeline.setNestedScrollingEnabled(true);
        findViewById(R.id.layout_static_steps).setVisibility(View.GONE);
        findViewById(R.id.layout_timeline_events).setVisibility(View.GONE);
        findViewById(R.id.card_incident_summary).setVisibility(View.GONE);
        findViewById(R.id.card_incident_timeline).setVisibility(View.GONE);
        cardResidentConfirm.setVisibility(View.GONE);

        btnBack.setOnClickListener(v -> finish());

        coordinator = new ResidentIncidentCoordinator(new FirebaseIncidentRepository().resident(), this);

        btnConfirmDone.setOnClickListener(v -> {
            if (!busy && !needsReload && currentIncident != null) {
                coordinator.confirm(currentIncident, true);
            }
        });

        btnConfirmNotDone.setOnClickListener(v -> {
            if (!busy && !needsReload && currentIncident != null) {
                showUnresolvedDialog(currentIncident);
            }
        });

        retry.setOnClickListener(v -> coordinator.loadDetail(incidentId));
        if (incidentId != null) {
            coordinator.loadDetail(incidentId);
        } else {
            onError(IncidentRepository.Error.NOT_FOUND);
        }
    }

    @Override
    protected void onDestroy() {
        evidence.close();
        super.onDestroy();
        if (coordinator != null) {
            coordinator.close();
        }
    }

    @Override
    public void onBusy(boolean busy) {
        this.busy = busy;
        setActionsEnabled(!busy && !needsReload);
        retry.setEnabled(!busy);
        showMessage(busy ? "Đang tải hoặc lưu…" : null);
    }

    @Override
    public void onList(List<IncidentSnapshot> incidents) {
        // Not used
    }

    @Override
    public void onDetail(IncidentSnapshot incident, List<IncidentHistorySnapshot> history) {
        currentIncident = incident;
        evidence.showIncident(incident.getId());
        needsReload = false;
        retry.setVisibility(View.GONE);
        showMessage(null);
        findViewById(R.id.card_incident_summary).setVisibility(View.VISIBLE);
        findViewById(R.id.card_incident_timeline).setVisibility(View.VISIBLE);
        tvDetailTitle.setText(incident.getTitle());
        tvDetailLocation.setText("Vị trí: " + incident.getLocation());
        tvDetailPriority.setText(IncidentUiText.priority(incident.getPriority()));
        tvDetailStatus.setText(IncidentUiText.status(incident.getStatus()));
        IncidentUiText.applyStatusBadge(tvDetailStatus, incident.getStatus());
        IncidentUiText.applyPriorityBadge(tvDetailPriority, incident.getPriority());
        tvDetailDesc.setText(incident.getDescription());
        tvDetailCreatedAt.setText("Thời gian gửi: " + IncidentUiText.time(incident.getCreatedAtMillis()));
        boolean overdue = com.example.baocaosuco.manager.ManagerIncidentCoordinator.isOverdue(
                incident, System.currentTimeMillis());
        tvDetailHandler.setText("Người xử lý: "
                + (incident.getAssignedTo() == null ? "Chưa phân công" : incident.getAssignedTo())
                + "\nHạn xử lý: " + IncidentUiText.time(incident.getDueAtMillis())
                + (overdue ? "\nĐã quá hạn xử lý" : ""));
        timeline.setAdapter(new TimelineAdapter(history));
        cardResidentConfirm.setVisibility(incident.getStatus() == IncidentStatus.COMPLETED
                ? View.VISIBLE : View.GONE);
        setActionsEnabled(!busy);
    }

    private void showUnresolvedDialog(IncidentSnapshot observed) {
        android.widget.EditText note = new android.widget.EditText(this);
        note.setHint("Ghi chú (không bắt buộc)");
        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Báo sự cố chưa được giải quyết")
                .setView(note)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Gửi", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String value = note.getText().toString().trim();
                    if (value.codePointCount(0, value.length()) > 500) {
                        note.setError("Ghi chú tối đa 500 ký tự");
                        return;
                    }
                    if (!busy && !needsReload && currentIncident == observed
                            && coordinator.confirm(observed, false, value.isEmpty() ? null : value)) {
                        dialog.dismiss();
                    }
                }));
        dialog.show();
    }

    private void setActionsEnabled(boolean enabled) {
        btnConfirmDone.setEnabled(enabled);
        btnConfirmNotDone.setEnabled(enabled);
    }

    private void showMessage(String text) {
        message.setText(text);
        message.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onSaved(IncidentSnapshot incident) {
        setResult(RESULT_OK);
        coordinator.loadDetail(incident.getId());
    }

    @Override
    public void onError(IncidentRepository.Error error) {
        needsReload = true;
        setActionsEnabled(false);
        if (error == IncidentRepository.Error.PERMISSION_DENIED
                || error == IncidentRepository.Error.AUTH_REQUIRED
                || error == IncidentRepository.Error.NOT_FOUND) {
            currentIncident = null;
            evidence.hide();
            findViewById(R.id.card_incident_summary).setVisibility(View.GONE);
            findViewById(R.id.card_incident_timeline).setVisibility(View.GONE);
            cardResidentConfirm.setVisibility(View.GONE);
        }
        retry.setVisibility(incidentId == null ? View.GONE : View.VISIBLE);
        showMessage(IncidentUiText.error(error));
    }

    private static final class TimelineAdapter extends RecyclerView.Adapter<TimelineHolder> {
        private final List<IncidentHistorySnapshot> events;

        TimelineAdapter(List<IncidentHistorySnapshot> events) {
            this.events = events;
        }

        @NonNull @Override
        public TimelineHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new TimelineHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_timeline_event, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull TimelineHolder holder, int position) {
            IncidentHistorySnapshot event = events.get(position);
            String from = event.getFromStatus() == null ? ""
                    : IncidentUiText.status(event.getFromStatus()) + " → ";
            holder.set(R.id.tv_timeline_action, action(event.getAction().name()));
            holder.set(R.id.tv_timeline_status, from + IncidentUiText.status(event.getToStatus()));
            IncidentUiText.applyStatusBadge(holder.itemView.findViewById(R.id.tv_timeline_status), event.getToStatus());
            holder.set(R.id.tv_timeline_timestamp, IncidentUiText.time(event.getOccurredAtMillis()));
            // No user-directory lookup is in the incident contract.
            holder.itemView.findViewById(R.id.tv_timeline_actor).setVisibility(View.GONE);
            holder.set(R.id.tv_timeline_note, event.getNote());
            holder.itemView.findViewById(R.id.tv_timeline_note).setVisibility(
                    event.getNote() == null || event.getNote().isEmpty() ? View.GONE : View.VISIBLE);
            holder.itemView.findViewById(R.id.view_timeline_line).setVisibility(
                    position + 1 == events.size() ? View.GONE : View.VISIBLE);
        }

        private static String action(String code) {
            switch (code) {
                case "CREATE": return "Tạo báo cáo";
                case "ASSIGN": return "Phân công";
                case "START_WORK": return "Bắt đầu xử lý";
                case "MARK_COMPLETE": return "Đánh dấu hoàn tất";
                case "CONFIRM_RESOLVED": return "Xác nhận đã giải quyết";
                case "REPORT_UNRESOLVED": return "Báo chưa giải quyết";
                default: return code;
            }
        }

        @Override public int getItemCount() { return events.size(); }
    }

    private static final class TimelineHolder extends RecyclerView.ViewHolder {
        TimelineHolder(View item) { super(item); }

        void set(int id, String value) {
            ((TextView) itemView.findViewById(id)).setText(value);
        }
    }
}

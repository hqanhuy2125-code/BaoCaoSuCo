package com.example.baocaosuco.manager;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.util.TypedValue;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import com.example.baocaosuco.R;
import com.example.baocaosuco.resident.photo.EvidencePanel;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.repository.FirebaseIncidentRepository;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.logic.IncidentStatus;
import com.google.android.material.button.MaterialButton;

import com.example.baocaosuco.integration.ui.IncidentUiText;
import java.util.List;

public class ManagerDetailActivity extends AppCompatActivity implements ManagerIncidentCoordinator.View {

    private ImageButton btnBack;
    private TextView tvPriority, tvStatus, tvTitle, tvLocation, tvResident, tvCreatedAt, tvDesc;
    private MaterialButton btnAssign, btnStartProcessing, btnComplete, retry;
    private TextView message;
    private boolean busy;
    private boolean needsReload;

    private ManagerIncidentCoordinator coordinator;
    private String incidentId;
    private EvidencePanel evidence;
    private IncidentSnapshot currentIncident;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_detail);

        IncidentUiText.applyInsets(this, findViewById(R.id.main));
        evidence = new EvidencePanel(findViewById(R.id.incident_evidence_panel));
        incidentId = getIntent().getStringExtra("incident_id");

        btnBack = findViewById(R.id.btn_manager_back);
        tvPriority = findViewById(R.id.tv_mgr_detail_priority);
        tvStatus = findViewById(R.id.tv_mgr_detail_status);
        tvTitle = findViewById(R.id.tv_mgr_detail_title);
        tvLocation = findViewById(R.id.tv_mgr_detail_location);
        tvResident = findViewById(R.id.tv_mgr_detail_resident);
        tvCreatedAt = findViewById(R.id.tv_mgr_detail_created_at);
        tvDesc = findViewById(R.id.tv_mgr_detail_desc);
        btnAssign = findViewById(R.id.btn_mgr_assign);
        retry = findViewById(R.id.incident_retry);
        message = findViewById(R.id.incident_message);
        findViewById(R.id.card_incident_summary).setVisibility(View.GONE);
        findViewById(R.id.card_manager_actions).setVisibility(View.GONE);
        btnStartProcessing = findViewById(R.id.btn_mgr_start_processing);
        btnComplete = findViewById(R.id.btn_mgr_complete);

        btnBack.setOnClickListener(v -> finish());

        coordinator = new ManagerIncidentCoordinator(new FirebaseIncidentRepository().manager(), this);
        btnAssign.setOnClickListener(v -> {
            if (canAct()) showAssignment(currentIncident);
        });
        btnStartProcessing.setOnClickListener(v -> {
            if (canAct()) coordinator.start(currentIncident);
        });
        btnComplete.setOnClickListener(v -> {
            if (canAct()) coordinator.complete(currentIncident);
        });
        retry.setOnClickListener(v -> coordinator.loadList());
        if (incidentId == null) onError(IncidentRepository.Error.NOT_FOUND);
        else coordinator.loadList(); // Resolve the ID against a newly loaded Manager page.
    }

    private boolean canAct() {
        return !busy && !needsReload && currentIncident != null;
    }

    private void showAssignment(IncidentSnapshot observed) {
        EditText input = new EditText(this);
        input.setHint("Tên người xử lý");
        input.setSingleLine();
        TypedValue surfaceText = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, surfaceText, true);
        input.setTextColor(surfaceText.data);
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, surfaceText, true);
        input.setHintTextColor(surfaceText.data);
        FrameLayout field = new FrameLayout(this);
        int inset = Math.round(24 * getResources().getDisplayMetrics().density);
        field.setPaddingRelative(inset, 0, inset, 0);
        field.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("Phân công")
                .setView(field).setNegativeButton("Hủy", null)
                .setPositiveButton("Giao việc", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty() || name.codePointCount(0, name.length()) > 120) {
                        input.setError("Nhập tên từ 1 đến 120 ký tự");
                        return;
                    }
                    if (canAct()) coordinator.assign(observed, name);
                    dialog.dismiss();
                }));
        dialog.show();
        int actionColor = androidx.core.content.ContextCompat.getColor(this, R.color.f7_link);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(actionColor);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(actionColor);
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
        retry.setEnabled(!busy);
        setActionsEnabled(canAct());
        if (busy) showMessage("Đang tải hoặc lưu…");
        else showMessage(null);
    }

    @Override
    public void onRows(List<IncidentSnapshot> rows) {
        if (incidentId != null) {
            coordinator.select(incidentId);
        }
    }

    @Override
    public void onDetail(IncidentSnapshot selected) {
        currentIncident = selected;
        evidence.showIncident(selected.getId());
        needsReload = false;
        retry.setVisibility(View.GONE);
        showMessage(null);
        findViewById(R.id.card_incident_summary).setVisibility(View.VISIBLE);
        tvTitle.setText(selected.getTitle());
        tvLocation.setText("Vị trí: " + selected.getLocation());
        tvPriority.setText(IncidentUiText.priority(selected.getPriority()));
        tvStatus.setText(IncidentUiText.status(selected.getStatus()));
        IncidentUiText.applyStatusBadge(tvStatus, selected.getStatus());
        IncidentUiText.applyPriorityBadge(tvPriority, selected.getPriority());
        tvDesc.setText(selected.getDescription());
        tvResident.setText("Mã sự cố: " + selected.getId());
        tvCreatedAt.setText("Thời gian gửi: " + IncidentUiText.time(selected.getCreatedAtMillis()));
        TextView deadline = findViewById(R.id.tv_mgr_detail_deadline);
        deadline.setText("Hạn xử lý: " + IncidentUiText.time(selected.getDueAtMillis()));
        TextView handler = findViewById(R.id.tv_mgr_detail_handler);
        handler.setText("Người xử lý: " + (selected.getAssignedTo() == null
                ? "Chưa phân công" : selected.getAssignedTo()));
        findViewById(R.id.tv_mgr_detail_assignment_info).setVisibility(View.GONE);
        findViewById(R.id.tv_mgr_detail_overdue_warning).setVisibility(
                ManagerIncidentCoordinator.isOverdue(selected, System.currentTimeMillis())
                        ? View.VISIBLE : View.GONE);

        IncidentStatus status = selected.getStatus();
        btnAssign.setVisibility(status == IncidentStatus.NEW ? View.VISIBLE : View.GONE);
        btnStartProcessing.setVisibility(status == IncidentStatus.ASSIGNED ? View.VISIBLE : View.GONE);
        btnComplete.setVisibility(status == IncidentStatus.IN_PROGRESS ? View.VISIBLE : View.GONE);
        findViewById(R.id.card_manager_actions).setVisibility(
                status == IncidentStatus.COMPLETED || status == IncidentStatus.CLOSED ? View.GONE : View.VISIBLE);
        setActionsEnabled(canAct());
    }

    private void setActionsEnabled(boolean enabled) {
        btnAssign.setEnabled(enabled);
        btnStartProcessing.setEnabled(enabled);
        btnComplete.setEnabled(enabled);
    }

    private void showMessage(String text) {
        message.setText(text);
        message.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onSaved(IncidentSnapshot incident) {
        onDetail(incident);
        setResult(RESULT_OK);
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
            findViewById(R.id.card_manager_actions).setVisibility(View.GONE);
        }
        retry.setVisibility(incidentId == null ? View.GONE : View.VISIBLE);
        showMessage(IncidentUiText.error(error));
    }
}

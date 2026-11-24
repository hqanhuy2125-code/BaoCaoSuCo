package com.example.baocaosuco.resident;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.baocaosuco.R;
import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.repository.FirebaseIncidentRepository;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.logic.IncidentStatus;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ResidentDetailActivity extends AppCompatActivity implements ResidentIncidentCoordinator.View {

    private ImageButton btnBack;
    private TextView tvDetailPriority, tvDetailStatus, tvDetailTitle, tvDetailLocation, tvDetailCreatedAt, tvDetailDesc, tvDetailHandler;
    private MaterialCardView cardResidentConfirm;
    private MaterialButton btnConfirmDone, btnConfirmNotDone;
    private TextView tvFeedbackStatus;

    private ResidentIncidentCoordinator coordinator;
    private String incidentId;
    private IncidentSnapshot currentIncident;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_detail);

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
        tvFeedbackStatus = findViewById(R.id.tv_feedback_status);

        btnBack.setOnClickListener(v -> finish());

        coordinator = new ResidentIncidentCoordinator(new FirebaseIncidentRepository().resident(), this);

        btnConfirmDone.setOnClickListener(v -> {
            if (currentIncident != null) {
                coordinator.confirm(currentIncident, true);
            }
        });

        btnConfirmNotDone.setOnClickListener(v -> {
            if (currentIncident != null) {
                coordinator.confirm(currentIncident, false);
            }
        });

        if (incidentId != null) {
            coordinator.loadDetail(incidentId);
        } else {
            Toast.makeText(this, "Không tìm thấy mã sự cố", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (coordinator != null) {
            coordinator.close();
        }
    }

    @Override
    public void onBusy(boolean busy) {
        // Optional loading indicator
    }

    @Override
    public void onList(List<IncidentSnapshot> incidents) {
        // Not used
    }

    @Override
    public void onDetail(IncidentSnapshot incident, List<IncidentHistorySnapshot> history) {
        currentIncident = incident;
        tvDetailTitle.setText(incident.getTitle());
        tvDetailLocation.setText("Vị trí: " + incident.getLocation());
        tvDetailPriority.setText(incident.getPriority());
        tvDetailStatus.setText(incident.getStatusCode());
        tvDetailDesc.setText(incident.getDescription());

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault());
        tvDetailCreatedAt.setText("Thời gian gửi: " + sdf.format(new Date(incident.getCreatedAtMillis())));

        String handler = incident.getAssignedTo();
        if (handler != null && !handler.trim().isEmpty()) {
            tvDetailHandler.setText("Kỹ thuật viên phụ trách: " + handler);
            tvDetailHandler.setVisibility(View.VISIBLE);
        } else {
            tvDetailHandler.setVisibility(View.GONE);
        }

        if (incident.getStatus() == IncidentStatus.COMPLETED) {
            cardResidentConfirm.setVisibility(View.VISIBLE);
        } else {
            cardResidentConfirm.setVisibility(View.GONE);
        }
    }

    @Override
    public void onSaved(IncidentSnapshot incident) {
        Toast.makeText(this, "Cập nhật thành công", Toast.LENGTH_SHORT).show();
        currentIncident = incident;
        onDetail(incident, null);
    }

    @Override
    public void onError(IncidentRepository.Error error) {
        Toast.makeText(this, "Lỗi: " + error, Toast.LENGTH_SHORT).show();
    }
}

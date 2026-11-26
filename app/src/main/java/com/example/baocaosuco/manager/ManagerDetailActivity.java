package com.example.baocaosuco.manager;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.baocaosuco.R;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.repository.FirebaseIncidentRepository;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.logic.IncidentStatus;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ManagerDetailActivity extends AppCompatActivity implements ManagerIncidentCoordinator.View {

    private ImageButton btnBack;
    private TextView tvPriority, tvStatus, tvTitle, tvLocation, tvResident, tvCreatedAt, tvDesc;
    private Spinner spinnerTechnician;
    private MaterialButton btnStartProcessing, btnComplete;

    private ManagerIncidentCoordinator coordinator;
    private String incidentId;
    private IncidentSnapshot currentIncident;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_detail);

        incidentId = getIntent().getStringExtra("incident_id");

        btnBack = findViewById(R.id.btn_manager_back);
        tvPriority = findViewById(R.id.tv_mgr_detail_priority);
        tvStatus = findViewById(R.id.tv_mgr_detail_status);
        tvTitle = findViewById(R.id.tv_mgr_detail_title);
        tvLocation = findViewById(R.id.tv_mgr_detail_location);
        tvResident = findViewById(R.id.tv_mgr_detail_resident);
        tvCreatedAt = findViewById(R.id.tv_mgr_detail_created_at);
        tvDesc = findViewById(R.id.tv_mgr_detail_desc);
        spinnerTechnician = findViewById(R.id.spinner_technician);
        btnStartProcessing = findViewById(R.id.btn_mgr_start_processing);
        btnComplete = findViewById(R.id.btn_mgr_complete);

        btnBack.setOnClickListener(v -> finish());

        // Setup technician spinner mock items
        String[] techs = {"Kỹ thuật viên Nam", "Kỹ thuật viên Hùng", "Kỹ thuật viên Tuấn"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, techs);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTechnician.setAdapter(adapter);

        coordinator = new ManagerIncidentCoordinator(new FirebaseIncidentRepository().manager(), this);

        btnStartProcessing.setOnClickListener(v -> {
            if (currentIncident != null) {
                if (currentIncident.getStatus() == IncidentStatus.NEW) {
                    String tech = spinnerTechnician.getSelectedItem() != null ? spinnerTechnician.getSelectedItem().toString() : "Kỹ thuật viên mặc định";
                    coordinator.assign(currentIncident, tech);
                } else if (currentIncident.getStatus() == IncidentStatus.ASSIGNED) {
                    coordinator.start(currentIncident);
                }
            }
        });

        btnComplete.setOnClickListener(v -> {
            if (currentIncident != null) {
                coordinator.complete(currentIncident);
            }
        });

        if (incidentId != null) {
            coordinator.loadList(); // Manager coordinator requires loading list before selecting
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
        // Loading state
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
        tvTitle.setText(selected.getTitle());
        tvLocation.setText("Vị trí: " + selected.getLocation());
        tvPriority.setText(selected.getPriority());
        tvStatus.setText(selected.getStatusCode());
        tvDesc.setText(selected.getDescription());
        tvResident.setText("Người báo: " + selected.getResidentId());

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault());
        tvCreatedAt.setText("Thời gian gửi: " + sdf.format(new Date(selected.getCreatedAtMillis())));

        if (selected.getStatus() == IncidentStatus.NEW) {
            btnStartProcessing.setText("Phân Công & Bắt Đầu");
            btnStartProcessing.setVisibility(View.VISIBLE);
            btnComplete.setVisibility(View.GONE);
        } else if (selected.getStatus() == IncidentStatus.ASSIGNED) {
            btnStartProcessing.setText("Bắt Đầu Xử Lý");
            btnStartProcessing.setVisibility(View.VISIBLE);
            btnComplete.setVisibility(View.GONE);
        } else if (selected.getStatus() == IncidentStatus.IN_PROGRESS) {
            btnStartProcessing.setVisibility(View.GONE);
            btnComplete.setVisibility(View.VISIBLE);
        } else {
            btnStartProcessing.setVisibility(View.GONE);
            btnComplete.setVisibility(View.GONE);
        }
    }

    @Override
    public void onSaved(IncidentSnapshot incident) {
        Toast.makeText(this, "Cập nhật thành công", Toast.LENGTH_SHORT).show();
        onDetail(incident);
    }

    @Override
    public void onError(IncidentRepository.Error error) {
        Toast.makeText(this, "Lỗi: " + error, Toast.LENGTH_SHORT).show();
    }
}

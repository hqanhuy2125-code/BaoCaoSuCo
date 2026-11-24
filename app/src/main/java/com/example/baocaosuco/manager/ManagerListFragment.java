package com.example.baocaosuco.manager;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.baocaosuco.R;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.repository.FirebaseIncidentRepository;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.resident.IncidentAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.List;

public class ManagerListFragment extends Fragment implements ManagerIncidentCoordinator.View {

    private RecyclerView rvIncidents;
    private LinearLayout layoutLoading;
    private LinearLayout layoutEmpty;
    private LinearLayout layoutError;
    private MaterialButton btnRetry;
    private Chip chipAll, chipOverdue, chipPending, chipProcessing, chipCompleted;

    private ManagerIncidentCoordinator coordinator;
    private IncidentAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manager_list, container, false);

        rvIncidents = view.findViewById(R.id.rv_manager_incidents);
        layoutLoading = view.findViewById(R.id.layout_manager_loading);
        layoutEmpty = view.findViewById(R.id.layout_manager_empty);
        layoutError = view.findViewById(R.id.layout_manager_error);
        btnRetry = view.findViewById(R.id.btn_manager_retry);

        chipAll = view.findViewById(R.id.chip_mgr_all);
        chipOverdue = view.findViewById(R.id.chip_mgr_overdue);
        chipPending = view.findViewById(R.id.chip_mgr_pending);
        chipProcessing = view.findViewById(R.id.chip_mgr_processing);
        chipCompleted = view.findViewById(R.id.chip_mgr_completed);

        rvIncidents.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new IncidentAdapter(true, incident -> {
            Intent intent = new Intent(requireContext(), ManagerDetailActivity.class);
            intent.putExtra("incident_id", incident.getId());
            startActivity(intent);
        });
        rvIncidents.setAdapter(adapter);

        coordinator = new ManagerIncidentCoordinator(new FirebaseIncidentRepository().manager(), this);

        if (chipAll != null) chipAll.setOnClickListener(v -> coordinator.setStatusFilter("ALL"));
        if (chipPending != null) chipPending.setOnClickListener(v -> coordinator.setStatusFilter("NEW"));
        if (chipProcessing != null) chipProcessing.setOnClickListener(v -> coordinator.setStatusFilter("IN_PROGRESS"));
        if (chipCompleted != null) chipCompleted.setOnClickListener(v -> coordinator.setStatusFilter("COMPLETED"));

        if (btnRetry != null) {
            btnRetry.setOnClickListener(v -> coordinator.loadList());
        }

        coordinator.loadList();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (coordinator != null) {
            coordinator.close();
        }
    }

    @Override
    public void onBusy(boolean busy) {
        if (layoutLoading != null) {
            layoutLoading.setVisibility(busy ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onRows(List<IncidentSnapshot> rows) {
        if (layoutLoading != null) layoutLoading.setVisibility(View.GONE);
        if (layoutError != null) layoutError.setVisibility(View.GONE);
        if (rows == null || rows.isEmpty()) {
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
            if (rvIncidents != null) rvIncidents.setVisibility(View.GONE);
        } else {
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
            if (rvIncidents != null) rvIncidents.setVisibility(View.VISIBLE);
            adapter.setItems(rows);
        }
    }

    @Override
    public void onDetail(IncidentSnapshot selected) {
        // Not used in list fragment
    }

    @Override
    public void onSaved(IncidentSnapshot incident) {
        // Not used in list fragment
    }

    @Override
    public void onError(IncidentRepository.Error error) {
        if (layoutLoading != null) layoutLoading.setVisibility(View.GONE);
        if (layoutError != null) layoutError.setVisibility(View.VISIBLE);
        Toast.makeText(requireContext(), "Lỗi: " + error, Toast.LENGTH_SHORT).show();
    }
}

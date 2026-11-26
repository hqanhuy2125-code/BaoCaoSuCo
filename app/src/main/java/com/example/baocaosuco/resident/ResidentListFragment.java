package com.example.baocaosuco.resident;

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
import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.repository.FirebaseIncidentRepository;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class ResidentListFragment extends Fragment implements ResidentIncidentCoordinator.View {

    private RecyclerView rvIncidents;
    private LinearLayout layoutLoading;
    private LinearLayout layoutEmpty;
    private LinearLayout layoutError;
    private MaterialButton btnRetry;

    private ResidentIncidentCoordinator coordinator;
    private IncidentAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_resident_list, container, false);

        rvIncidents = view.findViewById(R.id.rv_incidents);
        layoutLoading = view.findViewById(R.id.layout_loading);
        layoutEmpty = view.findViewById(R.id.layout_empty);
        layoutError = view.findViewById(R.id.layout_error);
        MaterialButton btnEmptyCreate = view.findViewById(R.id.btn_empty_create);
        btnRetry = view.findViewById(R.id.btn_retry);

        rvIncidents.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new IncidentAdapter(false, incident -> {
            Intent intent = new Intent(requireContext(), ResidentDetailActivity.class);
            intent.putExtra("incident_id", incident.getId());
            startActivity(intent);
        });
        rvIncidents.setAdapter(adapter);

        coordinator = new ResidentIncidentCoordinator(new FirebaseIncidentRepository().resident(), this);

        if (btnEmptyCreate != null) {
            btnEmptyCreate.setOnClickListener(v -> {
                if (requireActivity() instanceof ResidentMainActivity) {
                    ((ResidentMainActivity) requireActivity()).switchToCreateIncident();
                }
            });
        }

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
    public void onList(List<IncidentSnapshot> incidents) {
        if (layoutLoading != null) layoutLoading.setVisibility(View.GONE);
        if (layoutError != null) layoutError.setVisibility(View.GONE);
        if (incidents == null || incidents.isEmpty()) {
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
            if (rvIncidents != null) rvIncidents.setVisibility(View.GONE);
        } else {
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
            if (rvIncidents != null) rvIncidents.setVisibility(View.VISIBLE);
            adapter.setItems(incidents);
        }
    }

    @Override
    public void onDetail(IncidentSnapshot incident, List<IncidentHistorySnapshot> history) {
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

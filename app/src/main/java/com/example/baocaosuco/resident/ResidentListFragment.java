package com.example.baocaosuco.resident;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.baocaosuco.R;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.repository.FirebaseIncidentRepository;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.integration.ui.IncidentListUi;
import java.util.List;

public class ResidentListFragment extends Fragment implements ResidentIncidentCoordinator.View {
    private ResidentIncidentCoordinator coordinator;
    private IncidentListUi ui;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.fragment_resident_list, container, false);
        coordinator = new ResidentIncidentCoordinator(new FirebaseIncidentRepository().resident(), this);
        ui = new IncidentListUi(view, false, coordinator::loadList, incident -> {
            Intent intent = new Intent(requireContext(), ResidentDetailActivity.class);
            intent.putExtra("incident_id", incident.getId());
            startActivity(intent);
        });
        view.findViewById(R.id.btn_empty_create).setOnClickListener(
                v -> ((ResidentMainActivity) requireActivity()).switchToCreateIncident());
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        coordinator.loadList();
    }

    @Override
    public void onDestroyView() {
        coordinator.close();
        ui = null;
        super.onDestroyView();
    }

    @Override public void onBusy(boolean busy) { ui.busy(busy); }
    @Override public void onList(List<IncidentSnapshot> rows) { ui.showRows(rows); }
    @Override public void onError(IncidentRepository.Error error) { ui.showError(error); }
    @Override public void onSaved(IncidentSnapshot snapshot) {}
    @Override public void onDetail(IncidentSnapshot snapshot, List<IncidentHistorySnapshot> history) {}
}

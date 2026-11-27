package com.example.baocaosuco.manager;

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
import com.example.baocaosuco.data.repository.FirebaseIncidentRepository;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.integration.ui.IncidentListUi;
import java.util.List;

public class ManagerListFragment extends Fragment implements ManagerIncidentCoordinator.View {
    private ManagerIncidentCoordinator coordinator;
    private IncidentListUi ui;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.fragment_manager_list, container, false);
        coordinator = new ManagerIncidentCoordinator(new FirebaseIncidentRepository().manager(), this);
        ui = new IncidentListUi(view, true, coordinator::loadList, incident -> {
            Intent intent = new Intent(requireContext(), ManagerDetailActivity.class);
            intent.putExtra("incident_id", incident.getId());
            startActivity(intent);
        });
        if (state != null) ui.restoreSortMode(state.getString("manager_sort"));
        return view;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        if (ui != null) state.putString("manager_sort", ui.sortMode());
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
    @Override public void onRows(List<IncidentSnapshot> rows) { ui.showRows(rows); }
    @Override public void onError(IncidentRepository.Error error) { ui.showError(error); }
    @Override public void onSaved(IncidentSnapshot snapshot) {}
    @Override public void onDetail(IncidentSnapshot snapshot) {}
    void showOverdue() { if (ui != null) ui.showOverdue(); }
    void showAll() { if (ui != null) ui.showAll(); }
}

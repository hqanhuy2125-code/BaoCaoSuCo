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
import com.example.baocaosuco.data.repository.FirebaseIncidentRepository;
import com.example.baocaosuco.integration.ui.IncidentListUi;

public class ManagerListFragment extends Fragment {
    private IncidentListUi.PageLoader pages;
    private IncidentListUi ui;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.fragment_manager_list, container, false);
        ui = new IncidentListUi(view, true, this::reload, incident -> {
            Intent intent = new Intent(requireContext(), ManagerDetailActivity.class);
            intent.putExtra("incident_id", incident.getId());
            startActivity(intent);
        });
        pages = ui.bindPaging(new FirebaseIncidentRepository().managerPages());
        ui.setOnFilterChanged(filter -> ((ManagerMainActivity) requireActivity())
                .showListNavigation(filter == R.id.chip_mgr_overdue));
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
        reload();
    }

    @Override
    public void onPause() {
        pages.clear();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        pages.clear();
        pages = null;
        ui = null;
        super.onDestroyView();
    }

    private void reload() { pages.restart(); }

    void showOverdue() { if (ui != null) ui.showOverdue(); }
    void showAll() { if (ui != null) ui.showAll(); }
}

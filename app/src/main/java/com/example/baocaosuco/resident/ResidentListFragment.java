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
import com.example.baocaosuco.data.repository.FirebaseIncidentRepository;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.integration.ui.IncidentListUi;
import com.example.baocaosuco.integration.ui.IncidentUiText;
import com.google.android.material.snackbar.Snackbar;
import java.util.List;

public class ResidentListFragment extends Fragment implements ResidentForegroundStatusRefresh.View {
    private ResidentForegroundStatusRefresh refresh;
    private IncidentListUi ui;
    private IncidentListUi.PageLoader pages;
    private Snackbar statusNotice;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.fragment_resident_list, container, false);
        FirebaseIncidentRepository repository = new FirebaseIncidentRepository();
        refresh = new ResidentForegroundStatusRefresh(repository.resident(),
                new AndroidResidentRefreshScheduler(), this);
        ui = new IncidentListUi(view, false, this::retry, incident -> {
            Intent intent = new Intent(requireContext(), ResidentDetailActivity.class);
            intent.putExtra("incident_id", incident.getId());
            startActivity(intent);
        });
        pages = ui.bindPaging(repository.residentPages());
        view.findViewById(R.id.btn_empty_create).setOnClickListener(
                v -> ((ResidentMainActivity) requireActivity()).switchToCreateIncident());
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        pages.restart();
        refresh.onStart();
    }

    @Override
    public void onPause() {
        refresh.onStop();
        pages.clear();
        dismissStatusNotice();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        refresh.close();
        refresh = null;
        pages.clear();
        pages = null;
        ui = null;
        super.onDestroyView();
    }

    private void retry() {
        pages.restart();
        if (!refresh.refresh()) refresh.onStart();
    }

    @Override
    public void onPage(List<IncidentSnapshot> rows) {
        pages.reconcileHead(rows);
    }

    @Override
    public void onStatusChanged(IncidentSnapshot incident) {
        dismissStatusNotice();
        statusNotice = Snackbar.make(requireView(),
                incident.getTitle() + " → " + IncidentUiText.status(incident.getStatus()),
                Snackbar.LENGTH_LONG);
        statusNotice.show();
    }

    @Override
    public void onError(IncidentRepository.Error error) {
        dismissStatusNotice();
        if (error == IncidentRepository.Error.AUTH_REQUIRED
                || error == IncidentRepository.Error.PERMISSION_DENIED) {
            pages.clear();
            ui.busy(false);
            ui.showError(error);
        }
    }

    private void dismissStatusNotice() {
        if (statusNotice != null) {
            statusNotice.dismiss();
            statusNotice = null;
        }
    }
}

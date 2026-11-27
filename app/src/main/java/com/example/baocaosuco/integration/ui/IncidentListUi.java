package com.example.baocaosuco.integration.ui;

import android.view.View;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.widget.EditText;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.baocaosuco.R;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.manager.ManagerIncidentCoordinator;
import com.example.baocaosuco.resident.IncidentAdapter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Applies search and presentation filters to the authorized page returned by the coordinator. */
public final class IncidentListUi {
    private final RecyclerView list;
    private final View loading;
    private final View empty;
    private final View error;
    private final TextView errorMessage;
    private final EditText search;
    private final ChipGroup filters;
    private final IncidentAdapter adapter;
    private final int all;
    private List<IncidentSnapshot> rows = Collections.emptyList();
    private boolean loaded;
    private final boolean manager;
    private ManagerPageOrder sort = ManagerPageOrder.PRIORITY_THEN_DUE;
    private View sortButton;

    public IncidentListUi(View root, boolean manager, Runnable retry,
                          IncidentAdapter.OnItemClickListener onOpen) {
        this.manager = manager;
        list = root.findViewById(manager ? R.id.rv_manager_incidents : R.id.rv_incidents);
        loading = root.findViewById(manager ? R.id.layout_manager_loading : R.id.layout_loading);
        empty = root.findViewById(manager ? R.id.layout_manager_empty : R.id.layout_empty);
        error = root.findViewById(manager ? R.id.layout_manager_error : R.id.layout_error);
        errorMessage = error.findViewById(R.id.b07_list_error_message);
        search = root.findViewById(manager ? R.id.et_manager_search : R.id.et_search);
        filters = root.findViewById(manager ? R.id.chip_group_manager_filter : R.id.chip_group_filter);
        all = manager ? R.id.chip_mgr_all : R.id.chip_all;
        if (manager) {
            addChip(R.id.chip_mgr_assigned, "Đã phân công");
            addChip(R.id.chip_mgr_closed, "Đã đóng");
            sortButton = root.findViewById(R.id.f7_manager_sort);
            updateSortLabel();
            sortButton.setOnClickListener(this::showSortMenu);
        }
        for (int index = 0; index < filters.getChildCount(); index++) {
            Chip chip = (Chip) filters.getChildAt(index);
            android.content.res.ColorStateList tint = androidx.appcompat.content.res.AppCompatResources
                    .getColorStateList(chip.getContext(), R.color.f7_chip_fg);
            chip.setTextColor(tint);
            chip.setChipIconTint(tint);
            chip.setCheckedIconTint(tint);
            chip.setChipBackgroundColor(androidx.appcompat.content.res.AppCompatResources
                    .getColorStateList(chip.getContext(), R.color.f7_chip_bg));
        }
        filters.setSelectionRequired(true);
        filters.check(all);
        filters.setOnCheckedStateChangeListener((group, ids) -> render());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                render();
            }
            @Override public void afterTextChanged(Editable text) {}
        });
        root.findViewById(manager ? R.id.btn_manager_retry : R.id.btn_retry)
                .setOnClickListener(v -> retry.run());
        adapter = new IncidentAdapter(manager, onOpen);
        list.setLayoutManager(new LinearLayoutManager(root.getContext()));
        list.setAdapter(adapter);
    }

    private void showSortMenu(View anchor) {
        ManagerPageOrder[] orders = ManagerPageOrder.values();
        String[] labels = new String[orders.length];
        for (int i = 0; i < orders.length; i++) labels[i] = orders[i].label;
        new MaterialAlertDialogBuilder(anchor.getContext())
                .setTitle("Sắp xếp trang đã tải")
                .setSingleChoiceItems(labels, sort.ordinal(), (dialog, index) -> {
                    sort = orders[index];
                    updateSortLabel();
                    render();
                    dialog.dismiss();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    public String sortMode() { return sort.name(); }

    public void restoreSortMode(String mode) {
        if (!manager || mode == null) return;
        sort = ManagerPageOrder.valueOf(mode);
        updateSortLabel();
        render();
    }

    private void updateSortLabel() {
        sortButton.setContentDescription("Sắp xếp trang đã tải: " + sort.label);
    }

    private void addChip(int id, String label) {
        Chip chip = new Chip(filters.getContext());
        chip.setId(id);
        chip.setText(label);
        chip.setCheckable(true);
        filters.addView(chip);
    }

    public void showAll() { filters.check(all); }
    public void showOverdue() { filters.check(R.id.chip_mgr_overdue); }

    public void busy(boolean active) {
        loading.setVisibility(active ? View.VISIBLE : View.GONE);
        if (active) {
            loaded = false;
            error.setVisibility(View.GONE);
            empty.setVisibility(View.GONE);
            list.setVisibility(View.GONE);
        }
    }

    public void showRows(List<IncidentSnapshot> rows) {
        this.rows = rows;
        loaded = true;
        error.setVisibility(View.GONE);
        render();
    }

    public void showError(IncidentRepository.Error reason) {
        loaded = false;
        rows = Collections.emptyList();
        adapter.setItems(rows);
        list.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        errorMessage.setText(IncidentUiText.error(reason));
        error.setVisibility(View.VISIBLE);
    }

    private void render() {
        if (!loaded) return;
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int filter = filters.getCheckedChipId();
        long now = System.currentTimeMillis();
        List<IncidentSnapshot> matching = new ArrayList<>();
        for (IncidentSnapshot row : rows) {
            String description = (row.getTitle() + " " + row.getLocation()).toLowerCase(Locale.ROOT);
            if (description.contains(query) && matches(row, filter, now)) matching.add(row);
        }
        if (manager) sort.sort(matching);
        adapter.setItems(matching);
        empty.setVisibility(matching.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(matching.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private boolean matches(IncidentSnapshot row, int filter, long now) {
        if (filter == R.id.chip_mgr_overdue) return ManagerIncidentCoordinator.isOverdue(row, now);
        if (filter == R.id.chip_mgr_pending || filter == R.id.chip_pending) return "NEW".equals(row.getStatusCode());
        if (filter == R.id.chip_mgr_assigned) return "ASSIGNED".equals(row.getStatusCode());
        if (filter == R.id.chip_mgr_processing || filter == R.id.chip_processing) return "IN_PROGRESS".equals(row.getStatusCode());
        if (filter == R.id.chip_mgr_completed || filter == R.id.chip_completed) return "COMPLETED".equals(row.getStatusCode());
        if (filter == R.id.chip_mgr_closed) return "CLOSED".equals(row.getStatusCode());
        return true;
    }
}

package com.example.baocaosuco.integration.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

/** Searches, filters and sorts the authorized records loaded so far. */
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
    private MaterialButton pagingAction;
    private TextView pagingStatus;
    private IntConsumer onFilterChanged;

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
        addChip(R.id.chip_mgr_assigned, "Đã phân công");
        addChip(R.id.chip_mgr_closed, "Đã đóng");
        if (manager) {
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
        filters.setOnCheckedStateChangeListener((group, ids) -> {
            render();
            if (onFilterChanged != null) onFilterChanged.accept(group.getCheckedChipId());
        });
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
                .setTitle("Sắp xếp sự cố đã tải")
                .setSingleChoiceItems(labels, sort.ordinal(), (dialog, index) -> {
                    sort = orders[index];
                    updateSortLabel();
                    render();
                    dialog.dismiss();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    public void setOnFilterChanged(IntConsumer observer) {
        onFilterChanged = observer;
        if (observer != null) observer.accept(filters.getCheckedChipId());
    }

    public String sortMode() { return sort.name(); }

    public void restoreSortMode(String mode) {
        if (!manager || mode == null) return;
        sort = ManagerPageOrder.valueOf(mode);
        updateSortLabel();
        render();
    }

    private void updateSortLabel() {
        sortButton.setContentDescription("Sắp xếp sự cố đã tải: " + sort.label);
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

    public PageLoader bindPaging(IncidentRepository.PageSource source) {
        PageLoader loader = new PageLoader(source, new PageLoader.Listener() {
            @Override public void onPage(List<IncidentSnapshot> rows, boolean more, boolean loading) {
                showPage(rows, more, loading);
            }

            @Override public void onError(List<IncidentSnapshot> rows, IncidentRepository.Error error) {
                showPageError(rows, error);
            }
        });
        enablePaging(loader::openMoreOrStop);
        return loader;
    }

    private void enablePaging(Runnable nextOrStop) {
        View content = (View) list.getParent();
        LinearLayout container = (LinearLayout) content.getParent();
        LinearLayout strip = new LinearLayout(list.getContext());
        strip.setOrientation(LinearLayout.VERTICAL);
        pagingStatus = new TextView(list.getContext());
        pagingStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        strip.addView(pagingStatus);
        pagingAction = new MaterialButton(list.getContext());
        pagingAction.setOnClickListener(v -> nextOrStop.run());
        strip.addView(pagingAction, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(strip, container.indexOfChild(content));
    }

    public void showPage(List<IncidentSnapshot> result, boolean hasMore, boolean loadingMore) {
        busy(false);
        showRows(result);
        if (pagingStatus == null) return;
        pagingStatus.setText(loadingMore ? "Đang tải sự cố…" : hasMore
                ? "Đã tải " + result.size() + " sự cố · Có thể còn kết quả"
                : "Đã tải " + result.size() + " sự cố · Đã hết kết quả");
        pagingAction.setText(loadingMore ? "Dừng tải" : "Tải thêm");
        pagingAction.setVisibility(loadingMore || hasMore ? View.VISIBLE : View.GONE);
    }

    public void showPageError(List<IncidentSnapshot> result, IncidentRepository.Error reason) {
        busy(false);
        if (result.isEmpty()) {
            showError(reason);
        } else {
            showPage(result, true, false);
            pagingStatus.setText("Kết quả chưa đầy đủ · " + IncidentUiText.error(reason));
        }
    }

    public void showRows(List<IncidentSnapshot> rows) {
        this.rows = rows;
        loaded = true;
        error.setVisibility(View.GONE);
        render();
    }

    public void showError(IncidentRepository.Error reason) {
        if (pagingStatus != null) {
            pagingStatus.setText("");
            pagingAction.setVisibility(View.GONE);
        }
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
            String description = (row.getTitle() + " " + row.getLocation() + " " + row.getId())
                    .toLowerCase(Locale.ROOT);
            if (description.contains(query) && matches(row, filter, now)) matching.add(row);
        }
        if (manager) sort.sort(matching);
        adapter.setItems(matching);
        empty.setVisibility(matching.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(matching.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * Loads at most ten Firestore pages per start/load-more. View callbacks are confined to
     * the main thread. The generation invalidates any result from a stopped or replaced load.
     */
    public static final class PageLoader {
        public interface Listener {
            void onPage(List<IncidentSnapshot> rows, boolean hasMore, boolean loading);
            void onError(List<IncidentSnapshot> rows, IncidentRepository.Error error);
        }

        private static final int MAX_PAGES_PER_BATCH = 10;
        private final IncidentRepository.PageSource source;
        private final Listener listener;
        private final Map<String, IncidentSnapshot> rows = new LinkedHashMap<>();
        private IncidentRepository.PageCursor next;
        private boolean running;
        private boolean exhausted;
        private int generation;

        public PageLoader(IncidentRepository.PageSource source, Listener listener) {
            this.source = source;
            this.listener = listener;
        }

        public void restart() {
            clear();
            loadMore();
        }

        public void stop() {
            generation++;
            running = false;
        }

        public void clear() {
            stop();
            rows.clear();
            next = null;
            exhausted = false;
        }

        public void openMoreOrStop() {
            if (!running) {
                loadMore();
                return;
            }
            stop();
            publish();
        }

        public void reconcileHead(List<IncidentSnapshot> head) {
            for (IncidentSnapshot row : head) merge(row);
            publish();
        }

        private void loadMore() {
            if (running || exhausted) return;
            running = true;
            int token = ++generation;
            publish();
            fetch(token, MAX_PAGES_PER_BATCH);
        }

        private void fetch(int token, int remaining) {
            if (!running || generation != token) return;
            source.load(next, new IncidentRepository.Callback<IncidentRepository.Page>() {
                @Override public void onSuccess(IncidentRepository.Page page) {
                    if (generation != token || !running) return;
                    if (page == null || (page.next != null && page.rows.isEmpty())) {
                        onError(IncidentRepository.Error.SECURITY_NOT_READY);
                        return;
                    }
                    for (IncidentSnapshot row : page.rows) merge(row);
                    next = page.next;
                    exhausted = next == null;
                    running = !exhausted && remaining > 1;
                    publish();
                    if (running) fetch(token, remaining - 1);
                }

                @Override public void onError(IncidentRepository.Error error) {
                    if (generation != token || !running) return;
                    if (error == IncidentRepository.Error.AUTH_REQUIRED
                            || error == IncidentRepository.Error.PERMISSION_DENIED) clear();
                    else running = false;
                    listener.onError(snapshot(), error);
                }
            });
        }

        private void merge(IncidentSnapshot row) {
            IncidentSnapshot previous = rows.get(row.getId());
            if (previous == null || row.getRevision() > previous.getRevision()) rows.put(row.getId(), row);
        }

        private List<IncidentSnapshot> snapshot() {
            ArrayList<IncidentSnapshot> result = new ArrayList<>(rows.values());
            result.sort(java.util.Comparator.comparingLong(IncidentSnapshot::getCreatedAtMillis).reversed()
                    .thenComparing(IncidentSnapshot::getId));
            return result;
        }

        private void publish() {
            listener.onPage(snapshot(), !exhausted, running);
        }
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

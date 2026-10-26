package com.example.baocaosuco.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chuan bi du lieu danh sach su co cho giao dien Quan ly (MGR-05A).
 * Khong doc Firestore truc tiep; quyen Manager phai duoc thuc thi o backend.
 * Deadline va nhom trang thai duoc cung cap tu adapter da duoc phe duyet.
 */
public final class ManagerIncidentOverview {

    public enum State { LOADING, CONTENT, EMPTY, ERROR }
    public enum Overdue { YES, NO, UNKNOWN }
    public enum StatusCategory { ACTIVE, TERMINAL, UNKNOWN }
    public enum ReadError { AUTH_REQUIRED, PERMISSION_DENIED, NETWORK, INVALID_DATA, UNKNOWN }

    /** Adapter doc DU LIEU DA DUOC PHEP: khong nhan UID/role do UI tu khai. */
    public interface DataSource {
        void loadAll(Callback callback);
    }

    public interface Callback {
        void onLoaded(List<ManagerIncidentListPreview.Row> rows,
                      Map<String, Long> dueAtMillisByIncidentId);
        void onFailed(ReadError error);
    }

    /** Phan loai trang thai theo LOG/contract; khong hardcode ten enum. */
    public interface StatusClassifier {
        StatusCategory classify(String statusLabel);
    }

    /** Thoi diem hien tai duoc inject de kiem thu duong bien va tranh tinh lai SLA. */
    public interface TimeSource {
        long nowMillis();
    }

    public interface Listener {
        void onSnapshot(Snapshot snapshot);
    }

    public static final class DisplayRow {
        private final ManagerIncidentListPreview.Row incident;
        private final Long dueAtMillis;
        private final Overdue overdue;

        private DisplayRow(ManagerIncidentListPreview.Row incident, Long dueAtMillis,
                           Overdue overdue) {
            this.incident = incident;
            this.dueAtMillis = dueAtMillis;
            this.overdue = overdue;
        }
        public ManagerIncidentListPreview.Row getIncident() { return incident; }
        public Long getDueAtMillis() { return dueAtMillis; }
        public Overdue getOverdue() { return overdue; }
    }

    public static final class Snapshot {
        private final State state;
        private final List<DisplayRow> rows;
        private final ReadError error;
        private final int overdueCount;

        private Snapshot(State state, List<DisplayRow> rows, ReadError error) {
            this.state = state;
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            this.error = error;
            int count = 0;
            for (DisplayRow row : rows) {
                if (row.overdue == Overdue.YES) count++;
            }
            overdueCount = count;
        }
        public State getState() { return state; }
        public List<DisplayRow> getRows() { return rows; }
        public ReadError getError() { return error; }
        public int getOverdueCount() { return overdueCount; }
    }

    private final DataSource dataSource;
    private final StatusClassifier statusClassifier;
    private final TimeSource timeSource;
    private final AtomicLong requestVersion = new AtomicLong();

    public ManagerIncidentOverview(DataSource dataSource, StatusClassifier statusClassifier,
                                   TimeSource timeSource) {
        if (dataSource == null || statusClassifier == null || timeSource == null) {
            throw new IllegalArgumentException("Data source, status classifier and time source are required.");
        }
        this.dataSource = dataSource;
        this.statusClassifier = statusClassifier;
        this.timeSource = timeSource;
    }

    /**
     * Load moi thay the request cu. Khong de callback tre ghi de du lieu moi.
     * Adapter can bao dam callback ve main thread truoc khi giao dien su dung.
     */
    public void load(Listener listener) {
        if (listener == null) throw new IllegalArgumentException("Listener is required.");
        final long version = requestVersion.incrementAndGet();
        final AtomicBoolean finished = new AtomicBoolean(false);
        listener.onSnapshot(new Snapshot(State.LOADING, Collections.emptyList(), null));

        Callback callback = new Callback() {
            @Override
            public void onLoaded(List<ManagerIncidentListPreview.Row> rows,
                                 Map<String, Long> dueAtMillisByIncidentId) {
                if (version != requestVersion.get() || !finished.compareAndSet(false, true)) return;
                Snapshot result;
                try {
                    result = makeSnapshot(rows, dueAtMillisByIncidentId);
                } catch (RuntimeException ex) {
                    result = error(ReadError.INVALID_DATA);
                }
                if (version == requestVersion.get()) listener.onSnapshot(result);
            }

            @Override
            public void onFailed(ReadError failure) {
                if (version != requestVersion.get() || !finished.compareAndSet(false, true)) return;
                if (version == requestVersion.get()) {
                    listener.onSnapshot(error(failure == null ? ReadError.UNKNOWN : failure));
                }
            }
        };
        try {
            dataSource.loadAll(callback);
        } catch (RuntimeException exception) {
            callback.onFailed(ReadError.UNKNOWN);
        }
    }

    /** Goi khi Activity/Fragment dung lai: bo qua callback cu, khong huy Firebase listener. */
    public void invalidatePending() {
        requestVersion.incrementAndGet();
    }

    private Snapshot makeSnapshot(List<ManagerIncidentListPreview.Row> input,
                                  Map<String, Long> dueTimes) {
        if (input == null || dueTimes == null) return error(ReadError.INVALID_DATA);
        if (input.isEmpty()) return new Snapshot(State.EMPTY, Collections.emptyList(), null);
        long now = timeSource.nowMillis();
        List<DisplayRow> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ManagerIncidentListPreview.Row row : input) {
            if (row == null || blank(row.getId()) || !seen.add(row.getId())) {
                return error(ReadError.INVALID_DATA);
            }
            StatusCategory category = statusClassifier.classify(row.getStatusLabel());
            Long dueAt = dueTimes.get(row.getId());
            Overdue flag;
            if (category == null || category == StatusCategory.UNKNOWN || dueAt == null) {
                flag = Overdue.UNKNOWN;
            } else if (category == StatusCategory.TERMINAL) {
                flag = Overdue.NO;
            } else {
                flag = now > dueAt ? Overdue.YES : Overdue.NO;
            }
            result.add(new DisplayRow(row, dueAt, flag));
        }
        return new Snapshot(State.CONTENT, result, null);
    }

    private static Snapshot error(ReadError reason) {
        return new Snapshot(State.ERROR, Collections.emptyList(), reason);
    }

    private static boolean blank(String value) {
        if (value == null || value.isEmpty()) return true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) return false;
        }
        return true;
    }
}

package com.example.baocaosuco.manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Du lieu minh hoa cho danh sach tat ca su co cua Quan ly (MGR-01A).
 * Khong su dung Firebase, quyen MANAGER hay Data Contract chua duoc duyet.
 * Nhan muc uu tien va trang thai chi de hien thi, khong phai enum chinh thuc.
 */
public final class ManagerIncidentListPreview {

    public enum State {
        LOADING, CONTENT, EMPTY, ERROR
    }

    public static final class Row {
        private final String id;
        private final String demoResidentKey;
        private final String title;
        private final String location;
        private final String priorityLabel;
        private final String statusLabel;

        public Row(String id, String demoResidentKey, String title, String location,
                   String priorityLabel, String statusLabel) {
            this.id = id;
            this.demoResidentKey = demoResidentKey;
            this.title = title;
            this.location = location;
            this.priorityLabel = priorityLabel;
            this.statusLabel = statusLabel;
        }

        public String getId() { return id; }
        public String getDemoResidentKey() { return demoResidentKey; }
        public String getTitle() { return title; }
        public String getLocation() { return location; }
        public String getPriorityLabel() { return priorityLabel; }
        public String getStatusLabel() { return statusLabel; }
    }

    public static final class Snapshot {
        private final State state;
        private final List<Row> rows;
        private final String message;

        private Snapshot(State state, List<Row> rows, String message) {
            this.state = state;
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            this.message = message;
        }

        public State getState() { return state; }
        public List<Row> getRows() { return rows; }
        public String getMessage() { return message; }
    }

    private static final List<Row> DEMO_ROWS = Collections.unmodifiableList(Arrays.asList(
            new Row("mgr-demo-001", "demo-resident-a", "Den hanh lang khong sang",
                    "Tang 2", "Thuong", "Moi"),
            new Row("mgr-demo-002", "demo-resident-b", "Ro ri nuoc trong phong",
                    "Phong 301", "Khan", "Da giao"),
            new Row("mgr-demo-003", "demo-resident-a", "O khoa cua bi ket",
                    "Phong 105", "Thap", "Dang xu ly")
    ));

    private ManagerIncidentListPreview() {
    }

    /** Lay tat ca su co gia, khong loc theo tai khoan nhu man hinh Cu dan. */
    public static Snapshot demoAllIncidents() {
        return fromRows(DEMO_ROWS);
    }

    /** Snapshot tu danh sach gia duoc cap vao, dung cho kiem thu CONTENT/EMPTY. */
    public static Snapshot fromRows(List<Row> rows) {
        if (rows == null) {
            return error("Danh sach minh hoa khong hop le.");
        }
        for (Row row : rows) {
            if (row == null) {
                return error("Danh sach minh hoa chua muc rong.");
            }
        }
        if (rows.isEmpty()) {
            return new Snapshot(State.EMPTY, Collections.emptyList(), null);
        }
        return new Snapshot(State.CONTENT, rows, null);
    }

    /** Trang thai dang tai chi la tin hieu cho UI, chua thuc hien yeu cau mang. */
    public static Snapshot loading() {
        return new Snapshot(State.LOADING, Collections.emptyList(), null);
    }

    /** Trang thai loi de UI co the hien thi thong bao va chon thu lai. */
    public static Snapshot error(String message) {
        return new Snapshot(State.ERROR, Collections.emptyList(), message);
    }
}

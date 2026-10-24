package com.example.baocaosuco.resident;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Du lieu minh hoa rieng cho danh sach su co cua Cu dan (RES-01).
 * Khong phai Firestore repository, khong doc Firebase Auth, khong cap quyen truy cap.
 * Trang thai va muc uu tien trong mock chi la nhan hien thi, chua la enum chinh thuc.
 */
public final class ResidentIncidentListPreview {

    public enum State {
        LOADING,
        CONTENT,
        EMPTY,
        ERROR
    }

    public static final class Item {
        private final String id;
        private final String demoOwnerKey;
        private final String title;
        private final String location;
        private final String priorityLabel;
        private final String statusLabel;

        private Item(String id, String demoOwnerKey, String title, String location,
                     String priorityLabel, String statusLabel) {
            this.id = id;
            this.demoOwnerKey = demoOwnerKey;
            this.title = title;
            this.location = location;
            this.priorityLabel = priorityLabel;
            this.statusLabel = statusLabel;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getLocation() { return location; }
        public String getPriorityLabel() { return priorityLabel; }
        public String getStatusLabel() { return statusLabel; }
    }

    public static final class Snapshot {
        private final State state;
        private final List<Item> items;
        private final String message;

        private Snapshot(State state, List<Item> items, String message) {
            this.state = state;
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.message = message;
        }

        public State getState() { return state; }
        public List<Item> getItems() { return items; }
        public String getMessage() { return message; }
    }

    // Hoan toan la du lieu gia, khong su dung UID hay thong tin cu dan that.
    private static final List<Item> DEMO_ITEMS = Collections.unmodifiableList(Arrays.asList(
            new Item("demo-incident-01", "demo-resident-a", "Đèn hành lang không sáng",
                    "Tầng 2", "Thường", "Mới"),
            new Item("demo-incident-02", "demo-resident-a", "Rò rỉ nước trong phòng",
                    "Phòng 301", "Khẩn", "Đã giao"),
            new Item("demo-incident-03", "demo-resident-b", "Ổ khóa cửa bị kẹt",
                    "Phòng 105", "Thấp", "Đang xử lý")
    ));

    private ResidentIncidentListPreview() {
    }

    /** Trang thai dang tai de UI hien thi progress, khong phai yeu cau mang that. */
    public static Snapshot loading() {
        return new Snapshot(State.LOADING, Collections.emptyList(), null);
    }

    /** Trang thai loi cho UI; chi hien thi thong bao minh hoa do caller cung cap. */
    public static Snapshot failure(String message) {
        return new Snapshot(State.ERROR, Collections.emptyList(), message);
    }

    /**
     * Tao snapshot tu du lieu gia cua mot key minh hoa.
     * Viec loc o day chi phuc vu demo. Bao mat du lieu that phai do Firestore Rules.
     */
    public static Snapshot forDemoResident(String demoOwnerKey) {
        if (demoOwnerKey == null || demoOwnerKey.trim().isEmpty()) {
            return failure("Chưa chọn tài khoản cư dân minh họa.");
        }

        List<Item> matches = new ArrayList<>();
        for (Item item : DEMO_ITEMS) {
            if (item.demoOwnerKey.equals(demoOwnerKey)) {
                matches.add(item);
            }
        }
        if (matches.isEmpty()) {
            return new Snapshot(State.EMPTY, Collections.emptyList(), null);
        }
        return new Snapshot(State.CONTENT, matches, null);
    }
}

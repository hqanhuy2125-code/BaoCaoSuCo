package com.example.baocaosuco.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chi tiet su co va de xuat phan cong nguoi xu ly (MOCK, Java-only).
 * Khong cap quyen, khong chuyen trang thai, khong ghi Firebase/Firestore.
 */
public final class ManagerIncidentDetailDraft {

    public enum LookupState {
        FOUND, NOT_FOUND, INVALID
    }

    public enum AssignmentState {
        READY, MISSING_HANDLER, INVALID_INCIDENT
    }

    public static final class Detail {
        private final ManagerIncidentListPreview.Row summary;
        private final String description;
        private final String assignedHandler;

        private Detail(ManagerIncidentListPreview.Row summary, String description,
                       String assignedHandler) {
            this.summary = summary;
            this.description = description;
            this.assignedHandler = assignedHandler;
        }

        public String getId() { return summary.getId(); }
        public String getDemoResidentKey() { return summary.getDemoResidentKey(); }
        public String getTitle() { return summary.getTitle(); }
        public String getLocation() { return summary.getLocation(); }
        public String getPriorityLabel() { return summary.getPriorityLabel(); }
        public String getStatusLabel() { return summary.getStatusLabel(); }
        public String getDescription() { return description; }
        public String getAssignedHandler() { return assignedHandler; }
    }

    public static final class LookupResult {
        private final LookupState state;
        private final Detail detail;

        private LookupResult(LookupState state, Detail detail) {
            this.state = state;
            this.detail = detail;
        }

        public LookupState getState() { return state; }
        public Detail getDetail() { return detail; }
    }

    /**
     * Du lieu de xuat de sau nay gui qua repository da duoc phe duyet.
     * READY KHONG dong nghia da giao viec thanh cong hoac duoc cap quyen Manager.
     */
    public static final class AssignmentDraft {
        private final AssignmentState state;
        private final String incidentId;
        private final String handlerName;

        private AssignmentDraft(AssignmentState state, String incidentId, String handlerName) {
            this.state = state;
            this.incidentId = incidentId;
            this.handlerName = handlerName;
        }

        public AssignmentState getState() { return state; }
        public String getIncidentId() { return incidentId; }
        public String getHandlerName() { return handlerName; }
    }

    private ManagerIncidentDetailDraft() {
    }

    /** Tao ban chi tiet minh hoa tu hang danh sach, khong suy luan field Firestore. */
    public static Detail fromRow(ManagerIncidentListPreview.Row row, String description,
                                 String assignedHandler) {
        if (row == null || isBlank(row.getId())) {
            throw new IllegalArgumentException("A mock incident with an ID is required.");
        }
        return new Detail(row, description, assignedHandler);
    }

    /** Bo sung mo ta va ten nguoi duoc giao minh hoa cho data mock MGR-01A. */
    public static List<Detail> demoDetails() {
        List<Detail> details = new ArrayList<>();
        for (ManagerIncidentListPreview.Row row
                : ManagerIncidentListPreview.demoAllIncidents().getRows()) {
            String description;
            String assigned = null;
            if ("mgr-demo-001".equals(row.getId())) {
                description = "Den tang 2 khong sang khi bat cong tac.";
            } else if ("mgr-demo-002".equals(row.getId())) {
                description = "Ong nuoc phong 301 dang ro ri.";
                assigned = "To ky thuat A";
            } else if ("mgr-demo-003".equals(row.getId())) {
                description = "O khoa cua phong 105 bi ket.";
            } else {
                description = null;
            }
            details.add(fromRow(row, description, assigned));
        }
        return Collections.unmodifiableList(details);
    }

    /**
     * Tim chi tiet theo ID minh hoa; ID khong hop le, du lieu null/trung ID bi tu choi.
     * Khong phai kiem tra quyen truy cap du lieu Firestore.
     */
    public static LookupResult findById(List<Detail> details, String incidentId) {
        if (details == null || isBlank(incidentId)) {
            return new LookupResult(LookupState.INVALID, null);
        }
        Set<String> seenIds = new HashSet<>();
        Detail selected = null;
        for (Detail detail : details) {
            if (detail == null || isBlank(detail.getId()) || !seenIds.add(detail.getId())) {
                return new LookupResult(LookupState.INVALID, null);
            }
            if (detail.getId().equals(incidentId)) {
                selected = detail;
            }
        }
        return new LookupResult(selected == null ? LookupState.NOT_FOUND : LookupState.FOUND,
                selected);
    }

    /**
     * Chi kiem tra thong tin de xuat phan cong (su co + ten nguoi xu ly).
     * Viec NEW -> ASSIGNED, quyen MANAGER va cap nhat history la cua LOG/Rules/Repository.
     */
    public static AssignmentDraft prepareAssignment(Detail detail, String handlerName) {
        if (detail == null || isBlank(detail.getId())) {
            return new AssignmentDraft(AssignmentState.INVALID_INCIDENT, null, null);
        }
        if (isBlank(handlerName)) {
            return new AssignmentDraft(AssignmentState.MISSING_HANDLER, detail.getId(), null);
        }
        return new AssignmentDraft(AssignmentState.READY, detail.getId(),
                stripOuterSpaces(handlerName));
    }

    private static boolean isBlank(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (!Character.isWhitespace(character) && !Character.isSpaceChar(character)) {
                return false;
            }
        }
        return true;
    }

    private static String stripOuterSpaces(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isBlankChar(text.charAt(start))) {
            start++;
        }
        while (end > start && isBlankChar(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static boolean isBlankChar(char character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character);
    }
}

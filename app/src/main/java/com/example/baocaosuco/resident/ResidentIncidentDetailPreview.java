package com.example.baocaosuco.resident;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Du lieu chi tiet/timeline minh hoa; khong truy van Firestore va khong cap quyen. */
public final class ResidentIncidentDetailPreview {
    public enum State { LOADING, CONTENT, EMPTY, ERROR }
    public enum LookupState { FOUND, NOT_FOUND, INVALID }

    public static final class HistoryEntry {
        private final String statusLabel;
        private final String actorLabel;
        private final long demoOccurredAtMillis;
        private final String note;

        public HistoryEntry(String statusLabel, String actorLabel,
                            long demoOccurredAtMillis, String note) {
            if (blank(statusLabel) || blank(actorLabel)) {
                throw new IllegalArgumentException("History display fields are required.");
            }
            this.statusLabel = statusLabel;
            this.actorLabel = actorLabel;
            this.demoOccurredAtMillis = demoOccurredAtMillis;
            this.note = note;
        }
        public String getStatusLabel() { return statusLabel; }
        public String getActorLabel() { return actorLabel; }
        public long getDemoOccurredAtMillis() { return demoOccurredAtMillis; }
        public String getNote() { return note; }
    }

    public static final class Detail {
        private final ResidentIncidentListPreview.Item summary;
        private final String description;
        private final List<HistoryEntry> timeline;

        private Detail(ResidentIncidentListPreview.Item summary, String description,
                       List<HistoryEntry> history) {
            this.summary = summary;
            this.description = description;
            this.timeline = Collections.unmodifiableList(new ArrayList<>(history));
        }
        public String getId() { return summary.getId(); }
        public String getTitle() { return summary.getTitle(); }
        public String getLocation() { return summary.getLocation(); }
        public String getPriorityLabel() { return summary.getPriorityLabel(); }
        public String getStatusLabel() { return summary.getStatusLabel(); }
        public String getDescription() { return description; }
        public List<HistoryEntry> getTimeline() { return timeline; }
    }

    public static final class Snapshot {
        private final State state;
        private final List<Detail> details;
        private final String message;
        private Snapshot(State state, List<Detail> details, String message) {
            this.state = state;
            this.details = Collections.unmodifiableList(new ArrayList<>(details));
            this.message = message;
        }
        public State getState() { return state; }
        public List<Detail> getDetails() { return details; }
        public String getMessage() { return message; }
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

    private ResidentIncidentDetailPreview() { }

    public static Detail fromItem(ResidentIncidentListPreview.Item item, String description,
                                  List<HistoryEntry> timeline) {
        if (item == null || blank(item.getId()) || timeline == null) {
            throw new IllegalArgumentException("Incident and timeline are required.");
        }
        for (HistoryEntry event : timeline) {
            if (event == null) throw new IllegalArgumentException("Null history event.");
        }
        return new Detail(item, description, timeline);
    }

    public static Snapshot loading() {
        return new Snapshot(State.LOADING, Collections.emptyList(), null);
    }

    public static Snapshot error(String message) {
        return new Snapshot(State.ERROR, Collections.emptyList(), message);
    }

    public static Snapshot fromDetails(List<Detail> details) {
        if (details == null) return error("Invalid mock details.");
        Set<String> ids = new HashSet<>();
        for (Detail detail : details) {
            if (detail == null || blank(detail.getId()) || !ids.add(detail.getId())) {
                return error("Invalid or duplicate mock incident.");
            }
        }
        return new Snapshot(details.isEmpty() ? State.EMPTY : State.CONTENT, details, null);
    }

    /** Demo only: uses RES-01A's owner-filtered rows, not actual Firebase UID isolation. */
    public static Snapshot forDemoResident(String demoKey) {
        ResidentIncidentListPreview.Snapshot source =
                ResidentIncidentListPreview.forDemoResident(demoKey);
        if (source.getState() == ResidentIncidentListPreview.State.ERROR) {
            return error(source.getMessage());
        }
        List<Detail> details = new ArrayList<>();
        for (ResidentIncidentListPreview.Item item : source.getItems()) {
            List<HistoryEntry> events = new ArrayList<>();
            events.add(new HistoryEntry("Mới", "Cư dân minh họa", 1_000L, null));
            if ("demo-incident-02".equals(item.getId()) ||
                    "demo-incident-03".equals(item.getId())) {
                events.add(new HistoryEntry("Đã giao", "Quản lý minh họa", 2_000L, null));
            }
            if ("demo-incident-03".equals(item.getId())) {
                events.add(new HistoryEntry("Đang xử lý", "Quản lý minh họa", 3_000L, null));
            }
            details.add(fromItem(item, "Mô tả minh họa: " + item.getTitle(), events));
        }
        return fromDetails(details);
    }

    /** Missing IDs appear as NOT_FOUND even if another mock owner has that incident. */
    public static LookupResult findById(Snapshot snapshot, String incidentId) {
        if (snapshot == null || blank(incidentId) ||
                (snapshot.state != State.CONTENT && snapshot.state != State.EMPTY)) {
            return new LookupResult(LookupState.INVALID, null);
        }
        for (Detail detail : snapshot.details) {
            if (detail.getId().equals(incidentId)) {
                return new LookupResult(LookupState.FOUND, detail);
            }
        }
        return new LookupResult(LookupState.NOT_FOUND, null);
    }

    private static boolean blank(String text) {
        if (text == null || text.isEmpty()) return true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) return false;
        }
        return true;
    }
}

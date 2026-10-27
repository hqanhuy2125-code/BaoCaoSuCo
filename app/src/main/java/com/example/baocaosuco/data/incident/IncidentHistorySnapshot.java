package com.example.baocaosuco.data.incident;

import com.example.baocaosuco.logic.IncidentStatus;

/** Immutable CON-01 v1 nested-history read model. Not a Firestore persistence POJO. */
public final class IncidentHistorySnapshot {
    private final String eventId;
    private final String incidentId;
    private final HistoryAction action;
    private final IncidentStatus fromStatus;
    private final IncidentStatus toStatus;
    private final String actorId;
    private final long occurredAtMillis;
    private final int revision;
    private final String note;

    public IncidentHistorySnapshot(String eventId, String incidentId, HistoryAction action,
                                   IncidentStatus from, IncidentStatus to, String actorId,
                                   long time, int revision, String note) {
        this.eventId = NewIncidentInput.required(eventId, "eventId");
        this.incidentId = NewIncidentInput.required(incidentId, "incidentId");
        this.actorId = NewIncidentInput.required(actorId, "actorId");
        if (this.eventId.contains("/") || time < 0 || action == null || revision < 0) {
            throw new IllegalArgumentException("Invalid history metadata");
        }
        if (note != null && note.codePointCount(0, note.length()) > 500) {
            throw new IllegalArgumentException("Note too long");
        }
        if (!validShape(action, from, to, revision, note)) {
            throw new IllegalArgumentException("Invalid action/status/revision history shape");
        }
        this.action = action;
        this.fromStatus = from;
        this.toStatus = to;
        this.occurredAtMillis = time;
        this.revision = revision;
        this.note = note;
    }

    private static boolean validShape(HistoryAction action, IncidentStatus from,
                                      IncidentStatus to, int revision, String note) {
        if (action == HistoryAction.CREATE) {
            return from == null && to == IncidentStatus.NEW && revision == 0 && note == null;
        }
        if (from == null || to == null || from == to || revision <= 0) return false;
        switch (action) {
            case ASSIGN:
                return from == IncidentStatus.NEW && to == IncidentStatus.ASSIGNED;
            case START_WORK:
                return from == IncidentStatus.ASSIGNED && to == IncidentStatus.IN_PROGRESS;
            case MARK_COMPLETE:
                return from == IncidentStatus.IN_PROGRESS && to == IncidentStatus.COMPLETED;
            case CONFIRM_RESOLVED:
                return from == IncidentStatus.COMPLETED && to == IncidentStatus.CLOSED;
            case REPORT_UNRESOLVED:
                return from == IncidentStatus.COMPLETED && to == IncidentStatus.IN_PROGRESS;
            default:
                return false;
        }
    }

    public String getEventId() { return eventId; }
    public String getIncidentId() { return incidentId; }
    public HistoryAction getAction() { return action; }
    public IncidentStatus getFromStatus() { return fromStatus; }
    public IncidentStatus getToStatus() { return toStatus; }
    public String getActorId() { return actorId; }
    public long getOccurredAtMillis() { return occurredAtMillis; }
    public int getRevision() { return revision; }
    public String getNote() { return note; }
}

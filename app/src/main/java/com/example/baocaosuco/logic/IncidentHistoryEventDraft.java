package com.example.baocaosuco.logic;
public final class IncidentHistoryEventDraft {
    private final String incidentId, actorId, note;
    private final IncidentStatus fromStatus, toStatus;
    private final IncidentAction action;
    private final long timestampEpochMillis;
    IncidentHistoryEventDraft(String incidentId, String actorId, String note,
            IncidentStatus fromStatus, IncidentStatus toStatus, IncidentAction action, long timestamp) {
        this.incidentId = incidentId; this.actorId = actorId; this.note = note;
        this.fromStatus = fromStatus; this.toStatus = toStatus;
        this.action = action; this.timestampEpochMillis = timestamp;
    }
    public String getIncidentId() { return incidentId; }
    public String getActorId() { return actorId; }
    public String getNote() { return note; }
    public IncidentStatus getFromStatus() { return fromStatus; }
    public IncidentStatus getToStatus() { return toStatus; }
    public IncidentAction getAction() { return action; }
    public long getTimestampEpochMillis() { return timestampEpochMillis; }
}

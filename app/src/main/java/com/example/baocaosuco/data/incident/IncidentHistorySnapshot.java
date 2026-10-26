package com.example.baocaosuco.data.incident;

import com.example.baocaosuco.logic.IncidentStatus;

/** Immutable history read model. Collection path/event ID policy remains CON-01 PENDING. */
public final class IncidentHistorySnapshot {
    private final String eventId;
    private final String incidentId;
    private final IncidentStatus fromStatus;
    private final IncidentStatus toStatus;
    private final String actorId;
    private final long occurredAtMillis;
    private final String note;

    public IncidentHistorySnapshot(String eventId, String incidentId, IncidentStatus from,
                                   IncidentStatus to, String actorId, long time, String note) {
        this.eventId = NewIncidentInput.required(eventId, "eventId");
        this.incidentId = NewIncidentInput.required(incidentId, "incidentId");
        this.actorId = NewIncidentInput.required(actorId, "actorId");
        if (from == null || to == null || from == to) {
            throw new IllegalArgumentException("Different source/target status required");
        }
        this.fromStatus = from;
        this.toStatus = to;
        this.occurredAtMillis = time;
        this.note = note;
    }

    public String getEventId() { return eventId; }
    public String getIncidentId() { return incidentId; }
    public IncidentStatus getFromStatus() { return fromStatus; }
    public IncidentStatus getToStatus() { return toStatus; }
    public String getActorId() { return actorId; }
    public long getOccurredAtMillis() { return occurredAtMillis; }
    public String getNote() { return note; }
}

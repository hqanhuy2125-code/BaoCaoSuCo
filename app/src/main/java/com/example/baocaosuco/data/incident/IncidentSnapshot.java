package com.example.baocaosuco.data.incident;

import com.example.baocaosuco.logic.IncidentStatus;
import com.example.baocaosuco.logic.IncidentStatusCodec;

/**
 * Immutable repository read-model candidate. Absolute epoch milliseconds are a
 * Java boundary type; a Firebase adapter must convert validated Firestore Timestamp.
 * Not a Firestore POJO and not an accepted production collection schema.
 */
public final class IncidentSnapshot {
    private final String id;
    private final String residentId;
    private final String title;
    private final String description;
    private final String location;
    private final String priority;
    private final IncidentStatus status;
    private final String assignedTo;
    private final long createdAtMillis;
    private final long dueAtMillis;
    private final long updatedAtMillis;

    public IncidentSnapshot(String id, String residentId, NewIncidentInput input,
                            IncidentStatus status, String assignedTo, long createdAtMillis,
                            long dueAtMillis, long updatedAtMillis) {
        this.id = NewIncidentInput.required(id, "id");
        this.residentId = NewIncidentInput.required(residentId, "residentId");
        if (input == null || status == null) throw new IllegalArgumentException("Missing incident data");
        if (dueAtMillis < createdAtMillis || updatedAtMillis < createdAtMillis) {
            throw new IllegalArgumentException("Invalid incident timestamps");
        }
        this.title = input.getTitle();
        this.description = input.getDescription();
        this.location = input.getLocation();
        this.priority = input.getPriority();
        this.status = status;
        this.assignedTo = assignedTo;
        this.createdAtMillis = createdAtMillis;
        this.dueAtMillis = dueAtMillis;
        this.updatedAtMillis = updatedAtMillis;
    }

    public String getId() { return id; }
    public String getResidentId() { return residentId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public String getPriority() { return priority; }
    public IncidentStatus getStatus() { return status; }
    public String getStatusCode() { return IncidentStatusCodec.toCanonical(status); }
    public String getAssignedTo() { return assignedTo; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public long getDueAtMillis() { return dueAtMillis; }
    public long getUpdatedAtMillis() { return updatedAtMillis; }

    /** Copies rather than mutating the caller-visible record. */
    public IncidentSnapshot withTransition(IncidentStatus next, String handler, long updatedAt) {
        return new IncidentSnapshot(id, residentId,
                new NewIncidentInput(title, description, location, priority),
                next, handler, createdAtMillis, dueAtMillis, updatedAt);
    }
}

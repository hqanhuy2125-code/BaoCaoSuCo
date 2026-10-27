package com.example.baocaosuco.data.incident;

import com.example.baocaosuco.logic.IncidentDeadlinePolicy;
import com.example.baocaosuco.logic.IncidentStatus;
import com.example.baocaosuco.logic.IncidentStatusCodec;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable CON-01 v1 read model. dueAt is always derived from trusted createdAt
 * + canonical SLA; it is NOT a persisted, client-set Firestore field.
 * The real adapter MUST validate Firestore Timestamp and trust boundaries.
 */
public final class IncidentSnapshot {
    private static final Map<String, Long> MVP_SLA;
    static {
        Map<String, Long> values = new HashMap<>();
        values.put("URGENT", 4L * 60 * 60 * 1000);
        values.put("NORMAL", 72L * 60 * 60 * 1000);
        values.put("LOW", 168L * 60 * 60 * 1000);
        MVP_SLA = Collections.unmodifiableMap(values);
    }

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
    private final int revision;
    private final String lastEventId;

    public IncidentSnapshot(String id, String residentId, NewIncidentInput input,
                            IncidentStatus status, String assignedTo, long createdAtMillis,
                            long updatedAtMillis, int revision, String lastEventId) {
        this.id = NewIncidentInput.required(id, "id");
        this.residentId = NewIncidentInput.required(residentId, "residentId");
        if (input == null || status == null) {
            throw new IllegalArgumentException("Missing incident data");
        }
        if (createdAtMillis < 0 || updatedAtMillis < createdAtMillis || revision < 0) {
            throw new IllegalArgumentException("Invalid incident timestamps/revision");
        }
        if ((status == IncidentStatus.NEW) != (revision == 0)) {
            throw new IllegalArgumentException("Initial NEW status requires revision 0");
        }
        if (status == IncidentStatus.NEW && assignedTo != null) {
            throw new IllegalArgumentException("NEW incident cannot be assigned");
        }
        if (status != IncidentStatus.NEW && (assignedTo == null || assignedTo.trim().isEmpty())) {
            throw new IllegalArgumentException("Transitioned incident needs assignee");
        }
        this.title = input.getTitle();
        this.description = input.getDescription();
        this.location = input.getLocation();
        this.priority = input.getPriority();
        this.status = status;
        this.assignedTo = assignedTo == null ? null :
                NewIncidentInput.boundedRequired(assignedTo, "assignedTo", 120);
        this.createdAtMillis = createdAtMillis;
        try {
            this.dueAtMillis = IncidentDeadlinePolicy.calculateDueAt(createdAtMillis, priority, MVP_SLA);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Incident deadline overflow", overflow);
        }
        this.updatedAtMillis = updatedAtMillis;
        this.revision = revision;
        this.lastEventId = NewIncidentInput.required(lastEventId, "lastEventId");
        if (this.lastEventId.contains("/")) {
            throw new IllegalArgumentException("Invalid lastEventId path segment");
        }
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
    public int getRevision() { return revision; }
    public String getLastEventId() { return lastEventId; }

    /**
     * Pure snapshot copy for a prevalidated LOG transition, NOT authorization,
     * event persistence or a real transaction; identity comes from existing record.
     */
    public IncidentSnapshot withTransition(IncidentStatus next, String handler,
                                           long updatedAt, String nextEventId) {
        if (next == null || next == status || status == IncidentStatus.CLOSED ||
                updatedAt < updatedAtMillis || revision == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid local transition copy");
        }
        // Actual LOG role/owner and persisted version checks belong to secure Repository.
        return new IncidentSnapshot(id, residentId,
                new NewIncidentInput(title, description, location, priority),
                next, handler == null ? assignedTo : handler, createdAtMillis,
                updatedAt, revision + 1, nextEventId);
    }
}

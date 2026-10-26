package com.example.baocaosuco.logic;
/** Immutable write proposal. Not a commit confirmation. */
public final class IncidentTransitionPlan {
    private final String eventId;
    private final IncidentHistoryEventDraft history;
    private IncidentTransitionPlan(String eventId, IncidentHistoryEventDraft history) {
        this.eventId = eventId; this.history = history;
    }
    public static IncidentTransitionPlan prepare(String eventId, IncidentHistoryEventDraft history) {
        if (eventId == null || eventId.trim().isEmpty() || history == null)
            throw new IllegalArgumentException("Missing eventId/history");
        return new IncidentTransitionPlan(eventId, history);
    }
    public String getEventId() { return eventId; }
    public IncidentHistoryEventDraft getHistory() { return history; }
    public IncidentStatus getExpectedStatus() { return history.getFromStatus(); }
    public IncidentStatus getNextStatus() { return history.getToStatus(); }
}

package com.example.baocaosuco.logic;
/** Builds drafts only; Repository must verify trusted role/owner, version and atomic commit. */
public final class IncidentHistoryEventFactory {
    private IncidentHistoryEventFactory() {}
    public static IncidentHistoryEventDraft create(String incidentId, String actorId,
            IncidentStatus expectedStatus, IncidentStatus actualCurrentStatus,
            IncidentAction action, TransitionDecision decision, long trustedTimestamp, String note) {
        if (blank(incidentId) || blank(actorId)) throw new IllegalArgumentException("Missing ID");
        if (trustedTimestamp < 0) throw new IllegalArgumentException("Invalid timestamp");
        if (expectedStatus == null || actualCurrentStatus == null || expectedStatus != actualCurrentStatus)
            throw new IllegalArgumentException("STALE_STATUS");
        if (action == null || decision == null || !decision.isAllowed() || decision.getNextStatus() == null)
            throw new IllegalArgumentException("INVALID_TRANSITION_DECISION");
        IncidentStatus next = expectedNext(expectedStatus, action);
        if (next == null || next != decision.getNextStatus())
            throw new IllegalArgumentException("INVALID_TRANSITION_DECISION");
        return new IncidentHistoryEventDraft(incidentId, actorId, note, expectedStatus, next, action, trustedTimestamp);
    }
    private static IncidentStatus expectedNext(IncidentStatus from, IncidentAction action) {
        switch (action) {
            case ASSIGN: return from == IncidentStatus.NEW ? IncidentStatus.ASSIGNED : null;
            case START_WORK: return from == IncidentStatus.ASSIGNED ? IncidentStatus.IN_PROGRESS : null;
            case MARK_COMPLETE: return from == IncidentStatus.IN_PROGRESS ? IncidentStatus.COMPLETED : null;
            case CONFIRM_RESOLVED: return from == IncidentStatus.COMPLETED ? IncidentStatus.CLOSED : null;
            case REPORT_UNRESOLVED: return from == IncidentStatus.COMPLETED ? IncidentStatus.IN_PROGRESS : null;
            default: return null;
        }
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}

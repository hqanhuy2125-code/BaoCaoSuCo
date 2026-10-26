package com.example.baocaosuco.logic;
/** Pure policy: role and ownership must already be verified by a trusted layer. */
public final class IncidentTransitionPolicy {
    private IncidentTransitionPolicy() {}
    public static TransitionDecision evaluate(IncidentStatus current, IncidentAction action,
            ActorRole verifiedRole, boolean verifiedReportOwner, boolean assignedToPresent) {
        if (current == null || action == null || verifiedRole == null)
            return TransitionDecision.reject("INVALID_INPUT");
        if (current == IncidentStatus.CLOSED) return TransitionDecision.reject("ALREADY_CLOSED");
        switch (action) {
            case ASSIGN:
                if (verifiedRole != ActorRole.MANAGER) return TransitionDecision.reject("WRONG_ROLE");
                if (current != IncidentStatus.NEW) return TransitionDecision.reject("INVALID_TRANSITION");
                if (!assignedToPresent) return TransitionDecision.reject("MISSING_ASSIGNEE");
                return TransitionDecision.allow(IncidentStatus.ASSIGNED);
            case START_WORK:
                if (verifiedRole != ActorRole.MANAGER) return TransitionDecision.reject("WRONG_ROLE");
                return current == IncidentStatus.ASSIGNED
                    ? TransitionDecision.allow(IncidentStatus.IN_PROGRESS)
                    : TransitionDecision.reject("INVALID_TRANSITION");
            case MARK_COMPLETE:
                if (verifiedRole != ActorRole.MANAGER) return TransitionDecision.reject("WRONG_ROLE");
                return current == IncidentStatus.IN_PROGRESS
                    ? TransitionDecision.allow(IncidentStatus.COMPLETED)
                    : TransitionDecision.reject("INVALID_TRANSITION");
            case CONFIRM_RESOLVED:
            case REPORT_UNRESOLVED:
                if (verifiedRole != ActorRole.RESIDENT) return TransitionDecision.reject("WRONG_ROLE");
                if (!verifiedReportOwner) return TransitionDecision.reject("NOT_REPORT_OWNER");
                if (current != IncidentStatus.COMPLETED) return TransitionDecision.reject("INVALID_TRANSITION");
                return TransitionDecision.allow(action == IncidentAction.CONFIRM_RESOLVED
                    ? IncidentStatus.CLOSED : IncidentStatus.IN_PROGRESS);
            default: return TransitionDecision.reject("UNKNOWN_ACTION");
        }
    }
    public static boolean isAllowedTransition(IncidentStatus current, IncidentAction action,
            ActorRole verifiedRole, boolean verifiedReportOwner, boolean assignedToPresent) {
        return evaluate(current, action, verifiedRole, verifiedReportOwner, assignedToPresent).isAllowed();
    }
}

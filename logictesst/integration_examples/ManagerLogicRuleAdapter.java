package com.example.baocaosuco.integration;
import com.example.baocaosuco.logic.*;
import com.example.baocaosuco.manager.ManagerIncidentActionCoordinator;
/** Copy into integration branch only after MGR module is present. */
public final class ManagerLogicRuleAdapter implements ManagerIncidentActionCoordinator.RuleAdapter {
    @Override public ManagerIncidentActionCoordinator.Decision evaluate(
            ManagerIncidentActionCoordinator.Action action, String status, boolean hasAssignee) {
        IncidentStatus current = IncidentStatusCodec.parseCanonical(status);
        if (action == null || current == null) return ManagerIncidentActionCoordinator.Decision.reject();
        IncidentAction mapped;
        switch(action) {
            case ASSIGN: mapped = IncidentAction.ASSIGN; break;
            case START_WORK: mapped = IncidentAction.START_WORK; break;
            case MARK_COMPLETE: mapped = IncidentAction.MARK_COMPLETE; break;
            default: return ManagerIncidentActionCoordinator.Decision.reject();
        }
        TransitionDecision d = IncidentTransitionPolicy.evaluate(current, mapped, ActorRole.MANAGER, false, hasAssignee);
        return d.isAllowed() ? ManagerIncidentActionCoordinator.Decision.allow(d.getNextStatus().name())
                : ManagerIncidentActionCoordinator.Decision.reject();
    }
}

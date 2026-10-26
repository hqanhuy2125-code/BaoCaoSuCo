package com.example.baocaosuco.integration.mock;

import com.example.baocaosuco.logic.ActorRole;
import com.example.baocaosuco.logic.IncidentAction;
import com.example.baocaosuco.logic.IncidentStatus;
import com.example.baocaosuco.logic.IncidentStatusCodec;
import com.example.baocaosuco.logic.IncidentTransitionPolicy;
import com.example.baocaosuco.logic.TransitionDecision;
import com.example.baocaosuco.manager.ManagerIncidentActionCoordinator;

/**
 * MOCK UI demonstration of the LOG state machine only.
 * ActorRole.MANAGER below is a synthetic scenario, NOT a verified identity.
 * Never use as production authorization; CommitGateway must be trusted.
 */
public final class MockManagerLogicRuleAdapter
        implements ManagerIncidentActionCoordinator.RuleAdapter {

    @Override
    public ManagerIncidentActionCoordinator.Decision evaluate(
            ManagerIncidentActionCoordinator.Action action,
            String currentStatus, boolean assignedToPresent) {
        IncidentStatus current = MockIncidentValueMapper.previewStatus(currentStatus);
        if (current == null || action == null) {
            return ManagerIncidentActionCoordinator.Decision.reject();
        }
        IncidentAction mapped;
        switch (action) {
            case ASSIGN: mapped = IncidentAction.ASSIGN; break;
            case START_WORK: mapped = IncidentAction.START_WORK; break;
            case MARK_COMPLETE: mapped = IncidentAction.MARK_COMPLETE; break;
            default: return ManagerIncidentActionCoordinator.Decision.reject();
        }
        // Policy preview only. Authentication and stored status are NOT checked.
        TransitionDecision result = IncidentTransitionPolicy.evaluate(
                current, mapped, ActorRole.MANAGER, false, assignedToPresent);
        return result.isAllowed()
                ? ManagerIncidentActionCoordinator.Decision.allow(
                        IncidentStatusCodec.toCanonical(result.getNextStatus()))
                : ManagerIncidentActionCoordinator.Decision.reject();
    }

}

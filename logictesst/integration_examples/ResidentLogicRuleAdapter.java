package com.example.baocaosuco.integration;
import com.example.baocaosuco.logic.*;
import com.example.baocaosuco.resident.ResidentResolutionCoordinator;
/** Preliminary state check only. CommitGateway MUST independently verify auth.uid == report owner. */
public final class ResidentLogicRuleAdapter implements ResidentResolutionCoordinator.RuleAdapter {
    @Override public ResidentResolutionCoordinator.Decision evaluate(
            ResidentResolutionCoordinator.Feedback feedback, String status) {
        IncidentStatus current = IncidentStatusCodec.parseCanonical(status);
        if (feedback == null || current != IncidentStatus.COMPLETED)
            return ResidentResolutionCoordinator.Decision.reject();
        IncidentAction action = feedback == ResidentResolutionCoordinator.Feedback.RESOLVED
                ? IncidentAction.CONFIRM_RESOLVED : IncidentAction.REPORT_UNRESOLVED;
        // Cannot validate ownership with this interface: this is NOT authorization.
        IncidentStatus next = action == IncidentAction.CONFIRM_RESOLVED ? IncidentStatus.CLOSED : IncidentStatus.IN_PROGRESS;
        return ResidentResolutionCoordinator.Decision.allow(next.name());
    }
}

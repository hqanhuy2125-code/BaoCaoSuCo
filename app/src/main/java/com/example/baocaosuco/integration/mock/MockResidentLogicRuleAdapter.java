package com.example.baocaosuco.integration.mock;

import com.example.baocaosuco.logic.IncidentAction;
import com.example.baocaosuco.logic.IncidentStatus;
import com.example.baocaosuco.logic.IncidentStatusCodec;
import com.example.baocaosuco.resident.ResidentResolutionCoordinator;

/**
 * Preliminary state-only preview for resident feedback. Cannot verify
 * the report owner because RES-05A.RuleAdapter has no identity parameter.
 * Never attach a real CommitGateway without independently verifying auth.uid.
 */
public final class MockResidentLogicRuleAdapter
        implements ResidentResolutionCoordinator.RuleAdapter {

    @Override
    public ResidentResolutionCoordinator.Decision evaluate(
            ResidentResolutionCoordinator.Feedback feedback, String currentStatus) {
        IncidentStatus current = MockIncidentValueMapper.previewStatus(currentStatus);
        if (feedback == null || current != IncidentStatus.COMPLETED) {
            return ResidentResolutionCoordinator.Decision.reject();
        }
        IncidentAction action = feedback == ResidentResolutionCoordinator.Feedback.RESOLVED
                ? IncidentAction.CONFIRM_RESOLVED
                : IncidentAction.REPORT_UNRESOLVED;
        // Next-state preview only. This is not an owner/role authorization.
        IncidentStatus next = action == IncidentAction.CONFIRM_RESOLVED
                ? IncidentStatus.CLOSED : IncidentStatus.IN_PROGRESS;
        return ResidentResolutionCoordinator.Decision.allow(
                IncidentStatusCodec.toCanonical(next));
    }
}

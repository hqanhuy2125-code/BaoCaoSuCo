package com.example.baocaosuco.logic;

import org.junit.Test;
import static org.junit.Assert.*;

/** Exhaustive role/status/action regression contract for the approved two-role MVP. */
public class IncidentMvpPolicyMatrixTest {
    private static IncidentStatus expected(IncidentStatus from, IncidentAction action,
            ActorRole role) {
        if (role == ActorRole.MANAGER) {
            if (from == IncidentStatus.NEW && action == IncidentAction.ASSIGN)
                return IncidentStatus.ASSIGNED;
            if (from == IncidentStatus.ASSIGNED && action == IncidentAction.START_WORK)
                return IncidentStatus.IN_PROGRESS;
            if (from == IncidentStatus.IN_PROGRESS && action == IncidentAction.MARK_COMPLETE)
                return IncidentStatus.COMPLETED;
        }
        if (role == ActorRole.RESIDENT && from == IncidentStatus.COMPLETED) {
            if (action == IncidentAction.CONFIRM_RESOLVED) return IncidentStatus.CLOSED;
            if (action == IncidentAction.REPORT_UNRESOLVED) return IncidentStatus.IN_PROGRESS;
        }
        return null;
    }

    @Test public void everyRoleStatusActionMatchesMvpMatrix() {
        for (ActorRole role : ActorRole.values()) {
            for (IncidentStatus from : IncidentStatus.values()) {
                for (IncidentAction action : IncidentAction.values()) {
                    IncidentStatus next = expected(from, action, role);
                    TransitionDecision result = IncidentTransitionPolicy.evaluate(
                            from, action, role, true, true);
                    assertEquals(role + "/" + from + "/" + action,
                            next != null, result.isAllowed());
                    assertEquals(role + "/" + from + "/" + action,
                            next, result.getNextStatus());
                    assertEquals(result.isAllowed(), IncidentTransitionPolicy.isAllowedTransition(
                            from, action, role, true, true));
                }
            }
        }
    }

    @Test public void ownerMustBeVerifiedForBothResidentActions() {
        for (IncidentAction action : new IncidentAction[]{
                IncidentAction.CONFIRM_RESOLVED, IncidentAction.REPORT_UNRESOLVED}) {
            TransitionDecision decision = IncidentTransitionPolicy.evaluate(
                    IncidentStatus.COMPLETED, action, ActorRole.RESIDENT, false, true);
            assertFalse(decision.isAllowed());
            assertEquals("NOT_REPORT_OWNER", decision.getReason());
        }
    }

    @Test public void assignmentRequiresAssigneeOnlyWhenAssigning() {
        TransitionDecision missing = IncidentTransitionPolicy.evaluate(
                IncidentStatus.NEW, IncidentAction.ASSIGN, ActorRole.MANAGER, false, false);
        assertFalse(missing.isAllowed());
        assertEquals("MISSING_ASSIGNEE", missing.getReason());
        assertTrue(IncidentTransitionPolicy.evaluate(
                IncidentStatus.ASSIGNED, IncidentAction.START_WORK,
                ActorRole.MANAGER, false, false).isAllowed());
    }

    @Test public void historyFactoryRejectsMismatchedTargetForEveryValidAction() {
        for (IncidentStatus from : IncidentStatus.values()) {
            for (IncidentAction action : IncidentAction.values()) {
                ActorRole role = action == IncidentAction.CONFIRM_RESOLVED
                        || action == IncidentAction.REPORT_UNRESOLVED
                        ? ActorRole.RESIDENT : ActorRole.MANAGER;
                IncidentStatus next = expected(from, action, role);
                if (next == null) continue;
                TransitionDecision valid = TransitionDecision.allow(next);
                IncidentHistoryEventDraft event = IncidentHistoryEventFactory.create(
                        "incident", "actor", from, from, action, valid, 123L, null);
                assertEquals(next, event.getToStatus());
                for (IncidentStatus wrong : IncidentStatus.values()) {
                    if (wrong == next) continue;
                    try {
                        IncidentHistoryEventFactory.create("incident", "actor", from, from,
                                action, TransitionDecision.allow(wrong), 123L, null);
                        fail("Accepted invalid target: " + from + "/" + action + "/" + wrong);
                    } catch (IllegalArgumentException expectedException) {
                        assertEquals("INVALID_TRANSITION_DECISION", expectedException.getMessage());
                    }
                }
            }
        }
    }

    @Test public void canonicalStatusCodecRejectsAliasesAndNull() {
        for (IncidentStatus status : IncidentStatus.values()) {
            assertEquals(status, IncidentStatusCodec.parseCanonical(
                    IncidentStatusCodec.toCanonical(status)));
        }
        assertNull(IncidentStatusCodec.parseCanonical(null));
        assertNull(IncidentStatusCodec.parseCanonical("completed"));
        assertNull(IncidentStatusCodec.parseCanonical(" COMPLETED "));
        assertNull(IncidentStatusCodec.parseCanonical("REOPENED"));
        try {
            IncidentStatusCodec.toCanonical(null);
            fail("Expected null status rejection");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}

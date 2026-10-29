package com.example.baocaosuco.logic;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** Kiem tra cac bien dau vao cua LOG, khong thay doi hop dong chung. */
public class IncidentLogicBoundaryTest {
    @Test public void allCombinationsHaveExpectedTransitions() {
        for (IncidentStatus status : IncidentStatus.values())
            for (IncidentAction action : IncidentAction.values())
                for (ActorRole role : ActorRole.values())
                    for (boolean owner : new boolean[]{false, true})
                        for (boolean assignee : new boolean[]{false, true}) {
                            TransitionDecision decision = IncidentTransitionPolicy.evaluate(status, action, role, owner, assignee);
                            boolean manager = role == ActorRole.MANAGER;
                            boolean expected = (manager && (
                                    (status == IncidentStatus.NEW && action == IncidentAction.ASSIGN && assignee) ||
                                    (status == IncidentStatus.ASSIGNED && action == IncidentAction.START_WORK) ||
                                    (status == IncidentStatus.IN_PROGRESS && action == IncidentAction.MARK_COMPLETE))) ||
                                    (!manager && owner && status == IncidentStatus.COMPLETED &&
                                    (action == IncidentAction.CONFIRM_RESOLVED || action == IncidentAction.REPORT_UNRESOLVED));
                            assertEquals(status + "/" + action + "/" + role + "/" + owner + "/" + assignee,
                                    expected, decision.isAllowed());
                            assertEquals(expected, decision.getNextStatus() != null);
                        }
    }

    @Test public void nullInputsAreRejected() {
        assertFalse(IncidentTransitionPolicy.evaluate(null, IncidentAction.ASSIGN, ActorRole.MANAGER, false, true).isAllowed());
        assertFalse(IncidentTransitionPolicy.evaluate(IncidentStatus.NEW, null, ActorRole.MANAGER, false, true).isAllowed());
        assertFalse(IncidentTransitionPolicy.evaluate(IncidentStatus.NEW, IncidentAction.ASSIGN, null, false, true).isAllowed());
        assertNull(IncidentStatusCodec.parseCanonical(null));
        assertNull(IncidentStatusCodec.parseCanonical("new"));
        assertNull(IncidentStatusCodec.parseCanonical(" NEW "));
    }

    @Test public void canonicalStatusRoundTrip() {
        for (IncidentStatus status : IncidentStatus.values())
            assertEquals(status, IncidentStatusCodec.parseCanonical(IncidentStatusCodec.toCanonical(status)));
    }

    @Test public void deadlineBoundaryChecks() {
        Map<String, Long> sla = new HashMap<>(); sla.put("HIGH", 1L);
        assertEquals(1L, IncidentDeadlinePolicy.calculateDueAt(0L, "HIGH", sla));
        assertThrowsIllegalArgument(() -> IncidentDeadlinePolicy.calculateDueAt(-1L, "HIGH", sla));
        assertThrowsIllegalArgument(() -> IncidentDeadlinePolicy.calculateDueAt(0L, null, sla));
        sla.put("HIGH", 0L);
        assertThrowsIllegalArgument(() -> IncidentDeadlinePolicy.calculateDueAt(0L, "HIGH", sla));
        sla.put("HIGH", -1L);
        assertThrowsIllegalArgument(() -> IncidentDeadlinePolicy.calculateDueAt(0L, "HIGH", sla));
    }

    @Test public void historyRejectsForgedNextStatusAndEmptyIdentifiers() {
        assertThrowsIllegalArgument(() -> IncidentHistoryEventFactory.create("i", "a",
                IncidentStatus.NEW, IncidentStatus.NEW, IncidentAction.ASSIGN,
                TransitionDecision.allow(IncidentStatus.CLOSED), 1L, null));
        assertThrowsIllegalArgument(() -> IncidentHistoryEventFactory.create(" ", "a",
                IncidentStatus.NEW, IncidentStatus.NEW, IncidentAction.ASSIGN,
                TransitionDecision.allow(IncidentStatus.ASSIGNED), 1L, null));
        assertThrowsIllegalArgument(() -> IncidentHistoryEventFactory.create("i", " ",
                IncidentStatus.NEW, IncidentStatus.NEW, IncidentAction.ASSIGN,
                TransitionDecision.allow(IncidentStatus.ASSIGNED), 1L, null));
    }

    @Test public void transitionPlanRejectsMissingEventId() {
        IncidentHistoryEventDraft history = IncidentHistoryEventFactory.create("i", "a",
                IncidentStatus.NEW, IncidentStatus.NEW, IncidentAction.ASSIGN,
                TransitionDecision.allow(IncidentStatus.ASSIGNED), 1L, null);
        assertThrowsIllegalArgument(() -> IncidentTransitionPlan.prepare(" ", history));
        IncidentTransitionPlan plan = IncidentTransitionPlan.prepare("event-1", history);
        assertEquals(IncidentStatus.NEW, plan.getExpectedStatus());
        assertEquals(IncidentStatus.ASSIGNED, plan.getNextStatus());
    }

    private static void assertThrowsIllegalArgument(Runnable action) {
        try { action.run(); fail("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { /* expected */ }
    }
}

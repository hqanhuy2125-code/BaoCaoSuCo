package com.example.baocaosuco.logic;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** Additional boundary checks for LOG-01 through LOG-05. */
public class IncidentLogicBoundaryTest {
    private static void invalid(Runnable r) {
        try { r.run(); fail("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { /* pass */ }
    }
    @Test public void nullTransitionInputsRejected() {
        assertFalse(IncidentTransitionPolicy.evaluate(null, IncidentAction.ASSIGN, ActorRole.MANAGER, false, true).isAllowed());
        assertFalse(IncidentTransitionPolicy.evaluate(IncidentStatus.NEW, null, ActorRole.MANAGER, false, true).isAllowed());
        assertFalse(IncidentTransitionPolicy.evaluate(IncidentStatus.NEW, IncidentAction.ASSIGN, null, false, true).isAllowed());
    }
    @Test public void closedIncidentCannotTransition() {
        for (IncidentAction action : IncidentAction.values())
            for (ActorRole role : ActorRole.values())
                assertFalse(IncidentTransitionPolicy.evaluate(IncidentStatus.CLOSED, action, role, true, true).isAllowed());
    }
    @Test public void ownerAndRoleMustBeVerified() {
        assertFalse(IncidentTransitionPolicy.evaluate(IncidentStatus.COMPLETED, IncidentAction.CONFIRM_RESOLVED, ActorRole.RESIDENT, false, false).isAllowed());
        assertFalse(IncidentTransitionPolicy.evaluate(IncidentStatus.COMPLETED, IncidentAction.REPORT_UNRESOLVED, ActorRole.MANAGER, true, true).isAllowed());
        assertFalse(IncidentTransitionPolicy.evaluate(IncidentStatus.NEW, IncidentAction.ASSIGN, ActorRole.RESIDENT, true, true).isAllowed());
    }
    @Test public void missingAssigneeRejected() {
        assertFalse(IncidentTransitionPolicy.evaluate(IncidentStatus.NEW, IncidentAction.ASSIGN, ActorRole.MANAGER, false, false).isAllowed());
    }
    @Test public void invalidDeadlineInputsRejected() {
        Map<String,Long> sla = new HashMap<>(); sla.put("HIGH", 100L);
        invalid(() -> IncidentDeadlinePolicy.calculateDueAt(-1, "HIGH", sla));
        invalid(() -> IncidentDeadlinePolicy.calculateDueAt(0, null, sla));
        invalid(() -> IncidentDeadlinePolicy.calculateDueAt(0, "HIGH", null));
        invalid(() -> IncidentDeadlinePolicy.calculateDueAt(0, "LOW", sla));
    }
    @Test public void nonPositiveAndNullSlaRejected() {
        Map<String,Long> sla = new HashMap<>();
        for (Long value : new Long[]{null, 0L, -1L}) {
            sla.put("HIGH", value);
            invalid(() -> IncidentDeadlinePolicy.calculateDueAt(0, "HIGH", sla));
        }
    }
    @Test public void deadlineBoundaryAndOverflow() {
        Map<String,Long> sla = new HashMap<>(); sla.put("HIGH", 1L);
        assertEquals(1L, IncidentDeadlinePolicy.calculateDueAt(0, "HIGH", sla));
        try { IncidentDeadlinePolicy.calculateDueAt(Long.MAX_VALUE, "HIGH", sla); fail("Expected overflow"); }
        catch (ArithmeticException expected) { /* pass */ }
    }
    @Test public void historyMissingIdsAndNegativeTimestampRejected() {
        TransitionDecision d = TransitionDecision.allow(IncidentStatus.ASSIGNED);
        invalid(() -> IncidentHistoryEventFactory.create(" ", "actor", IncidentStatus.NEW, IncidentStatus.NEW, IncidentAction.ASSIGN, d, 0, null));
        invalid(() -> IncidentHistoryEventFactory.create("incident", null, IncidentStatus.NEW, IncidentStatus.NEW, IncidentAction.ASSIGN, d, 0, null));
        invalid(() -> IncidentHistoryEventFactory.create("incident", "actor", IncidentStatus.NEW, IncidentStatus.NEW, IncidentAction.ASSIGN, d, -1, null));
    }
    @Test public void historyRejectsStaleAndInvalidDecisions() {
        TransitionDecision allowed = TransitionDecision.allow(IncidentStatus.ASSIGNED);
        invalid(() -> IncidentHistoryEventFactory.create("i", "a", IncidentStatus.NEW, IncidentStatus.ASSIGNED, IncidentAction.ASSIGN, allowed, 0, null));
        invalid(() -> IncidentHistoryEventFactory.create("i", "a", IncidentStatus.NEW, IncidentStatus.NEW, IncidentAction.ASSIGN, TransitionDecision.reject("NO"), 0, null));
        invalid(() -> IncidentHistoryEventFactory.create("i", "a", IncidentStatus.NEW, IncidentStatus.NEW, IncidentAction.ASSIGN, TransitionDecision.allow(IncidentStatus.CLOSED), 0, null));
        invalid(() -> IncidentHistoryEventFactory.create("i", "a", IncidentStatus.NEW, IncidentStatus.NEW, null, allowed, 0, null));
    }
    @Test public void validHistoryPreservesFields() {
        IncidentHistoryEventDraft draft = IncidentHistoryEventFactory.create("i", "a", IncidentStatus.NEW, IncidentStatus.NEW, IncidentAction.ASSIGN, TransitionDecision.allow(IncidentStatus.ASSIGNED), 0, "note");
        assertEquals("i", draft.getIncidentId());
        assertEquals("a", draft.getActorId());
        assertEquals("note", draft.getNote());
        assertEquals(0L, draft.getTimestampEpochMillis());
        assertEquals(IncidentStatus.NEW, draft.getFromStatus());
        assertEquals(IncidentStatus.ASSIGNED, draft.getToStatus());
        assertEquals(IncidentAction.ASSIGN, draft.getAction());
    }
}

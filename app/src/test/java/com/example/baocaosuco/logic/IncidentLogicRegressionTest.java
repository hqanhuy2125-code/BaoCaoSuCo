package com.example.baocaosuco.logic;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** Regression checks against the MVP domain baseline in STATE_TRANSITIONS.md. */
public class IncidentLogicRegressionTest {
    private static final long HOUR = 60L * 60L * 1000L;

    private static Map<String, Long> mvpSla() {
        Map<String, Long> result = new HashMap<>();
        result.put("URGENT", 4L * HOUR);
        result.put("NORMAL", 72L * HOUR);
        result.put("LOW", 168L * HOUR);
        return result;
    }

    @Test public void threeApprovedMvpSlaDurations() {
        long createdAt = 1793000000000L;
        Map<String, Long> sla = mvpSla();
        assertEquals(createdAt + 4L * HOUR,
                IncidentDeadlinePolicy.calculateDueAt(createdAt, "URGENT", sla));
        assertEquals(createdAt + 72L * HOUR,
                IncidentDeadlinePolicy.calculateDueAt(createdAt, "NORMAL", sla));
        assertEquals(createdAt + 168L * HOUR,
                IncidentDeadlinePolicy.calculateDueAt(createdAt, "LOW", sla));
    }

    @Test public void completedReopenedCompletedClosedLoop() {
        IncidentStatus current = IncidentStatus.COMPLETED;
        TransitionDecision reopen = IncidentTransitionPolicy.evaluate(current,
                IncidentAction.REPORT_UNRESOLVED, ActorRole.RESIDENT, true, true);
        assertTrue(reopen.isAllowed());
        assertEquals(IncidentStatus.IN_PROGRESS, reopen.getNextStatus());
        current = reopen.getNextStatus();

        TransitionDecision completeAgain = IncidentTransitionPolicy.evaluate(current,
                IncidentAction.MARK_COMPLETE, ActorRole.MANAGER, false, true);
        assertTrue(completeAgain.isAllowed());
        assertEquals(IncidentStatus.COMPLETED, completeAgain.getNextStatus());
        current = completeAgain.getNextStatus();

        TransitionDecision close = IncidentTransitionPolicy.evaluate(current,
                IncidentAction.CONFIRM_RESOLVED, ActorRole.RESIDENT, true, true);
        assertTrue(close.isAllowed());
        assertEquals(IncidentStatus.CLOSED, close.getNextStatus());
        assertFalse(IncidentTransitionPolicy.evaluate(close.getNextStatus(),
                IncidentAction.REPORT_UNRESOLVED, ActorRole.RESIDENT, true, true).isAllowed());
    }

    @Test public void reopenedIncidentKeepsOriginalDeadlineInputs() {
        long originalCreatedAt = 1793000000000L;
        Map<String, Long> sla = mvpSla();
        long originalDueAt = IncidentDeadlinePolicy.calculateDueAt(
                originalCreatedAt, "NORMAL", sla);
        TransitionDecision reopen = IncidentTransitionPolicy.evaluate(
                IncidentStatus.COMPLETED, IncidentAction.REPORT_UNRESOLVED,
                ActorRole.RESIDENT, true, true);
        assertTrue(reopen.isAllowed());
        assertEquals(IncidentStatus.IN_PROGRESS, reopen.getNextStatus());
        long dueAtAfterReopen = IncidentDeadlinePolicy.calculateDueAt(
                originalCreatedAt, "NORMAL", sla);
        assertEquals(originalDueAt, dueAtAfterReopen);
    }

    @Test public void optionalNoteCanBeNullOnReopen() {
        TransitionDecision decision = IncidentTransitionPolicy.evaluate(
                IncidentStatus.COMPLETED, IncidentAction.REPORT_UNRESOLVED,
                ActorRole.RESIDENT, true, true);
        assertTrue(decision.isAllowed());
        IncidentHistoryEventDraft draft = IncidentHistoryEventFactory.create(
                "incident-1", "resident-1", IncidentStatus.COMPLETED,
                IncidentStatus.COMPLETED, IncidentAction.REPORT_UNRESOLVED,
                decision, 1000L, null);
        assertEquals(IncidentStatus.IN_PROGRESS, draft.getToStatus());
        assertNull(draft.getNote());
    }
}

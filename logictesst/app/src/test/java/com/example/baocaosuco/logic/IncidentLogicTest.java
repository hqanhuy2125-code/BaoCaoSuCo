package com.example.baocaosuco.logic;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.Map;
public class IncidentLogicTest {
    private TransitionDecision eval(IncidentStatus status, IncidentAction action, ActorRole role,
            boolean owner, boolean assignee) {
        return IncidentTransitionPolicy.evaluate(status, action, role, owner, assignee);
    }
    @Test public void allFiveAllowedTransitions() {
        assertEquals(IncidentStatus.ASSIGNED, eval(IncidentStatus.NEW, IncidentAction.ASSIGN, ActorRole.MANAGER, false, true).getNextStatus());
        assertEquals(IncidentStatus.IN_PROGRESS, eval(IncidentStatus.ASSIGNED, IncidentAction.START_WORK, ActorRole.MANAGER, false, false).getNextStatus());
        assertEquals(IncidentStatus.COMPLETED, eval(IncidentStatus.IN_PROGRESS, IncidentAction.MARK_COMPLETE, ActorRole.MANAGER, false, false).getNextStatus());
        assertEquals(IncidentStatus.CLOSED, eval(IncidentStatus.COMPLETED, IncidentAction.CONFIRM_RESOLVED, ActorRole.RESIDENT, true, false).getNextStatus());
        assertEquals(IncidentStatus.IN_PROGRESS, eval(IncidentStatus.COMPLETED, IncidentAction.REPORT_UNRESOLVED, ActorRole.RESIDENT, true, false).getNextStatus());
    }
    @Test public void invalidActionsRejected() {
        assertFalse(eval(IncidentStatus.NEW, IncidentAction.ASSIGN, ActorRole.MANAGER, false, false).isAllowed());
        assertFalse(eval(IncidentStatus.NEW, IncidentAction.MARK_COMPLETE, ActorRole.MANAGER, false, true).isAllowed());
        assertFalse(eval(IncidentStatus.COMPLETED, IncidentAction.CONFIRM_RESOLVED, ActorRole.MANAGER, true, false).isAllowed());
        assertFalse(eval(IncidentStatus.COMPLETED, IncidentAction.CONFIRM_RESOLVED, ActorRole.RESIDENT, false, false).isAllowed());
        assertFalse(eval(IncidentStatus.CLOSED, IncidentAction.START_WORK, ActorRole.MANAGER, false, true).isAllowed());
        assertFalse(eval(null, IncidentAction.ASSIGN, ActorRole.MANAGER, false, true).isAllowed());
        for (IncidentStatus status : IncidentStatus.values()) {
            for (IncidentAction action : IncidentAction.values()) {
                for (ActorRole role : ActorRole.values()) {
                    boolean allowed = eval(status, action, role, true, true).isAllowed();
                    boolean expected = (role == ActorRole.MANAGER && (
                        (status == IncidentStatus.NEW && action == IncidentAction.ASSIGN) ||
                        (status == IncidentStatus.ASSIGNED && action == IncidentAction.START_WORK) ||
                        (status == IncidentStatus.IN_PROGRESS && action == IncidentAction.MARK_COMPLETE))) ||
                        (role == ActorRole.RESIDENT && status == IncidentStatus.COMPLETED &&
                        (action == IncidentAction.CONFIRM_RESOLVED || action == IncidentAction.REPORT_UNRESOLVED));
                    assertEquals(status + " / " + action + " / " + role, expected, allowed);
                }
            }
        }
    }
    @Test public void deadlineIsConfigurableAndDeterministic() {
        Map<String, Long> sla = new HashMap<>(); sla.put("HIGH", 3600000L);
        long start = 1793000000000L;
        assertEquals(start + 3600000L, IncidentDeadlinePolicy.calculateDueAt(start, "HIGH", sla));
        sla.put("HIGH", 7200000L);
        assertEquals(start + 7200000L, IncidentDeadlinePolicy.calculateDueAt(start, "HIGH", sla));
    }
    @Test(expected = ArithmeticException.class) public void deadlineOverflowRejected() {
        Map<String, Long> sla = new HashMap<>(); sla.put("HIGH", 10L);
        IncidentDeadlinePolicy.calculateDueAt(Long.MAX_VALUE, "HIGH", sla);
    }
    @Test(expected = IllegalArgumentException.class) public void missingSlaRejected() {
        IncidentDeadlinePolicy.calculateDueAt(0, "HIGH", new HashMap<String, Long>());
    }
    @Test public void historyDraftHasCorrectFields() {
        TransitionDecision d = eval(IncidentStatus.NEW, IncidentAction.ASSIGN, ActorRole.MANAGER, false, true);
        IncidentHistoryEventDraft e = IncidentHistoryEventFactory.create("incident-1", "manager-1",
                IncidentStatus.NEW, IncidentStatus.NEW, IncidentAction.ASSIGN, d, 100L, "assigned");
        assertEquals(IncidentStatus.NEW, e.getFromStatus());
        assertEquals(IncidentStatus.ASSIGNED, e.getToStatus());
        assertEquals("manager-1", e.getActorId());
    }
    @Test(expected = IllegalArgumentException.class) public void rejectedDecisionCannotCreateHistory() {
        IncidentHistoryEventFactory.create("i", "a", IncidentStatus.NEW, IncidentStatus.NEW,
                IncidentAction.MARK_COMPLETE, TransitionDecision.reject("INVALID"), 1, null);
    }
    @Test(expected = IllegalArgumentException.class) public void staleStatusCannotCreateHistory() {
        IncidentHistoryEventFactory.create("i", "a", IncidentStatus.NEW, IncidentStatus.ASSIGNED,
                IncidentAction.ASSIGN, TransitionDecision.allow(IncidentStatus.ASSIGNED), 1, null);
    }
}

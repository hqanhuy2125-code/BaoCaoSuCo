package com.example.baocaosuco.data.repository;

import com.example.baocaosuco.data.incident.*;
import com.example.baocaosuco.logic.*;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure-Java contract tests. No Firebase credentials, Rules authorization or writes. */
public final class IncidentContractV1RegressionTest {
    private static NewIncidentInput input(String priority) {
        return new NewIncidentInput("Broken light", "Example incident", "Demo corridor", priority);
    }
    private static IncidentSnapshot created(String priority) {
        return new IncidentSnapshot("incident-1", "resident-A", input(priority),
                IncidentStatus.NEW, null, 1000L, 1000L, 0, "create-id");
    }
    private static String repeat(int n) {
        char[] data = new char[n]; Arrays.fill(data, 'x');
        return new String(data);
    }
    private static void denied(Runnable runnable) {
        try { runnable.run(); fail("must deny invalid contract input"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void fieldLengthBoundariesAcceptAndReject() {
        new NewIncidentInput(repeat(120), repeat(2000), repeat(160), "LOW");
        denied(() -> new NewIncidentInput(repeat(121), "detail", "room", "LOW"));
        denied(() -> new NewIncidentInput("title", repeat(2001), "room", "LOW"));
        denied(() -> new NewIncidentInput("title", "detail", repeat(161), "LOW"));
        denied(() -> new NewIncidentInput("title", "detail", "room", "low"));
    }

    @Test public void unicodeCharacterValidationCountsCodePoints() {
        String nonBmp = new String(Character.toChars(0x1F4A1));
        new NewIncidentInput(repeat(119) + nonBmp, "detail", "room", "NORMAL");
        denied(() -> new NewIncidentInput(repeat(120) + nonBmp, "detail", "room", "NORMAL"));
    }

    @Test public void deadlineIsDerivedFromCanonicalPrioritySla() {
        assertEquals(1000L + 4L * 3600000L, created("URGENT").getDueAtMillis());
        assertEquals(1000L + 72L * 3600000L, created("NORMAL").getDueAtMillis());
        assertEquals(1000L + 168L * 3600000L, created("LOW").getDueAtMillis());
        assertEquals(1000L, created("LOW").getCreatedAtMillis());
    }

    @Test public void snapshotRejectsOverflowAndInvalidRevision() {
        denied(() -> new IncidentSnapshot("i", "r", input("LOW"), IncidentStatus.NEW,
                null, Long.MAX_VALUE - 100L, Long.MAX_VALUE - 100L, 0, "e"));
        denied(() -> new IncidentSnapshot("i", "r", input("LOW"), IncidentStatus.ASSIGNED,
                "Team", 0L, 0L, 0, "e"));
        denied(() -> new IncidentSnapshot("i", "r", input("LOW"), IncidentStatus.NEW,
                null, 0L, 0L, 1, "e"));
        denied(() -> new IncidentSnapshot("i", "r", input("LOW"), IncidentStatus.NEW,
                null, 0L, 0L, -1, "e"));
    }

    @Test public void snapshotEnforcesAssigneeShapeAndEventId() {
        denied(() -> new IncidentSnapshot("i", "r", input("NORMAL"), IncidentStatus.NEW,
                "Team", 100L, 100L, 0, "e"));
        denied(() -> new IncidentSnapshot("i", "r", input("NORMAL"), IncidentStatus.ASSIGNED,
                null, 100L, 100L, 1, "e"));
        denied(() -> new IncidentSnapshot("i", "r", input("NORMAL"), IncidentStatus.ASSIGNED,
                repeat(121), 100L, 100L, 1, "e"));
        denied(() -> new IncidentSnapshot("i", "r", input("NORMAL"), IncidentStatus.NEW,
                null, 100L, 100L, 0, "illegal/path"));
    }

    @Test public void snapshotTransitionCopyKeepsCreatorAndOriginalDeadline() {
        IncidentSnapshot first = created("LOW");
        IncidentSnapshot next = first.withTransition(IncidentStatus.ASSIGNED,
                "  Team Alpha  ", 2000L, "event-1");
        assertEquals("Team Alpha", next.getAssignedTo());
        assertEquals("resident-A", next.getResidentId());
        assertEquals(first.getDueAtMillis(), next.getDueAtMillis());
        assertEquals(1, next.getRevision());
        assertEquals(0, first.getRevision());
        assertEquals("create-id", first.getLastEventId());
        denied(() -> first.withTransition(IncidentStatus.NEW, null, 2000L, "event-2"));
    }

    @Test public void historyCreateEventAcceptsOnlyNullFromAndZeroRevision() {
        IncidentHistorySnapshot e = new IncidentHistorySnapshot("evt0", "i",
                HistoryAction.CREATE, null, IncidentStatus.NEW, "resident-A", 100L, 0, null);
        assertNull(e.getFromStatus());
        assertEquals(HistoryAction.CREATE, e.getAction());
        assertEquals(0, e.getRevision());
        denied(() -> new IncidentHistorySnapshot("e", "i", HistoryAction.CREATE,
                IncidentStatus.NEW, IncidentStatus.NEW, "actor", 100L, 0, null));
        denied(() -> new IncidentHistorySnapshot("e", "i", HistoryAction.CREATE,
                null, IncidentStatus.NEW, "actor", 100L, 1, null));
        denied(() -> new IncidentHistorySnapshot("e", "i", HistoryAction.CREATE,
                null, IncidentStatus.ASSIGNED, "actor", 100L, 0, null));
    }

    @Test public void allFiveHistoryActionsFollowSelectedDomainEdges() {
        HistoryAction[] actions = { HistoryAction.ASSIGN, HistoryAction.START_WORK,
                HistoryAction.MARK_COMPLETE, HistoryAction.CONFIRM_RESOLVED,
                HistoryAction.REPORT_UNRESOLVED };
        IncidentStatus[] from = { IncidentStatus.NEW, IncidentStatus.ASSIGNED,
                IncidentStatus.IN_PROGRESS, IncidentStatus.COMPLETED, IncidentStatus.COMPLETED };
        IncidentStatus[] to = { IncidentStatus.ASSIGNED, IncidentStatus.IN_PROGRESS,
                IncidentStatus.COMPLETED, IncidentStatus.CLOSED, IncidentStatus.IN_PROGRESS };
        for (int i = 0; i < actions.length; i++) {
            IncidentHistorySnapshot valid = new IncidentHistorySnapshot("e" + i, "incident",
                    actions[i], from[i], to[i], "actor", 100L, i + 1, null);
            assertEquals(actions[i], valid.getAction());
            final int n = i;
            denied(() -> new IncidentHistorySnapshot("bad", "incident",
                    actions[n], to[n], from[n], "actor", 100L, n + 1, null));
        }
    }

    @Test public void createIsNotAnAdditionalLogTransitionAction() {
        assertEquals(5, IncidentAction.values().length);
        for (IncidentAction action : IncidentAction.values()) {
            assertEquals(action.name(), HistoryAction.fromTransition(action).name());
        }
        denied(() -> HistoryAction.fromTransition(null));
    }

    @Test public void historyRejectsInvalidMetadataAndNoteLengths() {
        denied(() -> new IncidentHistorySnapshot("bad/path", "i", HistoryAction.ASSIGN,
                IncidentStatus.NEW, IncidentStatus.ASSIGNED, "actor", 100L, 1, null));
        denied(() -> new IncidentHistorySnapshot("e", "i", HistoryAction.ASSIGN,
                IncidentStatus.NEW, IncidentStatus.ASSIGNED, "actor", -1L, 1, null));
        denied(() -> new IncidentHistorySnapshot("e", "i", HistoryAction.ASSIGN,
                IncidentStatus.NEW, IncidentStatus.ASSIGNED, "actor", 100L, 0, null));
        denied(() -> new IncidentHistorySnapshot("e", "i", HistoryAction.ASSIGN,
                IncidentStatus.NEW, IncidentStatus.ASSIGNED, "actor", 100L, 1, repeat(501)));
        IncidentHistorySnapshot e = new IncidentHistorySnapshot("e", "i", HistoryAction.ASSIGN,
                IncidentStatus.NEW, IncidentStatus.ASSIGNED, "actor", 100L, 1, repeat(500));
        assertEquals(500, e.getNote().length());
    }

    @Test public void commandPreconditionAcceptsCanonicalAndStableKey() {
        CommandPrecondition cmd = new CommandPrecondition("COMPLETED", 5, "req-1");
        assertEquals("COMPLETED", cmd.getExpectedCanonicalStatus());
        assertEquals(5, cmd.getExpectedRevision());
        assertEquals("req-1", cmd.getRequestId());
        assertTrue(cmd.matchesObserved("COMPLETED", 5));
        assertFalse(cmd.matchesObserved("ASSIGNED", 5));
        assertFalse(cmd.matchesObserved("COMPLETED", 6));
        assertEquals(cmd.getRequestId(), cmd.getRequestId());
    }

    @Test public void commandRejectsInvalidStatusRevisionAndRequestId() {
        denied(() -> new CommandPrecondition("Hoàn tất", 1, "e"));
        denied(() -> new CommandPrecondition("COMPLETED", -1, "e"));
        denied(() -> new CommandPrecondition("COMPLETED", 1, " "));
        denied(() -> new CommandPrecondition("COMPLETED", 1, "path/e"));
        denied(() -> new CommandPrecondition("NEW", 1, "e"));
        denied(() -> new CommandPrecondition("ASSIGNED", 0, "e"));
    }

    @Test public void repositoryMethodsRequireCommandPreconditions() throws Exception {
        assertNotNull(IncidentRepository.Manager.class.getMethod("assign", String.class,
                CommandPrecondition.class, String.class, IncidentRepository.Callback.class));
        assertNotNull(IncidentRepository.Manager.class.getMethod("start", String.class,
                CommandPrecondition.class, IncidentRepository.Callback.class));
        assertNotNull(IncidentRepository.Manager.class.getMethod("complete", String.class,
                CommandPrecondition.class, IncidentRepository.Callback.class));
        assertNotNull(IncidentRepository.Resident.class.getMethod("confirm", String.class,
                CommandPrecondition.class, boolean.class, IncidentRepository.Callback.class));
    }
}

package com.example.baocaosuco.manager;

import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.incident.NewIncidentInput;
import com.example.baocaosuco.data.repository.CommandPrecondition;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.logic.IncidentStatus;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import static org.junit.Assert.*;

public class ManagerIncidentCoordinatorTest {
    private static IncidentSnapshot item(String id, String priority, IncidentStatus status,
                                         int revision, long created) {
        return new IncidentSnapshot(id, "resident",
                new NewIncidentInput("Title", "Description", "Room A", priority), status,
                status == IncidentStatus.NEW ? null : "Team A",
                created, created + 1, revision, "event-" + revision);
    }
    private static final class Screen implements ManagerIncidentCoordinator.View {
        final List<IncidentRepository.Error> errors = new ArrayList<>();
        List<IncidentSnapshot> rows;
        IncidentSnapshot detail;
        IncidentSnapshot saved;
        boolean busy;
        @Override public void onBusy(boolean state) { busy = state; }
        @Override public void onRows(List<IncidentSnapshot> value) { rows = value; }
        @Override public void onDetail(IncidentSnapshot value) { detail = value; }
        @Override public void onSaved(IncidentSnapshot value) { saved = value; }
        @Override public void onError(IncidentRepository.Error error) { errors.add(error); }
    }
    private static final class Api implements IncidentRepository.Manager {
        IncidentRepository.Callback<List<IncidentSnapshot>> list;
        IncidentRepository.Callback<IncidentSnapshot> delayedTransition;
        CommandPrecondition lastPrecondition;
        String handler;
        int actions;
        @Override public void listAll(IncidentRepository.Callback<List<IncidentSnapshot>> cb) {
            list = cb;
        }
        @Override public void assign(String id, CommandPrecondition expected, String who,
                                     IncidentRepository.Callback<IncidentSnapshot> cb) {
            actions++; lastPrecondition = expected; handler = who;
            delayedTransition = cb;
        }
        @Override public void start(String id, CommandPrecondition expected,
                                    IncidentRepository.Callback<IncidentSnapshot> cb) {
            actions++; lastPrecondition = expected; delayedTransition = cb;
        }
        @Override public void complete(String id, CommandPrecondition expected,
                                       IncidentRepository.Callback<IncidentSnapshot> cb) {
            actions++; lastPrecondition = expected; delayedTransition = cb;
        }
    }

    @Test public void managerListUsesOnlyLoadedPageAndNewestFirstSort() {
        Api api = new Api(); Screen view = new Screen();
        ManagerIncidentCoordinator c = new ManagerIncidentCoordinator(api, view);
        assertTrue(c.loadList());
        List<IncidentSnapshot> source = new ArrayList<>();
        source.add(item("old", "NORMAL", IncidentStatus.NEW, 0, 1000));
        source.add(item("new", "LOW", IncidentStatus.ASSIGNED, 1, 2000));
        api.list.onSuccess(source);
        source.clear();
        assertEquals("new", view.rows.get(0).getId());
        assertEquals(2, view.rows.size());
        assertFalse(view.busy);
    }

    @Test public void localFilterAndDueSortDoNotFetchAnotherPage() {
        Api api = new Api(); Screen view = new Screen();
        ManagerIncidentCoordinator c = new ManagerIncidentCoordinator(api, view);
        c.loadList();
        api.list.onSuccess(java.util.Arrays.asList(
                item("urgent", "URGENT", IncidentStatus.NEW, 0, 1000),
                item("low", "LOW", IncidentStatus.NEW, 0, 500),
                item("assigned", "NORMAL", IncidentStatus.ASSIGNED, 1, 2000)));
        assertTrue(c.setStatusFilter("NEW"));
        assertEquals(2, view.rows.size());
        c.setDueDateFirst(true);
        assertEquals("urgent", view.rows.get(0).getId());
        assertFalse(c.setStatusFilter("UNKNOWN"));
        assertEquals(IncidentRepository.Error.VALIDATION, view.errors.get(0));
        assertTrue(c.setStatusFilter("ALL"));
        assertEquals(3, view.rows.size());
    }

    @Test public void selectionRequiresCachedServerListRow() {
        Api api = new Api(); Screen view = new Screen();
        ManagerIncidentCoordinator c = new ManagerIncidentCoordinator(api, view);
        assertFalse(c.select("unloaded"));
        c.loadList();
        api.list.onSuccess(Collections.singletonList(item("one", "LOW", IncidentStatus.NEW, 0, 1000)));
        assertTrue(c.select("one"));
        assertEquals("one", view.detail.getId());
        assertFalse(c.select("missing"));
    }

    @Test public void assignRequiresFreshCachedVersionAndAValidHandler() {
        Api api = new Api(); Screen view = new Screen();
        ManagerIncidentCoordinator c = new ManagerIncidentCoordinator(api, view);
        IncidentSnapshot item = item("one", "NORMAL", IncidentStatus.NEW, 0, 1000);
        c.loadList(); api.list.onSuccess(Collections.singletonList(item));
        assertFalse(c.assign(item, " "));
        assertEquals(0, api.actions);
        assertTrue(c.assign(item, " Team A "));
        assertEquals("Team A", api.handler);
        assertEquals("NEW", api.lastPrecondition.getExpectedCanonicalStatus());
        assertEquals(0, api.lastPrecondition.getExpectedRevision());
        assertFalse(c.assign(item, "Team A")); // double tap before callback
        api.delayedTransition.onSuccess(item.withTransition(IncidentStatus.ASSIGNED,
                "Team A", 3000, "assign-event"));
        assertEquals(1, api.actions);
        assertEquals("ASSIGNED", view.saved.getStatusCode());
        assertTrue(c.select("one"));
        assertEquals(1, view.detail.getRevision());
        assertFalse(c.assign(item, "Team A")); // stale row after successful update
        assertEquals(IncidentRepository.Error.CONFLICT, view.errors.get(view.errors.size()-1));
    }

    @Test public void stateActionsFollowOnlyLegalTransitions() {
        Api api = new Api(); Screen view = new Screen();
        ManagerIncidentCoordinator c = new ManagerIncidentCoordinator(api, view);
        IncidentSnapshot assigned = item("one", "NORMAL", IncidentStatus.ASSIGNED, 1, 1000);
        c.loadList(); api.list.onSuccess(Collections.singletonList(assigned));
        assertFalse(c.complete(assigned));
        assertTrue(c.start(assigned));
        IncidentSnapshot working = assigned.withTransition(IncidentStatus.IN_PROGRESS,
                null, 3000, "started");
        api.delayedTransition.onSuccess(working);
        assertTrue(c.complete(working));
        api.delayedTransition.onSuccess(working.withTransition(IncidentStatus.COMPLETED,
                null, 4000, "completed"));
        assertEquals("COMPLETED", view.saved.getStatusCode());
        assertEquals(2, api.actions);
    }

    @Test public void overdueOnlyOpenAndPastDeadline() {
        IncidentSnapshot old = item("one", "URGENT", IncidentStatus.NEW, 0, 1000);
        assertFalse(ManagerIncidentCoordinator.isOverdue(old, old.getDueAtMillis()));
        assertTrue(ManagerIncidentCoordinator.isOverdue(old, old.getDueAtMillis()+1));
        IncidentSnapshot done = item("two", "LOW", IncidentStatus.COMPLETED, 3, 1000);
        assertFalse(ManagerIncidentCoordinator.isOverdue(done, Long.MAX_VALUE));
    }

    @Test public void pendingCallbacksAfterCloseDoNotUpdateUI() {
        Api api = new Api(); Screen view = new Screen();
        ManagerIncidentCoordinator c = new ManagerIncidentCoordinator(api, view);
        c.loadList(); c.close();
        api.list.onSuccess(Collections.singletonList(item("one", "NORMAL", IncidentStatus.NEW, 0, 1000)));
        assertNull(view.rows);
        assertFalse(c.loadList());
    }

    @Test public void repositoryErrorEndsPendingAndAllowsRetry() {
        Api api = new Api(); Screen view = new Screen();
        ManagerIncidentCoordinator c = new ManagerIncidentCoordinator(api, view);
        c.loadList();
        api.list.onError(IncidentRepository.Error.NETWORK);
        assertEquals(IncidentRepository.Error.NETWORK, view.errors.get(0));
        assertFalse(view.busy);
        assertTrue(c.loadList());
    }
}

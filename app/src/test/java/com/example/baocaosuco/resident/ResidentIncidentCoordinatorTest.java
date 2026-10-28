package com.example.baocaosuco.resident;

import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.incident.NewIncidentInput;
import com.example.baocaosuco.data.repository.CommandPrecondition;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.logic.IncidentStatus;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class ResidentIncidentCoordinatorTest {
    private static IncidentSnapshot item(IncidentStatus status, int revision) {
        return new IncidentSnapshot("inc-1", "resident",
                new NewIncidentInput("Light", "Broken bulb", "Block A", "NORMAL"),
                status, status == IncidentStatus.NEW ? null : "Team A",
                1000L, 2000L, revision, "event-" + revision);
    }
    private static final class Screen implements ResidentIncidentCoordinator.View {
        final List<Boolean> busy = new ArrayList<>();
        final List<IncidentRepository.Error> errors = new ArrayList<>();
        List<IncidentSnapshot> list;
        IncidentSnapshot detail;
        IncidentSnapshot saved;
        List<IncidentHistorySnapshot> history;
        @Override public void onBusy(boolean value) { busy.add(value); }
        @Override public void onList(List<IncidentSnapshot> rows) { list = rows; }
        @Override public void onDetail(IncidentSnapshot item, List<IncidentHistorySnapshot> events) {
            detail = item; history = events;
        }
        @Override public void onSaved(IncidentSnapshot item) { saved = item; }
        @Override public void onError(IncidentRepository.Error error) { errors.add(error); }
    }
    private static final class Api implements IncidentRepository.Resident {
        IncidentRepository.Callback<List<IncidentSnapshot>> pendingList;
        IncidentRepository.Callback<IncidentSnapshot> pendingDetail;
        IncidentRepository.Callback<List<IncidentHistorySnapshot>> pendingHistory;
        NewIncidentInput input;
        CommandPrecondition precondition;
        Boolean resolved;
        int creates;
        int confirmations;
        @Override public void create(NewIncidentInput value, IncidentRepository.Callback<IncidentSnapshot> cb) {
            input = value; creates++; cb.onSuccess(item(IncidentStatus.NEW, 0));
        }
        @Override public void listMine(IncidentRepository.Callback<List<IncidentSnapshot>> cb) {
            pendingList = cb;
        }
        @Override public void getMine(String id, IncidentRepository.Callback<IncidentSnapshot> cb) {
            pendingDetail = cb;
        }
        @Override public void historyMine(String id,
                IncidentRepository.Callback<List<IncidentHistorySnapshot>> cb) { pendingHistory = cb; }
        @Override public void confirm(String id, CommandPrecondition expected, boolean answer,
                IncidentRepository.Callback<IncidentSnapshot> cb) {
            confirmations++; precondition = expected; resolved = answer;
            cb.onSuccess(item(answer ? IncidentStatus.CLOSED : IncidentStatus.IN_PROGRESS, 4));
        }
    }

    @Test public void submitUsesOnlyCanonicalInputAndReturnsServerSnapshot() {
        Api api = new Api(); Screen screen = new Screen();
        ResidentIncidentCoordinator c = new ResidentIncidentCoordinator(api, screen);
        assertTrue(c.submit(" Light ", " Broken bulb ", " Block A ", "NORMAL"));
        assertEquals("Light", api.input.getTitle());
        assertEquals("Broken bulb", api.input.getDescription());
        assertEquals("Block A", api.input.getLocation());
        assertEquals("NEW", screen.saved.getStatusCode());
        assertEquals(1, api.creates);
        assertEquals(Boolean.FALSE, screen.busy.get(screen.busy.size()-1));
    }

    @Test public void emptyFormAndUnsupportedPriorityDoNotHitRepository() {
        Api api = new Api(); Screen screen = new Screen();
        ResidentIncidentCoordinator c = new ResidentIncidentCoordinator(api, screen);
        assertFalse(c.submit("", "Description", "Room", "LOW"));
        assertFalse(c.submit("Title", "Description", "Room", "FAKE"));
        assertEquals(0, api.creates);
        assertEquals(2, screen.errors.size());
        assertEquals(IncidentRepository.Error.VALIDATION, screen.errors.get(0));
    }

    @Test public void listLimitsPresentationToRepositoryReturnedPageAndIsImmutable() {
        Api api = new Api(); Screen screen = new Screen();
        ResidentIncidentCoordinator c = new ResidentIncidentCoordinator(api, screen);
        assertTrue(c.loadList());
        List<IncidentSnapshot> items = new ArrayList<>();
        items.add(item(IncidentStatus.NEW, 0));
        api.pendingList.onSuccess(items);
        items.clear();
        assertEquals(1, screen.list.size());
        try { screen.list.clear(); fail("Expected read-only list"); }
        catch (UnsupportedOperationException expected) { /* pass */ }
    }

    @Test public void detailWaitsForServerHistoryAndRejectsBadId() {
        Api api = new Api(); Screen screen = new Screen();
        ResidentIncidentCoordinator c = new ResidentIncidentCoordinator(api, screen);
        assertFalse(c.loadDetail("bad/id"));
        assertTrue(c.loadDetail("inc-1"));
        api.pendingDetail.onSuccess(item(IncidentStatus.NEW, 0));
        assertNull(screen.detail);
        api.pendingHistory.onSuccess(new ArrayList<>());
        assertEquals("inc-1", screen.detail.getId());
        assertNotNull(screen.history);
    }

    @Test public void confirmUsesObservedRevisionAndNewRequestId() {
        Api api = new Api(); Screen screen = new Screen();
        ResidentIncidentCoordinator c = new ResidentIncidentCoordinator(api, screen);
        assertFalse(c.confirm(item(IncidentStatus.NEW, 0), true));
        assertTrue(c.confirm(item(IncidentStatus.COMPLETED, 3), false));
        assertEquals(Integer.valueOf(3), Integer.valueOf(api.precondition.getExpectedRevision()));
        assertEquals("COMPLETED", api.precondition.getExpectedCanonicalStatus());
        assertFalse(api.precondition.getRequestId().isEmpty());
        assertFalse(api.resolved);
        assertEquals("IN_PROGRESS", screen.saved.getStatusCode());
        assertEquals(1, api.confirmations);
    }

    @Test public void doubleTapIsIgnoredAndCloseDropsStaleCallback() {
        Api api = new Api(); Screen screen = new Screen();
        ResidentIncidentCoordinator c = new ResidentIncidentCoordinator(api, screen);
        assertTrue(c.loadList());
        assertFalse(c.loadList());
        c.close();
        api.pendingList.onSuccess(java.util.Collections.singletonList(item(IncidentStatus.NEW, 0)));
        assertNull(screen.list);
        assertFalse(c.loadList());
        assertEquals(1, screen.busy.size());
    }

    @Test public void closeDuringBusyStartNeverContactsRepository() {
        Api api = new Api();
        final ResidentIncidentCoordinator[] owner = new ResidentIncidentCoordinator[1];
        final List<IncidentSnapshot>[] delivered = new List[1];
        ResidentIncidentCoordinator.View view = new ResidentIncidentCoordinator.View() {
            @Override public void onBusy(boolean busy) {
                if (busy == true) owner[0].close();
            }
            @Override public void onList(List<IncidentSnapshot> rows) { delivered[0] = rows; }
            @Override public void onDetail(IncidentSnapshot item, List<IncidentHistorySnapshot> events) {}
            @Override public void onSaved(IncidentSnapshot item) {}
            @Override public void onError(IncidentRepository.Error error) {}
        };
        owner[0] = new ResidentIncidentCoordinator(api, view);
        assertFalse(owner[0].loadList());
        assertNull(api.pendingList);
    }

    @Test public void closeDuringBusyEndDropsLateResult() {
        Api api = new Api();
        final ResidentIncidentCoordinator[] owner = new ResidentIncidentCoordinator[1];
        final List<IncidentSnapshot>[] delivered = new List[1];
        ResidentIncidentCoordinator.View view = new ResidentIncidentCoordinator.View() {
            @Override public void onBusy(boolean busy) {
                if (busy == false) owner[0].close();
            }
            @Override public void onList(List<IncidentSnapshot> rows) { delivered[0] = rows; }
            @Override public void onDetail(IncidentSnapshot item, List<IncidentHistorySnapshot> events) {}
            @Override public void onSaved(IncidentSnapshot item) {}
            @Override public void onError(IncidentRepository.Error error) {}
        };
        owner[0] = new ResidentIncidentCoordinator(api, view);
        assertTrue(owner[0].loadList());
        api.pendingList.onSuccess(
                java.util.Collections.singletonList(item(IncidentStatus.NEW, 0)));
        assertNull(delivered[0]);
    }

    @Test public void repositoryErrorIsShownAndBusyEnds() {
        Api api = new Api(); Screen screen = new Screen();
        ResidentIncidentCoordinator c = new ResidentIncidentCoordinator(api, screen);
        assertTrue(c.loadList());
        api.pendingList.onError(IncidentRepository.Error.PERMISSION_DENIED);
        assertEquals(IncidentRepository.Error.PERMISSION_DENIED, screen.errors.get(0));
        assertEquals(Boolean.FALSE, screen.busy.get(1));
        assertTrue(c.loadList());
    }
}

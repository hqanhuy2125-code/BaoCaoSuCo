package com.example.baocaosuco.resident;

import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.incident.NewIncidentInput;
import com.example.baocaosuco.data.repository.CommandPrecondition;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.logic.IncidentStatus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ResidentForegroundStatusRefreshTest {
    private static IncidentSnapshot item(String id, IncidentStatus status, int revision) {
        return new IncidentSnapshot(id, "owner-resident",
                new NewIncidentInput("Broken lamp", "Needs attention", "Hall A", "NORMAL"),
                status, status == IncidentStatus.NEW ? null : "Team A",
                1_000L, 2_000L + revision, revision, "event-" + revision);
    }

    private static final class FakeScheduler implements ResidentForegroundStatusRefresh.Scheduler {
        Runnable next;
        long delay;
        int cancelled;
        @Override public void postDelayed(Runnable work, long millis) {
            assertNull("Never enqueue multiple refresh timers", next);
            next = work;
            delay = millis;
        }
        @Override public void removeCallbacks(Runnable work) {
            if (next == work) {
                next = null;
                cancelled++;
            }
        }
        void tick() {
            assertNotNull("Expected a pending timer", next);
            Runnable work = next;
            next = null;
            work.run();
        }
    }

    private static final class FakeView implements ResidentForegroundStatusRefresh.View {
        final List<List<IncidentSnapshot>> pages = new ArrayList<>();
        final List<IncidentSnapshot> changes = new ArrayList<>();
        final List<IncidentRepository.Error> errors = new ArrayList<>();
        @Override public void onPage(List<IncidentSnapshot> rows) { pages.add(rows); }
        @Override public void onStatusChanged(IncidentSnapshot row) { changes.add(row); }
        @Override public void onError(IncidentRepository.Error error) { errors.add(error); }
    }

    private static final class FakeRepo implements IncidentRepository.Resident {
        final List<IncidentRepository.Callback<List<IncidentSnapshot>>> calls = new ArrayList<>();
        @Override public void listMine(IncidentRepository.Callback<List<IncidentSnapshot>> callback) {
            calls.add(callback);
        }
        @Override public void create(NewIncidentInput input, IncidentRepository.Callback<IncidentSnapshot> callback) {
            fail("B06 is read-only");
        }
        @Override public void getMine(String id, IncidentRepository.Callback<IncidentSnapshot> callback) {
            fail("B06 may only query the bounded resident page");
        }
        @Override public void historyMine(String id,
                IncidentRepository.Callback<List<IncidentHistorySnapshot>> callback) {
            fail("B06 does not request history from each poll");
        }
        @Override public void confirm(String id, CommandPrecondition expected, boolean answer,
                IncidentRepository.Callback<IncidentSnapshot> callback) {
            fail("B06 must never change state");
        }
        void succeed(int which, IncidentSnapshot... rows) {
            calls.get(which).onSuccess(Arrays.asList(rows));
        }
    }

    private static final class Fixture {
        final FakeRepo repo = new FakeRepo();
        final FakeScheduler clock = new FakeScheduler();
        final FakeView view = new FakeView();
        final ResidentForegroundStatusRefresh refresh =
                new ResidentForegroundStatusRefresh(repo, clock, view);
    }

    @Test public void initialFetchSetsBaselineWithoutNotifications() {
        Fixture f = new Fixture();
        f.refresh.onStart();
        assertEquals(1, f.repo.calls.size());
        assertEquals(ResidentForegroundStatusRefresh.POLL_INTERVAL_MS, f.clock.delay);
        f.repo.succeed(0, item("i1", IncidentStatus.NEW, 0));
        assertEquals(1, f.view.pages.size());
        assertTrue(f.view.changes.isEmpty());
        try { f.view.pages.get(0).clear(); fail("Must be immutable"); }
        catch (UnsupportedOperationException expected) { /* PASS */ }
    }

    @Test public void managerStatusChangeIsDeliveredOnceOnNextForegroundPoll() {
        Fixture f = new Fixture();
        f.refresh.onStart();
        f.repo.succeed(0, item("i1", IncidentStatus.NEW, 0));
        f.clock.tick();
        f.repo.succeed(1, item("i1", IncidentStatus.ASSIGNED, 1));
        assertEquals(1, f.view.changes.size());
        assertEquals("ASSIGNED", f.view.changes.get(0).getStatusCode());
        assertEquals(2, f.view.pages.size());
        f.clock.tick();
        f.repo.succeed(2, item("i1", IncidentStatus.ASSIGNED, 1));
        assertEquals(1, f.view.changes.size());
        assertEquals(2, f.view.pages.size());
    }

    @Test public void sameStatusWithNewRevisionDoesNotProduceFalseStatusNotification() {
        Fixture f = new Fixture();
        f.refresh.onStart();
        f.repo.succeed(0, item("i1", IncidentStatus.COMPLETED, 3));
        f.clock.tick();
        f.repo.succeed(1, item("i1", IncidentStatus.COMPLETED, 4));
        assertTrue(f.view.changes.isEmpty());
        assertEquals(2, f.view.pages.size());
    }

    @Test public void stopRemovesTimerAndIgnoresLateFirebaseCallbacks() {
        Fixture f = new Fixture();
        f.refresh.onStart();
        assertFalse(f.refresh.refresh()); // in-flight callback
        f.refresh.onStop();
        assertNull(f.clock.next);
        f.repo.succeed(0, item("i1", IncidentStatus.ASSIGNED, 1));
        assertTrue(f.view.pages.isEmpty());
        assertTrue(f.view.changes.isEmpty());
        assertFalse(f.refresh.refresh());
    }

    @Test public void screenRestartGetsFreshBaselineAndDoesNotLeakOldAccountPage() {
        Fixture f = new Fixture();
        f.refresh.onStart();
        f.repo.succeed(0, item("old-user-item", IncidentStatus.NEW, 0));
        f.refresh.onStop();
        f.refresh.onStart();
        f.repo.succeed(1, item("new-user-item", IncidentStatus.ASSIGNED, 1));
        assertEquals(2, f.view.pages.size());
        assertTrue(f.view.changes.isEmpty());
    }

    @Test public void delayedReplyFromPreviousScreenSessionNeverReplacesNewPage() {
        Fixture f = new Fixture();
        f.refresh.onStart(); // pending old callback
        f.refresh.onStop();
        f.refresh.onStart(); // pending new callback
        f.repo.succeed(1, item("new", IncidentStatus.NEW, 0));
        f.repo.succeed(0, item("stale", IncidentStatus.COMPLETED, 3));
        assertEquals(1, f.view.pages.size());
        assertEquals("new", f.view.pages.get(0).get(0).getId());
    }

    @Test public void longRunningReadNeverStartsSecondReadUntilCallbackArrives() {
        Fixture f = new Fixture();
        f.refresh.onStart();
        f.clock.tick();
        assertEquals(1, f.repo.calls.size());
        assertNotNull(f.clock.next);
        f.repo.succeed(0, item("i1", IncidentStatus.NEW, 0));
        f.clock.tick();
        assertEquals(2, f.repo.calls.size());
    }

    @Test public void errorsRemainVisibleAndNextScheduledReadCanRecover() {
        Fixture f = new Fixture();
        f.refresh.onStart();
        f.repo.calls.get(0).onError(IncidentRepository.Error.NETWORK);
        assertEquals(IncidentRepository.Error.NETWORK, f.view.errors.get(0));
        f.clock.tick();
        f.repo.succeed(1, item("i1", IncidentStatus.NEW, 0));
        assertEquals(1, f.view.pages.size());
        assertTrue(f.view.changes.isEmpty());
    }

    @Test public void invalidOrDuplicateRowsFailClosedWithoutPublishing() {
        Fixture f = new Fixture();
        f.refresh.onStart();
        f.repo.calls.get(0).onSuccess(null);
        assertEquals(IncidentRepository.Error.UNKNOWN, f.view.errors.get(0));
        f.clock.tick();
        f.repo.succeed(1, item("dup", IncidentStatus.NEW, 0),
                item("dup", IncidentStatus.NEW, 0));
        assertEquals(IncidentRepository.Error.SECURITY_NOT_READY, f.view.errors.get(1));
        assertTrue(f.view.pages.isEmpty());
        f.clock.tick();
        f.repo.succeed(2, item("i1", IncidentStatus.NEW, 0));
        assertEquals(1, f.view.pages.size());
    }

    @Test public void closeIsIdempotentAndPreventsFuturePolls() {
        Fixture f = new Fixture();
        f.refresh.onStart();
        f.refresh.close();
        f.refresh.close();
        assertNull(f.clock.next);
        f.repo.succeed(0, item("i1", IncidentStatus.COMPLETED, 3));
        f.refresh.onStart();
        assertFalse(f.refresh.refresh());
        assertTrue(f.view.pages.isEmpty());
        assertEquals(1, f.repo.calls.size());
    }

    @Test public void manualRefreshIsBoundedByVisibilityAndInFlightGuard() {
        Fixture f = new Fixture();
        assertFalse(f.refresh.refresh());
        f.refresh.onStart();
        assertFalse(f.refresh.refresh());
        f.repo.succeed(0, item("i1", IncidentStatus.NEW, 0));
        assertTrue(f.refresh.refresh());
        assertEquals(2, f.repo.calls.size());
        f.repo.succeed(1, item("i1", IncidentStatus.NEW, 0));
    }

    @Test public void authRequiredClearsVisiblePrivateRowsAndStopsPolling() {
        Fixture f = new Fixture();
        f.refresh.onStart();
        f.repo.succeed(0, item("private-old-owner", IncidentStatus.NEW, 0));
        f.clock.tick();
        f.repo.calls.get(1).onError(IncidentRepository.Error.AUTH_REQUIRED);
        assertEquals(2, f.view.pages.size());
        assertTrue("Old private rows must be hidden", f.view.pages.get(1).isEmpty());
        assertEquals(IncidentRepository.Error.AUTH_REQUIRED, f.view.errors.get(0));
        assertNull("No further reads while role is invalid", f.clock.next);
        assertFalse(f.refresh.refresh());
        assertEquals(2, f.repo.calls.size());
    }

    @Test public void permissionDeniedAlsoDetachesAndErasesPrivatePage() {
        Fixture f = new Fixture();
        f.refresh.onStart();
        f.repo.succeed(0, item("private", IncidentStatus.NEW, 0));
        f.clock.tick();
        f.repo.calls.get(1).onError(IncidentRepository.Error.PERMISSION_DENIED);
        assertTrue(f.view.pages.get(f.view.pages.size() - 1).isEmpty());
        assertEquals(IncidentRepository.Error.PERMISSION_DENIED, f.view.errors.get(0));
        assertNull(f.clock.next);
    }

    @Test public void staleServerRevisionCannotOverwriteFreshPageOrEmitWrongNotice() {
        Fixture f = new Fixture();
        f.refresh.onStart();
        f.repo.succeed(0, item("i1", IncidentStatus.ASSIGNED, 2));
        f.clock.tick();
        f.repo.succeed(1, item("i1", IncidentStatus.NEW, 0));
        assertEquals(IncidentRepository.Error.CONFLICT, f.view.errors.get(0));
        assertEquals(1, f.view.pages.size());
        assertTrue(f.view.changes.isEmpty());
        f.clock.tick();
        f.repo.succeed(2, item("i1", IncidentStatus.IN_PROGRESS, 3));
        assertEquals(2, f.view.pages.size());
        assertEquals(1, f.view.changes.size());
        assertEquals("IN_PROGRESS", f.view.changes.get(0).getStatusCode());
    }

    @Test public void sameRevisionWithContradictoryStatusIsRejected() {
        Fixture f = new Fixture();
        f.refresh.onStart();
        f.repo.succeed(0, item("i1", IncidentStatus.ASSIGNED, 2));
        f.clock.tick();
        f.repo.succeed(1, item("i1", IncidentStatus.IN_PROGRESS, 2));
        assertEquals(IncidentRepository.Error.CONFLICT, f.view.errors.get(0));
        assertEquals(1, f.view.pages.size());
        assertTrue(f.view.changes.isEmpty());
    }

    @Test public void newlyArrivedItemsAreNotFalselyAnnouncedAsTransitions() {
        Fixture f = new Fixture();
        f.refresh.onStart();
        f.repo.succeed(0, item("i1", IncidentStatus.NEW, 0));
        f.clock.tick();
        f.repo.succeed(1, item("i1", IncidentStatus.NEW, 0),
                item("i2", IncidentStatus.ASSIGNED, 1));
        assertTrue(f.view.changes.isEmpty());
        assertEquals(2, f.view.pages.size());
    }
}

package com.example.baocaosuco.integration.ui;

import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.incident.NewIncidentInput;
import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.logic.IncidentStatus;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class IncidentListPagingTest {
    private static final class Cursor implements IncidentRepository.PageCursor {}

    private static final class Fixture implements IncidentListUi.PageLoader.Listener, IncidentRepository.PageSource {
        final ArrayDeque<IncidentRepository.Callback<IncidentRepository.Page>> requests = new ArrayDeque<>();
        IncidentRepository.PageCursor lastCursor;
        List<IncidentSnapshot> rows;
        IncidentRepository.Error error;
        boolean hasMore;
        boolean busy;
        final IncidentListUi.PageLoader loader = new IncidentListUi.PageLoader(this, this);

        @Override public void load(IncidentRepository.PageCursor after, IncidentRepository.Callback<IncidentRepository.Page> callback) {
            lastCursor = after;
            requests.add(callback);
        }
        @Override public void onPage(List<IncidentSnapshot> rows, boolean hasMore, boolean loading) {
            this.rows = rows;
            this.hasMore = hasMore;
            this.busy = loading;
        }
        @Override public void onError(List<IncidentSnapshot> rows, IncidentRepository.Error error) {
            this.rows = rows;
            this.error = error;
        }
        void reply(IncidentRepository.PageCursor next, IncidentSnapshot... rows) {
            requests.remove().onSuccess(new IncidentRepository.Page(Arrays.asList(rows), next));
        }
    }

    @Test public void tenPagesThenExplicitLoadMoreRetainsCursorAndMarksExhaustion() {
        Fixture f = new Fixture();
        f.loader.restart();
        assertNull(f.lastCursor);
        Cursor tenth = null;
        for (int ix = 1; ix <= 10; ix++) {
            tenth = new Cursor();
            f.reply(tenth, item("incident-" + ix, ix, 0));
        }
        assertEquals(10, f.rows.size());
        assertTrue(f.hasMore);
        assertFalse(f.busy);
        assertEquals(0, f.requests.size());
        f.loader.openMoreOrStop();
        assertSame(tenth, f.lastCursor);
        f.reply(null, item("incident-11", 11, 0));
        assertEquals(11, f.rows.size());
        assertEquals("incident-11", f.rows.get(0).getId());
        assertFalse(f.hasMore);
        assertFalse(f.busy);
    }

    @Test public void stoppedCallbackCannotRepopulateOrStartAnotherRead() {
        Fixture f = new Fixture();
        f.loader.restart();
        f.loader.openMoreOrStop();
        f.reply(new Cursor(), item("late", 1, 0));
        assertTrue(f.rows.isEmpty());
        assertEquals(0, f.requests.size());
        assertTrue(f.hasMore);
        assertFalse(f.busy);
    }

    @Test public void foregroundHeadWinsOverOlderPageAndAccessLossClearsRows() {
        Fixture f = new Fixture();
        f.loader.restart();
        f.loader.reconcileHead(Collections.singletonList(item("one", 1, 1)));
        f.reply(new Cursor(), item("one", 1, 0), item("two", 1, 0));
        assertEquals(2, f.rows.size());
        assertEquals(1, f.rows.get(0).getRevision());
        f.requests.remove().onError(IncidentRepository.Error.AUTH_REQUIRED);
        assertTrue(f.rows.isEmpty());
        assertEquals(IncidentRepository.Error.AUTH_REQUIRED, f.error);
    }

    @Test public void networkErrorKeepsLoadedRecordsAndRetriesSameCursor() {
        Fixture f = new Fixture();
        f.loader.restart();
        Cursor next = new Cursor();
        f.reply(next, item("one", 1, 0));
        f.requests.remove().onError(IncidentRepository.Error.NETWORK);
        assertEquals(1, f.rows.size());
        assertEquals(IncidentRepository.Error.NETWORK, f.error);
        f.loader.openMoreOrStop();
        assertSame(next, f.lastCursor);
        f.reply(null);
        assertFalse(f.hasMore);
        assertEquals(1, f.rows.size());
    }

    private static IncidentSnapshot item(String id, long createdAt, int revision) {
        return new IncidentSnapshot(id, "synthetic-resident",
                new NewIncidentInput("Title", "Description", "Location", "NORMAL"),
                revision == 0 ? IncidentStatus.NEW : IncidentStatus.ASSIGNED,
                revision == 0 ? null : "Handler", createdAt, createdAt + revision,
                revision, "event-" + id + "-" + revision);
    }
}

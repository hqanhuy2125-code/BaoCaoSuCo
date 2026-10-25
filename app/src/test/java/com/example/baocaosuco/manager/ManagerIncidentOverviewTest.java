package com.example.baocaosuco.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManagerIncidentOverviewTest {

    private static final class DeferredSource implements ManagerIncidentOverview.DataSource {
        final List<ManagerIncidentOverview.Callback> callbacks = new ArrayList<>();
        boolean throwOnLoad;
        @Override public void loadAll(ManagerIncidentOverview.Callback callback) {
            if (throwOnLoad) throw new IllegalStateException("no adapter");
            callbacks.add(callback);
        }
    }

    private static final class Collector implements ManagerIncidentOverview.Listener {
        final List<ManagerIncidentOverview.Snapshot> snapshots = new ArrayList<>();
        @Override public void onSnapshot(ManagerIncidentOverview.Snapshot snapshot) {
            snapshots.add(snapshot);
        }
        ManagerIncidentOverview.Snapshot last() {
            return snapshots.get(snapshots.size() - 1);
        }
    }

    private static final ManagerIncidentOverview.StatusClassifier CLASSIFIER = status -> {
        if ("demo-active".equals(status)) return ManagerIncidentOverview.StatusCategory.ACTIVE;
        if ("demo-terminal".equals(status)) return ManagerIncidentOverview.StatusCategory.TERMINAL;
        return ManagerIncidentOverview.StatusCategory.UNKNOWN;
    };

    private static ManagerIncidentOverview manager(DeferredSource source) {
        return new ManagerIncidentOverview(source, CLASSIFIER, () -> 100L);
    }

    private static ManagerIncidentListPreview.Row row(String id, String status) {
        return new ManagerIncidentListPreview.Row(id, "mock-u", "Broken light", "Room 1",
                "display-priority", status);
    }

    private static Map<String, Long> deadline(String id, long due) {
        Map<String, Long> result = new HashMap<>();
        result.put(id, due);
        return result;
    }

    @Test public void loadingIsEmittedImmediatelyWithoutPretendingDataArrived() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        manager(source).load(ui);
        assertEquals(1, ui.snapshots.size());
        assertEquals(ManagerIncidentOverview.State.LOADING, ui.last().getState());
        assertTrue(ui.last().getRows().isEmpty());
        assertEquals(1, source.callbacks.size());
    }

    @Test public void loadedRowsArePresentedWithoutModifyingInput() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        manager(source).load(ui);
        List<ManagerIncidentListPreview.Row> input = new ArrayList<>();
        input.add(row("a", "demo-active")); input.add(row("b", "demo-terminal"));
        Map<String, Long> due = deadline("a", 90L); due.put("b", 40L);
        source.callbacks.get(0).onLoaded(input, due);
        input.clear(); due.clear();
        assertEquals(ManagerIncidentOverview.State.CONTENT, ui.last().getState());
        assertEquals(2, ui.last().getRows().size());
        assertEquals(1, ui.last().getOverdueCount());
        assertEquals(ManagerIncidentOverview.Overdue.YES, ui.last().getRows().get(0).getOverdue());
        assertEquals(ManagerIncidentOverview.Overdue.NO, ui.last().getRows().get(1).getOverdue());
        assertEquals("Broken light", ui.last().getRows().get(0).getIncident().getTitle());
    }

    @Test public void equalityWithDeadlineIsNotOverdue() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        manager(source).load(ui);
        source.callbacks.get(0).onLoaded(Collections.singletonList(row("a", "demo-active")), deadline("a", 100L));
        assertEquals(ManagerIncidentOverview.Overdue.NO, ui.last().getRows().get(0).getOverdue());
    }

    @Test public void incompleteOrUnknownDataIsNotFalselyMarkedOverdue() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        manager(source).load(ui);
        List<ManagerIncidentListPreview.Row> rows = Arrays.asList(row("a", "demo-active"), row("b", "unclassified"));
        source.callbacks.get(0).onLoaded(rows, deadline("b", 1L));
        assertEquals(ManagerIncidentOverview.Overdue.UNKNOWN, ui.last().getRows().get(0).getOverdue());
        assertEquals(ManagerIncidentOverview.Overdue.UNKNOWN, ui.last().getRows().get(1).getOverdue());
        assertEquals(0, ui.last().getOverdueCount());
    }

    @Test public void terminalStateIsNotMarkedOverdueWhenPastDue() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        manager(source).load(ui);
        source.callbacks.get(0).onLoaded(Collections.singletonList(row("a", "demo-terminal")), deadline("a", 1L));
        assertEquals(ManagerIncidentOverview.Overdue.NO, ui.last().getRows().get(0).getOverdue());
    }

    @Test public void noIncidentsIsEmptyNotAnError() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        manager(source).load(ui);
        source.callbacks.get(0).onLoaded(Collections.emptyList(), Collections.emptyMap());
        assertEquals(ManagerIncidentOverview.State.EMPTY, ui.last().getState());
        assertNull(ui.last().getError());
        assertEquals(0, ui.last().getOverdueCount());
    }

    @Test public void accessDeniedHasNoDataAndNeverLooksLikeSuccess() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        manager(source).load(ui);
        source.callbacks.get(0).onFailed(ManagerIncidentOverview.ReadError.PERMISSION_DENIED);
        assertEquals(ManagerIncidentOverview.State.ERROR, ui.last().getState());
        assertEquals(ManagerIncidentOverview.ReadError.PERMISSION_DENIED, ui.last().getError());
        assertTrue(ui.last().getRows().isEmpty());
    }

    @Test public void networkFailureCanBeFollowedByRetry() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        ManagerIncidentOverview coordinator = manager(source);
        coordinator.load(ui);
        source.callbacks.get(0).onFailed(ManagerIncidentOverview.ReadError.NETWORK);
        assertEquals(ManagerIncidentOverview.ReadError.NETWORK, ui.last().getError());
        coordinator.load(ui);
        source.callbacks.get(1).onLoaded(Collections.singletonList(row("a", "demo-active")), deadline("a", 10L));
        assertEquals(ManagerIncidentOverview.State.CONTENT, ui.last().getState());
    }

    @Test public void nullFailureReasonMapsToUnknown() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        manager(source).load(ui);
        source.callbacks.get(0).onFailed(null);
        assertEquals(ManagerIncidentOverview.ReadError.UNKNOWN, ui.last().getError());
    }

    @Test public void invalidNullResultDoesNotExposeRows() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        manager(source).load(ui);
        source.callbacks.get(0).onLoaded(null, Collections.emptyMap());
        assertEquals(ManagerIncidentOverview.ReadError.INVALID_DATA, ui.last().getError());
        assertTrue(ui.last().getRows().isEmpty());
    }

    @Test public void nullDueLookupIsInvalidRatherThanFakeOnTime() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        manager(source).load(ui);
        source.callbacks.get(0).onLoaded(Collections.singletonList(row("a", "demo-active")), null);
        assertEquals(ManagerIncidentOverview.ReadError.INVALID_DATA, ui.last().getError());
    }

    @Test public void duplicateOrNullIncidentRowsAreInvalid() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        ManagerIncidentOverview coordinator = manager(source);
        coordinator.load(ui);
        coordinator.invalidatePending(); coordinator.load(ui);
        source.callbacks.get(1).onLoaded(Arrays.asList(row("a", "demo-active"), row("a", "demo-active")), deadline("a", 10L));
        assertEquals(ManagerIncidentOverview.ReadError.INVALID_DATA, ui.last().getError());
        coordinator.load(ui);
        source.callbacks.get(2).onLoaded(Arrays.asList(row("a", "demo-active"), null), deadline("a", 10L));
        assertEquals(ManagerIncidentOverview.ReadError.INVALID_DATA, ui.last().getError());
    }

    @Test public void earlierRequestResponseCannotOverwriteNewerRequest() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        ManagerIncidentOverview coordinator = manager(source);
        coordinator.load(ui);
        coordinator.load(ui);
        source.callbacks.get(1).onLoaded(Collections.emptyList(), Collections.emptyMap());
        source.callbacks.get(0).onLoaded(Collections.singletonList(row("a", "demo-active")), deadline("a", 1L));
        assertEquals(3, ui.snapshots.size());
        assertEquals(ManagerIncidentOverview.State.EMPTY, ui.last().getState());
    }

    @Test public void invalidatePendingIgnoresLateCallbacks() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        ManagerIncidentOverview coordinator = manager(source);
        coordinator.load(ui); coordinator.invalidatePending();
        source.callbacks.get(0).onFailed(ManagerIncidentOverview.ReadError.NETWORK);
        assertEquals(1, ui.snapshots.size());
    }

    @Test public void duplicateCallbacksAreIgnored() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        manager(source).load(ui);
        source.callbacks.get(0).onLoaded(Collections.emptyList(), Collections.emptyMap());
        source.callbacks.get(0).onFailed(ManagerIncidentOverview.ReadError.NETWORK);
        assertEquals(2, ui.snapshots.size());
        assertEquals(ManagerIncidentOverview.State.EMPTY, ui.last().getState());
    }

    @Test public void sourceExceptionIsReportedAsError() {
        DeferredSource source = new DeferredSource(); source.throwOnLoad = true;
        Collector ui = new Collector(); manager(source).load(ui);
        assertEquals(ManagerIncidentOverview.ReadError.UNKNOWN, ui.last().getError());
    }

    @Test public void classifierFailureIsInvalidDataNotSuccess() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        ManagerIncidentOverview obj = new ManagerIncidentOverview(source, label -> {
            throw new IllegalArgumentException("bad status");
        }, () -> 100L);
        obj.load(ui);
        source.callbacks.get(0).onLoaded(Collections.singletonList(row("a", "demo-active")), deadline("a", 1L));
        assertEquals(ManagerIncidentOverview.ReadError.INVALID_DATA, ui.last().getError());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void snapshotRowsCannotBeChangedByView() {
        DeferredSource source = new DeferredSource(); Collector ui = new Collector();
        manager(source).load(ui);
        source.callbacks.get(0).onLoaded(Collections.singletonList(row("a", "demo-active")), deadline("a", 1L));
        ui.last().getRows().clear();
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullDependenciesRejected() {
        new ManagerIncidentOverview(null, CLASSIFIER, () -> 100L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullListenerRejected() {
        manager(new DeferredSource()).load(null);
    }
}

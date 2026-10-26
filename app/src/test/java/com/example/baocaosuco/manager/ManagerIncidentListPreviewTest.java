package com.example.baocaosuco.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ManagerIncidentListPreviewTest {

    @Test
    public void demoAllIncidents_containsRowsFromDifferentResidents() {
        ManagerIncidentListPreview.Snapshot snapshot = ManagerIncidentListPreview.demoAllIncidents();
        assertEquals(ManagerIncidentListPreview.State.CONTENT, snapshot.getState());
        assertEquals(3, snapshot.getRows().size());
        assertEquals("demo-resident-a", snapshot.getRows().get(0).getDemoResidentKey());
        assertEquals("demo-resident-b", snapshot.getRows().get(1).getDemoResidentKey());
    }

    @Test
    public void demoRows_haveDisplayFieldsAndStableIds() {
        ManagerIncidentListPreview.Row row = ManagerIncidentListPreview.demoAllIncidents().getRows().get(0);
        assertEquals("mgr-demo-001", row.getId());
        assertFalse(row.getTitle().isEmpty());
        assertFalse(row.getLocation().isEmpty());
        assertFalse(row.getPriorityLabel().isEmpty());
        assertFalse(row.getStatusLabel().isEmpty());
    }

    @Test
    public void emptySource_returnsEmptySnapshot() {
        ManagerIncidentListPreview.Snapshot snapshot =
                ManagerIncidentListPreview.fromRows(Collections.emptyList());
        assertEquals(ManagerIncidentListPreview.State.EMPTY, snapshot.getState());
        assertTrue(snapshot.getRows().isEmpty());
        assertNull(snapshot.getMessage());
    }

    @Test
    public void nullSource_returnsErrorSnapshot() {
        ManagerIncidentListPreview.Snapshot snapshot = ManagerIncidentListPreview.fromRows(null);
        assertEquals(ManagerIncidentListPreview.State.ERROR, snapshot.getState());
        assertTrue(snapshot.getRows().isEmpty());
        assertFalse(snapshot.getMessage().isEmpty());
    }

    @Test
    public void nullRow_returnsErrorSnapshot() {
        ManagerIncidentListPreview.Snapshot snapshot =
                ManagerIncidentListPreview.fromRows(Arrays.asList(
                        ManagerIncidentListPreview.demoAllIncidents().getRows().get(0), null));
        assertEquals(ManagerIncidentListPreview.State.ERROR, snapshot.getState());
        assertTrue(snapshot.getRows().isEmpty());
    }

    @Test
    public void loading_returnsNoRows() {
        ManagerIncidentListPreview.Snapshot snapshot = ManagerIncidentListPreview.loading();
        assertEquals(ManagerIncidentListPreview.State.LOADING, snapshot.getState());
        assertTrue(snapshot.getRows().isEmpty());
    }

    @Test
    public void explicitError_returnsMessageWithoutRows() {
        ManagerIncidentListPreview.Snapshot snapshot =
                ManagerIncidentListPreview.error("Tai that bai");
        assertEquals(ManagerIncidentListPreview.State.ERROR, snapshot.getState());
        assertEquals("Tai that bai", snapshot.getMessage());
        assertTrue(snapshot.getRows().isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void snapshotRows_cannotBeModified() {
        ManagerIncidentListPreview.demoAllIncidents().getRows().clear();
    }

    @Test
    public void inputListMutation_doesNotChangeSnapshot() {
        List<ManagerIncidentListPreview.Row> source = new ArrayList<>(
                ManagerIncidentListPreview.demoAllIncidents().getRows());
        ManagerIncidentListPreview.Snapshot snapshot = ManagerIncidentListPreview.fromRows(source);
        source.clear();
        assertEquals(3, snapshot.getRows().size());
    }

    @Test
    public void repeatedDemoCalls_areStable() {
        ManagerIncidentListPreview.Snapshot first = ManagerIncidentListPreview.demoAllIncidents();
        ManagerIncidentListPreview.Snapshot second = ManagerIncidentListPreview.demoAllIncidents();
        assertEquals(first.getRows().size(), second.getRows().size());
        assertEquals(first.getRows().get(1).getId(), second.getRows().get(1).getId());
    }
}

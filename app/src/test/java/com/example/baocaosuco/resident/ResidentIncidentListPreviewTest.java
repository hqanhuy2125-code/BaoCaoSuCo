package com.example.baocaosuco.resident;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ResidentIncidentListPreviewTest {

    @Test
    public void demoResidentA_seesOnlyTwoOwnRows() {
        ResidentIncidentListPreview.Snapshot snapshot =
                ResidentIncidentListPreview.forDemoResident("demo-resident-a");

        assertEquals(ResidentIncidentListPreview.State.CONTENT, snapshot.getState());
        assertEquals(2, snapshot.getItems().size());
        assertEquals("demo-incident-01", snapshot.getItems().get(0).getId());
        assertEquals("demo-incident-02", snapshot.getItems().get(1).getId());
        assertNull(snapshot.getMessage());
    }

    @Test
    public void demoResidentB_doesNotSeeResidentARows() {
        ResidentIncidentListPreview.Snapshot snapshot =
                ResidentIncidentListPreview.forDemoResident("demo-resident-b");

        assertEquals(ResidentIncidentListPreview.State.CONTENT, snapshot.getState());
        assertEquals(1, snapshot.getItems().size());
        assertEquals("demo-incident-03", snapshot.getItems().get(0).getId());
        assertFalse(snapshot.getItems().get(0).getTitle().isEmpty());
        assertFalse(snapshot.getItems().get(0).getStatusLabel().isEmpty());
        assertFalse(snapshot.getItems().get(0).getPriorityLabel().isEmpty());
    }

    @Test
    public void unknownMockOwner_isEmptyState() {
        ResidentIncidentListPreview.Snapshot snapshot =
                ResidentIncidentListPreview.forDemoResident("demo-resident-none");

        assertEquals(ResidentIncidentListPreview.State.EMPTY, snapshot.getState());
        assertTrue(snapshot.getItems().isEmpty());
    }

    @Test
    public void missingMockOwner_isErrorState() {
        ResidentIncidentListPreview.Snapshot snapshot =
                ResidentIncidentListPreview.forDemoResident("  ");

        assertEquals(ResidentIncidentListPreview.State.ERROR, snapshot.getState());
        assertTrue(snapshot.getItems().isEmpty());
        assertFalse(snapshot.getMessage().isEmpty());
    }

    @Test
    public void nullMockOwner_isErrorState() {
        assertEquals(ResidentIncidentListPreview.State.ERROR,
                ResidentIncidentListPreview.forDemoResident(null).getState());
    }

    @Test
    public void loadingAndError_haveNoRows() {
        ResidentIncidentListPreview.Snapshot loading = ResidentIncidentListPreview.loading();
        ResidentIncidentListPreview.Snapshot error =
                ResidentIncidentListPreview.failure("Không tải được dữ liệu.");

        assertEquals(ResidentIncidentListPreview.State.LOADING, loading.getState());
        assertTrue(loading.getItems().isEmpty());
        assertEquals(ResidentIncidentListPreview.State.ERROR, error.getState());
        assertTrue(error.getItems().isEmpty());
        assertEquals("Không tải được dữ liệu.", error.getMessage());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void snapshotList_cannotBeModifiedByUi() {
        List<ResidentIncidentListPreview.Item> items =
                ResidentIncidentListPreview.forDemoResident("demo-resident-a").getItems();
        items.clear();
    }

    @Test
    public void repeatedReads_doNotAlterSharedMockData() {
        ResidentIncidentListPreview.Snapshot first =
                ResidentIncidentListPreview.forDemoResident("demo-resident-a");
        ResidentIncidentListPreview.Snapshot second =
                ResidentIncidentListPreview.forDemoResident("demo-resident-a");

        assertEquals(2, first.getItems().size());
        assertEquals(2, second.getItems().size());
        assertEquals(first.getItems().get(0).getLocation(),
                second.getItems().get(0).getLocation());
    }
}

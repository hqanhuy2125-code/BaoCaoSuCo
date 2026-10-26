package com.example.baocaosuco.resident;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ResidentIncidentDetailPreviewTest {
    private static ResidentIncidentDetailPreview.Snapshot residentA() {
        return ResidentIncidentDetailPreview.forDemoResident("demo-resident-a");
    }
    private static ResidentIncidentDetailPreview.Detail first() {
        return residentA().getDetails().get(0);
    }
    @Test public void residentAOnlyGetsItsTwoMockDetails() {
        assertEquals(ResidentIncidentDetailPreview.State.CONTENT, residentA().getState());
        assertEquals(2, residentA().getDetails().size());
        assertEquals("demo-incident-01", residentA().getDetails().get(0).getId());
        assertEquals("demo-incident-02", residentA().getDetails().get(1).getId());
    }
    @Test public void residentBOnlyGetsOwnMockDetail() {
        ResidentIncidentDetailPreview.Snapshot b =
                ResidentIncidentDetailPreview.forDemoResident("demo-resident-b");
        assertEquals(1, b.getDetails().size());
        assertEquals("demo-incident-03", b.getDetails().get(0).getId());
    }
    @Test public void missingMockOwnerProducesErrorWithoutDetails() {
        ResidentIncidentDetailPreview.Snapshot s = ResidentIncidentDetailPreview.forDemoResident("  ");
        assertEquals(ResidentIncidentDetailPreview.State.ERROR, s.getState());
        assertTrue(s.getDetails().isEmpty());
        assertEquals(ResidentIncidentDetailPreview.State.ERROR,
                ResidentIncidentDetailPreview.forDemoResident(null).getState());
    }
    @Test public void unknownMockOwnerProducesEmptySnapshot() {
        assertEquals(ResidentIncidentDetailPreview.State.EMPTY,
                ResidentIncidentDetailPreview.forDemoResident("not-found").getState());
    }
    @Test public void findByIdReturnsDetailedFields() {
        ResidentIncidentDetailPreview.LookupResult result =
                ResidentIncidentDetailPreview.findById(residentA(), "demo-incident-02");
        assertEquals(ResidentIncidentDetailPreview.LookupState.FOUND, result.getState());
        assertEquals("Phòng 301", result.getDetail().getLocation());
        assertEquals("Đã giao", result.getDetail().getStatusLabel());
        assertFalse(result.getDetail().getDescription().isEmpty());
        assertEquals(2, result.getDetail().getTimeline().size());
    }
    @Test public void cannotFindOtherMockResidentIncident() {
        ResidentIncidentDetailPreview.LookupResult result =
                ResidentIncidentDetailPreview.findById(residentA(), "demo-incident-03");
        assertEquals(ResidentIncidentDetailPreview.LookupState.NOT_FOUND, result.getState());
        assertNull(result.getDetail());
    }
    @Test public void unknownIdReturnsNotFound() {
        assertEquals(ResidentIncidentDetailPreview.LookupState.NOT_FOUND,
                ResidentIncidentDetailPreview.findById(residentA(), "nothing").getState());
    }
    @Test public void missingIdRejected() {
        assertEquals(ResidentIncidentDetailPreview.LookupState.INVALID,
                ResidentIncidentDetailPreview.findById(residentA(), " \u3000 ").getState());
        assertEquals(ResidentIncidentDetailPreview.LookupState.INVALID,
                ResidentIncidentDetailPreview.findById(residentA(), null).getState());
    }
    @Test public void loadingOrErrorCannotProvideDetails() {
        assertEquals(ResidentIncidentDetailPreview.LookupState.INVALID,
                ResidentIncidentDetailPreview.findById(ResidentIncidentDetailPreview.loading(), "x").getState());
        assertEquals(ResidentIncidentDetailPreview.LookupState.INVALID,
                ResidentIncidentDetailPreview.findById(ResidentIncidentDetailPreview.error("offline"), "x").getState());
        assertEquals(ResidentIncidentDetailPreview.LookupState.INVALID,
                ResidentIncidentDetailPreview.findById(null, "x").getState());
    }
    @Test public void copiedTimelineCannotBeModifiedAfterConstruction() {
        List<ResidentIncidentDetailPreview.HistoryEntry> source = new ArrayList<>();
        source.add(new ResidentIncidentDetailPreview.HistoryEntry("Mock", "Actor", 1L, "note"));
        ResidentIncidentDetailPreview.Detail d = ResidentIncidentDetailPreview.fromItem(
                ResidentIncidentListPreview.forDemoResident("demo-resident-a").getItems().get(0), "Desc", source);
        source.clear();
        assertEquals(1, d.getTimeline().size());
        assertEquals("note", d.getTimeline().get(0).getNote());
    }
    @Test(expected = UnsupportedOperationException.class)
    public void timelineIsImmutable() { first().getTimeline().clear(); }
    @Test(expected = UnsupportedOperationException.class)
    public void snapshotIsImmutable() { residentA().getDetails().clear(); }
    @Test public void duplicateIdsRejectWholeSnapshot() {
        assertEquals(ResidentIncidentDetailPreview.State.ERROR,
                ResidentIncidentDetailPreview.fromDetails(Arrays.asList(first(), first())).getState());
    }
    @Test public void nullOrCorruptListRejectsWithoutPartialRows() {
        assertEquals(ResidentIncidentDetailPreview.State.ERROR,
                ResidentIncidentDetailPreview.fromDetails(null).getState());
        ResidentIncidentDetailPreview.Snapshot invalid =
                ResidentIncidentDetailPreview.fromDetails(Arrays.asList(first(), null));
        assertEquals(ResidentIncidentDetailPreview.State.ERROR, invalid.getState());
        assertTrue(invalid.getDetails().isEmpty());
    }
    @Test(expected = IllegalArgumentException.class)
    public void fromItemRejectsNullSummary() {
        ResidentIncidentDetailPreview.fromItem(null, "desc", Collections.emptyList());
    }
    @Test(expected = IllegalArgumentException.class)
    public void fromItemRejectsNullEvent() {
        ResidentIncidentDetailPreview.fromItem(
                ResidentIncidentListPreview.forDemoResident("demo-resident-a").getItems().get(0),
                "desc", Arrays.asList((ResidentIncidentDetailPreview.HistoryEntry) null));
    }
    @Test(expected = IllegalArgumentException.class)
    public void historyRejectsBlankStatus() {
        new ResidentIncidentDetailPreview.HistoryEntry("\u00a0", "Mock", 1L, null);
    }
    @Test public void timelineIsChronologicalInDemoAndRepeatable() {
        ResidentIncidentDetailPreview.Detail d = ResidentIncidentDetailPreview.forDemoResident("demo-resident-b")
                .getDetails().get(0);
        assertEquals(3, d.getTimeline().size());
        assertTrue(d.getTimeline().get(0).getDemoOccurredAtMillis() < d.getTimeline().get(1).getDemoOccurredAtMillis());
        assertTrue(d.getTimeline().get(1).getDemoOccurredAtMillis() < d.getTimeline().get(2).getDemoOccurredAtMillis());
        assertEquals(first().getTitle(), residentA().getDetails().get(0).getTitle());
    }
}

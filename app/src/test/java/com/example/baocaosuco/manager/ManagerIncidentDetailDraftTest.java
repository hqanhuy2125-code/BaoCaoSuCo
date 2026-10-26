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

public class ManagerIncidentDetailDraftTest {

    private static ManagerIncidentDetailDraft.Detail first() {
        return ManagerIncidentDetailDraft.demoDetails().get(0);
    }

    @Test
    public void demoDetails_includeItemsFromManagerList() {
        List<ManagerIncidentDetailDraft.Detail> details = ManagerIncidentDetailDraft.demoDetails();
        assertEquals(3, details.size());
        assertEquals("demo-resident-a", details.get(0).getDemoResidentKey());
        assertEquals("demo-resident-b", details.get(1).getDemoResidentKey());
        assertEquals("mgr-demo-001", details.get(0).getId());
    }

    @Test
    public void demoDetails_exposeFullDisplayFieldsAndMockDescription() {
        ManagerIncidentDetailDraft.Detail detail = first();
        assertFalse(detail.getTitle().isEmpty());
        assertFalse(detail.getLocation().isEmpty());
        assertFalse(detail.getPriorityLabel().isEmpty());
        assertFalse(detail.getStatusLabel().isEmpty());
        assertFalse(detail.getDescription().isEmpty());
        assertNull(detail.getAssignedHandler());
    }

    @Test
    public void findById_returnsMatchingDetail() {
        ManagerIncidentDetailDraft.LookupResult result = ManagerIncidentDetailDraft.findById(
                ManagerIncidentDetailDraft.demoDetails(), "mgr-demo-002");
        assertEquals(ManagerIncidentDetailDraft.LookupState.FOUND, result.getState());
        assertEquals("To ky thuat A", result.getDetail().getAssignedHandler());
    }

    @Test
    public void findById_unknown_returnsNotFound() {
        ManagerIncidentDetailDraft.LookupResult result = ManagerIncidentDetailDraft.findById(
                ManagerIncidentDetailDraft.demoDetails(), "unknown");
        assertEquals(ManagerIncidentDetailDraft.LookupState.NOT_FOUND, result.getState());
        assertNull(result.getDetail());
    }

    @Test
    public void findById_emptyList_returnsNotFound() {
        assertEquals(ManagerIncidentDetailDraft.LookupState.NOT_FOUND,
                ManagerIncidentDetailDraft.findById(Collections.emptyList(), "mgr-demo-001")
                        .getState());
    }

    @Test
    public void findById_nullListIsInvalid() {
        assertEquals(ManagerIncidentDetailDraft.LookupState.INVALID,
                ManagerIncidentDetailDraft.findById(null, "mgr-demo-001").getState());
    }

    @Test
    public void findById_blankOrNullIdIsInvalid() {
        assertEquals(ManagerIncidentDetailDraft.LookupState.INVALID,
                ManagerIncidentDetailDraft.findById(ManagerIncidentDetailDraft.demoDetails(), " \u00a0 ")
                        .getState());
        assertEquals(ManagerIncidentDetailDraft.LookupState.INVALID,
                ManagerIncidentDetailDraft.findById(ManagerIncidentDetailDraft.demoDetails(), null)
                        .getState());
    }

    @Test
    public void findById_nullEntryIsInvalid() {
        List<ManagerIncidentDetailDraft.Detail> details = new ArrayList<>();
        details.add(first());
        details.add(null);
        assertEquals(ManagerIncidentDetailDraft.LookupState.INVALID,
                ManagerIncidentDetailDraft.findById(details, "mgr-demo-001").getState());
    }

    @Test
    public void findById_duplicateIdsAreInvalid() {
        List<ManagerIncidentDetailDraft.Detail> details = Arrays.asList(first(), first());
        assertEquals(ManagerIncidentDetailDraft.LookupState.INVALID,
                ManagerIncidentDetailDraft.findById(details, "mgr-demo-001").getState());
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromRow_rejectsNullSummary() {
        ManagerIncidentDetailDraft.fromRow(null, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromRow_rejectsEmptyId() {
        ManagerIncidentDetailDraft.fromRow(
                new ManagerIncidentListPreview.Row("\t", "u", "a", "loc", "normal", "new"),
                null, null);
    }

    @Test
    public void prepareAssignment_requiresHandlerName() {
        ManagerIncidentDetailDraft.AssignmentDraft draft =
                ManagerIncidentDetailDraft.prepareAssignment(first(), " \u3000 \t");
        assertEquals(ManagerIncidentDetailDraft.AssignmentState.MISSING_HANDLER, draft.getState());
        assertEquals(first().getId(), draft.getIncidentId());
        assertNull(draft.getHandlerName());
    }

    @Test
    public void prepareAssignment_rejectsNullHandler() {
        assertEquals(ManagerIncidentDetailDraft.AssignmentState.MISSING_HANDLER,
                ManagerIncidentDetailDraft.prepareAssignment(first(), null).getState());
    }

    @Test
    public void prepareAssignment_rejectsMissingDetail() {
        ManagerIncidentDetailDraft.AssignmentDraft draft =
                ManagerIncidentDetailDraft.prepareAssignment(null, "Person A");
        assertEquals(ManagerIncidentDetailDraft.AssignmentState.INVALID_INCIDENT, draft.getState());
        assertNull(draft.getIncidentId());
        assertNull(draft.getHandlerName());
    }

    @Test
    public void prepareAssignment_readyWithTrimmedHandlerAndStableId() {
        ManagerIncidentDetailDraft.AssignmentDraft draft =
                ManagerIncidentDetailDraft.prepareAssignment(first(), "\u00a0 Ms Linh \u3000");
        assertEquals(ManagerIncidentDetailDraft.AssignmentState.READY, draft.getState());
        assertEquals("mgr-demo-001", draft.getIncidentId());
        assertEquals("Ms Linh", draft.getHandlerName());
    }

    @Test
    public void prepareAssignment_doesNotMutateDetailOrStatus() {
        ManagerIncidentDetailDraft.Detail detail = first();
        String previous = detail.getStatusLabel();
        ManagerIncidentDetailDraft.prepareAssignment(detail, "Person A");
        assertEquals(previous, detail.getStatusLabel());
        assertNull(detail.getAssignedHandler());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void demoDetails_areImmutable() {
        ManagerIncidentDetailDraft.demoDetails().clear();
    }

    @Test
    public void repeatedLookupIsStable() {
        ManagerIncidentDetailDraft.LookupResult one = ManagerIncidentDetailDraft.findById(
                ManagerIncidentDetailDraft.demoDetails(), "mgr-demo-001");
        ManagerIncidentDetailDraft.LookupResult two = ManagerIncidentDetailDraft.findById(
                ManagerIncidentDetailDraft.demoDetails(), "mgr-demo-001");
        assertTrue(one.getDetail() != two.getDetail());
        assertEquals(one.getDetail().getTitle(), two.getDetail().getTitle());
    }
}

package com.example.baocaosuco.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManagerIncidentListFilterSorterTest {

    private static List<ManagerIncidentListPreview.Row> rows() {
        return Arrays.asList(
                new ManagerIncidentListPreview.Row("a", "u1", "A", "P1", "Normal", "New"),
                new ManagerIncidentListPreview.Row("b", "u2", "B", "P2", "Urgent", "Assigned"),
                new ManagerIncidentListPreview.Row("c", "u3", "C", "P3", "Low", "New"),
                new ManagerIncidentListPreview.Row("d", "u1", "D", "P4", "Urgent", "New"),
                new ManagerIncidentListPreview.Row("e", "u1", "E", "P5", "Undefined", "New")
        );
    }

    private static List<String> order() {
        return Arrays.asList("Urgent", "Normal", "Low");
    }

    private static Map<String, Long> due() {
        Map<String, Long> timestamps = new HashMap<>();
        timestamps.put("a", 300L);
        timestamps.put("b", 100L);
        timestamps.put("c", 200L);
        timestamps.put("d", 50L);
        return timestamps; // e lacks a due date intentionally
    }

    private static List<ManagerIncidentListPreview.Row> run(String filter,
            ManagerIncidentListFilterSorter.SortMode mode) {
        return ManagerIncidentListFilterSorter.filterAndSort(rows(), filter, mode, order(), due());
    }

    private static String ids(List<ManagerIncidentListPreview.Row> result) {
        StringBuilder builder = new StringBuilder();
        for (ManagerIncidentListPreview.Row row : result) {
            builder.append(row.getId());
        }
        return builder.toString();
    }

    @Test
    public void original_keepsAllRowsAndSourceOrder() {
        assertEquals("abcde", ids(run(null, ManagerIncidentListFilterSorter.SortMode.ORIGINAL)));
    }

    @Test
    public void blankStatusFilter_keepsAllRows() {
        assertEquals("abcde", ids(run(" \t", ManagerIncidentListFilterSorter.SortMode.ORIGINAL)));
    }

    @Test
    public void exactStatusFilter_keepsOnlyMatchingRows() {
        assertEquals("acde", ids(run("New", ManagerIncidentListFilterSorter.SortMode.ORIGINAL)));
    }

    @Test
    public void unknownStatusFilter_isEmpty() {
        assertTrue(run("Completed", ManagerIncidentListFilterSorter.SortMode.ORIGINAL).isEmpty());
    }

    @Test
    public void prioritySorting_respectsCallerProvidedOrderUnknownLast() {
        assertEquals("bdace", ids(run(null, ManagerIncidentListFilterSorter.SortMode.PRIORITY_HIGH_FIRST)));
    }

    @Test
    public void priorityTie_preservesSourceOrder() {
        assertEquals("bd", ids(ManagerIncidentListFilterSorter.filterAndSort(
                Arrays.asList(rows().get(1), rows().get(3)), "",
                ManagerIncidentListFilterSorter.SortMode.PRIORITY_HIGH_FIRST, order(), due())));
    }

    @Test
    public void earliestDueSorting_missingDatesLast() {
        assertEquals("dbc ae".replace(" ", ""),
                ids(run(null, ManagerIncidentListFilterSorter.SortMode.DUE_SOONEST_FIRST)));
    }

    @Test
    public void priorityThenDue_breaksTiesByEarliestDue() {
        assertEquals("dbace", ids(run(null, ManagerIncidentListFilterSorter.SortMode.PRIORITY_THEN_DUE)));
    }

    @Test
    public void statusFilterIsAppliedBeforePrioritySorting() {
        assertEquals("dace", ids(run("New", ManagerIncidentListFilterSorter.SortMode.PRIORITY_THEN_DUE)));
    }

    @Test
    public void callerCanChangePriorityOrderWithoutCodeChange() {
        assertEquals("cbdae", ids(ManagerIncidentListFilterSorter.filterAndSort(
                rows(), null, ManagerIncidentListFilterSorter.SortMode.PRIORITY_HIGH_FIRST,
                Arrays.asList("Low", "Urgent", "Normal"), due())));
    }

    @Test
    public void inputListNotMutatedBySorting() {
        List<ManagerIncidentListPreview.Row> source = new ArrayList<>(rows());
        ManagerIncidentListFilterSorter.filterAndSort(source, null,
                ManagerIncidentListFilterSorter.SortMode.DUE_SOONEST_FIRST, order(), due());
        assertEquals("abcde", ids(source));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void resultIsImmutable() {
        run(null, ManagerIncidentListFilterSorter.SortMode.ORIGINAL).clear();
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullSourceRejected() {
        ManagerIncidentListFilterSorter.filterAndSort(null, null,
                ManagerIncidentListFilterSorter.SortMode.ORIGINAL, order(), due());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullRowRejected() {
        ManagerIncidentListFilterSorter.filterAndSort(Arrays.asList(rows().get(0), null), null,
                ManagerIncidentListFilterSorter.SortMode.ORIGINAL, order(), due());
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicatePriorityLabelsRejected() {
        ManagerIncidentListFilterSorter.filterAndSort(rows(), null,
                ManagerIncidentListFilterSorter.SortMode.PRIORITY_HIGH_FIRST,
                Arrays.asList("Urgent", "Urgent"), due());
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingPriorityConfigRejected() {
        ManagerIncidentListFilterSorter.filterAndSort(rows(), null,
                ManagerIncidentListFilterSorter.SortMode.PRIORITY_HIGH_FIRST, null, due());
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingDueConfigRejected() {
        ManagerIncidentListFilterSorter.filterAndSort(rows(), null,
                ManagerIncidentListFilterSorter.SortMode.DUE_SOONEST_FIRST, order(), null);
    }

    @Test
    public void sourceWithNullStatus_isSafeToFilter() {
        ManagerIncidentListPreview.Row row = new ManagerIncidentListPreview.Row(
                "z", "u", "x", "loc", "Unknown", null);
        assertEquals(0, ManagerIncidentListFilterSorter.filterAndSort(
                Collections.singletonList(row), "New",
                ManagerIncidentListFilterSorter.SortMode.ORIGINAL, order(), due()).size());
    }
}

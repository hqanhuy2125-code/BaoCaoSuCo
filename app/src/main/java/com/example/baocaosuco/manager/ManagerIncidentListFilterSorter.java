package com.example.baocaosuco.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loc va sap xep danh sach su co minh hoa cua Quan ly, KHONG truy cap Firestore.
 * Thu tu uu tien va han xu ly duoc cung cap tu ben ngoai; khong tu dat SLA.
 */
public final class ManagerIncidentListFilterSorter {

    public enum SortMode {
        ORIGINAL,
        PRIORITY_HIGH_FIRST,
        DUE_SOONEST_FIRST,
        PRIORITY_THEN_DUE
    }

    private ManagerIncidentListFilterSorter() {
    }

    /**
     * Loc theo nhan trang thai dung nguyen van; null/rong la xem tat ca.
     * priorityHighToLow: danh sach nhan uu tien theo thu tu cao xuong thap.
     * dueAtMillisById: moc thoi gian demo, KHONG phai deadline duoc tinh tu client.
     * Ket qua la list bat bien; cac muc dong hang giu nguyen thu tu dau vao.
     */
    public static List<ManagerIncidentListPreview.Row> filterAndSort(
            List<ManagerIncidentListPreview.Row> source,
            String statusFilter,
            SortMode sortMode,
            List<String> priorityHighToLow,
            Map<String, Long> dueAtMillisById) {
        if (source == null || sortMode == null) {
            throw new IllegalArgumentException("Source and sort mode are required.");
        }
        boolean byPriority = sortMode == SortMode.PRIORITY_HIGH_FIRST
                || sortMode == SortMode.PRIORITY_THEN_DUE;
        boolean byDue = sortMode == SortMode.DUE_SOONEST_FIRST
                || sortMode == SortMode.PRIORITY_THEN_DUE;
        if (byPriority && priorityHighToLow == null) {
            throw new IllegalArgumentException("Priority order is required for priority sorting.");
        }
        if (byDue && dueAtMillisById == null) {
            throw new IllegalArgumentException("Due-date lookup is required for due sorting.");
        }

        Map<String, Integer> rank = new HashMap<>();
        if (byPriority) {
            for (int i = 0; i < priorityHighToLow.size(); i++) {
                String label = priorityHighToLow.get(i);
                if (label == null || label.trim().isEmpty() || rank.containsKey(label)) {
                    throw new IllegalArgumentException("Priority order has an empty or duplicate label.");
                }
                rank.put(label, i);
            }
        }

        String target = statusFilter == null ? "" : statusFilter.trim();
        List<ManagerIncidentListPreview.Row> result = new ArrayList<>();
        for (ManagerIncidentListPreview.Row row : source) {
            if (row == null) {
                throw new IllegalArgumentException("Incident list cannot contain null rows.");
            }
            if (target.isEmpty() || target.equals(row.getStatusLabel())) {
                result.add(row);
            }
        }

        if (sortMode != SortMode.ORIGINAL) {
            Comparator<ManagerIncidentListPreview.Row> comparator = (left, right) -> {
                if (byPriority) {
                    int leftRank = rank.getOrDefault(left.getPriorityLabel(), Integer.MAX_VALUE);
                    int rightRank = rank.getOrDefault(right.getPriorityLabel(), Integer.MAX_VALUE);
                    int compared = Integer.compare(leftRank, rightRank);
                    if (compared != 0) {
                        return compared;
                    }
                }
                if (byDue) {
                    Long leftDue = dueAtMillisById.get(left.getId());
                    Long rightDue = dueAtMillisById.get(right.getId());
                    if (leftDue == null) {
                        return rightDue == null ? 0 : 1;
                    }
                    if (rightDue == null) {
                        return -1;
                    }
                    return Long.compare(leftDue, rightDue);
                }
                return 0;
            };
            // TimSort (List.sort) on ArrayList is stable for equal comparator values.
            result.sort(comparator);
        }
        return Collections.unmodifiableList(result);
    }
}

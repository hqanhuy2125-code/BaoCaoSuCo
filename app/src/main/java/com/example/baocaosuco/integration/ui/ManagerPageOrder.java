package com.example.baocaosuco.integration.ui;

import com.example.baocaosuco.data.incident.IncidentSnapshot;
import java.util.Comparator;
import java.util.List;

/** Presentation order for a single Manager page. Deadlines are already derived by the domain. */
enum ManagerPageOrder {
    PRIORITY_THEN_DUE("Ưu tiên rồi đến hạn"),
    DUE_FIRST("Hạn gần nhất"),
    NEWEST("Mới tạo trước");

    private static final Comparator<IncidentSnapshot> RECENT = Comparator
            .comparingLong(IncidentSnapshot::getCreatedAtMillis).reversed()
            .thenComparing(IncidentSnapshot::getId);
    final String label;

    ManagerPageOrder(String label) {
        this.label = label;
    }

    void sort(List<IncidentSnapshot> rows) {
        Comparator<IncidentSnapshot> order = RECENT;
        if (this != NEWEST) {
            order = Comparator.comparingLong(IncidentSnapshot::getDueAtMillis).thenComparing(RECENT);
            if (this == PRIORITY_THEN_DUE) {
                order = Comparator.comparingInt(ManagerPageOrder::priorityRank).thenComparing(order);
            }
        }
        rows.sort(order);
    }

    private static int priorityRank(IncidentSnapshot item) {
        switch (item.getPriority()) {
            case "URGENT": return 0;
            case "NORMAL": return 1;
            default: return 2;
        }
    }
}

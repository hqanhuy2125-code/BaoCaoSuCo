package com.example.baocaosuco.integration.mock;

import com.example.baocaosuco.manager.ManagerIncidentListPreview;
import com.example.baocaosuco.manager.ManagerIncidentOverview;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Binds MGR-05A to mock status classification and injected CON-02 MVP SLA.
 * Callers MUST supply explicit synthetic createdAt values; missing dates stay UNKNOWN.
 * No Firebase read/write, no real manager permission or real clock authority.
 */
public final class MockManagerOverviewWiring {
    private MockManagerOverviewWiring() { }

    public static ManagerIncidentOverview fromDemo(
            Map<String, Long> demoCreatedAtMillisByIncidentId,
            ManagerIncidentOverview.TimeSource now) {
        if (demoCreatedAtMillisByIncidentId == null || now == null) {
            throw new IllegalArgumentException("Explicit synthetic timestamps and clock required");
        }
        Map<String, Long> dates = Collections.unmodifiableMap(
                new HashMap<>(demoCreatedAtMillisByIncidentId));
        ManagerIncidentOverview.DataSource source = callback -> {
            List<ManagerIncidentListPreview.Row> rows =
                    ManagerIncidentListPreview.demoAllIncidents().getRows();
            Map<String, Long> due = new HashMap<>();
            try {
                for (ManagerIncidentListPreview.Row row : rows) {
                    Long createdAt = dates.get(row.getId());
                    if (createdAt != null) {
                        due.put(row.getId(),
                                MockManagerOverduePolicy.previewDueAt(
                                        createdAt, row.getPriorityLabel()));
                    }
                }
                callback.onLoaded(rows, Collections.unmodifiableMap(due));
            } catch (IllegalArgumentException | ArithmeticException ex) {
                callback.onFailed(ManagerIncidentOverview.ReadError.INVALID_DATA);
            }
        };
        return new ManagerIncidentOverview(source, new MockManagerOverduePolicy(), now);
    }
}

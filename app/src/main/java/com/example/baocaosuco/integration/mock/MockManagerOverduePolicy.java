package com.example.baocaosuco.integration.mock;

import com.example.baocaosuco.logic.IncidentDeadlinePolicy;
import com.example.baocaosuco.logic.IncidentStatus;
import com.example.baocaosuco.manager.ManagerIncidentOverview;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CON-02 MVP domain defaults for mock-only sorting and overdue previews.
 * CreatedAt and now are explicit test/demo inputs, not trusted Firestore time.
 */
public final class MockManagerOverduePolicy
        implements ManagerIncidentOverview.StatusClassifier {

    private static final Map<String, Long> APPROVED_MVP_SLA;
    static {
        Map<String, Long> values = new HashMap<>();
        values.put("URGENT", 4L * 60L * 60L * 1000L);
        values.put("NORMAL", 72L * 60L * 60L * 1000L);
        values.put("LOW", 168L * 60L * 60L * 1000L);
        APPROVED_MVP_SLA = Collections.unmodifiableMap(values);
    }

    public static Map<String, Long> mvpSlaMillis() {
        return APPROVED_MVP_SLA;
    }

    public static List<String> managerPreviewPrioritiesHighFirst() {
        return Collections.unmodifiableList(Arrays.asList("Khan", "Thuong", "Thap"));
    }

    /** Demo-createdAt is supplied by the caller; never invent a persisted timestamp. */
    public static long previewDueAt(long demoCreatedAtEpochMillis, String priorityLabel) {
        String priority = MockIncidentValueMapper.previewPriority(priorityLabel);
        if (priority == null) throw new IllegalArgumentException("Unknown mock priority");
        return IncidentDeadlinePolicy.calculateDueAt(
                demoCreatedAtEpochMillis, priority, APPROVED_MVP_SLA);
    }

    @Override
    public ManagerIncidentOverview.StatusCategory classify(String statusLabel) {
        IncidentStatus status = MockIncidentValueMapper.previewStatus(statusLabel);
        if (status == null) return ManagerIncidentOverview.StatusCategory.UNKNOWN;
        switch (status) {
            case NEW:
            case ASSIGNED:
            case IN_PROGRESS:
                return ManagerIncidentOverview.StatusCategory.ACTIVE;
            case COMPLETED:
            case CLOSED:
                return ManagerIncidentOverview.StatusCategory.TERMINAL;
            default:
                return ManagerIncidentOverview.StatusCategory.UNKNOWN;
        }
    }
}

package com.example.baocaosuco.logic;
import java.util.Map;
/** Absolute epoch milliseconds. SLA values must be supplied by caller after group approval. */
public final class IncidentDeadlinePolicy {
    private IncidentDeadlinePolicy() {}
    public static <P> long calculateDueAt(long createdAtEpochMillis, P priority,
            Map<P, Long> approvedSlaMillis) {
        if (createdAtEpochMillis < 0) throw new IllegalArgumentException("Invalid createdAt");
        if (priority == null || approvedSlaMillis == null || !approvedSlaMillis.containsKey(priority))
            throw new IllegalArgumentException("Missing priority/SLA");
        Long duration = approvedSlaMillis.get(priority);
        if (duration == null || duration <= 0) throw new IllegalArgumentException("SLA must be positive");
        return Math.addExact(createdAtEpochMillis, duration);
    }
}

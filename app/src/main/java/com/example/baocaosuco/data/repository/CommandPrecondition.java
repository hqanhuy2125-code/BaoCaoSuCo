package com.example.baocaosuco.data.repository;

import com.example.baocaosuco.logic.IncidentStatus;
import com.example.baocaosuco.logic.IncidentStatusCodec;

/** Immutable client-observed precondition; NOT actor/role authorization. */
public final class CommandPrecondition {
    private final String expectedCanonicalStatus;
    private final int expectedRevision;
    private final String requestId;

    public CommandPrecondition(String expectedCanonicalStatus, int expectedRevision, String requestId) {
        IncidentStatus status = IncidentStatusCodec.parseCanonical(expectedCanonicalStatus);
        if (status == null || expectedRevision < 0 ||
                (status == IncidentStatus.NEW) != (expectedRevision == 0) ||
                requestId == null || requestId.trim().isEmpty() || requestId.contains("/")) {
            throw new IllegalArgumentException("Invalid command precondition");
        }
        this.expectedCanonicalStatus = status.name();
        this.expectedRevision = expectedRevision;
        this.requestId = requestId.trim();
    }

    public String getExpectedCanonicalStatus() { return expectedCanonicalStatus; }
    public int getExpectedRevision() { return expectedRevision; }
    public String getRequestId() { return requestId; }

    public boolean matchesObserved(String persistedCanonicalStatus, int persistedRevision) {
        return expectedRevision == persistedRevision &&
                expectedCanonicalStatus.equals(persistedCanonicalStatus);
    }
}

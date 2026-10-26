package com.example.baocaosuco.integration.mock;

import com.example.baocaosuco.manager.ManagerIncidentActionCoordinator;
import com.example.baocaosuco.resident.ResidentResolutionCoordinator;

/**
 * Explicit fail-closed demo gateways. No Firebase or repository writes.
 * Never returns onCommitted(), so mock preview cannot impersonate persistence.
 */
public final class MockDeniedCommitGateways {
    public static final String REASON = "MOCK_ONLY_NO_PERSISTENCE";
    private MockDeniedCommitGateways() { }

    public static ManagerIncidentActionCoordinator.CommitGateway manager() {
        return (command, callback) -> callback.onFailed(REASON);
    }

    public static ResidentResolutionCoordinator.CommitGateway resident() {
        return (command, callback) -> callback.onFailed(REASON);
    }
}

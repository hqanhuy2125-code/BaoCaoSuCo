package com.example.baocaosuco.integration.mock;

import com.example.baocaosuco.manager.ManagerIncidentListPreview;
import com.example.baocaosuco.manager.ManagerIncidentOverview;
import com.example.baocaosuco.resident.ResidentIncidentDetailPreview;
import com.example.baocaosuco.resident.ResidentResolutionCoordinator;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Targeted CP-05A review-coverage tests.
 * These verify mock presentation and failure handling, never Firebase permissions.
 */
public final class MockIntegrationCoverageGatesTest {

    @Test public void closedStatusThroughManagerOverviewDoesNotDisplayOverdue() {
        ManagerIncidentListPreview.Row closed = new ManagerIncidentListPreview.Row(
                "closed-demo", "synthetic-resident", "Closed report", "Demo room",
                "Thường", "Đã đóng");

        ManagerIncidentOverview.DataSource source = callback ->
                callback.onLoaded(Collections.singletonList(closed),
                        Collections.singletonMap("closed-demo", 1_000L));
        AtomicReference<ManagerIncidentOverview.Snapshot> latest = new AtomicReference<>();
        new ManagerIncidentOverview(source, new MockManagerOverduePolicy(),
                () -> 900_000L).load(latest::set);

        assertNotNull(latest.get());
        assertEquals(ManagerIncidentOverview.State.CONTENT, latest.get().getState());
        assertEquals(1, latest.get().getRows().size());
        assertEquals(ManagerIncidentOverview.Overdue.NO,
                latest.get().getRows().get(0).getOverdue());
        assertEquals(0, latest.get().getOverdueCount());
        assertEquals("Đã đóng",
                latest.get().getRows().get(0).getIncident().getStatusLabel());
    }

    @Test public void residentCoordinatorDeniedGatewayFailsOnceAndAllowsRetry() {
        // The existing RES demo fixture has status "Mới", not COMPLETED.
        // Synthetic allow rule exercises coordinator/gateway transport ONLY,
        // not the actual LOG transition rule nor report-owner authorization.
        ResidentIncidentDetailPreview.Detail original =
                ResidentIncidentDetailPreview.forDemoResident("demo-resident-a")
                        .getDetails().get(0);
        String unchangedStatus = original.getStatusLabel();
        int unchangedHistoryCount = original.getTimeline().size();

        AtomicInteger commitsAttempted = new AtomicInteger();
        AtomicInteger callbacksFailed = new AtomicInteger();
        AtomicInteger callbacksCommitted = new AtomicInteger();
        AtomicReference<ResidentResolutionCoordinator.Command> command = new AtomicReference<>();
        List<ResidentResolutionCoordinator.Result> emitted = new ArrayList<>();

        ResidentResolutionCoordinator.RuleAdapter syntheticRule =
                (feedback, currentStatus) ->
                        ResidentResolutionCoordinator.Decision.allow("CLOSED");
        ResidentResolutionCoordinator.CommitGateway guardedGateway = (cmd, cb) -> {
            commitsAttempted.incrementAndGet();
            command.set(cmd);
            MockDeniedCommitGateways.resident().commit(cmd,
                    new ResidentResolutionCoordinator.CommitCallback() {
                        @Override public void onCommitted() {
                            callbacksCommitted.incrementAndGet();
                            cb.onCommitted();
                        }
                        @Override public void onFailed(String reason) {
                            callbacksFailed.incrementAndGet();
                            cb.onFailed(reason);
                        }
                    });
        };
        ResidentResolutionCoordinator coordinator =
                new ResidentResolutionCoordinator(syntheticRule, guardedGateway);

        coordinator.submit(original, ResidentResolutionCoordinator.Feedback.RESOLVED,
                emitted::add);
        assertEquals(1, commitsAttempted.get());
        assertNotNull(command.get());
        assertEquals(original.getId(), command.get().getIncidentId());
        assertEquals(unchangedStatus, command.get().getExpectedStatus());
        assertEquals("CLOSED", command.get().getNextStatus());
        assertEquals(1, emitted.size());
        assertEquals(ResidentResolutionCoordinator.Outcome.FAILED,
                emitted.get(0).getOutcome());
        assertEquals(MockDeniedCommitGateways.REASON, emitted.get(0).getMessage());
        assertEquals(1, callbacksFailed.get());
        assertEquals(0, callbacksCommitted.get());

        coordinator.submit(original, ResidentResolutionCoordinator.Feedback.RESOLVED,
                emitted::add);
        assertEquals(2, commitsAttempted.get());
        assertEquals(2, callbacksFailed.get());
        assertEquals(0, callbacksCommitted.get());
        assertEquals(2, emitted.size());
        assertEquals(ResidentResolutionCoordinator.Outcome.FAILED,
                emitted.get(1).getOutcome());
        assertEquals(MockDeniedCommitGateways.REASON, emitted.get(1).getMessage());

        // A mock rejection neither mutates the fixture nor appends fake history.
        assertEquals(unchangedStatus, original.getStatusLabel());
        assertEquals(unchangedHistoryCount, original.getTimeline().size());
    }
}

package com.example.baocaosuco.integration.mock;

import com.example.baocaosuco.logic.IncidentStatus;
import com.example.baocaosuco.logic.IncidentStatusCodec;
import com.example.baocaosuco.manager.ManagerIncidentActionCoordinator;
import com.example.baocaosuco.manager.ManagerIncidentDetailDraft;
import com.example.baocaosuco.manager.ManagerIncidentListPreview;
import com.example.baocaosuco.manager.ManagerIncidentOverview;
import com.example.baocaosuco.resident.ResidentResolutionCoordinator;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class MockLogIntegrationTest {

    @Test public void canonicalStatusAndVietnamesePreviewLabelsAreSeparate() {
        assertNull(IncidentStatusCodec.parseCanonical("Mới"));
        assertEquals(IncidentStatus.NEW, IncidentStatusCodec.parseCanonical("NEW"));
        assertEquals(IncidentStatus.NEW, MockIncidentValueMapper.previewStatus("Mới"));
        assertEquals(IncidentStatus.NEW, MockIncidentValueMapper.previewStatus("Moi"));
        assertEquals(IncidentStatus.ASSIGNED, MockIncidentValueMapper.previewStatus("Đã giao"));
        assertEquals(IncidentStatus.ASSIGNED, MockIncidentValueMapper.previewStatus("Da giao"));
        assertEquals(IncidentStatus.IN_PROGRESS, MockIncidentValueMapper.previewStatus("Đang xử lý"));
        assertEquals(IncidentStatus.IN_PROGRESS, MockIncidentValueMapper.previewStatus("Dang xu ly"));
        assertEquals(IncidentStatus.COMPLETED, MockIncidentValueMapper.previewStatus("Hoàn tất"));
        assertEquals(IncidentStatus.CLOSED, MockIncidentValueMapper.previewStatus("Da dong"));
        assertNull(MockIncidentValueMapper.previewStatus(null));
        assertNull(MockIncidentValueMapper.previewStatus("CLOSED_WRONG"));
        assertNull(MockIncidentValueMapper.previewStatus(" new "));
        for (IncidentStatus code : IncidentStatus.values()) {
            assertEquals(code, MockIncidentValueMapper.previewStatus(code.name()));
            assertEquals(code, MockIncidentValueMapper.previewStatus(
                    MockIncidentValueMapper.displayStatus(code)));
        }
    }

    @Test public void previewManagerBridgeCoversAllThreeValidManagerActions() {
        MockManagerLogicRuleAdapter rules = new MockManagerLogicRuleAdapter();
        assertEquals("ASSIGNED", rules.evaluate(ManagerIncidentActionCoordinator.Action.ASSIGN,
                "Moi", true).getNextStatus());
        assertEquals("IN_PROGRESS", rules.evaluate(
                ManagerIncidentActionCoordinator.Action.START_WORK, "Da giao", false).getNextStatus());
        assertEquals("COMPLETED", rules.evaluate(
                ManagerIncidentActionCoordinator.Action.MARK_COMPLETE, "Dang xu ly", false).getNextStatus());
    }

    @Test public void previewManagerRejectsInvalidStatusesAndAssignment() {
        MockManagerLogicRuleAdapter rules = new MockManagerLogicRuleAdapter();
        assertFalse(rules.evaluate(ManagerIncidentActionCoordinator.Action.ASSIGN, "Mới",
                false).isAllowed());
        assertFalse(rules.evaluate(ManagerIncidentActionCoordinator.Action.ASSIGN, "CLOSED",
                true).isAllowed());
        assertFalse(rules.evaluate(ManagerIncidentActionCoordinator.Action.MARK_COMPLETE, "NEW",
                true).isAllowed());
        assertFalse(rules.evaluate(ManagerIncidentActionCoordinator.Action.ASSIGN, "WRONG",
                true).isAllowed());
        assertFalse(rules.evaluate(null, "NEW", true).isAllowed());
        assertFalse(rules.evaluate(ManagerIncidentActionCoordinator.Action.ASSIGN, null,
                true).isAllowed());
    }

    @Test public void residentFeedbackPreviewCoversBothActionsWithoutOwnerAuthorization() {
        MockResidentLogicRuleAdapter rules = new MockResidentLogicRuleAdapter();
        assertEquals("CLOSED", rules.evaluate(ResidentResolutionCoordinator.Feedback.RESOLVED,
                "Hoàn tất").getNextStatus());
        assertEquals("IN_PROGRESS", rules.evaluate(
                ResidentResolutionCoordinator.Feedback.UNRESOLVED, "COMPLETED").getNextStatus());
        assertFalse(rules.evaluate(ResidentResolutionCoordinator.Feedback.RESOLVED,
                "Đã giao").isAllowed());
        assertFalse(rules.evaluate(ResidentResolutionCoordinator.Feedback.UNRESOLVED,
                "CLOSED").isAllowed());
        assertFalse(rules.evaluate(null, "COMPLETED").isAllowed());
        assertFalse(rules.evaluate(ResidentResolutionCoordinator.Feedback.RESOLVED,
                "invalid").isAllowed());
    }

    @Test public void approvedMvpSlaAndPriorityPreviewMapping() {
        long at = 1_000_000L;
        assertEquals(at + 14_400_000L,
                MockManagerOverduePolicy.previewDueAt(at, "Khẩn"));
        assertEquals(at + 259_200_000L,
                MockManagerOverduePolicy.previewDueAt(at, "Thuong"));
        assertEquals(at + 604_800_000L,
                MockManagerOverduePolicy.previewDueAt(at, "LOW"));
        assertEquals(Arrays.asList("Khan", "Thuong", "Thap"),
                MockManagerOverduePolicy.managerPreviewPrioritiesHighFirst());
        assertEquals(3, MockManagerOverduePolicy.mvpSlaMillis().size());
        try {
            MockManagerOverduePolicy.mvpSlaMillis().put("NEW", 10L);
            fail("SLA map should be immutable");
        } catch (UnsupportedOperationException expected) { }
    }

    @Test public void slaRejectsUnknownAndOverflowInputs() {
        try {
            MockManagerOverduePolicy.previewDueAt(0, "Unknown");
            fail("Unknown priority must fail");
        } catch (IllegalArgumentException expected) { }
        try {
            MockManagerOverduePolicy.previewDueAt(Long.MAX_VALUE, "URGENT");
            fail("Timestamp overflow must fail");
        } catch (ArithmeticException expected) { }
    }

    @Test public void overdueClassificationMatchesCon02DomainBaseline() {
        MockManagerOverduePolicy rules = new MockManagerOverduePolicy();
        for (String active : Arrays.asList("NEW", "Moi", "Đã giao", "IN_PROGRESS")) {
            assertEquals(ManagerIncidentOverview.StatusCategory.ACTIVE, rules.classify(active));
        }
        for (String terminal : Arrays.asList("COMPLETED", "Hoàn tất", "CLOSED", "Da dong")) {
            assertEquals(ManagerIncidentOverview.StatusCategory.TERMINAL, rules.classify(terminal));
        }
        assertEquals(ManagerIncidentOverview.StatusCategory.UNKNOWN, rules.classify("NOPE"));
        assertEquals(ManagerIncidentOverview.StatusCategory.UNKNOWN, rules.classify(null));
    }

    @Test public void overviewUsesStrictDueBoundaryAndNeverMarksCompletedOverdue() {
        MockManagerOverduePolicy rules = new MockManagerOverduePolicy();
        ManagerIncidentListPreview.Row active = new ManagerIncidentListPreview.Row(
                "x", "demo-only", "title", "room", "Khan", "Moi");
        ManagerIncidentListPreview.Row completed = new ManagerIncidentListPreview.Row(
                "y", "demo-only", "title", "room", "Thuong", "Hoan tat");
        Map<String, Long> dues = new HashMap<>();
        dues.put("x", 2_000L);
        dues.put("y", 1_000L);
        ManagerIncidentOverview.DataSource source = callback -> callback.onLoaded(
                Arrays.asList(active, completed), dues);
        AtomicReference<ManagerIncidentOverview.Snapshot> state = new AtomicReference<>();
        new ManagerIncidentOverview(source, rules, () -> 2_000L).load(state::set);
        assertEquals(ManagerIncidentOverview.State.CONTENT, state.get().getState());
        assertEquals(ManagerIncidentOverview.Overdue.NO,
                state.get().getRows().get(0).getOverdue());
        assertEquals(ManagerIncidentOverview.Overdue.NO,
                state.get().getRows().get(1).getOverdue());
        new ManagerIncidentOverview(source, rules, () -> 2_001L).load(state::set);
        assertEquals(ManagerIncidentOverview.Overdue.YES,
                state.get().getRows().get(0).getOverdue());
        assertEquals(ManagerIncidentOverview.Overdue.NO,
                state.get().getRows().get(1).getOverdue());
        assertEquals(1, state.get().getOverdueCount());
    }

    @Test public void coordinatorMustFailClosedWithoutRepositoryCommit() {
        ManagerIncidentListPreview.Row row = new ManagerIncidentListPreview.Row(
                "mgr-demo-001", "demo", "title", "room", "Thuong", "Moi");
        ManagerIncidentDetailDraft.Detail detail = ManagerIncidentDetailDraft.fromRow(
                row, "description", null);
        ManagerIncidentActionCoordinator coordinator = new ManagerIncidentActionCoordinator(
                new MockManagerLogicRuleAdapter(), MockDeniedCommitGateways.manager());
        AtomicReference<ManagerIncidentActionCoordinator.Result> result = new AtomicReference<>();
        coordinator.submit(detail, ManagerIncidentActionCoordinator.Action.ASSIGN,
                "Team A", result::set);
        assertEquals(ManagerIncidentActionCoordinator.Outcome.FAILED, result.get().getOutcome());
        assertEquals(MockDeniedCommitGateways.REASON, result.get().getMessage());
        // The mock row is immutable and cannot be treated as a committed assignment.
        assertEquals("Moi", detail.getStatusLabel());
        assertNull(detail.getAssignedHandler());
    }

    @Test public void originalManagerAndResidentMocksRemainUnchanged() {
        assertEquals(3, ManagerIncidentListPreview.demoAllIncidents().getRows().size());
        assertEquals("Moi",
                ManagerIncidentListPreview.demoAllIncidents().getRows().get(0).getStatusLabel());
        assertEquals("Mới",
                com.example.baocaosuco.resident.ResidentIncidentListPreview
                        .forDemoResident("demo-resident-a").getItems().get(0).getStatusLabel());
        assertEquals(2,
                com.example.baocaosuco.resident.ResidentIncidentListPreview
                        .forDemoResident("demo-resident-a").getItems().size());
    }

    @Test public void mockGatewaysNeverClaimDurableSuccess() {
        MockDeniedCommitGateways.manager().commit(null,
                new ManagerIncidentActionCoordinator.CommitCallback() {
                    @Override public void onCommitted() { fail("No mock commit allowed"); }
                    @Override public void onFailed(String why) {
                        assertEquals(MockDeniedCommitGateways.REASON, why);
                    }
                });
        MockDeniedCommitGateways.resident().commit(null,
                new ResidentResolutionCoordinator.CommitCallback() {
                    @Override public void onCommitted() { fail("No mock commit allowed"); }
                    @Override public void onFailed(String why) {
                        assertEquals(MockDeniedCommitGateways.REASON, why);
                    }
                });
    }
}

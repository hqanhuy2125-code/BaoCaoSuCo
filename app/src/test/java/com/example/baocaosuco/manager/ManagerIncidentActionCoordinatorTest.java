package com.example.baocaosuco.manager;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

public class ManagerIncidentActionCoordinatorTest {

    private static final class StubRules implements ManagerIncidentActionCoordinator.RuleAdapter {
        boolean allowed = true;
        String target = "mock-next";
        boolean throwError;
        ManagerIncidentActionCoordinator.Action requested;
        String oldStatus;
        boolean hasAssignee;
        int evaluations;
        @Override public ManagerIncidentActionCoordinator.Decision evaluate(
                ManagerIncidentActionCoordinator.Action action, String currentStatus,
                boolean assignedToPresent) {
            evaluations++;
            requested = action;
            oldStatus = currentStatus;
            hasAssignee = assignedToPresent;
            if (throwError) throw new IllegalStateException("policy unavailable");
            return allowed ? ManagerIncidentActionCoordinator.Decision.allow(target)
                    : ManagerIncidentActionCoordinator.Decision.reject();
        }
    }

    private static final class StubGateway implements ManagerIncidentActionCoordinator.CommitGateway {
        final List<ManagerIncidentActionCoordinator.Command> sent = new ArrayList<>();
        final List<ManagerIncidentActionCoordinator.CommitCallback> pending = new ArrayList<>();
        boolean throwError;
        @Override public void commit(ManagerIncidentActionCoordinator.Command command,
                                     ManagerIncidentActionCoordinator.CommitCallback callback) {
            if (throwError) throw new IllegalStateException("network unavailable");
            sent.add(command);
            pending.add(callback);
        }
    }

    private static final class Collector implements ManagerIncidentActionCoordinator.Listener {
        final List<ManagerIncidentActionCoordinator.Result> results = new ArrayList<>();
        @Override public void onResult(ManagerIncidentActionCoordinator.Result result) {
            results.add(result);
        }
        ManagerIncidentActionCoordinator.Outcome single() {
            assertEquals(1, results.size());
            return results.get(0).getOutcome();
        }
    }

    private static ManagerIncidentDetailDraft.Detail detail() {
        return ManagerIncidentDetailDraft.demoDetails().get(0);
    }

    @Test public void assignBuildsCommandUsingRulesButDoesNotCommitOptimistically() {
        StubRules rules = new StubRules();
        StubGateway gateway = new StubGateway();
        Collector c = new Collector();
        ManagerIncidentActionCoordinator coordinator = new ManagerIncidentActionCoordinator(rules,gateway);
        coordinator.submit(detail(),ManagerIncidentActionCoordinator.Action.ASSIGN," \u00a0 Chi An \u3000 ",c);
        assertTrue(c.results.isEmpty());
        assertEquals(1,gateway.sent.size());
        assertEquals("mgr-demo-001",gateway.sent.get(0).getIncidentId());
        assertEquals(detail().getStatusLabel(),gateway.sent.get(0).getExpectedStatus());
        assertEquals("mock-next",gateway.sent.get(0).getNextStatus());
        assertEquals("Chi An",gateway.sent.get(0).getAssignedTo());
        assertTrue(rules.hasAssignee);
        gateway.pending.get(0).onCommitted();
        assertEquals(ManagerIncidentActionCoordinator.Outcome.COMMITTED,c.single());
    }

    @Test public void startActionPassesThroughPolicyWithoutExtraHandler() {
        StubRules rules = new StubRules();
        StubGateway gw = new StubGateway();
        Collector c = new Collector();
        new ManagerIncidentActionCoordinator(rules,gw).submit(detail(),
                ManagerIncidentActionCoordinator.Action.START_WORK,null,c);
        assertEquals(ManagerIncidentActionCoordinator.Action.START_WORK,rules.requested);
        assertFalse(rules.hasAssignee);
        assertNull(gw.sent.get(0).getAssignedTo());
    }

    @Test public void completeActionPassesThroughPolicyWithoutHardcodedTarget() {
        StubRules rules = new StubRules(); rules.target = "external-target";
        StubGateway gw = new StubGateway();
        new ManagerIncidentActionCoordinator(rules,gw).submit(detail(),
                ManagerIncidentActionCoordinator.Action.MARK_COMPLETE,null,new Collector());
        assertEquals("external-target",gw.sent.get(0).getNextStatus());
    }

    @Test public void policyRejectionPreventsGateway() {
        StubRules rules = new StubRules(); rules.allowed = false;
        StubGateway gw = new StubGateway(); Collector c = new Collector();
        new ManagerIncidentActionCoordinator(rules,gw).submit(detail(),
                ManagerIncidentActionCoordinator.Action.MARK_COMPLETE,null,c);
        assertEquals(ManagerIncidentActionCoordinator.Outcome.POLICY_REJECTED,c.single());
        assertTrue(gw.sent.isEmpty());
    }

    @Test public void sameStatusOrBlankTargetCannotDispatch() {
        StubRules rules = new StubRules(); rules.target=detail().getStatusLabel();
        StubGateway gw = new StubGateway(); Collector a=new Collector();
        ManagerIncidentActionCoordinator coordinator = new ManagerIncidentActionCoordinator(rules,gw);
        coordinator.submit(detail(),ManagerIncidentActionCoordinator.Action.START_WORK,null,a);
        assertEquals(ManagerIncidentActionCoordinator.Outcome.POLICY_REJECTED,a.single());
        rules.target=" \u3000"; Collector b=new Collector();
        coordinator.submit(detail(),ManagerIncidentActionCoordinator.Action.START_WORK,null,b);
        assertEquals(ManagerIncidentActionCoordinator.Outcome.POLICY_REJECTED,b.single());
        assertTrue(gw.sent.isEmpty());
    }

    @Test public void blankAssignmentCannotDispatchOrEvaluatePolicy() {
        StubRules rules=new StubRules();StubGateway gw=new StubGateway();Collector c=new Collector();
        new ManagerIncidentActionCoordinator(rules,gw).submit(detail(),
                ManagerIncidentActionCoordinator.Action.ASSIGN," \u00a0 \u3000 ",c);
        assertEquals(ManagerIncidentActionCoordinator.Outcome.INVALID_INPUT,c.single());
        assertEquals(0,rules.evaluations);
        assertTrue(gw.sent.isEmpty());
    }

    @Test public void nullInputRejected() {
        StubRules rules=new StubRules();StubGateway gw=new StubGateway();Collector c=new Collector();
        new ManagerIncidentActionCoordinator(rules,gw).submit(null,
                ManagerIncidentActionCoordinator.Action.START_WORK,null,c);
        assertEquals(ManagerIncidentActionCoordinator.Outcome.INVALID_INPUT,c.single());
        Collector d=new Collector();
        new ManagerIncidentActionCoordinator(rules,gw).submit(detail(),null,null,d);
        assertEquals(ManagerIncidentActionCoordinator.Outcome.INVALID_INPUT,d.single());
        assertTrue(gw.sent.isEmpty());
    }

    @Test public void duplicatePendingIncidentIsRejectedUntilCallback() {
        StubRules rules=new StubRules();StubGateway gw=new StubGateway();
        ManagerIncidentActionCoordinator co=new ManagerIncidentActionCoordinator(rules,gw);
        Collector a=new Collector(),b=new Collector(),c=new Collector();
        co.submit(detail(),ManagerIncidentActionCoordinator.Action.START_WORK,null,a);
        co.submit(detail(),ManagerIncidentActionCoordinator.Action.MARK_COMPLETE,null,b);
        assertEquals(ManagerIncidentActionCoordinator.Outcome.ALREADY_PENDING,b.single());
        assertEquals(1,gw.sent.size());
        gw.pending.get(0).onCommitted();
        assertEquals(ManagerIncidentActionCoordinator.Outcome.COMMITTED,a.single());
        co.submit(detail(),ManagerIncidentActionCoordinator.Action.START_WORK,null,c);
        assertEquals(2,gw.sent.size());
        assertTrue(c.results.isEmpty());
    }

    @Test public void failedWriteDoesNotReportSuccessAndCanRetry() {
        StubGateway gw=new StubGateway();
        ManagerIncidentActionCoordinator co=new ManagerIncidentActionCoordinator(new StubRules(),gw);
        Collector a=new Collector(),b=new Collector();
        co.submit(detail(),ManagerIncidentActionCoordinator.Action.START_WORK,null,a);
        gw.pending.get(0).onFailed("stale-status");
        assertEquals(ManagerIncidentActionCoordinator.Outcome.FAILED,a.single());
        assertEquals("stale-status",a.results.get(0).getMessage());
        co.submit(detail(),ManagerIncidentActionCoordinator.Action.START_WORK,null,b);
        assertEquals(2,gw.sent.size());
    }

    @Test public void duplicateGatewayCallbacksDeliveredOnlyOnce() {
        StubGateway gw=new StubGateway(); Collector c=new Collector();
        new ManagerIncidentActionCoordinator(new StubRules(),gw).submit(detail(),
                ManagerIncidentActionCoordinator.Action.START_WORK,null,c);
        gw.pending.get(0).onCommitted();
        gw.pending.get(0).onFailed("late");
        assertEquals(ManagerIncidentActionCoordinator.Outcome.COMMITTED,c.single());
    }

    @Test public void synchronousGatewayFailureReleasesPending() {
        StubGateway gw=new StubGateway(); gw.throwError=true;
        ManagerIncidentActionCoordinator co=new ManagerIncidentActionCoordinator(new StubRules(),gw);
        Collector first=new Collector(),second=new Collector();
        co.submit(detail(),ManagerIncidentActionCoordinator.Action.START_WORK,null,first);
        assertEquals(ManagerIncidentActionCoordinator.Outcome.FAILED,first.single());
        co.submit(detail(),ManagerIncidentActionCoordinator.Action.START_WORK,null,second);
        assertEquals(ManagerIncidentActionCoordinator.Outcome.FAILED,second.single());
    }

    @Test public void rulesFailureNeverCallsGateway() {
        StubRules rules=new StubRules();rules.throwError=true;StubGateway gw=new StubGateway();Collector c=new Collector();
        new ManagerIncidentActionCoordinator(rules,gw).submit(detail(),
                ManagerIncidentActionCoordinator.Action.START_WORK,null,c);
        assertEquals(ManagerIncidentActionCoordinator.Outcome.FAILED,c.single());
        assertTrue(gw.sent.isEmpty());
    }

    @Test public void startAndCompleteDoNotChangeOriginalDetail() {
        ManagerIncidentDetailDraft.Detail original=detail();
        StubGateway gw=new StubGateway();
        new ManagerIncidentActionCoordinator(new StubRules(),gw).submit(original,
                ManagerIncidentActionCoordinator.Action.MARK_COMPLETE,null,new Collector());
        assertEquals(ManagerIncidentDetailDraft.demoDetails().get(0).getStatusLabel(),original.getStatusLabel());
        assertNull(original.getAssignedHandler());
    }

    @Test(expected=IllegalArgumentException.class)
    public void missingListenerIsProgrammingError() {
        new ManagerIncidentActionCoordinator(new StubRules(),new StubGateway())
                .submit(detail(),ManagerIncidentActionCoordinator.Action.START_WORK,null,null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void nullDependenciesRejected() {new ManagerIncidentActionCoordinator(null,new StubGateway());}
}

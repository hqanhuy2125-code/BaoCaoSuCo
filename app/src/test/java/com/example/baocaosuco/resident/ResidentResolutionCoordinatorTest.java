package com.example.baocaosuco.resident;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

public class ResidentResolutionCoordinatorTest {
    static final class FakeRules implements ResidentResolutionCoordinator.RuleAdapter {
        boolean allow = true; String target = "mock-result"; boolean throwsException;
        ResidentResolutionCoordinator.Feedback seenFeedback; String seenStatus;
        int calls;
        @Override public ResidentResolutionCoordinator.Decision evaluate(
                ResidentResolutionCoordinator.Feedback feedback, String currentStatus) {
            calls++; seenFeedback=feedback; seenStatus=currentStatus;
            if (throwsException) throw new IllegalStateException("logic unavailable");
            return allow ? ResidentResolutionCoordinator.Decision.allow(target) :
                    ResidentResolutionCoordinator.Decision.reject();
        }
    }
    static final class FakeGateway implements ResidentResolutionCoordinator.CommitGateway {
        final List<ResidentResolutionCoordinator.Command> commands=new ArrayList<>();
        final List<ResidentResolutionCoordinator.CommitCallback> callbacks=new ArrayList<>();
        boolean throwsException;
        @Override public void commit(ResidentResolutionCoordinator.Command c,
                                      ResidentResolutionCoordinator.CommitCallback cb) {
            if (throwsException) throw new IllegalStateException("network offline");
            commands.add(c); callbacks.add(cb);
        }
    }
    static final class Collector implements ResidentResolutionCoordinator.Listener {
        final List<ResidentResolutionCoordinator.Result> results=new ArrayList<>();
        @Override public void onResult(ResidentResolutionCoordinator.Result r){results.add(r);}
        ResidentResolutionCoordinator.Outcome one(){assertEquals(1,results.size());return results.get(0).getOutcome();}
    }
    private static ResidentIncidentDetailPreview.Detail demoDetail(){
        return ResidentIncidentDetailPreview.forDemoResident("demo-resident-a").getDetails().get(0);
    }
    @Test public void resolvedRequestsRuleAndWaitsForCommit() {
        FakeRules rule=new FakeRules();FakeGateway gateway=new FakeGateway();Collector ui=new Collector();
        new ResidentResolutionCoordinator(rule,gateway).submit(demoDetail(),
                ResidentResolutionCoordinator.Feedback.RESOLVED,ui);
        assertTrue(ui.results.isEmpty());
        assertEquals(ResidentResolutionCoordinator.Feedback.RESOLVED,rule.seenFeedback);
        assertEquals(demoDetail().getStatusLabel(),rule.seenStatus);
        assertEquals("demo-incident-01",gateway.commands.get(0).getIncidentId());
        assertEquals(rule.seenStatus,gateway.commands.get(0).getExpectedStatus());
        assertEquals("mock-result",gateway.commands.get(0).getNextStatus());
        assertEquals(ResidentResolutionCoordinator.Feedback.RESOLVED,gateway.commands.get(0).getFeedback());
        gateway.callbacks.get(0).onCommitted();
        assertEquals(ResidentResolutionCoordinator.Outcome.COMMITTED,ui.one());
    }
    @Test public void unresolvedFeedbackPassesToRuleWithoutInventingDestination() {
        FakeRules rules=new FakeRules();rules.target="external-reopened";
        FakeGateway gateway=new FakeGateway();
        new ResidentResolutionCoordinator(rules,gateway).submit(demoDetail(),
                ResidentResolutionCoordinator.Feedback.UNRESOLVED,new Collector());
        assertEquals(ResidentResolutionCoordinator.Feedback.UNRESOLVED,rules.seenFeedback);
        assertEquals("external-reopened",gateway.commands.get(0).getNextStatus());
    }
    @Test public void policyDeniedNeverWrites() {
        FakeRules rules=new FakeRules();rules.allow=false;FakeGateway gateway=new FakeGateway();Collector ui=new Collector();
        new ResidentResolutionCoordinator(rules,gateway).submit(demoDetail(),
                ResidentResolutionCoordinator.Feedback.RESOLVED,ui);
        assertEquals(ResidentResolutionCoordinator.Outcome.POLICY_REJECTED,ui.one());
        assertTrue(gateway.commands.isEmpty());
    }
    @Test public void unchangedOrEmptyStatusNeverWrites() {
        FakeRules rules=new FakeRules();FakeGateway gateway=new FakeGateway();
        ResidentResolutionCoordinator coordinator=new ResidentResolutionCoordinator(rules,gateway);
        rules.target=demoDetail().getStatusLabel();Collector a=new Collector();
        coordinator.submit(demoDetail(),ResidentResolutionCoordinator.Feedback.RESOLVED,a);
        assertEquals(ResidentResolutionCoordinator.Outcome.POLICY_REJECTED,a.one());
        rules.target=" \u3000 ";Collector b=new Collector();
        coordinator.submit(demoDetail(),ResidentResolutionCoordinator.Feedback.RESOLVED,b);
        assertEquals(ResidentResolutionCoordinator.Outcome.POLICY_REJECTED,b.one());
        assertTrue(gateway.commands.isEmpty());
    }
    @Test public void nullInputRejectedBeforeCallingAdapters() {
        FakeRules rules=new FakeRules();FakeGateway gateway=new FakeGateway();
        ResidentResolutionCoordinator coordinator=new ResidentResolutionCoordinator(rules,gateway);
        Collector a=new Collector(),b=new Collector();
        coordinator.submit(null,ResidentResolutionCoordinator.Feedback.RESOLVED,a);
        coordinator.submit(demoDetail(),null,b);
        assertEquals(ResidentResolutionCoordinator.Outcome.INVALID_INPUT,a.one());
        assertEquals(ResidentResolutionCoordinator.Outcome.INVALID_INPUT,b.one());
        assertEquals(0,rules.calls);assertTrue(gateway.commands.isEmpty());
    }
    @Test public void duplicatePendingRequestDeniedUntilCallback() {
        FakeGateway gateway=new FakeGateway();ResidentResolutionCoordinator coordinator=
                new ResidentResolutionCoordinator(new FakeRules(),gateway);
        Collector first=new Collector(),second=new Collector(),third=new Collector();
        coordinator.submit(demoDetail(),ResidentResolutionCoordinator.Feedback.RESOLVED,first);
        coordinator.submit(demoDetail(),ResidentResolutionCoordinator.Feedback.UNRESOLVED,second);
        assertEquals(ResidentResolutionCoordinator.Outcome.ALREADY_PENDING,second.one());
        assertEquals(1,gateway.commands.size());
        gateway.callbacks.get(0).onCommitted();
        assertEquals(ResidentResolutionCoordinator.Outcome.COMMITTED,first.one());
        coordinator.submit(demoDetail(),ResidentResolutionCoordinator.Feedback.UNRESOLVED,third);
        assertEquals(2,gateway.commands.size());
    }
    @Test public void gatewayFailureDoesNotProduceSuccessAndAllowsRetry() {
        FakeGateway gateway=new FakeGateway();ResidentResolutionCoordinator coordinator=
                new ResidentResolutionCoordinator(new FakeRules(),gateway);
        Collector a=new Collector(),b=new Collector();
        coordinator.submit(demoDetail(),ResidentResolutionCoordinator.Feedback.RESOLVED,a);
        gateway.callbacks.get(0).onFailed("conflict");
        assertEquals(ResidentResolutionCoordinator.Outcome.FAILED,a.one());
        assertEquals("conflict",a.results.get(0).getMessage());
        coordinator.submit(demoDetail(),ResidentResolutionCoordinator.Feedback.RESOLVED,b);
        assertEquals(2,gateway.commands.size());
    }
    @Test public void duplicateCallbackCannotDoubleNotify() {
        FakeGateway gateway=new FakeGateway();Collector ui=new Collector();
        new ResidentResolutionCoordinator(new FakeRules(),gateway).submit(demoDetail(),
                ResidentResolutionCoordinator.Feedback.RESOLVED,ui);
        gateway.callbacks.get(0).onCommitted();gateway.callbacks.get(0).onFailed("late");
        assertEquals(ResidentResolutionCoordinator.Outcome.COMMITTED,ui.one());
    }
    @Test public void synchronousGatewayExceptionYieldsFailureAndReleasesLock() {
        FakeGateway gateway=new FakeGateway();gateway.throwsException=true;
        ResidentResolutionCoordinator coordinator=new ResidentResolutionCoordinator(new FakeRules(),gateway);
        Collector a=new Collector(),b=new Collector();
        coordinator.submit(demoDetail(),ResidentResolutionCoordinator.Feedback.RESOLVED,a);
        assertEquals(ResidentResolutionCoordinator.Outcome.FAILED,a.one());
        coordinator.submit(demoDetail(),ResidentResolutionCoordinator.Feedback.RESOLVED,b);
        assertEquals(ResidentResolutionCoordinator.Outcome.FAILED,b.one());
    }
    @Test public void policyExceptionYieldsFailureWithoutCommit() {
        FakeRules rules=new FakeRules();rules.throwsException=true;FakeGateway gateway=new FakeGateway();Collector ui=new Collector();
        new ResidentResolutionCoordinator(rules,gateway).submit(demoDetail(),
                ResidentResolutionCoordinator.Feedback.RESOLVED,ui);
        assertEquals(ResidentResolutionCoordinator.Outcome.FAILED,ui.one());
        assertTrue(gateway.commands.isEmpty());
    }
    @Test public void completionDoesNotMutateMockDetailOrTimeline() {
        ResidentIncidentDetailPreview.Detail original=demoDetail();
        String oldStatus=original.getStatusLabel();int oldHistory=original.getTimeline().size();
        FakeGateway gateway=new FakeGateway();
        new ResidentResolutionCoordinator(new FakeRules(),gateway).submit(original,
                ResidentResolutionCoordinator.Feedback.RESOLVED,new Collector());
        gateway.callbacks.get(0).onCommitted();
        assertEquals(oldStatus,original.getStatusLabel());
        assertEquals(oldHistory,original.getTimeline().size());
    }
    @Test(expected=IllegalArgumentException.class)
    public void constructorRejectsMissingRules() {new ResidentResolutionCoordinator(null,new FakeGateway());}
    @Test(expected=IllegalArgumentException.class)
    public void constructorRejectsMissingGateway() {new ResidentResolutionCoordinator(new FakeRules(),null);}
    @Test(expected=IllegalArgumentException.class)
    public void listenerRequired() {new ResidentResolutionCoordinator(new FakeRules(),new FakeGateway())
            .submit(demoDetail(),ResidentResolutionCoordinator.Feedback.RESOLVED,null);}
}

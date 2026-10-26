package com.example.baocaosuco.resident;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dieu phoi phan hoi Da xong/Chua xong cua Cu dan (RES-05A).
 * Khong tu quyet dinh quy tac trang thai, quyen owner, hay ghi Firestore.
 */
public final class ResidentResolutionCoordinator {
    public enum Feedback { RESOLVED, UNRESOLVED }
    public enum Outcome { INVALID_INPUT, POLICY_REJECTED, ALREADY_PENDING, COMMITTED, FAILED }

    /** Hưng/CON-02 quyet dinh chuyen trang thai nao hop le. */
    public interface RuleAdapter {
        Decision evaluate(Feedback feedback, String currentStatus);
    }
    /** Repository/Rules xac minh auth.uid chu bao, expectedStatus va atomic history write. */
    public interface CommitGateway {
        void commit(Command command, CommitCallback callback);
    }
    public interface CommitCallback {
        void onCommitted();
        void onFailed(String message);
    }
    public interface Listener {
        void onResult(Result result);
    }

    public static final class Decision {
        private final boolean allowed;
        private final String nextStatus;
        private Decision(boolean allowed, String nextStatus) {
            this.allowed = allowed;
            this.nextStatus = nextStatus;
        }
        public static Decision allow(String nextStatus) { return new Decision(true, nextStatus); }
        public static Decision reject() { return new Decision(false, null); }
        public boolean isAllowed() { return allowed; }
        public String getNextStatus() { return nextStatus; }
    }

    public static final class Command {
        private final String incidentId;
        private final String expectedStatus;
        private final String nextStatus;
        private final Feedback feedback;
        private Command(String incidentId, String expectedStatus, String nextStatus,
                        Feedback feedback) {
            this.incidentId = incidentId;
            this.expectedStatus = expectedStatus;
            this.nextStatus = nextStatus;
            this.feedback = feedback;
        }
        public String getIncidentId() { return incidentId; }
        public String getExpectedStatus() { return expectedStatus; }
        public String getNextStatus() { return nextStatus; }
        public Feedback getFeedback() { return feedback; }
    }

    public static final class Result {
        private final Outcome outcome;
        private final String message;
        private Result(Outcome outcome, String message) {
            this.outcome = outcome;
            this.message = message;
        }
        public Outcome getOutcome() { return outcome; }
        public String getMessage() { return message; }
    }

    private final RuleAdapter rules;
    private final CommitGateway gateway;
    private final Set<String> pendingIds = new HashSet<>();

    public ResidentResolutionCoordinator(RuleAdapter rules, CommitGateway gateway) {
        if (rules == null || gateway == null) {
            throw new IllegalArgumentException("RuleAdapter and CommitGateway are required.");
        }
        this.rules = rules;
        this.gateway = gateway;
    }

    public void submit(ResidentIncidentDetailPreview.Detail detail, Feedback feedback,
                       Listener listener) {
        if (listener == null) throw new IllegalArgumentException("Listener is required.");
        if (detail == null || feedback == null || blank(detail.getId()) ||
                blank(detail.getStatusLabel())) {
            emitResult(listener, Outcome.INVALID_INPUT, "Missing incident or feedback.");
            return;
        }
        Decision decision;
        try {
            decision = rules.evaluate(feedback, detail.getStatusLabel());
        } catch (RuntimeException e) {
            emitResult(listener, Outcome.FAILED, "State policy unavailable.");
            return;
        }
        if (decision == null || !decision.isAllowed() || blank(decision.getNextStatus()) ||
                detail.getStatusLabel().equals(decision.getNextStatus())) {
            emitResult(listener, Outcome.POLICY_REJECTED, "Feedback rejected by state policy.");
            return;
        }

        final String id = detail.getId();
        final boolean busy;
        synchronized (pendingIds) {
            busy = !pendingIds.add(id);
        }
        if (busy) {
            emitResult(listener, Outcome.ALREADY_PENDING, "Feedback already pending.");
            return;
        }

        final AtomicBoolean finished = new AtomicBoolean(false);
        final CommitCallback callback = new CommitCallback() {
            @Override public void onCommitted() { complete(Outcome.COMMITTED, null); }
            @Override public void onFailed(String message) { complete(Outcome.FAILED, message); }
            private void complete(Outcome outcome, String message) {
                if (!finished.compareAndSet(false, true)) return;
                synchronized (pendingIds) { pendingIds.remove(id); }
                emitResult(listener, outcome, message);
            }
        };
        Command command = new Command(id, detail.getStatusLabel(),
                decision.getNextStatus(), feedback);
        try {
            gateway.commit(command, callback);
        } catch (RuntimeException e) {
            callback.onFailed("Repository gateway unavailable.");
        }
    }

    private static void emitResult(Listener listener, Outcome outcome, String message) {
        listener.onResult(new Result(outcome, message));
    }
    private static boolean blank(String text) {
        if (text == null || text.isEmpty()) return true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) return false;
        }
        return true;
    }
}

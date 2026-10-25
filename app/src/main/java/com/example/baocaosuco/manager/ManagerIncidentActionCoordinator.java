package com.example.baocaosuco.manager;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dieu phoi thao tac cua Quan ly; khong dinh nghia State Machine hay ghi Firestore.
 * LOG cung cap quy tac, repository tin cay xu ly role, version va history.
 */
public final class ManagerIncidentActionCoordinator {

    public enum Action { ASSIGN, START_WORK, MARK_COMPLETE }
    public enum Outcome { INVALID_INPUT, POLICY_REJECTED, ALREADY_PENDING, COMMITTED, FAILED }

    /** Adapter cho logic trang thai cua Hưng sau khi CON-02 duoc duyet. */
    public interface RuleAdapter {
        Decision evaluate(Action action, String currentStatus, boolean assignedToPresent);
    }

    /** Adapter repository sau khi CON-03 va Security Rules duoc duyet. */
    public interface CommitGateway {
        void commit(Command command, CommitCallback callback);
    }

    public interface CommitCallback {
        void onCommitted();
        void onFailed(String reason);
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

    /** Command mang expectedStatus de gateway kiem tra tranh ghi de trang thai cu. */
    public static final class Command {
        private final String incidentId;
        private final String expectedStatus;
        private final String nextStatus;
        private final Action action;
        private final String assignedTo;

        private Command(String incidentId, String expectedStatus, String nextStatus,
                        Action action, String assignedTo) {
            this.incidentId = incidentId;
            this.expectedStatus = expectedStatus;
            this.nextStatus = nextStatus;
            this.action = action;
            this.assignedTo = assignedTo;
        }

        public String getIncidentId() { return incidentId; }
        public String getExpectedStatus() { return expectedStatus; }
        public String getNextStatus() { return nextStatus; }
        public Action getAction() { return action; }
        public String getAssignedTo() { return assignedTo; }
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
    private final Set<String> pendingIncidentIds = new HashSet<>();

    public ManagerIncidentActionCoordinator(RuleAdapter rules, CommitGateway gateway) {
        if (rules == null || gateway == null) {
            throw new IllegalArgumentException("Rules and gateway are required.");
        }
        this.rules = rules;
        this.gateway = gateway;
    }

    /**
     * Chi xu ly thao tac; khong cho UI tu chon status dich.
     * Chi bao COMMITTED khi gateway tra loi onCommitted, khong optimistic update.
     * Backend phai xac thuc Manager, kiem tra expectedStatus va ghi history atomic.
     */
    public void submit(ManagerIncidentDetailDraft.Detail detail, Action action,
                       String assignmentName, Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener is required.");
        }
        if (detail == null || action == null || blank(detail.getId())
                || blank(detail.getStatusLabel())) {
            emit(listener, Outcome.INVALID_INPUT, "Thieu su co, trang thai hoac thao tac.");
            return;
        }

        String assignee = detail.getAssignedHandler();
        if (action == Action.ASSIGN) {
            ManagerIncidentDetailDraft.AssignmentDraft draft =
                    ManagerIncidentDetailDraft.prepareAssignment(detail, assignmentName);
            if (draft.getState() != ManagerIncidentDetailDraft.AssignmentState.READY) {
                emit(listener, Outcome.INVALID_INPUT, "Can co ten nguoi xu ly.");
                return;
            }
            assignee = draft.getHandlerName();
        }

        Decision decision;
        try {
            decision = rules.evaluate(action, detail.getStatusLabel(), !blank(assignee));
        } catch (RuntimeException ex) {
            emit(listener, Outcome.FAILED, "Khong the kiem tra quy tac trang thai.");
            return;
        }
        if (decision == null || !decision.isAllowed() || blank(decision.getNextStatus())
                || detail.getStatusLabel().equals(decision.getNextStatus())) {
            emit(listener, Outcome.POLICY_REJECTED, "Thao tac khong duoc bo quy tac chap nhan.");
            return;
        }

        final String incidentId = detail.getId();
        final boolean wasPending;
        synchronized (pendingIncidentIds) {
            wasPending = !pendingIncidentIds.add(incidentId);
        }
        if (wasPending) {
            emit(listener, Outcome.ALREADY_PENDING, "Su co dang duoc xu ly.");
            return;
        }

        final AtomicBoolean completed = new AtomicBoolean(false);
        final Command command = new Command(incidentId, detail.getStatusLabel(),
                decision.getNextStatus(), action, assignee);
        final CommitCallback callback = new CommitCallback() {
            @Override
            public void onCommitted() {
                finish(Outcome.COMMITTED, null);
            }

            @Override
            public void onFailed(String reason) {
                finish(Outcome.FAILED, reason);
            }

            private void finish(Outcome outcome, String reason) {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                synchronized (pendingIncidentIds) {
                    pendingIncidentIds.remove(incidentId);
                }
                emit(listener, outcome, reason);
            }
        };
        try {
            gateway.commit(command, callback);
        } catch (RuntimeException exception) {
            callback.onFailed("Khong the gui thao tac.");
        }
    }

    private static void emit(Listener listener, Outcome outcome, String message) {
        listener.onResult(new Result(outcome, message));
    }

    private static boolean blank(String value) {
        if (value == null || value.isEmpty()) return true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) return false;
        }
        return true;
    }
}

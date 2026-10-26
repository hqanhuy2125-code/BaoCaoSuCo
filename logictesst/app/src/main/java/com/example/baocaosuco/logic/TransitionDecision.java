package com.example.baocaosuco.logic;
public final class TransitionDecision {
    private final boolean allowed;
    private final IncidentStatus nextStatus;
    private final String reason;
    private TransitionDecision(boolean allowed, IncidentStatus nextStatus, String reason) {
        this.allowed = allowed; this.nextStatus = nextStatus; this.reason = reason;
    }
    public static TransitionDecision allow(IncidentStatus nextStatus) {
        if (nextStatus == null) throw new IllegalArgumentException("nextStatus required");
        return new TransitionDecision(true, nextStatus, "OK");
    }
    public static TransitionDecision reject(String reason) {
        return new TransitionDecision(false, null, reason);
    }
    public boolean isAllowed() { return allowed; }
    public IncidentStatus getNextStatus() { return nextStatus; }
    public String getReason() { return reason; }
}

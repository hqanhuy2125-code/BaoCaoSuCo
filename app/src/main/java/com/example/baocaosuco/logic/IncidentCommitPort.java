package com.example.baocaosuco.logic;
/** Contract only. Trusted backend must enforce ownership, role and idempotent atomic write. */
public interface IncidentCommitPort {
    interface Callback { void onCommitted(); void onRejected(String reason); }
    void commit(IncidentTransitionPlan plan, Callback callback);
}

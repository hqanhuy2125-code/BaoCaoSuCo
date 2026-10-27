package com.example.baocaosuco.data.incident;

import com.example.baocaosuco.logic.IncidentAction;

/** History-only CREATE event is NOT a sixth IncidentAction transition. */
public enum HistoryAction {
    CREATE, ASSIGN, START_WORK, MARK_COMPLETE, CONFIRM_RESOLVED, REPORT_UNRESOLVED;

    public static HistoryAction fromTransition(IncidentAction action) {
        if (action == null) throw new IllegalArgumentException("Missing LOG action");
        return HistoryAction.valueOf(action.name());
    }
}

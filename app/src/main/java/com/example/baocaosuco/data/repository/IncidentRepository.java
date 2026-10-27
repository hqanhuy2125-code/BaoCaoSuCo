package com.example.baocaosuco.data.repository;

import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.incident.NewIncidentInput;

import java.util.List;

/**
 * CON-03 technical v1 API: shared Resident/Manager capability interfaces.
 * Implementation MUST verify Auth UID/protected role and Firestore Rules.
 * No Firebase implementation or security permission is granted by this API.
 */
public interface IncidentRepository {
    enum Error {
        AUTH_REQUIRED, PERMISSION_DENIED, VALIDATION, NOT_FOUND,
        CONFLICT, NETWORK, SECURITY_NOT_READY, UNKNOWN
    }
    interface Callback<T> {
        void onSuccess(T data);
        void onError(Error error);
    }
    interface Subscription {
        /** Release listeners; must be idempotent in implementations. */
        void cancel();
    }
    interface Resident {
        void create(NewIncidentInput input, Callback<IncidentSnapshot> callback);
        void listMine(Callback<List<IncidentSnapshot>> callback);
        void getMine(String incidentId, Callback<IncidentSnapshot> callback);
        void historyMine(String incidentId, Callback<List<IncidentHistorySnapshot>> callback);
        void confirm(String incidentId, CommandPrecondition precondition,
                     boolean resolved, Callback<IncidentSnapshot> callback);
    }
    interface Manager {
        void listAll(Callback<List<IncidentSnapshot>> callback);
        void assign(String incidentId, CommandPrecondition precondition, String handler,
                    Callback<IncidentSnapshot> callback);
        void start(String incidentId, CommandPrecondition precondition,
                   Callback<IncidentSnapshot> callback);
        void complete(String incidentId, CommandPrecondition precondition,
                      Callback<IncidentSnapshot> callback);
    }
}

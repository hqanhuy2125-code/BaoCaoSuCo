package com.example.baocaosuco.data.repository;

import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.incident.NewIncidentInput;

import java.util.List;

/**
 * CON-03 candidate API only. Real implementations MUST obtain FirebaseAuth UID,
 * protected Manager role, trusted timestamps and current incident state from
 * authoritative sources and enforce Firestore Rules + atomic history security.
 *
 * Separate capability interfaces avoid surfacing Manager write commands to
 * Resident UI. A client interface is not an authorization boundary.
 * All callbacks are asynchronous-facing; real Android adapter must dispatch
 * on main thread and report success only after a durable verified commit.
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
        /** Idempotent: release listeners when Android UI stops. */
        void cancel();
    }
    interface Resident {
        void create(NewIncidentInput input, Callback<IncidentSnapshot> callback);
        void listMine(Callback<List<IncidentSnapshot>> callback);
        void getMine(String incidentId, Callback<IncidentSnapshot> callback);
        void historyMine(String incidentId, Callback<List<IncidentHistorySnapshot>> callback);
        void confirm(String incidentId, String expectedCanonicalStatus,
                     boolean resolved, Callback<IncidentSnapshot> callback);
    }
    interface Manager {
        void listAll(Callback<List<IncidentSnapshot>> callback);
        void assign(String incidentId, String expectedCanonicalStatus, String handler,
                    Callback<IncidentSnapshot> callback);
        void start(String incidentId, String expectedCanonicalStatus,
                   Callback<IncidentSnapshot> callback);
        void complete(String incidentId, String expectedCanonicalStatus,
                      Callback<IncidentSnapshot> callback);
    }
}

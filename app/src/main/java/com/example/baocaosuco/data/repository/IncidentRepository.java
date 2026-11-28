package com.example.baocaosuco.data.repository;

import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.incident.NewIncidentInput;

import java.util.ArrayList;
import java.util.Collections;
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
    interface PageCursor {}

    interface PageSource {
        void load(PageCursor after, Callback<Page> callback);
    }

    final class Page {
        public final List<IncidentSnapshot> rows;
        public final PageCursor next;

        public Page(List<IncidentSnapshot> rows, PageCursor next) {
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            this.next = next;
        }
    }

    interface Resident {
        void create(NewIncidentInput input, Callback<IncidentSnapshot> callback);
        default void create(NewIncidentInput input, String attemptId,
                            Callback<IncidentSnapshot> callback) {
            callback.onError(Error.SECURITY_NOT_READY);
        }
        default void reconcileCreate(String attemptId, Callback<IncidentSnapshot> callback) {
            callback.onError(Error.SECURITY_NOT_READY);
        }
        void listMine(Callback<List<IncidentSnapshot>> callback);
        void getMine(String incidentId, Callback<IncidentSnapshot> callback);
        void historyMine(String incidentId, Callback<List<IncidentHistorySnapshot>> callback);
        void confirm(String incidentId, CommandPrecondition precondition,
                     boolean resolved, Callback<IncidentSnapshot> callback);
        default void confirm(String incidentId, CommandPrecondition precondition, boolean resolved,
                             String note, Callback<IncidentSnapshot> callback) {
            if (note == null) confirm(incidentId, precondition, resolved, callback);
            else callback.onError(Error.SECURITY_NOT_READY);
        }
    }
    interface Manager {
        void listAll(Callback<List<IncidentSnapshot>> callback);
        default void get(String incidentId, Callback<IncidentSnapshot> callback) {
            callback.onError(Error.SECURITY_NOT_READY);
        }
        void assign(String incidentId, CommandPrecondition precondition, String handler,
                    Callback<IncidentSnapshot> callback);
        void start(String incidentId, CommandPrecondition precondition,
                   Callback<IncidentSnapshot> callback);
        void complete(String incidentId, CommandPrecondition precondition,
                      Callback<IncidentSnapshot> callback);
    }
}

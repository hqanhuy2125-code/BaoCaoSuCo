package com.example.baocaosuco.data.repository;

import android.os.Handler;
import android.os.Looper;

import com.example.baocaosuco.data.incident.HistoryAction;
import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.incident.NewIncidentInput;
import com.example.baocaosuco.logic.ActorRole;
import com.example.baocaosuco.logic.IncidentAction;
import com.example.baocaosuco.logic.IncidentStatus;
import com.example.baocaosuco.logic.IncidentTransitionPolicy;
import com.example.baocaosuco.logic.TransitionDecision;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REPO-02: Firebase implementation of the existing CON-03 v1 incident API.
 *
 * No UI, XML, role provisioning, or Firestore Rules deployment is performed here.
 * Every command obtains Auth UID and a fresh protected users/{uid} profile; Firestore
 * Security Rules are the final authorization boundary, not this Java class.
 * Requires the reviewed incident+history Rules before real cloud writes can succeed.
 */
public final class FirebaseIncidentRepository {
    private static final String INCIDENTS = "incidents";
    private static final String USERS = "users";
    private static final String HISTORY = "history";

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final Handler mainHandler;

    private final IncidentRepository.Resident resident = new ResidentApi();
    private final IncidentRepository.Manager manager = new ManagerApi();

    public FirebaseIncidentRepository() {
        this(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance());
    }

    public FirebaseIncidentRepository(FirebaseAuth auth, FirebaseFirestore firestore) {
        if (auth == null || firestore == null) throw new IllegalArgumentException("Firebase required");
        this.auth = auth;
        this.firestore = firestore;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public IncidentRepository.Resident resident() { return resident; }
    public IncidentRepository.Manager manager() { return manager; }

    private static final class Actor {
        final String uid;
        final ActorRole role;
        Actor(String uid, ActorRole role) { this.uid = uid; this.role = role; }
    }

    private interface ActorReady {
        void run(Actor actor);
    }

    private <T> void withActor(ActorRole required, IncidentRepository.Callback<T> callback,
                               ActorReady ready) {
        FirebaseUser current = auth.getCurrentUser();
        if (current == null) {
            error(callback, IncidentRepository.Error.AUTH_REQUIRED);
            return;
        }
        String uid = current.getUid();
        firestore.collection(USERS).document(uid).get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || !uid.equals(doc.getString("uid"))
                            || !required.name().equals(doc.getString("role"))) {
                        error(callback, IncidentRepository.Error.PERMISSION_DENIED);
                        return;
                    }
                    if (!sameAuth(uid)) {
                        error(callback, IncidentRepository.Error.AUTH_REQUIRED);
                        return;
                    }
                    ready.run(new Actor(uid, required));
                })
                .addOnFailureListener(e -> error(callback, firebaseError(e)));
    }

    public IncidentRepository.PageSource residentPages() {
        return (after, callback) -> withActor(ActorRole.RESIDENT, callback,
                actor -> loadPage(actor, true, after, callback));
    }

    public IncidentRepository.PageSource managerPages() {
        return (after, callback) -> withActor(ActorRole.MANAGER, callback,
                actor -> loadPage(actor, false, after, callback));
    }

    private final class IncidentCursor implements IncidentRepository.PageCursor {
        final FirebaseIncidentRepository repository = FirebaseIncidentRepository.this;
        final String uid;
        final boolean mine;
        final DocumentSnapshot last;

        IncidentCursor(Actor actor, boolean mine, DocumentSnapshot last) {
            this.uid = actor.uid;
            this.mine = mine;
            this.last = last;
        }
    }

    private void loadPage(Actor actor, boolean mine, IncidentRepository.PageCursor after,
                          IncidentRepository.Callback<IncidentRepository.Page> callback) {
        int limit = mine ? 20 : 50;
        Query query = incidents().orderBy("createdAt", Query.Direction.DESCENDING).limit(limit);
        if (mine) query = query.whereEqualTo("residentId", actor.uid);
        if (after != null) {
            if (!(after instanceof IncidentCursor)) {
                error(callback, IncidentRepository.Error.VALIDATION);
                return;
            }
            IncidentCursor cursor = (IncidentCursor) after;
            if (cursor.repository != this || cursor.mine != mine || !actor.uid.equals(cursor.uid)) {
                error(callback, IncidentRepository.Error.PERMISSION_DENIED);
                return;
            }
            // A snapshot cursor includes Firestore's document ID tie-breaker at equal createdAt.
            query = query.startAfter(cursor.last);
        }
        query.get(Source.SERVER).addOnSuccessListener(snapshot -> {
            try {
                List<IncidentSnapshot> rows = new ArrayList<>();
                List<DocumentSnapshot> documents = snapshot.getDocuments();
                for (DocumentSnapshot doc : documents) {
                    IncidentSnapshot row = decode(doc);
                    if (mine && !actor.uid.equals(row.getResidentId())) {
                        throw new Reject(IncidentRepository.Error.PERMISSION_DENIED);
                    }
                    rows.add(row);
                }
                IncidentRepository.PageCursor next = documents.size() < limit ? null
                        : new IncidentCursor(actor, mine, documents.get(documents.size() - 1));
                successForActor(actor, callback, new IncidentRepository.Page(rows, next));
            } catch (Reject invalid) {
                error(callback, invalid.error);
            }
        }).addOnFailureListener(e -> error(callback, firebaseError(e)));
    }

    private boolean sameAuth(String uid) {
        FirebaseUser current = auth.getCurrentUser();
        return current != null && uid.equals(current.getUid());
    }

    private CollectionReference incidents() { return firestore.collection(INCIDENTS); }

    private DocumentReference root(String id) {
        if (id == null || id.trim().isEmpty() || id.contains("/")) {
            throw new IllegalArgumentException("Invalid incident ID");
        }
        return incidents().document(id);
    }

    private static <T> void require(T value, String name) {
        if (value == null) throw new IllegalArgumentException("Missing " + name);
    }

    private <T> void success(IncidentRepository.Callback<T> callback, T value) {
        if (callback != null) mainHandler.post(() -> callback.onSuccess(value));
    }

    /** Never deliver a previous account's private snapshot after the active session switches. */
    private <T> void successForActor(Actor actor, IncidentRepository.Callback<T> callback, T value) {
        if (callback != null) mainHandler.post(() -> {
            if (sameAuth(actor.uid)) callback.onSuccess(value);
            else callback.onError(IncidentRepository.Error.AUTH_REQUIRED);
        });
    }

    private <T> void error(IncidentRepository.Callback<T> callback, IncidentRepository.Error kind) {
        if (callback != null) mainHandler.post(() -> callback.onError(kind));
    }

    private static IncidentRepository.Error firebaseError(Exception failure) {
        if (failure instanceof Reject) return ((Reject) failure).error;
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof Reject) return ((Reject) cause).error;
            if (cause instanceof FirebaseFirestoreException) {
                return IncidentFirestoreFailureMapper.classify(
                        ((FirebaseFirestoreException) cause).getCode().name());
            }
            cause = cause.getCause();
        }
        return IncidentRepository.Error.UNKNOWN;
    }

    private static final class Reject extends RuntimeException {
        final IncidentRepository.Error error;
        Reject(IncidentRepository.Error error) { super(error.name()); this.error = error; }
    }

    private IncidentSnapshot decode(DocumentSnapshot doc) {
        if (!doc.exists()) throw new Reject(IncidentRepository.Error.NOT_FOUND);
        try {
            return IncidentFirestoreMapper.incident(doc.getId(), doc.getData());
        } catch (IllegalArgumentException badDocument) {
            throw new Reject(IncidentRepository.Error.SECURITY_NOT_READY);
        }
    }

    private IncidentHistorySnapshot decodeHistory(String incidentId, DocumentSnapshot doc) {
        try {
            return IncidentFirestoreMapper.history(doc.getId(), incidentId, doc.getData());
        } catch (IllegalArgumentException badDocument) {
            throw new Reject(IncidentRepository.Error.SECURITY_NOT_READY);
        }
    }

    private void readAfterCommit(Actor actor, DocumentReference document,
                                 IncidentRepository.Callback<IncidentSnapshot> callback) {
        if (!sameAuth(actor.uid)) {
            error(callback, IncidentRepository.Error.AUTH_REQUIRED);
            return;
        }
        document.get(Source.SERVER).addOnSuccessListener(doc -> {
            try {
                successForActor(actor, callback, decode(doc));
            } catch (Reject invalid) {
                error(callback, invalid.error);
            }
        }).addOnFailureListener(e -> error(callback, firebaseError(e)));
    }

    private static Map<String, Object> event(String action, String fromStatus, String toStatus,
                                              String uid, int revision, String note) {
        Map<String, Object> values = new HashMap<>();
        values.put("action", action);
        values.put("fromStatus", fromStatus);
        values.put("toStatus", toStatus);
        values.put("actorId", uid);
        values.put("revision", revision);
        values.put("note", note);
        values.put("createdAt", FieldValue.serverTimestamp());
        return values;
    }

    private static void put(Map<String, Object> values, String name, Object data) {
        values.put(name, data);
    }

    private void createIncident(Actor actor, NewIncidentInput input, String attemptId,
                                IncidentRepository.Callback<IncidentSnapshot> callback) {
        if (!sameAuth(actor.uid)) { error(callback, IncidentRepository.Error.AUTH_REQUIRED); return; }
        DocumentReference incident = root(attemptId);
        String eventId = attemptId;
        Map<String, Object> data = new HashMap<>();
        put(data, "residentId", actor.uid);
        put(data, "title", input.getTitle());
        put(data, "description", input.getDescription());
        put(data, "location", input.getLocation());
        put(data, "priority", input.getPriority());
        put(data, "status", IncidentStatus.NEW.name());
        put(data, "assignedTo", null);
        put(data, "createdAt", FieldValue.serverTimestamp());
        put(data, "updatedAt", FieldValue.serverTimestamp());
        put(data, "revision", 0);
        put(data, "lastEventId", eventId);
        WriteBatch batch = firestore.batch();
        batch.set(incident, data);
        batch.set(incident.collection(HISTORY).document(eventId),
                event("CREATE", null, "NEW", actor.uid, 0, null));
        // Rules allow this pair only on creation. A replay uses the same two document IDs.
        batch.commit().addOnSuccessListener(ignored -> reconcileCreate(actor, attemptId, input,
                        IncidentRepository.Error.NETWORK, callback))
                .addOnFailureListener(e -> reconcileCreate(actor, attemptId, input, firebaseError(e), callback));
    }

    private void reconcileCreate(Actor actor, String attemptId, NewIncidentInput input,
                                 IncidentRepository.Error fallback,
                                 IncidentRepository.Callback<IncidentSnapshot> callback) {
        DocumentReference incident = root(attemptId);
        incident.get(Source.SERVER).addOnSuccessListener(document -> {
            final IncidentSnapshot saved;
            try {
                if (!document.exists()) {
                    error(callback, fallback);
                    return;
                }
                saved = decode(document);
                if (!saved.getResidentId().equals(actor.uid)) {
                    error(callback, IncidentRepository.Error.PERMISSION_DENIED);
                    return;
                }
                if (input != null && (!input.getTitle().equals(saved.getTitle())
                        || !input.getDescription().equals(saved.getDescription())
                        || !input.getLocation().equals(saved.getLocation())
                        || !input.getPriority().equals(saved.getPriority()))) {
                    error(callback, IncidentRepository.Error.CONFLICT);
                    return;
                }
            } catch (Reject rejected) {
                error(callback, rejected.error);
                return;
            }
            incident.collection(HISTORY).document(attemptId).get(Source.SERVER)
                    .addOnSuccessListener(documentEvent -> {
                        try {
                            if (!documentEvent.exists()) throw new Reject(IncidentRepository.Error.CONFLICT);
                            IncidentHistorySnapshot created = decodeHistory(attemptId, documentEvent);
                            if (created.getAction() != HistoryAction.CREATE || created.getRevision() != 0
                                    || !actor.uid.equals(created.getActorId())) {
                                throw new Reject(IncidentRepository.Error.CONFLICT);
                            }
                            successForActor(actor, callback, saved);
                        } catch (Reject rejected) {
                            error(callback, rejected.error);
                        }
                    })
                    .addOnFailureListener(e -> error(callback, firebaseError(e)));
        }).addOnFailureListener(e -> {
            IncidentRepository.Error readFailure = firebaseError(e);
            error(callback, readFailure == IncidentRepository.Error.PERMISSION_DENIED ? fallback : readFailure);
        });
    }

    private static boolean validAttemptId(String value) {
        if (value == null) return false;
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private void list(Actor actor, boolean mine,
                      IncidentRepository.Callback<List<IncidentSnapshot>> callback) {
        Query query = incidents().orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(mine ? 20 : 50);
        if (mine) query = query.whereEqualTo("residentId", actor.uid);
        query.get(Source.SERVER).addOnSuccessListener(snapshot -> {
            try {
                List<IncidentSnapshot> results = new ArrayList<>();
                for (DocumentSnapshot item : snapshot.getDocuments()) {
                    IncidentSnapshot mapped = decode(item);
                    if (mine && !actor.uid.equals(mapped.getResidentId())) {
                        throw new Reject(IncidentRepository.Error.PERMISSION_DENIED);
                    }
                    results.add(mapped);
                }
                successForActor(actor, callback, results);
            } catch (Reject invalid) {
                error(callback, invalid.error);
            }
        }).addOnFailureListener(e -> error(callback, firebaseError(e)));
    }

    private void getIncident(Actor actor, String incidentId,
                             IncidentRepository.Callback<IncidentSnapshot> callback) {
        final DocumentReference ref;
        try { ref = root(incidentId); }
        catch (IllegalArgumentException badId) { error(callback, IncidentRepository.Error.VALIDATION); return; }
        ref.get(Source.SERVER).addOnSuccessListener(doc -> {
            try {
                IncidentSnapshot mapped = decode(doc);
                if (actor.role == ActorRole.RESIDENT && !actor.uid.equals(mapped.getResidentId()))
                    throw new Reject(IncidentRepository.Error.PERMISSION_DENIED);
                successForActor(actor, callback, mapped);
            } catch (Reject invalid) { error(callback, invalid.error); }
        }).addOnFailureListener(e -> error(callback, firebaseError(e)));
    }

    private void historyMine(Actor actor, String incidentId,
                             IncidentRepository.Callback<List<IncidentHistorySnapshot>> callback) {
        final DocumentReference parent;
        try { parent = root(incidentId); }
        catch (IllegalArgumentException badId) { error(callback, IncidentRepository.Error.VALIDATION); return; }
        parent.get(Source.SERVER).addOnSuccessListener(doc -> {
            try {
                IncidentSnapshot mapped = decode(doc);
                if (!actor.uid.equals(mapped.getResidentId()))
                    throw new Reject(IncidentRepository.Error.PERMISSION_DENIED);
            } catch (Reject invalid) { error(callback, invalid.error); return; }
            parent.collection(HISTORY)
                    .orderBy("createdAt", Query.Direction.ASCENDING).limit(50)
                    .get(Source.SERVER).addOnSuccessListener(historyDocs -> {
                        try {
                            List<IncidentHistorySnapshot> result = new ArrayList<>();
                            for (DocumentSnapshot item : historyDocs.getDocuments())
                                result.add(decodeHistory(incidentId, item));
                            successForActor(actor, callback, result);
                        } catch (Reject invalid) { error(callback, invalid.error); }
                    }).addOnFailureListener(e -> error(callback, firebaseError(e)));
        }).addOnFailureListener(e -> error(callback, firebaseError(e)));
    }

    private void transition(Actor actor, String incidentId, CommandPrecondition precondition,
                            IncidentAction action, String handler, String note,
                            IncidentRepository.Callback<IncidentSnapshot> callback) {
        final DocumentReference parent;
        final DocumentReference eventRef;
        try {
            require(precondition, "precondition");
            String requestId = precondition.getRequestId();
            if (requestId.codePointCount(0, requestId.length()) > 128) {
                throw new IllegalArgumentException("Request ID exceeds CON-01 limit");
            }
            parent = root(incidentId);
            eventRef = parent.collection(HISTORY).document(requestId);
        } catch (IllegalArgumentException invalid) {
            error(callback, IncidentRepository.Error.VALIDATION);
            return;
        }
        if (!sameAuth(actor.uid)) { error(callback, IncidentRepository.Error.AUTH_REQUIRED); return; }
        final String cleanHandler;
        if (action == IncidentAction.ASSIGN) {
            cleanHandler = handler == null ? "" : handler.trim();
            if (cleanHandler.isEmpty() || cleanHandler.codePointCount(0, cleanHandler.length()) > 120) {
                error(callback, IncidentRepository.Error.VALIDATION);
                return;
            }
        } else {
            cleanHandler = null;
        }
        firestore.runTransaction(tx -> {
            // Every transaction read precedes the writes; Firestore retries transparently
            // on conflicting commits while the same requestId is kept stable.
            DocumentSnapshot previous = tx.get(parent);
            IncidentSnapshot current = decode(previous);
            DocumentSnapshot priorEvent = tx.get(eventRef);
            if (priorEvent.exists()) throw new Reject(IncidentRepository.Error.CONFLICT);
            if (!precondition.matchesObserved(current.getStatusCode(), current.getRevision()))
                throw new Reject(IncidentRepository.Error.CONFLICT);
            if (current.getRevision() == Integer.MAX_VALUE)
                throw new Reject(IncidentRepository.Error.CONFLICT);
            boolean owner = actor.uid.equals(current.getResidentId());
            TransitionDecision decision = IncidentTransitionPolicy.evaluate(current.getStatus(),
                    action, actor.role, owner,
                    action == IncidentAction.ASSIGN ? !cleanHandler.isEmpty()
                            : current.getAssignedTo() != null);
            if (!decision.isAllowed()) throw new Reject(IncidentRepository.Error.PERMISSION_DENIED);
            Map<String, Object> update = new HashMap<>();
            update.put("status", decision.getNextStatus().name());
            update.put("revision", current.getRevision() + 1);
            update.put("lastEventId", precondition.getRequestId());
            update.put("updatedAt", FieldValue.serverTimestamp());
            if (action == IncidentAction.ASSIGN) update.put("assignedTo", cleanHandler);
            tx.update(parent, update);
            tx.set(eventRef, event(action.name(), current.getStatusCode(),
                    decision.getNextStatus().name(), actor.uid, current.getRevision() + 1, note));
            return null;
        }).addOnSuccessListener(ignored -> readAfterCommit(actor, parent, callback))
                .addOnFailureListener(e -> error(callback, firebaseError(e)));
    }

    private final class ResidentApi implements IncidentRepository.Resident {
        @Override public void create(NewIncidentInput input,
                                     IncidentRepository.Callback<IncidentSnapshot> callback) {
            create(input, UUID.randomUUID().toString(), callback);
        }

        @Override public void create(NewIncidentInput input, String attemptId,
                                     IncidentRepository.Callback<IncidentSnapshot> callback) {
            if (input == null || !validAttemptId(attemptId)) {
                error(callback, IncidentRepository.Error.VALIDATION);
                return;
            }
            withActor(ActorRole.RESIDENT, callback, actor -> createIncident(actor, input, attemptId, callback));
        }

        @Override public void reconcileCreate(String attemptId,
                                              IncidentRepository.Callback<IncidentSnapshot> callback) {
            if (!validAttemptId(attemptId)) { error(callback, IncidentRepository.Error.VALIDATION); return; }
            withActor(ActorRole.RESIDENT, callback, actor -> FirebaseIncidentRepository.this.reconcileCreate(
                    actor, attemptId, null, IncidentRepository.Error.NOT_FOUND, callback));
        }

        @Override public void listMine(IncidentRepository.Callback<List<IncidentSnapshot>> callback) {
            withActor(ActorRole.RESIDENT, callback, actor -> list(actor, true, callback));
        }

        @Override public void getMine(String id, IncidentRepository.Callback<IncidentSnapshot> callback) {
            withActor(ActorRole.RESIDENT, callback, actor -> getIncident(actor, id, callback));
        }

        @Override public void historyMine(String id,
                            IncidentRepository.Callback<List<IncidentHistorySnapshot>> callback) {
            withActor(ActorRole.RESIDENT, callback, actor -> FirebaseIncidentRepository.this.historyMine(actor, id, callback));
        }

        @Override public void confirm(String id, CommandPrecondition expected, boolean resolved,
                                      IncidentRepository.Callback<IncidentSnapshot> callback) {
            confirm(id, expected, resolved, null, callback);
        }

        @Override public void confirm(String id, CommandPrecondition expected, boolean resolved, String note,
                                      IncidentRepository.Callback<IncidentSnapshot> callback) {
            final String trimmed = note == null ? "" : note.trim();
            if ((!trimmed.isEmpty() && resolved) || trimmed.codePointCount(0, trimmed.length()) > 500) {
                error(callback, IncidentRepository.Error.VALIDATION);
                return;
            }
            withActor(ActorRole.RESIDENT, callback, actor -> transition(actor, id, expected,
                    resolved ? IncidentAction.CONFIRM_RESOLVED : IncidentAction.REPORT_UNRESOLVED,
                    null, trimmed.isEmpty() ? null : trimmed, callback));
        }
    }

    private final class ManagerApi implements IncidentRepository.Manager {
        @Override public void get(String id, IncidentRepository.Callback<IncidentSnapshot> callback) {
            withActor(ActorRole.MANAGER, callback, actor -> getIncident(actor, id, callback));
        }

        @Override public void listAll(IncidentRepository.Callback<List<IncidentSnapshot>> callback) {
            withActor(ActorRole.MANAGER, callback, actor -> list(actor, false, callback));
        }

        @Override public void assign(String id, CommandPrecondition expected, String handler,
                                     IncidentRepository.Callback<IncidentSnapshot> callback) {
            withActor(ActorRole.MANAGER, callback,
                    actor -> transition(actor, id, expected, IncidentAction.ASSIGN, handler, null, callback));
        }

        @Override public void start(String id, CommandPrecondition expected,
                                    IncidentRepository.Callback<IncidentSnapshot> callback) {
            withActor(ActorRole.MANAGER, callback,
                    actor -> transition(actor, id, expected, IncidentAction.START_WORK, null, null, callback));
        }

        @Override public void complete(String id, CommandPrecondition expected,
                                       IncidentRepository.Callback<IncidentSnapshot> callback) {
            withActor(ActorRole.MANAGER, callback,
                    actor -> transition(actor, id, expected, IncidentAction.MARK_COMPLETE, null, null, callback));
        }
    }
}

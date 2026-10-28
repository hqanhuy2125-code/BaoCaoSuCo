package com.example.baocaosuco.data.repository;

import android.os.Handler;
import android.os.Looper;

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
                FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) cause).getCode();
                if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                    return IncidentRepository.Error.PERMISSION_DENIED;
                if (code == FirebaseFirestoreException.Code.UNAVAILABLE
                        || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
                    return IncidentRepository.Error.NETWORK;
                if (code == FirebaseFirestoreException.Code.ABORTED
                        || code == FirebaseFirestoreException.Code.ALREADY_EXISTS)
                    return IncidentRepository.Error.CONFLICT;
                if (code == FirebaseFirestoreException.Code.FAILED_PRECONDITION)
                    return IncidentRepository.Error.SECURITY_NOT_READY;
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
                                              String uid, int revision) {
        Map<String, Object> values = new HashMap<>();
        values.put("action", action);
        values.put("fromStatus", fromStatus);
        values.put("toStatus", toStatus);
        values.put("actorId", uid);
        values.put("revision", revision);
        values.put("note", null);
        values.put("createdAt", FieldValue.serverTimestamp());
        return values;
    }

    private static void put(Map<String, Object> values, String name, Object data) {
        values.put(name, data);
    }

    private void createIncident(Actor actor, NewIncidentInput input,
                                IncidentRepository.Callback<IncidentSnapshot> callback) {
        if (!sameAuth(actor.uid)) { error(callback, IncidentRepository.Error.AUTH_REQUIRED); return; }
        DocumentReference incident = incidents().document();
        String eventId = UUID.randomUUID().toString();
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
                event("CREATE", null, "NEW", actor.uid, 0));
        batch.commit().addOnSuccessListener(ignored -> readAfterCommit(actor, incident, callback))
                .addOnFailureListener(e -> error(callback, firebaseError(e)));
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

    private void getMine(Actor actor, String incidentId,
                         IncidentRepository.Callback<IncidentSnapshot> callback) {
        final DocumentReference ref;
        try { ref = root(incidentId); }
        catch (IllegalArgumentException badId) { error(callback, IncidentRepository.Error.VALIDATION); return; }
        ref.get(Source.SERVER).addOnSuccessListener(doc -> {
            try {
                IncidentSnapshot mapped = decode(doc);
                if (!actor.uid.equals(mapped.getResidentId()))
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
                            IncidentAction action, String handler,
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
                    decision.getNextStatus().name(), actor.uid, current.getRevision() + 1));
            return null;
        }).addOnSuccessListener(ignored -> readAfterCommit(actor, parent, callback))
                .addOnFailureListener(e -> error(callback, firebaseError(e)));
    }

    private final class ResidentApi implements IncidentRepository.Resident {
        @Override public void create(NewIncidentInput input,
                                     IncidentRepository.Callback<IncidentSnapshot> callback) {
            if (input == null) { error(callback, IncidentRepository.Error.VALIDATION); return; }
            withActor(ActorRole.RESIDENT, callback, actor -> createIncident(actor, input, callback));
        }

        @Override public void listMine(IncidentRepository.Callback<List<IncidentSnapshot>> callback) {
            withActor(ActorRole.RESIDENT, callback, actor -> list(actor, true, callback));
        }

        @Override public void getMine(String id, IncidentRepository.Callback<IncidentSnapshot> callback) {
            withActor(ActorRole.RESIDENT, callback, actor -> FirebaseIncidentRepository.this.getMine(actor, id, callback));
        }

        @Override public void historyMine(String id,
                            IncidentRepository.Callback<List<IncidentHistorySnapshot>> callback) {
            withActor(ActorRole.RESIDENT, callback, actor -> FirebaseIncidentRepository.this.historyMine(actor, id, callback));
        }

        @Override public void confirm(String id, CommandPrecondition expected, boolean resolved,
                                      IncidentRepository.Callback<IncidentSnapshot> callback) {
            withActor(ActorRole.RESIDENT, callback, actor -> transition(actor, id, expected,
                    resolved ? IncidentAction.CONFIRM_RESOLVED : IncidentAction.REPORT_UNRESOLVED,
                    null, callback));
        }
    }

    private final class ManagerApi implements IncidentRepository.Manager {
        @Override public void listAll(IncidentRepository.Callback<List<IncidentSnapshot>> callback) {
            withActor(ActorRole.MANAGER, callback, actor -> list(actor, false, callback));
        }

        @Override public void assign(String id, CommandPrecondition expected, String handler,
                                     IncidentRepository.Callback<IncidentSnapshot> callback) {
            withActor(ActorRole.MANAGER, callback,
                    actor -> transition(actor, id, expected, IncidentAction.ASSIGN, handler, callback));
        }

        @Override public void start(String id, CommandPrecondition expected,
                                    IncidentRepository.Callback<IncidentSnapshot> callback) {
            withActor(ActorRole.MANAGER, callback,
                    actor -> transition(actor, id, expected, IncidentAction.START_WORK, null, callback));
        }

        @Override public void complete(String id, CommandPrecondition expected,
                                       IncidentRepository.Callback<IncidentSnapshot> callback) {
            withActor(ActorRole.MANAGER, callback,
                    actor -> transition(actor, id, expected, IncidentAction.MARK_COMPLETE, null, callback));
        }
    }
}

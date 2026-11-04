# CON-03 — Shared IncidentRepository API v1 review (24/10/2026)

**Status: DRAFT_FOR_REVIEW / NOT FROZEN.** Source: `ARCHITECTURE.md` interface sketch, `DATA_CONTRACT.md`, `RESIDENT_MODULE_START.md`, and MVP scope. No Java implementation is added in this task.

## Ownership / dependency

- One shared interface under proposed `data/repository/**`; do **not** create separate incompatible repositories inside `resident/**` and `manager/**`.
- Shared author and Manager Java feature owner are **UNASSIGNED**. Trần Quốc Huy coordinates integration and resident client usage; review by affected authors required.
- Depends on CON-01 (models and queries), CON-02 (state commands) and SEC-01/SEC-02 (authorization).
- Firebase SDK calls should be encapsulated in a repository adapter. UI should not accept a caller-supplied arbitrary resident UID as authorization.

## Java signatures for review (illustrative, not compile-ready)

```java
interface ResultCallback<T> {
    void onSuccess(T result);
    void onError(RepositoryError error);
}
interface Subscription {
    void cancel(); // idempotent; release Firestore listener on UI stop
}
interface IncidentRepository {
    // Resident; implementation resolves authenticated UID securely
    void createIncident(NewIncidentInput input, ResultCallback<String> cb);
    void listMyIncidents(ResultCallback<List<Incident>> cb);
    void getMyIncident(String incidentId, ResultCallback<Incident> cb);
    Subscription observeMyIncidents(ResultCallback<List<Incident>> cb);
    Subscription observeMyIncident(String incidentId, ResultCallback<Incident> cb);
    Subscription observeMyHistory(String incidentId, ResultCallback<List<IncidentHistory>> cb);
    void confirmResolution(String incidentId, boolean resolved, ResultCallback<Void> cb);

    // Manager-only operations, protected by persistence authorization
    void listAllIncidents(ManagerIncidentQuery query, ResultCallback<List<Incident>> cb);
    void assignIncident(String incidentId, String assignedTo, ResultCallback<Void> cb);
    void startIncident(String incidentId, ResultCallback<Void> cb);
    void completeIncident(String incidentId, ResultCallback<Void> cb);
}
```

Final API may separate resident/manager interfaces backed by a single shared implementation. Decide this during review; no public signature is frozen above.

## Proposed DTO/data semantics

- `NewIncidentInput`: title, description, location, priority only; no residentId/status/createdAt/dueAt from UI.
- `Incident`: immutable read model conforming to CON-01; `IncidentHistory`: read model with actor/status/time/note.
- `ManagerIncidentQuery`: approved filter/sort/pagination fields, no arbitrary collection path.
- Error domain should distinguish `AUTH_REQUIRED`, `PERMISSION_DENIED`, `VALIDATION`, `NOT_FOUND`, `CONFLICT`, `NETWORK`, `UNKNOWN`. Names remain proposed.
- Callback should run on Android main thread for UI consumers, **if accepted**; mock and Firebase implementations must follow identical contract.
- Reads of another resident's incident must be rejected by Rules even if `incidentId` is guessed; manager operations must never use UI role flags as sole security.
- Listener lifecycle: call `cancel()` on stop/destroy, no leaked Activity reference. Decide initial emission, errors and offline/cache semantics.
- Mutating operations report success **only after committed persistence result**; duplicate buttons disabled until callback.
- Mock seed data must be demonstrably fake and separate from Firebase; mock PASS ≠ cloud integration PASS.

## Proposed implementation / verification split

1. CON-03 decision: sign off exact signatures, models, ownership, errors and threading.
2. CORE-01: compile shared models/interface, no Firebase writes.
3. REPO-01: mock adapter for RES/MGR UI, with tests.
4. SEC-02: reviewed/verified rules and state/history atomicity.
5. REPO-02: Firebase implementation after security gate, with cross-role regression.
6. RES-04/MGR-03: feature modules consume shared interface, no duplicated raw Firestore queries.

## Contract exit gates

- [ ] Java model types and nullability align with frozen CON-01
- [ ] Roles/status methods align with frozen CON-02
- [ ] Owner of shared source and reviewer(s) recorded
- [ ] Response/error semantics, pagination and listener lifecycle agreed
- [ ] Atomic state+history API implementation strategy approved
- [ ] Canonical `ARCHITECTURE.md` and `DECISION_LOG.md` updated through review
- [ ] Android compilation/unit tests and Rules tests scheduled/recorded by actual implementers

**Until these gates pass:** only design and mock planning are authorized; production Firestore operations remain blocked.

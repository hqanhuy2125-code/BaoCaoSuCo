# CON-01 / CON-03 — CORE-01 Java contract implementation candidate

**Status:** `CANDIDATE / NOT_FROZEN / NO_FIRESTORE_ADAPTER`
**Baseline:** `main@51f3b16f0008501bc53c3f649587de68879f8627`
**Related:** [CON-01 #10](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/10), [CON-03 #12](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/12), [CORE-01 SEC-02A #40](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/40).

## Facts from checked-in source
- `FirebaseAuthRepository` writes five fields to `users/{uid}`: `uid`, `displayName`, `email`, `role`, `createdAt`. Current canonical v0 schema omits `uid`; do not silently migrate or rewrite Auth.
- `firestore.rules` protects users and has catch-all deny for `incidents` and history. No incident Firestore service is implemented in main.
- Five status transitions and 4h/72h/168h SLA are selected as *domain-only* CON-02 MVP baseline. Role provision, trusted time and atomic history are independent security gates.

## Candidate API (not Firebase-backed)
- `data/incident/NewIncidentInput`: immutable; only title/description/location/canonical priority. No caller-supplied `uid`, `status`, `assignedTo`, `createdAt` or `dueAt`.
- `data/incident/IncidentSnapshot`: immutable read model using `IncidentStatus`, timestamps as absolute epoch milliseconds at Java boundary (Firestore adapter must verify/convert Timestamp).
- `data/incident/IncidentHistorySnapshot`: immutable history read model; event collection location **NOT DECIDED** here.
- `data/repository/IncidentRepository`: separate `Resident` and `Manager` capability interfaces; explicit `Error` domain and `Callback<T>`; `Subscription` interface reserved for controlled future listener lifecycle. Interfaces alone never convey permission.

## Known pending contract choices
1. **History path:** `incidents/{incidentId}/history/{eventId}` preferred versus `history/{eventId}`. No freeze here.
2. **Fields:** title/description/location maximum lengths (120/2000/160 remain proposed only), `imageUrl`, timestamp nullability and allowed writer.
3. **Trust:** MANAGER role must be granted through trusted provisioning, never self-assigned. Android UI cannot supply an arbitrary resident UID or claim `verifiedOwner`.
4. **Persistence:** incident status + append-only history in enforceable transaction; fresh stored `expectedStatus`, actor/time verification, duplicate-click/event replay protection.
5. **Lifecycle:** API candidate uses callbacks, no actual `observe...()` yet. Decide listener/query/index/dispatcher contract before real adapter.

## Safety and test posture
This PR **does not** change `firestore.rules`, `auth/**`, `manager/**`, `resident/**`, `logic/**`, any UI XML, Android Gradle, live collections or Firebase credentials. These DTOs are Java candidates, not accepted Firestore field serialization or production authorization. Tests/Gradle on their exact commit must be recorded before using them.

See independent SEC-02A Emulator regression against the **actual root `firestore.rules`**. Its incident/history checks should currently be **expected DENY**, not false claims of a completed report lifecycle.

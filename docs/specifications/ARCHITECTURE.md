# CON-03 — Android architecture and IncidentRepository API contract v1

**Status:** `TECHNICAL_MVP_V1_LOCKED` on 2026-10-27 for SEC-02B test design and CORE-02 adapter work. `FORMAL_GROUP_SIGNOFF_PENDING_GOV02`; `PRODUCTION_FIRESTORE_WRITES_BLOCKED`.

**Decision:** [PR #44](https://github.com/hqanhuy2125-code/BaoCaoSuCo/pull/44), CON-03 [#12](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/12), DEC-016, and canonical [DATA_CONTRACT.md](DATA_CONTRACT.md). This contract selects API behavior; it does not claim the current source implements every selected method.

## Actual current source versus next implementation

- App is Java/XML, minSdk 24, Java source compatibility 11. `MainActivity` routes after session/profile lookup; `AuthRepositories.USE_FIREBASE = true`. Existing `auth/` uses Firebase Auth and Firestore user profiles.
- `resident/` and `manager/` contain Java-only mock logic and role-checked Activity shells; not yet real incident screens wired to Firebase.
- `logic/` contains canonical `IncidentTransitionPolicy`, `IncidentDeadlinePolicy`, status/action mappings. Do not reimplement the state machine in Android screens or Firestore mapping.
- Merged CORE-01 introduced `data/incident/{NewIncidentInput,IncidentSnapshot,IncidentHistorySnapshot}` and `data/repository/IncidentRepository`; JVM JUnit and APK tests PASS as foundation (Issue #40).
- **No `FirebaseIncidentRepository` exists yet.** The v1-delta below does not silently change method signatures or implement transactions.

## Dependency and ownership rules

```text
resident UI / manager UI (Android Activities/XML)
  -> shared IncidentRepository.Resident / .Manager capability interface
     -> FirebaseIncidentRepository (REPO-02, after SEC-02B accepted)
        -> FirebaseAuth for current UID
        -> protected users/{uid} role and Firestore incident/history documents
        -> LOG pure state/deadline rules + checked canonical mapping
```

- One shared Repository service; expose only `.Resident` to Resident UI and only `.Manager` to Manager UI. This is code organization **not a substitute for Firestore Rules authorization**.
- Preserve `auth/**`, `logic/**`, `manager/**`, `resident/**`, UI XML, `MainActivity` and Gradle until feature-specific integration tasks are separately reviewed.
- `data/incident/**` DTOs are immutable **Java read models**, not direct Firestore POJOs. The adapter must validate required Firestore Timestamp/enum/types and reject malformed data without accidental default/privilege escalation.

## Agreed v1 API semantics (CURRENT vs NEXT DELTA)

| Capability | Current CORE-01 source | Selected v1 behavior / required follow-up |
|---|---|---|
| Create | `Resident.create(NewIncidentInput, Callback<IncidentSnapshot>)` | Input contains only title/description/location/priority; Auth UID, status/timestamps, revision and CREATE history are generated/validated in secure transaction |
| Read | `listMine`, `getMine`, `historyMine`, `listAll` | Use verified user identity/Manager role; bounded owner-filtered paginated queries; no arbitrary UID argument; explicit `NOT_FOUND` vs `PERMISSION_DENIED` behavior consistent with Rules |
| Manager commands | `assign/start/complete(incidentId, expectedCanonicalStatus,...)` | **CORE-02 delta required:** also provide `expectedRevision` and stable unique `requestId` for mutation attempt |
| Resident feedback | `confirm(incidentId, expectedCanonicalStatus, resolved,...)` | **CORE-02 delta required:** also provide `expectedRevision` and stable unique `requestId`; allowed only from COMPLETED for original owner |
| History read | Current `IncidentHistorySnapshot` rejects null source status | **CORE-02 delta required:** CREATE history uses `fromStatus=null`, `action=CREATE`, `revision=0` |
| Snapshots | Current `IncidentSnapshot` stores `dueAtMillis` but not `revision/lastEventId` | **CORE-02 delta required:** map derived dueAt from trusted createdAt+priority; include `revision` and `lastEventId` from persisted document |
| Errors | `AUTH_REQUIRED`, `PERMISSION_DENIED`, `VALIDATION`, `NOT_FOUND`, `CONFLICT`, `NETWORK`, `SECURITY_NOT_READY`, `UNKNOWN` | Preserve distinct error semantics in Android and test them; avoid leaking other-user data through an error or cache |
| Subscriptions | `Subscription.cancel()` type exists; no observe methods | **Follow-up UI/REPO adapter delta:** explicit `observeMine`, `observeAll`, `observeIncident`, `observeHistory` with lifecycle tests only as required by MVP notifications |

**Mutation precondition model for CORE-02:** immutable `CommandPrecondition(expectedCanonicalStatus, expectedRevision, requestId)`. `expectedRevision` is a nonnegative integer from the last accepted read; `requestId` is a freshly generated opaque event ID, reused on transaction retries/duplicate UI dispatches, not derived from UI-provided role or status. The repository must validate current persisted revision and status, and it must atomically write the referenced `history/{requestId}` event once. A duplicate request may return a confirmed previously committed result **only if** its stored event and actor/action/incident context match; otherwise return `CONFLICT`. Wrong-role/owner always deny.

## Atomicity, error and lifecycle semantics

- Retrieve authenticated user at invocation and revalidate when committing. Manager role is a trusted, protected `users/{uid}.role` or a separately approved trusted authorization source; never a manually toggled Activity flag.
- An incident create or transition is **one durable atomic incident + matching history commit**, consistent with `revision`, `lastEventId`, `actorId`, `action` and canonical statuses. No `onSuccess` for preview/mock-only/queued writes.
- Use a Firestore transaction and fresh server record reads to check `expectedCanonicalStatus` and `expectedRevision`; map stale/conflicting operations to `CONFLICT`. Replays must not append duplicate history. Only authentic permission checks enforced by tested Rules (or an approved trusted backend) count as authorization.
- `Callback<T>` one-shot success or failure exactly once. Main-thread callback dispatch in Android; fail closed for no Auth, malformed docs, unavailable network, permission denial. Transactions that require server reads/commit cannot be reported as successfully completed while offline.
- A `Subscription` has an idempotent `cancel()`; listener starts with an initial snapshot or error, follows Android STOP/DESTROY, and does not capture a destroyed Activity indefinitely. Cache/latency may show `LOADING` or stale read state but cannot imply durable write success.
- All persisted status values must be canonical English enum codes. The Vietnamese screen labels are presentation-only.

## Query/limit and UI integration contract

- Resident: `whereEqualTo(residentId, currentAuthUid)`, `orderBy(createdAt, DESC)`, `limit(20)`; no unscoped list. Manager: `orderBy(createdAt, DESC)`, `limit(50)`, then Java status/priority/derived-deadline sorting **within that page**. History for one permitted parent: `createdAt ASC` with deterministic event ID tie-break, `limit(50)`.
- Need checked Firestore indexes and cursor pagination before an app consumer may claim globally sorted all-incidents semantics. Query permission must be validated by SEC-02B Emulator, not inferred from `SEC-02A` read-only fixtures.
- UI must handle LOADING, EMPTY, ERROR, retries and logged-out state. Secure Repository write methods are not yet connected to UI; no feature E2E PASS exists.

## Required gates / next tasks

1. **CORE-02** minimal model/interface delta: CREATE event, `revision/lastEventId`, expectedRevision/requestId; JUnit regression and compatibility review.
2. **SEC-02B [#45](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/45)**: test-only Rules with actual Emulator proof for all five state edges, owner/Manager checks, timed immutable fields, pair history, replay, race and Rules call budgets. Reject on any critical failure; **do not weaken Rules** to get green.
3. **REPO-02**: Firebase adapter only after secure design is proven, with Android real-Auth/Emulator integration and durable callback tests.
4. **Production privilege/deployment**: separate accountable Firebase owner approval, independent security review, approved indexes and E2E; not covered by technical contract freeze.

This v1 is an **implementation-design baseline only**. DEC-010/GOV-02 still need named independent reviewers for full group signoff and production security roles. Changing this API contract requires a versioned decision.

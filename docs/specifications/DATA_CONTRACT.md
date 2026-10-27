# CON-01 — Canonical Firestore Data Contract v1 (technical MVP)

**Status: `TECHNICAL_MVP_V1_LOCKED` (2026-10-27).** This means field/collection/API design is fixed for the **SEC-02B Emulator implementation** under the acting technical integration lead's explicit instruction. **`FORMAL_GROUP_SIGNOFF_PENDING_GOV02`; `PRODUCTION_WRITES_NOT_AUTHORIZED`.** This is not independent security approval, proof of deployability, or authorization to change live Firebase.

**Decision source:** [PR #44](https://github.com/hqanhuy2125-code/BaoCaoSuCo/pull/44), CON-01 [#10](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/10), CON-03 [#12](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/12), DEC-015/DEC-016. DOMAIN baseline CON-02 DEC-014 is unchanged. Prevailing `users` contract is source-implemented; incident/history are **design-only**, not a statement about deployed data.

## Collection paths and identity

- `users/{uid}`: existing Firebase Auth UID as document ID, with immutable fields `uid` (must equal ID/Auth UID), `email`, `role`, `createdAt`; owner may update `displayName`. Exactly the current five fields are used. No user migration or Auth refactor.
- `incidents/{incidentId}`: Firestore-generated incident document ID. No separate mutable `id` field.
- `incidents/{incidentId}/history/{eventId}`: **sole** incident-history location for MVP. No parallel top-level `history/{id}`; no collection-group history query in MVP. The event document ID is the **idempotency/request key** for that operation. Generate it once before a transaction and reuse it for transaction retries; never let the UI choose its value as proof of authority.
- Canonical roles: `RESIDENT`, `MANAGER`. Android signup may create only `RESIDENT`; promotion to Manager must occur through a **separately authorized trusted-admin procedure**. Client flags and display labels are never authorization sources.

## Incident document: exact MVP field contract

| Field | Firestore type | Required / initial value | Writer and invariants |
|---|---|---|---|
| `residentId` | string | Required; authenticated creator's UID | Immutable; derive/verify from current Auth UID, not form input |
| `title` | string | Required, trimmed, 1–120 chars | Creator at CREATE only |
| `description` | string | Required, trimmed, 1–2000 chars | Creator at CREATE only |
| `location` | string | Required, trimmed, 1–160 chars | Creator at CREATE only |
| `priority` | string enum | Required: `URGENT`, `NORMAL` or `LOW` | Creator at CREATE, immutable afterward |
| `status` | string enum | Required; initially `NEW` | Only five authorized CON-02 transitions, no other edges |
| `assignedTo` | string or null | Required; initially null | On `NEW→ASSIGNED`, Manager sets trimmed handler name (1–120 chars); immutable afterward |
| `createdAt` | Timestamp | Required; trusted server timestamp on CREATE | Immutable |
| `updatedAt` | Timestamp | Required; trusted server timestamp on CREATE / allowed transition | Never supplied as trusted UI clock |
| `revision` | integer | Required; initially `0` | Increment exactly once for each authorized status transition |
| `lastEventId` | string | Required; initial CREATE event ID | Must point to the matching immutable event in the same atomic operation |

**No persisted `dueAt` field.** In the Java read model, `dueAtMillis = createdAtMillis + SLA(priority)`. The selected CON-02 SLA is `URGENT=4 hours`, `NORMAL=72 hours`, `LOW=168 hours`; UTC/epoch arithmetic with overflow checks. Overdue iff `now > dueAt` and status is one of `NEW`, `ASSIGNED`, `IN_PROGRESS`; `COMPLETED`/`CLOSED` never overdue. Reopen retains the original creation clock. **Tradeoff:** no Firestore `orderBy(dueAt)`; Manager sorts a bounded fetched page in Java, not all incidents globally. A future globally due-sorted feed requires a separate indexed/trusted-computed-field decision.

**Images** excluded from the MVP write shape: do not write `imageUrl` (neither string nor null) or enable Storage/billing. No other arbitrary top-level keys are allowed under this version. A later image feature is a versioned contract change.

**Timestamp authority:** client writes `serverTimestamp()` placeholders, not literal local times; SEC-02B must demonstrate `request.resource.data.createdAt/updatedAt == request.time` (or equally authoritative enforced equivalent) in Emulator and validate paired event time. The Java read DTO uses epoch milliseconds *only after* validated Firestore Timestamp conversion. A client-supplied epoch timestamp is never accepted as authority.

## History event document: immutable MVP field contract

Path: `incidents/{incidentId}/history/{eventId}`, **CREATE only, never UPDATE or DELETE** from the Android client.

| Field | Firestore type | CREATE event | Transition event |
|---|---|---|---|
| `action` | string enum | `CREATE` | `ASSIGN`, `START_WORK`, `MARK_COMPLETE`, `CONFIRM_RESOLVED`, `REPORT_UNRESOLVED` |
| `fromStatus` | string or null | `null` | The immediately previous canonical status |
| `toStatus` | string | `NEW` | The authorized CON-02 next status |
| `actorId` | string UID | Creator Auth UID | Auth UID performing the authorized operation |
| `createdAt` | Timestamp | Trusted transaction/server time | Trusted transaction/server time |
| `revision` | integer | `0` | Equals incremented incident revision |
| `note` | string or null | `null` | Optional trimmed note of at most 500 characters; `null` permitted |

The initial **incident + CREATE history event must be committed atomically**. For every later transition, the incident's `status`/`revision`/`lastEventId`/`updatedAt` and exactly its matching history event must be committed atomically; reject standalone history, standalone status changes, fake actor, duplicate or stale revision, wrong owner or role. **This invariant is a security acceptance criterion, not a claim that current Rules enforce it.** SEC-02B must prove a candidate Rules construction via adversarial Emulator tests, or record an explicit design change to a trusted backend if Rules cannot guarantee it.

**DTO delta acknowledged:** current `IncidentHistorySnapshot` constructor rejects null `fromStatus` and has no `action` or `revision`. Current `IncidentSnapshot` requires a `dueAtMillis` constructor argument but has no `revision/lastEventId`. Their *current source is not v1 persistence serialization*. A separate narrowly scoped CORE-02/REPO-02 adaptation with regression tests is mandatory.

## Canonical transitions (CON-02)

| Action | From → To | Caller | Additional rule |
|---|---|---|---|
| `ASSIGN` | `NEW → ASSIGNED` | Verified MANAGER | Nonblank handler; freeze assignedTo after |
| `START_WORK` | `ASSIGNED → IN_PROGRESS` | Verified MANAGER | No reassignment in MVP |
| `MARK_COMPLETE` | `IN_PROGRESS → COMPLETED` | Verified MANAGER | Preserve assignee/creator |
| `CONFIRM_RESOLVED` | `COMPLETED → CLOSED` | Original verified RESIDENT | Must match incident.residentId |
| `REPORT_UNRESOLVED` | `COMPLETED → IN_PROGRESS` | Original verified RESIDENT | Note optional; preserve original due calculation |

Every other transition is rejected. Do not trust mock adapters' synthetic role/translated Vietnamese labels. Manager is a **protected account role**, not an independent technician account.

## Reads, pagination and Firestore indexes

- **Resident:** only `get` own incident ID; list via `whereEqualTo("residentId", currentAuthUid)`, `orderBy("createdAt", DESC)`, `limit(20)`. Never query all incidents then filter locally. A composite index for `(residentId ASC, createdAt DESC)` is expected; verify index specification in SEC-02B/REPO-02 before deploy.
- **Manager:** after trusted Manager authorization, list `orderBy("createdAt", DESC)`, `limit(50)`; filter/status/priority/due ordering may happen in Java **only within that limited page**. Do not claim this as global priority/due sort. New filters requiring Firestore `whereEqualTo` need documented indexes and privacy tests.
- **History:** for one incident, `orderBy("createdAt", ASC)`, then deterministic event ID tie-break; use a bounded page (suggest `limit(50)`), with per-parent resident/Manager access checks. Never provide a top-level history list for a different resident.
- **Queries must satisfy Rules**; Rules do not act as filters. Rules policy must validate permitted access/query constraints and deny unexpected unbounded collection reads. Indexes and Rules are **not deployed** by this document.

## CON-01 acceptance boundary

**TECHNICAL_MVP_V1_LOCKED:** exact field/collection/nullability/limits/transition/query defaults selected for test-oriented development by acting integration lead (2026-10-27).

**Still OPEN:** GOV-02 #9 independent shared-contract reviewer and Manager/security owners; SEC-02B #45 adversarial Rules enforceability; real Manager provisioning; cloud deployment authorization; Firebase indexes, Android device E2E. Do not mark a full L3 production security signoff, actual secure writes, or tests that have not run. Changing selected fields now requires a recorded v1.x decision and affected tests.

# CON-01 + CON-03 — MVP Firestore data / Repository decision candidate

**Status:** `DECISIONS_SELECTED_2026-10-27 / TECHNICAL_MVP_V1_LOCKED / FORMAL_GOV02_SIGNOFF_PENDING / NOT_DEPLOYABLE`
**Prepared:** 2026-10-27
**Verified baseline:** `main@96eb03c65718087280a98548073e187f83781a5f`
**Trackers:** [CON-01 #10](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/10), [CON-03 #12](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/12), [CON-02 #11](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/11), [Foundation #40](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/40), [Audit #43](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/43).

This document preserves the **proposal-to-decision history**. The acting technical lead selected D01–D10/R01–R08 for the technical MVP on 2026-10-27, with the explicit implementation refinements in canonical `DATA_CONTRACT.md`, `ARCHITECTURE.md`, `FIREBASE_CONTRACT.md` and DEC-015/DEC-016. It does not approve production `firestore.rules`, privilege grants or security deployment. The acting technical lead decides domain/API choices; actual privileged Manager provisioning and production Firebase Rules deployment need explicit authorized console control. No technical approval is fabricated on behalf of teammates.

## 1. Observed facts versus proposed changes

| Source-backed observed facts | Evidence |
|---|---|
| Current Auth creates `users/{uid}` with `uid`, `displayName`, `email`, `role=RESIDENT`, `createdAt` | `FirebaseAuthRepository.java`, `User.java`, tracked root `firestore.rules` |
| `DATA_CONTRACT.md` v0 omits `uid` in initial table but documents this observed delta; v1 is not frozen | Existing main `DATA_CONTRACT.md` |
| CON-02 selected five canonical state transitions and SLA 4h / 72h / 168h as technical MVP domain baseline | `STATE_TRANSITIONS.md`, `DECISION_LOG.md` DEC-014 |
| Main has immutable candidate DTOs and separate `IncidentRepository.Resident` / `.Manager` operation interfaces; NO real Firestore adapter | Merged PR #41 (CORE-01) |
| Original root Rules deny all incident/history client reads/writes. Test-only read-only candidate is NOT deployed | Root `firestore.rules`, merged PR #42 (SEC-02A) |
| Windows post-merge verification at exact baseline: Android JUnit **158/158 PASS**, Emulator **38/38 PASS**, Gradle/APK PASS | Issue #40 evidence; this validates foundation/mock + deny/read-only Rules tests, **not incident writes** |
| Tooling audit: 10 reported package findings (5 high, 5 moderate) spanning 4 advisories; exploitability and remediation pending | Issue #40; separated AUDIT-01 #43 |

## 2. CON-01 — Data decisions for technical lead

| ID | Choice recommended for MVP | Status / consequences |
|---|---|---|
| D01 | Keep `users/{authUid}` with the **existing five fields** `uid`, `displayName`, `email`, `role`, `createdAt`; `uid == document ID == auth.uid` at creation. No profile migration | **SOURCE-BACKED RECOMMENDATION**, requires canonical v1 table correction |
| D02 | Use `incidents/{incidentId}` auto-ID plus **nested** `incidents/{incidentId}/history/{eventId}`, not top-level `history/{id}` | **SELECT ONE**; nested path allows per-incident timeline and bound parent ownership; current candidate emulator already tests nested reads |
| D03 | Incident fields on CREATE: `residentId` (UID), `title`, `description`, `location`, `priority`, `status=NEW`, `assignedTo=null`, `createdAt`, `updatedAt`, and **new proposed** `revision=0` plus `lastEventId`. No client-populated `id` field | **SCHEMA CHANGE CANDIDATE** (revision/event link not in v0/CORE-01). Required if Rules are to bind history to a versioned transition |
| D04 | Required title/description/location: trim, nonblank, max **120/2000/160** characters respectively; `assignedTo` null initially, later nonblank display name max **120**. `priority` one of `URGENT/NORMAL/LOW` and immutable after create. Do not add a technician login role | **SELECT LIMITS**; first 3 values originate as proposals in PR #8, handler max is a new recommendation |
| D05 | **Do not persist a writable `dueAt` in v1.** Calculate read-model `dueAtMillis = createdAtMillis + SLA(priority)` from canonical trusted Firestore `createdAt` using LOG. `updatedAt` from server timestamp, history time from server timestamp; client UI never supplies authoritative UID/time/deadline | **EXPLICIT TRADEOFF**: v0 proposes persisted `dueAt`; choosing derived dueAt avoids a forged client timestamp or impossible exact `request.time + SLA` value in a client-written field. Manager due-date sorting then happens in a bounded fetched set, not Firestore `orderBy(dueAt)`; if server sorting of all incidents is mandatory, revisit via trusted backend/indexed computed field |
| D06 | Image uploads excluded from required MVP write path, omit `imageUrl` from CREATE (keep future-compatible read DTO plan); no Storage activation or billing change | Consistent with confirmed scope postponing images |
| D07 | **Create one history event atomically with incident CREATE**: `action=CREATE`, `fromStatus=null`, `toStatus=NEW`, `actorId=auth.uid`, `createdAt=server timestamp`, `revision=0`, `note=null`, `eventId` auto-ID. Incident `lastEventId` equals the event ID | **NEW DESIGN CHOICE**, not present in `IncidentHistorySnapshot` (currently rejects null fromStatus); requires a separate reviewed DTO/test delta |
| D08 | For status changes, append one immutable history event: `action` canonical CON-02 action, `fromStatus`, `toStatus`, `actorId=auth.uid`, `createdAt` server time, `revision=old+1`, optional `note` max **500**. Incident `lastEventId` references this new event and `revision` increments exactly once | **NEW DESIGN CHOICE**, note max is a recommendation. Deny history update/delete, blank actor, status skipping, forged role/owner/time and independent writes |
| D09 | Never store mutable `residentId`, `createdAt`, `priority` after CREATE. Allow `assignedTo` to change only on `NEW→ASSIGNED`; keep it thereafter. `CLOSED` remains terminal | Consistent with CON-02; enforce in security tests and actual Rules |
| D10 | Initial reads: resident `whereEqualTo(residentId, authUid)`, order `createdAt DESC`, `limit(20)`; manager bounded `limit(50)` by `createdAt DESC`, then Java status/priority/due sorting **within fetched set**, not globally. Per-incident history `createdAt ASC` and event ID as stable tie-break | **SELECT QUERY/PAGINATION**: no misleading global-sort claim; add/check composite indexes when real Rules/query tests require them. Never rely on Rules to filter a full unscoped resident collection query |

### Incident sample (illustrative schema — NOT a live Firestore write)

```text
incidents/{incidentId}
  residentId: <trusted Auth uid>        // immutable
  title, description, location: string
  priority: URGENT | NORMAL | LOW       // immutable
  status: NEW                           // initial
  assignedTo: null                      // initial
  createdAt: <server Timestamp>
  updatedAt: <server Timestamp>
  revision: 0
  lastEventId: <initial history event id>
  // dueAt is derived in the Java read model, NOT a stored field in this candidate

incidents/{incidentId}/history/{lastEventId}
  action: CREATE
  fromStatus: null
  toStatus: NEW
  actorId: <trusted Auth uid>
  createdAt: <server Timestamp>
  revision: 0
  note: null
```

History `actorId` and `revision` must be server/rules-verified against the incident mutation. Do not accept a client-provided display name, UI Boolean or historical mock fixture as security evidence.

## 3. CON-03 — Repository API and lifecycle decisions

| ID | Recommended behavior | Status / implementation impact |
|---|---|---|
| R01 | Retain **one shared `IncidentRepository` definition**, with separately exposed `Resident` and `Manager` capability interfaces backed by a single secured adapter. The UI only receives its appropriate interface | Existing CORE-01 model; an interface is not a permission boundary |
| R02 | Authenticate current UID from `FirebaseAuth.getCurrentUser()` at request/transaction time; Manager role from a **protected** `users/{uid}.role`, not an Activity-supplied role. The same checks must be enforceable in Rules. Provision Manager accounts only through a trusted admin procedure | **SEC-02B prerequisite**; current registration cannot self-promote, but manager provisioning not implemented here |
| R03 | Keep current `Callback<T>` and `Error`: `AUTH_REQUIRED`, `PERMISSION_DENIED`, `VALIDATION`, `NOT_FOUND`, `CONFLICT`, `NETWORK`, `SECURITY_NOT_READY`, `UNKNOWN`. Deliver UI callbacks on Android main thread, exactly once for a completed one-shot operation | **SELECT CALLBACK CONTRACT**; add tests for failure, cancellation, offline, repeated tap and process lifecycle; no success before durable commit |
| R04 | Extend the existing `Subscription.cancel()` type into explicit `observeMine`, `observeAll`, `observeIncident`/`observeHistory` methods only when UI wiring actually needs them. On subscription start: initial snapshot or explicit error; `cancel()` idempotent and listener removed on STOP/DESTROY | **SELECT LISTENER/LIFECYCLE**; existing `IncidentRepository` does not yet expose observer methods |
| R05 | Creation must commit incident + CREATE history together after Rules authorization. Manager `assign/start/complete` and owner Resident `confirm` must commit new canonical status + matching history together in a Firestore transaction or separately reviewed trusted backend. `onSuccess` strictly after durable commit | **SEC-02B must prove enforceable Rules**, not merely Java local policy or a client batch |
| R06 | Add a **revision / lastEventId / requestId** contract for mutation. Transaction reads latest incident, checks caller's `expectedStatus` and revision, validates action with LOG, increments revision and creates one event ID linked in incident. Duplicate requests must not create extra history; stale reads reject as `CONFLICT` | **EXPLICIT API DELTA REQUIRED**: current commands accept only `expectedCanonicalStatus`, not expected revision/request ID. A repeated identical request may return prior verified result or deterministic CONFLICT; settle before code |
| R07 | Never accept Vietnamese UI labels in persistence; Android model adapter converts Firestore canonical enum to display-only labels, not vice versa for authority. Read/write errors map to explicit Repository errors; unknown enum/shape fails closed | Already supported by CON-02 but requires adapter tests |
| R08 | Listener queries must include required owner filter; no unbounded full collection listeners. Sorting scope and limit semantics must be documented (D10). Transactions not available offline must return an error; no fake optimistic `COMMITTED` | **SELECT READ CONTRACT** and verify with Emulator/device tests |

**IMPORTANT design feasibility gate:** Firestore documents allow atomic transactions/batches; Rules `getAfter()` can test post-transaction state. These APIs do **not automatically prove** actor, history immutability, replay resistance, or an enforceable one-event-per-transition invariant. Implement a test-only candidate SEC-02B Rules policy and run malicious-client Emulator cases. If a critical invariant cannot be enforced within Rules constraints and access-call budget, change design to a **trusted backend** instead of weakening authorization. Client-side transactions and `lastEventId` are insufficient proof by themselves.

## 4. SEC-02B → REPO-02 test-first dependency sequence

1. **CONTRACT DECISION:** Acting technical integration lead selects/amends D01–D10 and R01–R08, especially derived dueAt and initial CREATE history event; records decision and accountable production-security authority. This document is a candidate, not that decision.
2. **CANONICAL DOCUMENT PR:** Update `DATA_CONTRACT.md`, `ARCHITECTURE.md`, `FIREBASE_CONTRACT.md`, `DECISION_LOG.md` *after* accountable technical choices. Do not falsely mark Firebase console role provisioning / security approval as completed.
3. **SEC-02B TEST-ONLY:** Create an independent `security/rules/` candidate Rules file (NOT root), Firebase Emulator tests for owner and provisioned Manager; valid CREATE+history, five allowed edges, stale revision, duplicate request/event, forged actor/time/role, malicious standalone history write, field tampering, immutable close, cross-resident reads and pagination queries.
4. **REPO-02 ANDROID ADAPTER:** Only after the Rules approach passes can a branch implement the real Firestore adapter with trusted user, validated DTOs and atomic operations. Any persisted field/API evolution must be covered by Android JUnit/Emulator combined regression; no broad rewrites of existing RES/MGR/LOG files.
5. **RELEASE GATE:** Reviewed root Rules changes/console operation, Emulator tests (including adversarial cases), Android E2E under both roles, and authorization from actual console owner. Nothing from this proposal deploys, grants privileges, or writes production incidents.

## 5. Conflict/ownership guardrails

- The working technical lead is the domain/API coordinator and integration custodian; other members own only specifically assigned modules. Do **not** ask Hưng or the group leader to validate intricate cross-module code as a fake reviewer gate.
- Rules and Firebase Console **production deployment** are separate privileged operations requiring explicit accountable approval by the actual authorized console owner. Automated review/tests support but do not impersonate an independent human.
- Keep older [Draft PR #8](https://github.com/hqanhuy2125-code/BaoCaoSuCo/pull/8) as historical proposal; do not merge it blindly because source state and DEC-014 evolved.
- Source `Resident`, `Manager`, `logic`, `auth`, `firestore.rules`, `app/build.gradle.kts` and production credentials are **out of scope** for this proposal PR.
- Open [AUDIT-01 #43](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/43) handles the 10 Node dev-tool audit findings separately; do not auto-upgrade dependencies or misstate risk.

### Decision receipt template

```text
Decision owner: Technical integration lead
Decision date:
CON-01: D01–D10 ACCEPT for technical MVP; v1 canonical docs control exact fields/limits
CON-03: R01–R08 ACCEPT for technical MVP; CORE-02 source delta still required
Backend security design: test-only Rules + getAfter pilot, fallback trusted backend if Emulator proof fails
Production Rules/role provisioning authority: named account owner (not yet granted)
CON-01/03 technical implementation baseline: LOCKED v1; formal GOV-02 independent review pending
Deployment: NOT AUTHORIZED
```

## Technical-lead decision receipt (2026-10-27)

**Decision:** D01–D10 / R01–R08 **selected as an implementation baseline** via direct user instruction to conduct the CON-01/03 Contract Freeze, with the canonical exact schema/API now established in `DATA_CONTRACT.md`, `ARCHITECTURE.md`, `FIREBASE_CONTRACT.md`, DEC-015/DEC-016. The higher-level recommendation tables above are historical proposal records and are superseded where a canonical exact field description differs.

**Not asserted:** external human reviewer, whole-group GOV-02 approval, production L3 Security Rules approval, actual Firestore incident data, provisioned real Manager, deployed index/Rules, write/security Emulator PASS, or device E2E. PR #44 remains an auditable documentation-only change.

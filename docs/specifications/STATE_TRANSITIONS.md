# Incident state transitions and permissions — MVP domain baseline v1

**Domain state:** `MVP_DOMAIN_BASELINE_SELECTED` (technical implementation direction, 2026-10-26).
**Security/persistence state:** `NOT_APPROVED / NOT_IMPLEMENTED`. This document defines business behavior only; it does **not** authorize Firestore incident writes, manager provisioning, Security Rules or backend deployment.

**Origin:** Five state transitions are specified in `docs/PROJECT_SCOPE.md`. Canonical codes match the 11 LOG Java classes in `app/src/main/java/com/example/baocaosuco/logic/`, merged as PR #32 + #34. The proposed implementation choices below were presented to the technical integration lead before the 2026-10-26 instruction to continue with the next step. They are recorded as the **MVP technical baseline**, not as independent formal agreement by every team member. See `docs/governance/DECISION_LOG.md` and Issue #11 for traceability.

## 1. Canonical states, display labels, and roles

| Canonical persisted status | Vietnamese display label |
|---|---|
| `NEW` | Mới |
| `ASSIGNED` | Đã giao |
| `IN_PROGRESS` | Đang xử lý |
| `COMPLETED` | Hoàn tất |
| `CLOSED` | Đã đóng |

Roles: `MANAGER` and `RESIDENT` (consistent with LOG `ActorRole`). These values are **domain codes**. A role value provided by the UI, an unverified Firestore profile or a caller-controlled boolean is **not proof of authorization**.

`IncidentStatusCodec.parseCanonical()` accepts **canonical English strings only**, never Vietnamese display labels. Mock Resident/Manager modules may contain Vietnamese labels; production repository adapters must map from canonical status data to labels at the presentation boundary, and reject unknown persisted codes.

## 2. The only five allowed transitions

| Current | Action | Verified actor | Next | Preconditions |
|---|---|---|---|---|
| `NEW` | `ASSIGN` | `MANAGER` | `ASSIGNED` | A nonblank assignee name is recorded |
| `ASSIGNED` | `START_WORK` | `MANAGER` | `IN_PROGRESS` | Latest persisted state is still ASSIGNED |
| `IN_PROGRESS` | `MARK_COMPLETE` | `MANAGER` | `COMPLETED` | Latest persisted state is still IN_PROGRESS |
| `COMPLETED` | `CONFIRM_RESOLVED` | `RESIDENT` who originally reported incident | `CLOSED` | Authenticated UID equals incident `residentId` |
| `COMPLETED` | `REPORT_UNRESOLVED` | `RESIDENT` who originally reported incident | `IN_PROGRESS` | Authenticated UID equals incident `residentId` |

All other transitions are denied. In particular: no NEW → COMPLETED skipping; no same-state status update; CLOSED is terminal; a manager cannot close on behalf of the resident; a resident cannot assign/start/complete or close another resident's report.

**MVP product decision:** Reassigning a handler in ASSIGNED or IN_PROGRESS is *out of MVP scope*. If later included, it requires a separately specified actor rule, immutable history event, and persistence test, not a silent extra transition.

**Resident unresolved feedback:** On `REPORT_UNRESOLVED`, the note is **optional** (can be null); the action still generates a history event even without a note. Any note-length/text validation belongs to CON-01 form/schema rules; do not invent a limit here.

## 3. Priority, SLA and overdue treatment

**Technical MVP baseline selected for implementation** (source scope listed these as illustrative numbers; they are chosen here as the default technical plan, not falsely attributed to earlier group approval):

| Priority code | UI label | Duration from createdAt |
|---|---|---|
| `URGENT` | Khẩn | **4 hours** = 14,400,000 ms |
| `NORMAL` | Thường | **72 hours** = 259,200,000 ms |
| `LOW` | Thấp | **168 hours** = 604,800,000 ms |

`dueAt = createdAt + SLA(priority)` using absolute epoch milliseconds (not local wall-clock addition); Firestore stores timestamps, with trusted `createdAt` and a controlled dueAt calculation. The `IncidentDeadlinePolicy` accepts an injected priority-duration map and uses overflow-checked addition; production caller must use the agreed table, not submit arbitrary UI SLA values.

**Overdue:** A report is overdue only when `now > dueAt` **and** its current canonical state is `NEW`, `ASSIGNED`, or `IN_PROGRESS`. Exactly at `now == dueAt`, it is not overdue. `COMPLETED` and `CLOSED` do not display as overdue. When a resident chooses `Chưa xong` (COMPLETED → IN_PROGRESS), keep the *original* dueAt; an old issue may immediately become overdue again. Reopening **does not restart SLA**. These are business display/priority semantics, not authority to edit dueAt.

**Clock/time authority:** UI clock may be injected to calculate a display flag; creation time, last transition time and dueAt must be validated against a trustworthy persisted source when real data is written. Timezone is for display only. Exact Firestore timestamp/transaction implementation belongs to CON-01/CON-03/SEC-02, not this domain baseline.

## 4. Policy and history API boundaries

The existing pure Java LOG exposes:

- `IncidentTransitionPolicy.evaluate(current, action, verifiedRole, verifiedReportOwner, assignedToPresent)` and `isAllowedTransition(...)`: deterministic state checks based on passed-in values. It **does not verify** Firebase Auth, role, report owner or current database state.
- `IncidentDeadlinePolicy.calculateDueAt(createdAtEpochMillis, priority, approvedSlaMillis)`: calculates an absolute dueAt with injected durations; it does not authenticate time source.
- `IncidentHistoryEventFactory.create(...)`: produces a draft after checking expected/current/action/next consistency using caller-supplied values. It does not persist anything and does not authenticate actor ID/time.
- `IncidentTransitionPlan` and `IncidentCommitPort`: an immutable write proposal and an interface, **not** a proven authorized atomic commit.

**Mandatory persistence requirements (NOT YET IMPLEMENTED):**
1. Identify actor from Firebase Auth and verify MANAGER provisioning or original report ownership against persisted data.
2. Fetch latest persisted incident status and prevent stale/concurrent updates; reject duplicate event IDs.
3. Verify allowed field changes; never trust client-supplied role, resident owner, actor ID, createdAt, dueAt or timestamps.
4. Commit the incident mutation **and** matching append-only history event atomically through an enforceable Rules + transaction design or a trusted backend. A client batch alone is not a security proof.
5. Report success only after durable commit; never optimistic success on rejected writes.

**History collection layout:** **PENDING CON-01** — nested `incidents/{incidentId}/history/{eventId}` is preferred but **not frozen here**. This document does not decide indexes, Rules design, event ID authority or notification persistence.

## 5. Notification baseline and scope

MVP candidate: use an approved Firestore listener to reflect status changes while the app is open. There is **no push notification when the app is closed**, and no new notification persistence/write model is approved by CON-02. The UX/lifecycle and actual listener API remain CON-03 integration matters; this is not acceptance of implemented LOG-04.

## 6. Required acceptance tests and integration gates

**Domain JUnit:** five legal edges, wrong role/owner, missing assignee, skip/same-state/CLOSED denial, unknown/null codes; three priority durations; missing/invalid/overflow SLA; `now == dueAt` and `now > dueAt`; completed/closed non-overdue; reopen retains dueAt; full `COMPLETED → IN_PROGRESS → COMPLETED → CLOSED` loop and note optional. Existing LOG 8/8 passes on `main@ac4b366` per CP-02B local Gradle evidence, **but not all new domain edge tests are yet checked in**.

**Security/Firebase Emulator — separate SEC-02 gate:** another resident denied; forged manager role/actor/timestamps/dueAt/history denied; unknown state/status skip denied; duplicate event ID, race/stale status and partial history update denied; legitimate atomic transitions accepted. No production incident writes or Firebase Rules deployment until this is actually verified.

**Dependencies:** CON-01 #10 must finalize incident/history fields and paths; CON-03 #12 must finalize repository API/callbacks; secure manager provisioning and Firestore Rule tests need assigned implementation/approval. PR #35 is a proposal reference and PR #8 remains draft; neither is automatically merged.

**Change control:** Altering any baseline SLA, transition or overdue behavior requires a new scoped domain decision and regression. This domain baseline must not be presented as a whole-team signature or as a production security authorization.

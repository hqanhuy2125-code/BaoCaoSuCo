# CON-02 — Technical decision package for MVP incident state and deadline

**Status:** `TECHNICAL_PROPOSAL / NOT_FROZEN / NO_FIRESTORE_WRITE_AUTHORIZATION`
**Prepared:** 2026-10-26
**Source baseline:** `main@ac4b36651af36f6f88ad11570c5c40066806f550`
**Primary tracker:** [CON-02 Issue #11](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/11); [LOG integration Issue #33](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/33)
**Current LOG:** Hưng's unchanged 11 Java classes and 8 JUnit tests merged from PR #32 + source move PR #34. CP-02B official Windows Gradle/JUnit at merge SHA: LOG 8/8, Example 1/1, assembleDebug PASS according to recorded Codex evidence. **Pure-code PASS is not Firestore authorization PASS.**

## 1. Decision ledger (do not silently turn recommendations into approvals)

| ID | Topic | Proposed v1 MVP baseline | Evidence and boundary | Decision state |
|---|---|---|---|---|
| C02-01 | Canonical states | `NEW`, `ASSIGNED`, `IN_PROGRESS`, `COMPLETED`, `CLOSED` | Exactly matches `IncidentStatus` in merged LOG; Vietnamese UI labels are presentation only | PROPOSED_TO_FREEZE |
| C02-02 | Actions, actor and transitions | The five edges in section 2; deny all others | Matches `docs/PROJECT_SCOPE.md`, `STATE_TRANSITIONS.md` and LOG pure policy | SCOPE_SUPPORTED / TECHNICAL_MAPPING_PENDING |
| C02-03 | Role codes | `MANAGER`, `RESIDENT` | LOG `ActorRole`; real actor/owner must be derived from trusted authentication + stored incident, never UI | PROPOSED_TO_FREEZE |
| C02-04 | Priority/SLA | `URGENT` 4h, `NORMAL` 72h, `LOW` 168h | **Examples only** in `PROJECT_SCOPE.md`, not approved constants. Inject duration table; do not hardcode until chosen | NEEDS_EXPLICIT_CHOICE |
| C02-05 | Deadline calculation | `dueAt = createdAt + SLA(priority)` in absolute milliseconds/UTC, using checked addition; store Firestore Timestamp | `IncidentDeadlinePolicy` handles injected SLA and overflow; trusted `createdAt`/dueAt validation remains to design | TECHNICAL_PROPOSAL / SECURITY_PENDING |
| C02-06 | Definition of overdue | Propose `now > dueAt` only for `NEW, ASSIGNED, IN_PROGRESS`; `COMPLETED, CLOSED` not overdue. If user chooses `Chưa xong`, the original dueAt remains, so reopened incident can immediately be overdue | No group-approved active/terminal category for `COMPLETED` yet; Manager MGR-05A requires a supplied classifier | NEEDS_EXPLICIT_CHOICE |
| C02-07 | Manager reassignment | No reassignment during `ASSIGNED/IN_PROGRESS` in MVP; no hidden action, no extra transition | Five-edge scope does not define reassignment; propose defer feature instead of inventing policy | NEEDS_EXPLICIT_CHOICE |
| C02-08 | Resident `Chưa xong` note | Optional short note in v1 (nullable), not a reason to grant or deny status transition. If absent, history still records actor/status/time | Existing `IncidentHistoryEventFactory` allows nullable note; scope does not require note | NEEDS_EXPLICIT_CHOICE |
| C02-09 | Time & identity | Actor UID from Firebase Auth and verified role; incident owner from stored `residentId`; use trusted timestamp for createdAt/transition history, not arbitrary UI value | `TransitionPolicy` boolean/role parameters are not evidence of authentication | SECURITY_DESIGN_REQUIRED |
| C02-10 | Atomic status + history | Treat status/assignee/history as ONE authorized commit. Check fresh stored expectedStatus, reject stale/conflicting attempts, stable unique `eventId` to prevent replay, success callback only after durable commit | `IncidentCommitPort` is an interface only. Feasibility under Rules/transactions + Emulator tests is still unknown | SECURITY_DESIGN_REQUIRED |
| C02-11 | History storage | **Prefer** `incidents/{incidentId}/history/{eventId}`, append-only; an event records fromStatus/toStatus/action/actorId/time/note | Alternative top-level `history/{eventId}` remains open in CON-01. This decision **depends on CON-01 #10** | DEPENDS_CON01 |
| C02-12 | Notifications | MVP shows state changes while app is open using approved repository listener; no push when app is closed, no notification-specific Firestore writes proposed here | This is only a delivery candidate; existing LOG does not implement notification | SCOPE_DECISION_PENDING |

**No member or AI is recorded as having approved the rows labeled PROPOSED/PENDING.** The acting technical integration lead can document a choice and evidence; until then, these are review candidates. Do not set DEC-007 to CONFIRMED or CON-02 to FROZEN.

## 2. Five allowed transitions — source-backed

| Current canonical | Action | Next canonical | Required verified actor | Additional condition |
|---|---|---|---|---|
| `NEW` | `ASSIGN` | `ASSIGNED` | `MANAGER` | Nonempty assignee recorded |
| `ASSIGNED` | `START_WORK` | `IN_PROGRESS` | `MANAGER` | Latest stored status = `ASSIGNED` |
| `IN_PROGRESS` | `MARK_COMPLETE` | `COMPLETED` | `MANAGER` | Latest stored status = `IN_PROGRESS` |
| `COMPLETED` | `CONFIRM_RESOLVED` | `CLOSED` | `RESIDENT` | Authenticated UID equals original `residentId` |
| `COMPLETED` | `REPORT_UNRESOLVED` | `IN_PROGRESS` | `RESIDENT` | Authenticated UID equals original `residentId` |

Deny every unspecified edge, including `NEW → COMPLETED`, any transition from `CLOSED`, manager closing on behalf of resident, other resident closing, resident managing assignee, same-status writes and bypassed intermediate statuses.

`IncidentTransitionPolicy.evaluate(current, action, verifiedRole, verifiedReportOwner, assignedToPresent)` is a **pure policy function**. Treat all inputs as trusted **only after independent repository/Rules verification**. A client boolean or an illustrative adapter must never grant access.

## 3. Canonical values vs Vietnamese labels

| Canonical persistence code | Display-only label |
|---|---|
| `NEW` | Mới |
| `ASSIGNED` | Đã giao |
| `IN_PROGRESS` | Đang xử lý |
| `COMPLETED` | Hoàn tất |
| `CLOSED` | Đã đóng |

The current `IncidentStatusCodec.parseCanonical` accepts **only canonical English codes**, not Vietnamese labels. Mock Manager/Resident screens currently expose display labels. Before app integration, build one reviewed conversion boundary between real Firestore canonical values and localized presentation labels; do not change LOG's codec merely to accept arbitrary UI strings or treat mock labels as authoritative persisted status.

## 4. Repository and Rules obligations — not solved by CON-02 policy alone

**READ source of truth:** resident and Manager identity from Firebase Auth and trusted profile/provisioning; original `residentId`, current `status`, `assignedTo` and `createdAt` from Firestore. Do not accept a caller-supplied `verifiedReportOwner` or `ActorRole.MANAGER` without verification.

**WRITE behavior:** compare expected vs freshly stored status in authorized transaction/approved backend, validate allowed fields and next status, create corresponding immutable history event together with status write, ensure idempotency/duplicate-event handling, and reject unauthorized or stale actions. `onCommitted` must only fire once a real commit succeeds. `IncidentHistoryEventFactory` produces a draft and must not be mistaken for a persistence safeguard.

**SECURITY GATE:** Root `firestore.rules` currently defaults to denying incident collections, so live writes must remain unavailable until a separately authorized implementation and Firebase Emulator rules tests prove protections. Never loosen Rules just to make the demo succeed; if Rules cannot securely validate atomic linked writes, use a reviewed trusted-backend design.

## 5. Required test matrix before integration acceptance

**Unit/domain:** all five valid edges; wrong role; wrong owner; no assignee; invalid/null input; `CLOSED` terminal; every disallowed skip/same-state; SLA per priority after approval; missing/zero/negative/overflow SLA; dueAt exact boundary `now == dueAt` and `now > dueAt`; `COMPLETED` status category as chosen.

**Repository/security:** manager spoof through UI; forged `residentId`, `actorId`, timestamps, dueAt and status; manager self-provisioning; cross-resident reads; conflicting concurrent state changes; repeated eventId/duplicate callback; partial history write; transition skipping; history update/delete; `COMPLETED → IN_PROGRESS → COMPLETED → CLOSED` history continuity. Use Firestore Emulator + Android integration tests and show real evidence.

**Feature-adapter contract:** Manager actions map to LOG actions; Resident feedback maps to `CONFIRM_RESOLVED` or `REPORT_UNRESOLVED`. UI labels not passed directly to canonical parser. Adapters do not grant roles or owner access; persistence alone enforces roles/status/atomicity.

## 6. Process and acceptance gates

1. **TECH-DECISION:** Acting technical lead reviews C02-01 through C02-12; records the chosen values and accountable security approver without fabricating consent. For C02-04/06/07/08, select exactly one of the proposed behavior or a concrete alternative.
2. **CON-02 DOMAIN FREEZE:** Make a **separate**, narrowly scoped canonical-contract PR updating `STATE_TRANSITIONS.md` and `DECISION_LOG.md` *after* an attributable technical-lead decision. This domain freeze does not authorize any real Firestore writes.
3. **CON-01/CON-03:** Independently choose history path, DTOs, repository methods/error callbacks and role provisioning; write and review one shared contract before Manager/Resident adapters consume it.
4. **SECURITY IMPLEMENTATION:** Design validated transaction or trusted backend and approved Firestore Rules; run Emulator tests with real permission enforcement. Do not assume a client batch + local policy is secure.
5. **INTEGRATION:** On separate source branch after contracts, adapt Manager/Resident Java and build combined `:app:testDebugUnitTest :app:assembleDebug`. Run browser/device/Emulator E2E separately. No production Firebase operation can be labeled PASS from mock/JUnit alone.

**Scope of this document/PR:** documentation-only proposed decision package; does not modify canonical docs, code, data, Rules, UI, credentials or billing. Preserve draft PR #8 unchanged to avoid overlap.

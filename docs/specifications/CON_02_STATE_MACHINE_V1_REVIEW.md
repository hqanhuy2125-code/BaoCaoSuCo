# CON-02 — State transitions and deadline contract v1 review (24/10/2026)

**Status: DRAFT_FOR_REVIEW / NOT FROZEN.** Based on `PROJECT_SCOPE.md` and `STATE_TRANSITIONS.md`. Implementation owner for `logic/**` remains subject to reconfirmation; technical integration review is coordinated by Trần Quốc Huy.

## Source-backed business transition matrix

| Current | Action | Next | Authorized actor | Preconditions |
|---|---|---|---|---|
| `NEW` | assign | `ASSIGNED` | `MANAGER` | `assignedTo` recorded |
| `ASSIGNED` | start | `IN_PROGRESS` | `MANAGER` | Valid incident, authorized actor |
| `IN_PROGRESS` | complete | `COMPLETED` | `MANAGER` | Valid incident, authorized actor |
| `COMPLETED` | resolved | `CLOSED` | `RESIDENT` report owner | `residentId == auth.uid` |
| `COMPLETED` | unresolved | `IN_PROGRESS` | `RESIDENT` report owner | `residentId == auth.uid` |

All other transitions rejected; this includes skipping `NEW → COMPLETED`, manager closing on behalf of resident, a different resident confirming, and a resident directly marking an incident complete. `CLOSED` is terminal unless a later approved scope change explicitly defines otherwise.

**Evidence distinction:** five Vietnamese states and resident feedback flow exist in the scope. Exact English constant spelling is still a **PROPOSED mapping**, not a confirmed team-wide enum.

## Logic contract candidates

- Pure deterministic function: `isAllowedTransition(current, next, actorRole, isOwner, assignedToPresent)` (signature subject to review).
- Deadline calculator: `calculateDueAt(createdAt, priority)`; use one table whose values are approved in `DECISION_LOG.md`.
- Reject null, unknown roles/status/priority; do not silently default to a privileged action.
- UI may show only eligible actions but must never be the only authorization layer.
- Immutable: reporter, initial creation time; resident cannot mutate handler/priority/deadline/status via arbitrary generic update.
- State write and corresponding history event must agree atomically. Deny client-supplied `actorId` different from authenticated UID.
- Define concurrent transitions (stale `COMPLETED` confirmation, duplicate submit, manager updates) using version/precondition/transaction semantics before implementation.

## Open decisions blocking freeze

| Decision | Known source position | Gate |
|---|---|---|
| Priority durations | Scope gives **examples**: urgent 4h / normal 3 days / low 7 days | Exact SLA approval required |
| Role MANAGER provisioning | Not in Android signup; owner/provisioning not selected | Security decision required |
| Reassign while assigned/in-progress | PENDING | Choose allowed/denied and required audit event |
| Reopen note | PENDING | Decide optional/mandatory note |
| Server time / dueAt validation | Not specified | Security design and test required |
| History structure | Two alternatives in DATA_CONTRACT v0 | Choose A or B |
| In-app update/notification | Firestore listener proposed | Confirm required scope, lifecycle |
| Atomic state/history enforcement | No implementation | Emulator-tested Rules or trusted backend |

## Required tests before acceptance

1. Five allowed transitions PASS for correct role/owner; invalid/skip transitions FAIL.
2. All wrong-role/other-owner forged Firestore updates rejected at persistence layer.
3. `COMPLETED → IN_PROGRESS → COMPLETED → CLOSED` preserves chronological history.
4. Same-state duplicate submission and concurrent manager/resident changes do not create inconsistent history.
5. Deadline calculations PASS across hour/day and time-zone boundaries using the *approved* SLA.
6. After failed write, UI does not display uncommitted state as successful.

## Exit

After identified owners approve enum, deadlines, privileges and persistence scheme, update canonical `STATE_TRANSITIONS.md`, security contract and `DECISION_LOG.md` in reviewed PR. Until then: **no runtime implementation approval is implied**.

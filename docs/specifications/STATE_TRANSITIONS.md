# Incident state transitions and permissions

**Status:** `PROPOSED` implementation details. The five Vietnamese states and the resident 'chưa xong' loop originate from `docs/PROJECT_SCOPE.md`; the English constants below are a proposal needing group sign-off.

| Current | Action | Actor | Next | Condition |
|---|---|---|---|---|
| `NEW` | Assign handler | MANAGER | `ASSIGNED` | `assignedTo` recorded |
| `ASSIGNED` | Start work | MANAGER | `IN_PROGRESS` | Authorized manager |
| `IN_PROGRESS` | Mark complete | MANAGER | `COMPLETED` | Authorized manager |
| `COMPLETED` | Confirm resolved | RESIDENT (report owner) | `CLOSED` | `auth.uid == residentId` |
| `COMPLETED` | Report unresolved | RESIDENT (report owner) | `IN_PROGRESS` | `auth.uid == residentId` |

All other transitions refused. No skip `NEW → COMPLETED`, no unauthorized role changes, no edit on another resident's report. Whether manager can reassign while `ASSIGNED` or `IN_PROGRESS` remains `PENDING_DECISION`.

## Business service contract

- `isAllowedTransition(current, next, actorRole, isOwner, assignedToPresent)` should be a deterministic/pure function for tests.
- `calculateDueAt(createdAt, priority)` uses one group-approved duration table; the scope's 4 hours / 3 days / 7 days are **examples** until confirmed.
- The UI should only display allowed actions, but **UI hiding is not security**: Firestore Rules or trusted backend must reject forged requests.
- Transition and history creation should succeed/fail as a unit where possible. If direct client writes cannot securely enforce both, stop and revise backend design before MVP acceptance.

## Essential tests

1. Every allowed transition succeeds for right role/owner.
2. Every wrong-role, wrong-owner or skipped transition is rejected at persistence layer, not only in UI.
3. Resident cannot change priority, assignee, status or due date arbitrarily after creation.
4. Manager cannot impersonate a resident's final confirmation.
5. Concurrent updates do not silently overwrite each other; event history matches final state.
6. After rejection, report can return `COMPLETED → IN_PROGRESS → COMPLETED → CLOSED` without losing history.

## Open decisions

Role enum naming, deadline durations/timezone, reopen notes requirement, manager reassign, notification mechanism, Firestore rule validation strategy and trusted time source.

# Decision log (living record)

**This file is not a substitute for a team meeting.** The initial entries reflect what the current `README.md` and `docs/PROJECT_SCOPE.md` state; others remain open.

| ID | Decision topic | Current status | Basis / next action |
|---|---|---|---|
| DEC-001 | Topic: dorm incident reporting | CONFIRMED | `docs/PROJECT_SCOPE.md` |
| DEC-002 | Java + XML; min SDK 24 | CONFIRMED | README / `AGENT.md` |
| DEC-003 | 4 members; 5 build weeks + week 6 report | CONFIRMED | Scope says group lead confirmed; verify course deadline |
| DEC-004 | Firebase Auth + Cloud Firestore | PROPOSED | Preferred for shared database; group lead/team formal acceptance pending |
| DEC-005 | 2 login roles; technician as display name only | PROPOSED | Resolve contradictory technician login question in scope |
| DEC-006 | Exact Firestore collections/fields/types/indexes | PENDING_DECISION | Review `DATA_CONTRACT.md` |
| DEC-007 | State transitions + authorized roles | PROPOSED | Review `STATE_TRANSITIONS.md`; protect in security rules |
| DEC-008 | Schema/history update authorization | PENDING_DECISION | Review `FIREBASE_CONTRACT.md` |
| DEC-009 | Feature package layout and repository interfaces | PROPOSED | Review `ARCHITECTURE.md` |
| DEC-010 | Module owners, reviewer and branch protection | PENDING_DECISION | Assign accounts and enable GitHub protections |
| DEC-011 | AI + worktree guardrails | PROPOSED | Explicit team adoption required |
| DEC-012 | Image cloud storage/billing | PENDING_DECISION | MVP image optional; avoid Blaze without approved billing owner |
| DEC-013 | Mobile-specific mechanisms required by instructor | PENDING_DECISION | Confirm minimum number and whether in-app updates qualify |

## 2026-10-24 partial responsibility note — GOV-01 (not a complete DEC-010 approval)

- Session instruction from Trần Quốc Huy (Member 2): takes acting **technical integration lead** role while retaining Resident implementation.
- Trần Huỳnh Hồng Phúc's responsibility is now **UI/UX only**, not Manager business logic.
- Existing PR #3/#4 evidence attributes earlier Auth/Firebase implementation to the Member 1 track; future task ownership is not reconfirmed.
- **Still unassigned:** Manager Java module implementation, shared model/repository code author, security/Firestore rules implementer; Member 4 continuation and all reviewer identities require confirmation.
- This note records only the stated redistribution. It does **not** authorize changing `DEC-004` through `DEC-009`, nor does it close `DEC-010`; a decision record with owners, reviewers and affected member acceptance is still required.
- For current module boundaries see `MODULE_OWNERSHIP.md`. Avoid editing another member's Java/XML or shared protected paths without task-specific authorization.

## New decision record

```text
ID:
DATE:
TOPIC:
STATUS: CONFIRMED | PROPOSED | PENDING_DECISION | OUT_OF_SCOPE
OPTIONS:
DECISION:
APPROVER(S):
REASON:
AFFECTED_DOCS/MODULES:
EVIDENCE: issue/PR/meeting note URL
```

Use actual approver identity and evidence, not 'AI approved'. Do not change approval statuses based solely on chat conversation outside the group.

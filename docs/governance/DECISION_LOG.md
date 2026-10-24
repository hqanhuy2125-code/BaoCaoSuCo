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

## 2026-10-24 GOV-01: partial assignment recorded; DEC-010 still PENDING_DECISION

**Source:** Direct instruction from Trần Quốc Huy (Member 2) during technical handoff, 24/10/2026. Recorded technical coordination responsibilities are not independent proof of whole-team sign-off or security authorization.

| Responsibility / path | Recorded assignment | Implementation / acceptance status |
|---|---|---|
| Technical Lead / Integration Owner; shared-path and contract coordinator | **Trần Quốc Huy** | Session-confirmed coordination. Can inspect/review integrations; every L2/L3 implementation still needs a scoped task and required review. |
| Resident Java feature | **Trần Quốc Huy** | Module responsibility recorded; individual RES implementation task still required. |
| UI/UX | **Trần Huỳnh Hồng Phúc** | Session-confirmed UI/UX only; XML paths only under an approved UI task. Not Manager Java owner. |
| Auth/Firebase past work | Hà Quang Huy | Historical PR #3/#4 merge evidence; future maintainer not yet re-confirmed. |
| Workflow logic/tests | Nguyễn Đức Hưng | Based on prior allocation; renewal not confirmed. |
| Java Manager business implementation | **UNASSIGNED** | **BLOCKED** until implementer and reviewer are selected. |
| `data/**`, `common/**`, shared repositories | **Trần Quốc Huy = integration custodian** | Implementation author and independent reviewer unassigned; no generic write permission. |
| Firestore Rules, indexes, Manager provisioning, billing | **Trần Quốc Huy = technical coordinator** | L3 implementer/security reviewer/group-lead or delegated approver not yet identified; changes **BLOCKED**. |

**Permission boundaries:** L1 feature-local changes need an authorized task/review; L2 shared code/contracts need integration and affected module review; L3 security/privileges/billing still need explicit accountable approval under `CHANGE_CONTROL.md`. Huy cannot count his own GitHub-authored PR as independent approval. The precise path matrix and blockers are in `MODULE_OWNERSHIP.md`.

**DEC-010 closure gate:** Confirm the Manager Java owner, Member 1/4 continued assignments, independent reviewers for Huy's shared changes, implementation owner for the shared data layer, security/Rules owner and approval chain, and branch-protection decisions. Log evidence/affected-member acceptance before marking `DEC-010` as `CONFIRMED`.

**Other decisions unchanged:** This note is not approval of `DEC-004`–`DEC-009` or `DEC-011`–`DEC-013`. Contract drafts CON-01/02/03 may be reviewed but are **NOT FROZEN** and cannot authorize live incident writes.

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

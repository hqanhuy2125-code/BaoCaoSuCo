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
| DEC-007 | Incident state transitions, actor-role requirements and MVP SLA | CONFIRMED (TECHNICAL MVP DOMAIN BASELINE ONLY) | Five scope-backed transitions, proposed canonical enum and selected MVP defaults recorded 2026-10-26 in `STATE_TRANSITIONS.md`; **not** approval of Firebase role provisioning, Rules, schema or production writes |
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


## 2026-10-26 — DEC-014: MVP domain baseline selected for implementation (CON-02)

**DATE:** 2026-10-26
**TOPIC:** Domain-only state/action mapping, priority SLA and overdue behavior to unblock isolated Android LOG/Resident/Manager integration work.
**STATUS:** `CONFIRMED` for the **technical MVP domain baseline only**; `PENDING_DECISION` for history schema, secured transaction/Rules, manager role provisioning, authoritative timestamps and production authorization.
**DECISION SOURCE:** Acting technical integration lead requested proceeding with the immediately preceding CON-02 proposed MVP default choices (current implementation work request). This record is **not** presented as Hưng's, Phúc's, the group leader's or an external security reviewer's independent sign-off. Technical implementation coordination is differentiated from group administrative approvals.

| Domain decision | Implementation choice selected | Boundary |
|---|---|---|
| Canonical states | `NEW, ASSIGNED, IN_PROGRESS, COMPLETED, CLOSED` | Fixed domain codes only, matching existing Hưng LOG |
| Canonical roles/actions | `MANAGER/RESIDENT`; only five approved-in-scope edges described in `STATE_TRANSITIONS.md` | Backend must still authenticate role and incident ownership |
| SLA values (scope examples promoted to MVP technical defaults) | `URGENT=4h`, `NORMAL=72h`, `LOW=168h` | Chosen for MVP implementation in this baseline, **not** claimed as previously team-approved |
| Deadline formula | `dueAt=createdAt+SLA(priority)` with absolute epoch time and overflow checks | Must validate createdAt and computed dueAt using trusted persistence |
| Overdue | `now > dueAt` for `NEW/ASSIGNED/IN_PROGRESS`; `COMPLETED/CLOSED` not overdue | Reopened issues retain original dueAt and may become overdue immediately |
| Manager reassign | Not supported during ASSIGNED/IN_PROGRESS in MVP | Add through later explicit scope/contract |
| Resident `Chưa xong` note | Optional; history must still be recorded | Note type/limit decided with data contract |
| Notification scope | Foreground status updates through a permitted repository listener, no push when app closed | Actual listener and security behavior still not implemented |
| Status labels | Display Vietnamese labels only; persist canonical English enums | Existing feature mock labels must be adapted, not treated as authoritative |

**EVIDENCE:** `docs/PROJECT_SCOPE.md` for five transitions and example SLA; merged LOG `main@ac4b36651af36f6f88ad11570c5c40066806f550` for code; [Issue #33](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/33) for CP-02B Windows Gradle JUnit 8/8 and APK PASS; [CON-02 Issue #11](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/11) and [Draft PR #35](https://github.com/hqanhuy2125-code/BaoCaoSuCo/pull/35) for options and rationale; current explicit implementation continuation instruction from the acting integration lead.

**UNRESOLVED / NOT AUTHORIZED:**
- `DEC-004` Firestore platform/production choice, `DEC-005` trustworthy Manager role provisioning, `DEC-006` schema/history location, `DEC-008` Rules/atomicity and `DEC-009` shared Repository/API **remain at their original statuses**.
- The expected-state check, immutable actor/history data, replay protection and atomic status/history write are **mandatory security design obligations**, not claims of completion.
- `firestore.rules` for incidents remain default deny. Do not enable cloud writes, relax Rules, create privileged Manager users, change billing or merge feature adapters by citing DEC-014 alone.
- Draft PR #8 and #35 remain supporting proposals; this decision record must not be construed as silently merging their unrelated CON-01/03 choices.

**Affected docs/modules:** `docs/specifications/STATE_TRANSITIONS.md` and `docs/governance/DECISION_LOG.md` only for this decision PR. Any Java SLA wiring, label mapping, Repository, Rules and integration tests require separate scoped PRs.

**Verification before production acceptance:** complete missing unit-boundary coverage, freeze CON-01/CON-03 interfaces separately, pass Android build plus Firestore Emulator security tests and full cross-role E2E. A domain decision is **not a security certification**.

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

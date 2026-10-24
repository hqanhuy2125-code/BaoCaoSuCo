# GOV-02 — Assignment proposal and reviewer matrix (24/10/2026)

**Status: NOMINATED / NOT RATIFIED.** This is a concrete staffing proposal for review, not evidence that a person accepted a new workload, a role was delegated, or security was approved. Refer to [GOV-02 Issue #9](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/9). Baseline: `main@287e93519473b3ee52e314e95e2ad3883b93f6ba` (PR #6 GOV-01 merged).

## Basis for assignments (facts versus nominations)

- **RECORDED:** Trần Quốc Huy (Member 2) is acting Technical Lead/Integration Owner and retains Java Resident module; coordinates `data/**`, `common/**`, specifications, integration and security reviews. Not blanket write/merge/security approval.
- **RECORDED:** Trần Huỳnh Hồng Phúc (Member 3) is UI/UX only; XML implementation requires a specifically approved UI task; no Manager Java ownership.
- **SOURCE-BACKED HISTORICAL WORK:** Hà Quang Huy (Member 1 track, GitHub PR #3/#4 created by `hqanhuy2125-code`) implemented Auth/Firebase user profiles; **future work not accepted yet**.
- **OLD PLAN, NOT RECONFIRMED:** Nguyễn Đức Hưng (Member 4) was responsible for pure state logic/deadlines/tests.
- **PROPOSAL:** Assign Manager Java to Nguyễn Đức Hưng; shared Java repository/Firebase adapter and Rules authoring to Hà Quang Huy, leveraging earlier experience. Require individual acceptance, suitable time allocation and reviewers. These are not mandatory commitments.

## Proposed owners, reviewers, files and risk controls

| Work area | Nominated implementer | Review/coordination | Task-approved file scope | Gate |
|---|---|---|---|---|
| Java Resident features (RES-01..05) | **Trần Quốc Huy** (already recorded) | Independent authorized team member (TBD, e.g. Hà Quang Huy if accepted); Huy coordinates integration | `app/src/main/java/com/example/baocaosuco/resident/**`, resident tests | L1 each task; do not modify shared models/manifest by implication |
| UI/UX and dedicated XML | **Trần Huỳnh Hồng Phúc** (design role recorded; XML task nomination) | Trần Quốc Huy for Resident screens; Manager Java author for Manager screens | UI design, specifically named `resident_*.xml`, `manager_*.xml`, dedicated UI resources | L1/L2 by touched paths; freeze screen IDs with Java consumers |
| Java Manager features (MGR-01..03) | **Nguyễn Đức Hưng — NOMINATED** | **Trần Quốc Huy — nominated reviewer**; Phúc for UI integration | `app/src/main/java/com/example/baocaosuco/manager/**`, manager Java tests | L1; Hưng must accept scope; manager shell role checks/logout preserved; depends on CON/LOG and mock API |
| State machine/deadline tests (LOG-01/02) | **Nguyễn Đức Hưng — NOMINATED/RECONFIRMATION** | **Trần Quốc Huy — nominated reviewer** | `app/src/main/java/com/example/baocaosuco/logic/**`, logic tests | L1/L2 depending on shared API; avoid simultaneous edits to `manager/**` in same task |
| Auth maintenance | **Hà Quang Huy — RECONFIRMATION NEEDED** | **Trần Quốc Huy — nominated reviewer** | `app/src/main/java/com/example/baocaosuco/auth/**` and task-named Auth XML | L1/L2, no credential/config changes without approval |
| Shared model/repository interface/mock/Firebase adapter (CORE-01, REPO-01, REPO-02) | **Hà Quang Huy — NOMINATED** | **Trần Quốc Huy — nominated independent reviewer/integration custodian**, plus Hưng on Manager contract | `app/src/main/java/com/example/baocaosuco/data/**`, `common/**`, isolated tests; only named files per task | L2: CON-01/02/03 sign-off before canonical implementation; REPO-02 only after SEC-02 Rules verification |
| Firestore Rules and indexes (SEC-02) | **Hà Quang Huy — NOMINATED Rules author** | **Trần Quốc Huy — nominated independent security/data reviewer**; Hưng nominated negative-test contributor | Root `firestore.rules`, approved Firestore indexes and dedicated emulator tests | **L3:** group lead or explicit authorized delegate additionally approves; forbid permissive rules, client role self-promotion, deploy until tests PASS |
| Trusted MANAGER provisioning (SEC-01) | **Hà Quang Huy — NOMINATED technical author**, authority pending | **Trần Quốc Huy — nominated review** | Documentation first; approved code/admin operations defined later | **L3 approval required**; no live role grants until explicit approval |
| Shared Manifest, Gradle, navigation, governance/contracts | **Task-assigned author only**; Huy is custodian | Affected feature owners + independent reviewer if Huy authors | Explicit listed files only | L2/L3 by change type; no blanket write permission |

The table **does not** assign an AI as a human owner/reviewer. AI may prepare code under a named human's approved task; it cannot provide the required independent human approval.

## Proposed capacity split (must be accepted)

- **Hưng** is nominated for both LOG and MGR Java. Prioritize `LOG-01/02` before `MGR-01/02`, allow parallel UI mock, and confirm workload is achievable with the course timetable. If Hưng declines, **MGR stays UNASSIGNED**; do not silently transfer to Huy or Phúc.
- **Hà Quang Huy** is nominated for shared repository and Rules plus possible Auth maintenance. Split into separate tasks/branches: `CORE-01`, `REPO-01`, `SEC-01`, `SEC-02`, `REPO-02`. Never combine security rule changes with feature/UI PRs. If declined, tasks remain blocked.
- **Trần Quốc Huy** reviews and integrates; retains Resident Java. If Huy authors a shared/L2/L3 PR, another independent authorized reviewer must approve.
- **Phúc** has UI/UX scope only; Manager UI XML does not make Phúc the Manager Java author.

## Approval and access restrictions

1. Every task uses `TASK_ID, OWNER, REVIEWER, BASE_SHA, BRANCH, ALLOWED_PATHS, PROTECTED_PATHS, DEPENDENCIES, EXPECTED_OUTPUT, ACCEPTANCE_CRITERIA, APPROVAL_REFERENCE`. Use one writer per task/branch.
2. L2 shared models/contracts require integration + affected module owner sign-off. `DEC-006/007/008/009` are not automatically confirmed by a nomination.
3. L3 Firestore Rules, privileged role administration, data loss and billing require authorization from **group lead or an explicitly delegated L3 decision-maker** plus independent review. No such delegation is proven by the acting integration role alone.
4. No Firestore incident/history writes, Manager role grants or Rules deployment until schema/security design is approved and actual Firebase Emulator tests pass.
5. Current `main` had no enforced branch protection in the checkpoint. Repo admin must explicitly decide and configure required reviews/checks; no claim that this document activates protections.

## Required acceptance record — issue comments or equivalent decision evidence

Ask each nominated individual to acknowledge the work and reviewer responsibility under [Issue #9](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/9):

```text
ROLE/AREA:
ACCEPT or DECLINE:
PERSON AND GITHUB USERNAME:
IMPLEMENTATION TASKS / CAPACITY:
REVIEWER ACCEPTANCE:
ALLOWED_PATHS ACKNOWLEDGED:
DATE:
APPROVAL EVIDENCE URL:
```

- [ ] Hà Quang Huy confirms Auth maintenance, shared repository/Rules author tasks and available capacity.
- [ ] Nguyễn Đức Hưng confirms LOG + MGR Java tasks and available capacity.
- [ ] Trần Huỳnh Hồng Phúc confirms UI/UX/optional task-approved XML handoff boundaries.
- [ ] Trần Quốc Huy confirms reviewer workload and appoints a **different reviewer** for PRs he writes.
- [ ] Named group lead or delegated L3 authority confirms security review and deployment approval procedure.
- [ ] GitHub repository admin confirms required reviewers/branch protection enforcement (if available).
- [ ] DEC-010 record updated from PENDING_DECISION only **after** evidence above, with separate reviewed PR.

## Next handoffs when accepted

1. **CON-01 Issue #10**: approve Firestore documents and history path, fields, permissions.
2. **CON-02 Issue #11**: approve state changes, deadline SLA and trusted write-history rules.
3. **CON-03 Issue #12**: approve Java repository signatures and callback semantics with both feature owners.
4. Only then proceed with CORE-01/LOG-01/02/REPO-01/SEC-01/02/REPO-02 and RES/MGR tasks in separate PRs.

**Verdict:** GOV-02 responsibility proposal ready for team acceptance; **DEC-010 remains PENDING_DECISION**, and the proposed owners are not automatically authorized to write shared source.

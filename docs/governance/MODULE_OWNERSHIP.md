# Module ownership and shared-path access

**Status:** `PENDING_DECISION` for full DEC-010 closure. **Partial allocation and V2 Manager Java proposal recorded on 2026-10-24** from the acting integration lead's instruction. This file distinguishes the named **path custodian/integration coordinator** from the **task implementer** and the **independent reviewer**. A custodian assignment does not authorize blanket editing, merging, changing Firebase Rules or approving one's own work.

## Current responsibility record (24/10/2026)

| Person | Recorded responsibility | Boundary / outstanding confirmation |
|---|---|---|
| **Trần Quốc Huy (Member 2)** | **Technical Lead / Integration Owner**, coordinator of shared contracts and protected paths; Java Resident module | Confirmed by Member 2 in this working session. Author of each L2/L3 change and an independent reviewer must still be assigned per task. |
| **Trần Huỳnh Hồng Phúc (Member 3)** | **UI/UX only**: designs, screen flows, visual consistency; XML implementation only when a task explicitly assigns layouts | **Not** the Manager Java/business logic owner. Changes to layouts used by another module need agreed view IDs/owner review. |
| Hà Quang Huy (Member 1) | Auth/Firebase contributor evidenced by merged PR #3 and PR #4 | Future Auth maintenance and integration tasks **NOT RECONFIRMED**; no silent edits to `auth/**`. |
| Nguyễn Đức Hưng (Member 4) | Workflow state logic, deadlines and testing under the earlier allocation | Future `logic/**` task acceptance **NOT RECONFIRMED**. |
| **Trần Quốc Huy — V2 PROPOSED** | **Manager Java/business module**, in addition to Resident Java; supported by the prior-session assignment proposal | **Not yet formally ratified in DEC-010.** Implement only under a scoped MGR task and independent PR reviewer. |

**Evidence limit:** Member 2's integration takeover and Phúc's UI/UX-only reassignment are already recorded. The user has now supplied a prior-session **V2 proposal** for Member 2 to take Java Manager as well; this is recorded without assuming team ratification. Approval or continued availability of other members has not been independently established. DEC-010 remains `PENDING_DECISION` until its open slots and reviewer assignments are recorded.

## Protected-path stewardship and edit permissions

The path custodian **for technical coordination of all shared/protected paths is Trần Quốc Huy**. That identifies who must be consulted before changing shared files; it does **not** confer the right to self-approve or change other members' work. All path patterns below are relative to the repository root.

| Paths / assets | Custodian or feature owner | Allowed task implementer | Review gate |
|---|---|---|---|
| `app/src/main/java/com/example/baocaosuco/resident/**`, resident Java tests | Trần Quốc Huy | Huy or a separately assigned Resident task implementer | L1 task approval; independent review for a PR authored by Huy |
| `app/src/main/java/com/example/baocaosuco/manager/**`, manager Java tests | **Trần Quốc Huy — V2 PROPOSED** | Huy only on task-approved MGR Java paths after v2 acceptance | Independently authorized reviewer; Huy cannot self-review his own Manager PR |
| `app/src/main/java/com/example/baocaosuco/auth/**`, `auth_*.xml` | Auth feature owner **PENDING RECONFIRMATION** (historically Hà Quang Huy) | Only specifically assigned Auth task implementer | Auth owner + Huy integration review |
| `app/src/main/java/com/example/baocaosuco/logic/**`, logic tests | Earlier allocation: Nguyễn Đức Hưng; **PENDING RECONFIRMATION** | Assigned LOG task implementer only | Logic owner + integration review |
| `app/src/main/res/layout/resident_*.xml`, `resident_main.xml` | UI/UX: Phúc; Resident integration: Huy | Phúc when XML task approved; otherwise named task author | Huy checks IDs/navigation and existing shell ownership |
| `app/src/main/res/layout/manager_*.xml`, `manager_main.xml` | UI/UX: Phúc; Manager Java consumer: **Huy (V2 PROPOSED)** | Phúc when XML task approved | Huy validates View IDs, navigation and wiring; independent reviewer if Huy authored related shared PR |
| UI designs, screen specifications, dedicated UI resources | Phúc | Phúc within approved UI-01/02/03 scope | Huy integration for cross-module resource changes |
| `app/src/main/java/com/example/baocaosuco/data/**`, `common/**`, shared repository interface/adapter | **Huy = integration custodian**; implementation author **UNASSIGNED** | Named author in L2 task only | Huy + affected feature owners; no self-only approval |
| `app/src/main/java/com/example/baocaosuco/MainActivity.java`, `app/src/main/AndroidManifest.xml`, Gradle/config files | **Huy = integration custodian** | Named integration/feature author in L2 task | Huy + affected module owners; independent reviewer when Huy authors |
| `app/src/main/res/values/**`, shared drawable/theme/string resources | **Huy = integration custodian**; design contribution from Phúc | Named UI/integration author in L2 task | Phúc for design impact + Huy + affected screen owner |
| `firestore.rules`, Firestore indexes, role provisioning and Firebase Console/billing settings | **Huy = technical coordinator**, security author/reviewer **UNASSIGNED** | **BLOCKED** until L3 task, security reviewer and accountable approver named | Group lead or explicitly delegated security authority + impacted owners; actual Rules tests |
| `docs/specifications/**`, `docs/governance/**`, `README.md`, `AGENTS.md`, `AGENT.md`, scope documents | **Huy = documentation/change coordinator** | Named documentation task author | Integration + affected owners; human evidence before changing decision status |

The existing screen shell files (`ResidentMainActivity.java`, `ManagerMainActivity.java`, `resident_main.xml`, `manager_main.xml`) were introduced before this allocation. Preserve role checks, logout, existing XML IDs and feature navigation until the affected owner reviews any change.

## Operational rules (no implicit approval)

1. **One task = one focused branch/PR.** A task names `OWNER`, `REVIEWER`, `BASE_SHA`, `ALLOWED_PATHS`, `PROTECTED_PATHS`, dependencies and actual test evidence. One active writer per checkout/branch.
2. **L1:** Only module-local Java or task-approved layouts/tests; no implicit Manifest, navigation, shared data or Gradle changes.
3. **L2:** Shared models/interfaces, repository implementation, navigation, Android configuration and contracts require Huy as integration custodian **plus affected owners**. If Huy wrote the PR, a different authorized human reviews it before merge.
4. **L3:** Firestore Security Rules, trusted manager role provisioning, billing and destructive operations still require explicit group-lead/security-owner authorization under `CHANGE_CONTROL.md`. Huy's integration assignment alone is not blanket L3 approval.
5. **Review vs implementation:** Phúc may own UI/UX without owning Java business logic; a Java Manager implementer must be appointed independently. No self-selection of missing owners.
6. **Contract freeze gate:** CON-01/02/03 can be drafted/reviewed while ownership is partial, but cannot be marked FROZEN or authorized for shared runtime implementation until impacted reviewers and shared source owners have accepted the contract.
7. Never silently merge, force push, reset/clean, overwrite local work, enable Firebase billing, deploy rules or grant Manager accounts.

## Open GOV-01 decisions required to close DEC-010

- [ ] Record group acceptance of **Trần Quốc Huy as Java Manager owner (V2 PROPOSED)** and assign an independent reviewer for Huy-authored MGR work.
- [ ] Reconfirm Auth maintainer and Member 4's logic/testing commitment.
- [ ] Assign an actual author and independent reviewer for `data/**`, `common/**` and repository interfaces.
- [ ] Name Firestore Rules author, security reviewer and group-lead/delegated L3 approver.
- [ ] Confirm independent PR reviewer for Huy-authored governance/contracts/shared code; record acceptance by impacted module owners.
- [ ] Confirm GitHub branch/PR protection policy (the previously inspected remote `main` reported `protected=false`).

## Task header template

```text
TASK_ID:
OWNER:
REVIEWER:
BASE_SHA:
BRANCH:
ALLOWED_PATHS:
PROTECTED_PATHS:
DEPENDENCIES:
EXPECTED_OUTPUT:
ACCEPTANCE_CRITERIA:
APPROVAL_REFERENCE:
```

## Cross-module dependencies

- If Member 4 accepts the renewed LOG task, publish pure state/deadline functions with unit tests under the approved shared contract.
- Resident and Manager **Java implementers** consume one centrally approved repository interface; Phúc contributes UI designs/XML via an agreed view-ID handoff.
- Any new field, role, status or security permission requires contract review and affected owner sign-off before implementation. Mock screens may proceed only under explicitly approved, isolated paths.

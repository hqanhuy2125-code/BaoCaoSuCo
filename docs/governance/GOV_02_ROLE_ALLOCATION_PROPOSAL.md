# GOV-02 — Module assignment v2 (24/10/2026)

**Status: V2 PROPOSED / AWAITING FORMAL TEAM RECORD.** This document records the v2 arrangement reproduced by Trần Quốc Huy from the previous session on 24/10/2026. Its source describes **Huy taking both Resident and Manager Java** as a proposal and identifies that the team/governance decision still requires formal recording. Do not mislabel prior assistant advice as evidence of all members' acceptance.

## 1. Members and fixed module scope (v2)

| Member | v2 module / responsibility | Scope boundary |
|---|---|---|
| **Hà Quang Huy (Member 1)** | **AUTH — Authentication** | Login, registration, logout, Firebase Authentication, user profile, Auth maintenance, interface handoff. PR #3/#4 already merged. No IncidentRepository/incident Firestore Rules assignment. |
| **Trần Quốc Huy (Member 2)** | **RES + MGR Java + Technical Lead/Integration** | Resident Java, Manager Java/business flow, contract/integration coordination. **Manager Java is a v2 proposal brought from prior session; formal assignment still needs a decision record.** Shared-file custodian does not automatically become author of all data/security changes. |
| **Trần Huỳnh Hồng Phúc (Member 3)** | **UI/UX — entire app** | UI design, screen flows, colors, components, mockups; XML only when a task explicitly assigns paths/IDs. **No Manager Java**. |
| **Nguyễn Đức Hưng (Member 4)** | **LOG — Core Business Logic + Testing** | Pure state/transition and deadline logic; history rules and related unit tests as defined in v2. **No Manager Java; no shared Repository or Firestore Rules by implication.** |

## 2. Task inventory by module (no extra teammate modules)

### AUTH — Hà Quang Huy

| Task | Required module work | Evidence / output |
|---|---|---|
| AUTH-01 | Login/Register UI and sign-in/sign-up flows | **Existing source** in merged PR #3; preserve working implementation |
| AUTH-02 | Firebase Authentication and `users/{uid}` profile integration | **Existing source** in merged PR #4; do not claim independently verified Android E2E |
| AUTH-03 | Auth errors, session state, role routing and regression | Test report and focused Auth fixes if needed |
| AUTH-04 | Handoff existing AuthRepository/currentUser interface to Java RES/MGR consumer | Documented API/usage and limitations |

### RES + MGR Java — Trần Quốc Huy

| Task | Required module work |
|---|---|
| RES-01 | List own incidents (mock initially), status/loading/empty/error |
| RES-02 | New incident form: title, description, location, priority and validation |
| RES-03 | Incident detail and chronological history |
| RES-04 | Connect Resident to agreed repository, only after approved contract/security |
| RES-05 | Resident confirmation "Đã xong / Chưa xong" and authorization tests |
| RES-06 | Image attachment **optional**, only after scope/storage-cost approval |
| MGR-01 | All-incidents list (mock initially) |
| MGR-02 | Filter status, sort by priority/due date |
| MGR-03 | Incident details and assign handler name |
| MGR-04 | Manager transitions: NEW → ASSIGNED → IN_PROGRESS → COMPLETED |
| MGR-05 | Firestore integration, overdue display and Manager-role tests (after approved security) |

### UI/UX — Trần Huỳnh Hồng Phúc

| Task | Required module work | Output |
|---|---|---|
| UI-01 | Design color, typography, buttons, inputs, status chips | UI guidelines / components |
| UI-02 | Auth screen UI design | Login/Register mockups, adapt existing Auth |
| UI-03 | Resident screens: list, create, detail, history, confirmation | Mockups and screen specification |
| UI-04 | Manager screens: list/filter/detail/assign/update | Mockups and screen specification |
| UI-05 | Resource consistency; XML/layout updates **only under separately approved tasks** | Design assets, IDs/layout handoff and UI review |

### LOG — Nguyễn Đức Hưng

| Task | Required module work |
|---|---|
| LOG-01 | Enumerate proposed states and legal transition matrix |
| LOG-02 | Pure `isAllowedTransition()` logic for valid actor/owner/state |
| LOG-03 | Pure `calculateDueAt()` logic using **approved** priority SLA |
| LOG-04 | History event business rules associated with state transitions; no direct privileged Firestore writes by implication |
| LOG-05 | Unit tests for valid/invalid transitions, deadlines and boundary cases |

## 3. Source paths and no-overlap rules

| Path / asset | v2 module writer |
|---|---|
| `auth/**` | Hà Quang Huy, Auth tasks only |
| `resident/**` Java | Trần Quốc Huy |
| `manager/**` Java | **Trần Quốc Huy — v2 proposed assignment**, after scoped task approval |
| `logic/**` Java / related unit tests | Nguyễn Đức Hưng, LOG tasks only |
| UI designs; task-approved `resident_*.xml`, `manager_*.xml`, Auth XML | Trần Huỳnh Hồng Phúc for specified UI tasks, coordinate exact layout/View IDs with Java owner |
| `data/**`, `common/**`, shared repository interfaces/adapters | Shared/protected; implementation author and independent reviewer assigned per task. Huy coordinates |
| `firestore.rules`, index/role provisioning, billing, Manifest, Gradle | Protected; scope-specific review and explicit L3 approval where required |

**No automatic cross-module assignments:** Hà Quang Huy is not the incident Rules/repository author; Nguyễn Đức Hưng is not the Manager Java owner; Phúc owns UI/UX but not Java business logic.

## 4. Verification and decision gates

- GOV-01 PR #6 already merged. **GOV-02 PR #13 remains Draft** until the v2 responsibility changes and reviewers are accepted and documented.
- Keep `DEC-010 = PENDING_DECISION` until team approval and task-specific reviewer identities are recorded. No claim that a quotation of prior advice alone ratifies the v2 assignment.
- `CON-01/02/03` PR #8 remains Draft / NOT_FROZEN. A coding task can only use a canonical frozen contract where required.
- No incident Firestore live writes, Manager role grants or Rules deployment before schema/security approval and actual tests.
- One task = one branch/PR, allowed file paths defined; no force push, unwanted shared source modifications or self-only L2/L3 approvals.

**Primary evidence:** user-provided text from prior ChatGPT session (2026-10-24); merged PR #3/#4 and PR #6; source-scope document. **Unverified:** human/team sign-off to v2 and renewed assignments for Member 1/4.

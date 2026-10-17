# Delivery plan — 5 implementation weeks + week 6 report

**Status:** based on existing `docs/PROJECT_SCOPE.md` schedule. Exact calendar dates, owner names and instructor criteria need confirmation. Aim for one integrated demo, not four isolated screens.

| Week | Main work | Exit gate / evidence |
|---|---|---|
| 1 | Confirm tech, module owners, data/state/security contracts, baseline build, login shell and mock interfaces | All members can build checkout; decisions logged; signatures shared |
| 2 | Resident and manager UI on mock data; logic functions + unit tests; auth flow | Each feature demo with mock; small PRs reviewed |
| 3 | Firebase integration (if confirmed), real incident creation/read/assignment | Cross-account report and assignment with secure access checks |
| 4 | Completion/confirmation, history, in-app live updates; end-to-end tests | Full 5-step flow verified on two accounts |
| 5 | Regression, bug fixes, optional images only if safe, report/demo package | APK, test matrix, known limitations and demo script ready |
| 6 | Course presentation/viva | Each owner can explain their logic and evidence |

## Cross-member delivery agreement

- Member 1: app shell/integration/review; not the default person to fix every feature defect.
- Member 2: resident screens/validation/repository calls via contract.
- Member 3: manager list/assignment/sort/overdue alerts.
- Member 4: pure state logic, deadlines, history mechanism and tests; publish signatures in week 1.

## Workboard

Task fields: `ID`, `owner`, `reviewer`, `priority`, `dependencies`, `acceptance criteria`, `due`, `state (TODO/DOING/REVIEW/DONE/BLOCKED)` and PR URL. Blockers reported early (recommend same day instead of waiting >2 days).

## Definition of Ready

Approved scope, owner, allowed/protected paths, needed contract/interface, acceptance criteria, test approach, branch/baseline.

## Definition of Done

Feature behaves as specified, no out-of-scope diff, build/test evidence captured, error states handled, PR review complete, merged into `main` by authorized maintainer. A screen that compiles only on one person's machine is not done.

## Cutline

Optional photos, extra statistics/QR/offline support are first candidates for removal, but confirm course's required mobile mechanisms **before** cutting photo/notification features. Never cut auth, report creation, assignment, valid state transitions, due dates and core E2E flow without renegotiating MVP.

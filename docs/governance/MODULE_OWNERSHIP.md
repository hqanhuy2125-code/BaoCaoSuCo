# Module ownership and shared-path access

**Status:** `PARTIALLY_UPDATED / PENDING_TEAM_SIGNOFF` (2026-10-24). This is a documented, narrow responsibility update from the active Member 2 / technical integration handoff. It does not imply all shared source owners, reviewers, or security approvals have been assigned.

## Responsibility update recorded 2026-10-24

| Member | Confirmed in current handoff | Implementation boundary |
|---|---|---|
| Trần Quốc Huy (Member 2) | Acting technical integration lead; retains Resident module responsibilities | Coordinates shared contracts/PR integration and Resident Java. Individual shared changes still require task-specific review. |
| Trần Huỳnh Hồng Phúc (Member 3) | UI/UX only, no longer owner of Manager business logic | Design responsibility confirmed; XML layout implementation and precise allowed paths require a task assignment. |
| Hà Quang Huy (Member 1) | Historical Auth/Firebase work evidenced by PR #3/#4 | Future Auth ownership/review availability not re-confirmed. |
| Nguyễn Đức Hưng (Member 4) | Previous plan allocates workflow logic and tests | New-task responsibility not re-confirmed during this handoff. |

**Unassigned/blocking:** Manager **Java implementation** has no confirmed owner. Author/editor of `data/**`, `common/**`, shared `IncidentRepository` and Firestore Rules likewise needs explicit task allocation. Trần Quốc Huy is the named **integration reviewer/coordinator**, not automatic implementer of every shared file. Do not silently assign Manager implementation to Phúc or Huy.

**Evidence boundary:** this is an instruction received from Member 2 in the 2026-10-24 working session. Team-wide ratification/other owners' acceptance has not been independently verified. `DEC-010` remains `PENDING_DECISION` until remaining owner/reviewer slots and policy are recorded.

| Area | Primary role | Editable paths after approval | Dependencies |
|---|---|---|---|
| Technical integration / shared review | Trần Quốc Huy (acting integration lead) | Changes to shared paths only under a separately approved L2/L3 task | Shared contracts; approval from impacted owner(s) |
| Auth/shell | Hà Quang Huy (implemented PR #3/#4; future owner pending) | `auth/**`, root activity/navigation only after renewed task assignment | Integration review |
| Resident Java feature | Trần Quốc Huy (Member 2) | `resident/**`, resident Java tests (task-approved paths) | Shared models/repository/state contract |
| UI/UX | Trần Huỳnh Hồng Phúc (Member 3) | Design deliverables; `resident_*.xml` / `manager_*.xml` only when task specifically assigns XML implementation | Screen IDs/navigation contract |
| Manager Java feature | **UNASSIGNED** | `manager/**` Java only after owner/reviewer assignment | Shared models/logic/security |
| Workflow/business logic | Nguyễn Đức Hưng (prior allocation; pending reconfirmation) | `logic/**` and tests only after task assignment | Shared types/state |
| Shared data/repositories/security | **IMPLEMENTATION OWNER UNASSIGNED**; integration coordinator: Trần Quốc Huy | `data/**`, `common/**`, repository interfaces, Firestore rules each require explicit approval | Data/state/security decisions |

Paths refer to the proposed Java root `app/src/main/java/com/example/baocaosuco/`; layout XML files live under `app/src/main/res/layout/`. Agree on filename prefixes such as `resident_` and `manager_` to reduce collisions.

## Shared/protected paths

- `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `gradle/**`
- `app/src/main/AndroidManifest.xml`, root `MainActivity.java`, themes/colors when shared
- `data/**`, `common/**`, Firestore rules/indexes, `AGENTS.md`, scope/contracts
- `README.md` and governance docs affecting all contributors

These require an integration/shared-code owner plus review from affected module owner(s). **No owner assigned yet**: a module owner cannot infer permission to edit shared paths.

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

- Member 4 publishes pure functions/interfaces early, with examples and tests; Member 2/3 mock the interface until implementation is ready.
- The shared repository interface is owned centrally, not redefined independently in `resident/` and `manager/`.
- Any new field, status or permission needs a contract update and dependent owner sign-off before implementation.

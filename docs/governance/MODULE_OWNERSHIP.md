# Module ownership and shared-path access

**Status:** `PROPOSED`; member names and package structure await approval. Names must come from the team's allocation, not guessed by AI.

| Area | Primary role | Editable paths after approval | Dependencies |
|---|---|---|---|
| Application shell / login / integration | Member 1 / integration lead | `auth/**`, root activity/navigation | Shared contracts; integration |
| Resident feature | Member 2 | `resident/**`, resident layouts, resident tests | `Incident`, `User`, `History`, repository interface, state transitions |
| Manager feature | Member 3 | `manager/**`, manager layouts/tests | Shared models, due date and transition service |
| Workflow/business logic | Member 4 | `logic/**`, logic tests | Shared types + state rules |

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

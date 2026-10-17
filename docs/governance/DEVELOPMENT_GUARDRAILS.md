# Development guardrails — people and AI

**Status:** `PROPOSED`; requires group adoption. **Scope:** all code, docs, scripts, configuration and AI tools operating on this repository.

## Working principle

AI agents are implementers **within** an approved task, not autonomous architects or project owners. A proposed solution does not become a requirement. Preserve the original Android starter source unless a task authorizes modifying it.

## Risk levels and approvals

| Level | Example | Authority before write |
|---|---|---|
| L1 | Resident XML layout or Java class within `resident/` on own branch | Assigned module owner, with approved task |
| L2 | `data/model`, `common`, shared repositories, navigation, Gradle, manifest, contract documents | Affected owner(s) + integration owner |
| L3 | Roles/security rules, production data, deleting files, migrations, history rewrite, default-branch changes | Group lead explicit approval; coordinate affected people |

Approval is **task-specific** and must include the paths, effect, and decision record; verbal delegation to set up documents does not implicitly authorize dangerous Git operations.

## Must do

1. Check repository/worktree state; follow `WORKTREE_POLICY.md`.
2. Confirm task scope, `ALLOWED_PATHS`, `PROTECTED_PATHS`, owner and acceptance criteria.
3. Make a small diff; respect Java/XML conventions and package boundaries.
4. Keep UI loading, empty, validation and error paths in scope.
5. Validate changes (compile/tests/manual checks as applicable); cite actual commands/outcomes.
6. Send handoff with changed files and tests. Review before merge.

## Never do silently

- `reset --hard`, `clean -fd(x)`, discard/overwrite uncommitted changes, implicit stash, history rewrite, force push or deletion of unknown assets.
- Modify another person's in-progress files or auto-merge into `main`.
- Mark `CONFIRMED` without an identifiable human approval record.
- Create manager/admin privileges by allowing self-selection of `role=manager`.
- Open Firestore security rules to all authenticated users as a permanent workaround.
- Commit secrets, keystores, service-account credentials, tokens, local machine paths or real personal data in demo fixtures.

## If blocked

Pause only affected writes; preserve dirty state and record: exact blocker, path, branch, HEAD, current diff, test state, options and who must decide. Do not 'repair' by deleting existing work.

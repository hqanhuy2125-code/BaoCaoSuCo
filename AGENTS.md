# AGENTS.md — Instructions for AI coding agents

> Governance overlay prepared against `main` at `694545e63e5afe02d39bea7ce8ed23227c3a3b47`. This document supplements, **does not replace**, the existing `AGENT.md`, `README.md`, `docs/PROJECT_SCOPE.md` and `docs/TEAM_WORKFLOW.md`. Review/approval is required before merging.

## Mandatory reading order

1. `README.md` and `AGENT.md` (existing project rules)
2. `docs/PROJECT_SCOPE.md` (scope and status labels)
3. `docs/START_HERE.md` (document map and conflict resolution)
4. `docs/governance/DEVELOPMENT_GUARDRAILS.md` and `WORKTREE_POLICY.md`
5. `docs/governance/MODULE_OWNERSHIP.md`, the relevant specification and the current task

Use **Java**, **XML layouts**, `minSdk=24`, package root `com.example.baocaosuco`. Do not switch to Kotlin/Compose. Firebase Auth + Firestore remains `PROPOSED` until the group records its approval, even if it is the preferred option.

## Before making any change

- Start in **read-only inspection**. Report `git status --short`, `git branch --show-current`, `git rev-parse HEAD`, `git diff --stat`, `git worktree list` (when a checkout is available).
- Get a narrowly scoped task with owner, allowed paths, protected paths, acceptance criteria and approval status. If absent, analyze and propose only.
- Preserve all pre-existing work (tracked, staged, untracked and ignored). Never assume the working tree is clean.
- Treat `main`, build configuration, `AndroidManifest.xml`, `data/`, `common/`, Firebase rules, project contracts and shared navigation as shared/protected paths.
- Conflicting instructions, missing contract or dirty protected paths: **STOP before writing**; report conflict and request an authorized decision.

## Execution and safety

- Do not execute `git reset --hard`, `git clean`, implicit stash/pop, discard changes, force push, destructive branch switching, broad `git add .`, rebase, merge, or direct push to `main` without explicit task-specific approval and a recovery plan.
- Do not add/modify dependencies, schema, privileges, routing, build scripts or another owner's module without approval.
- Do not expose secrets, Firebase service account JSON, tokens or private keys. Do not assert that a client-side role check alone provides security.
- Limit code changes to the approved task; keep commits and pull requests focused.

## Verification and handoff

- Run appropriate tests/build **if the environment permits**. Separate `PASS`, `FAIL`, `BLOCKED`, and `NOT_RUN`; never invent test results.
- Before finishing, report changed paths, `git status`, diff summary, tests actually executed, risks, pending approvals, and next safe step using `docs/governance/SESSION_HANDOFF_TEMPLATE.md`.
- AI may propose; humans approve shared contracts, security rules, repository changes and final acceptance.

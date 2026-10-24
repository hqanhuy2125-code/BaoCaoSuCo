# Project state — checkpoint for handoffs

**Snapshot baseline:** public repository `hqanhuy2125-code/BaoCaoSuCo`, branch `main`, commit `bca86b786804068b60646e30aa3e32a8206d8a89` (observed 2026-10-24). **Point-in-time remote snapshot; not a live status feed.** No assertion is made about contributors' local worktrees.

## Observed code on remote `main` (not end-to-end acceptance)

- Android project uses Java/XML, package `com.example.baocaosuco`, minSdk 24, compile/target SDK 37, Java compatibility 11.
- PR #1 merged documentation/governance on 2026-10-17; PR #2 (AI write-access smoke) was closed without merge, and its remote test branch was still present at this checkpoint.
- PR #3 (merged 2026-10-21) added Login/Register and mock authentication, role-based navigation, and resident/manager home shells.
- PR #4 (merged 2026-10-21; merge SHA is this snapshot HEAD) added Firebase Authentication + Cloud Firestore user profiles.
- `MainActivity.java` now routes via `AuthRepositories.getCurrentUser` to Login/Resident/Manager; it is **not** the old `Hello World!` screen.
- `AuthRepositories.USE_FIREBASE = true`; `FirebaseAuthRepository` has register/signIn/getCurrentUser/signOut and reads/writes `users/{uid}`.
- `User.java` contains `uid`, `displayName`, `email`, `role`, `createdAt`; role constants are `RESIDENT` and `MANAGER`.
- `ResidentMainActivity` and `ManagerMainActivity` are greeting/logout shells with role checks, **not** functional incident dashboards.
- `firestore.rules` declares permissions for `users/{uid}`, restricts client-side role promotion, and denies other collections by default. This does **not** prove those rules are deployed to a Firebase project.
- No incident forms, incident repository, incident/history storage implementation, `logic/` state machine or security rules allowing incident/history access were found in the inspected `main` tree.

## Remote PR, CI and test evidence

- At inspection, PR #1/#3/#4 were merged; PR #2 was closed without merge; **0 open PRs**.
- GitHub reported **0 Actions workflow runs** and no commit combined status entries for the inspected HEAD. The repository's existing two Java tests are the Android starter example tests.
- PR #4's author reported successful Firebase signup/signin, selected Firestore Rules checks, cold-start routing, `assembleDebug` and unit tests. **Author-reported evidence only**: no independent CI, device run or end-to-end acceptance was performed during this documentation reconciliation.
- Remote `main` reported `protected=false` when inspected. Do not infer branch policy approval from this observation.

## Decisions and integration blockers

- `DEC-004` Firebase choice, `DEC-005` role model, `DEC-007` state permissions and `DEC-009` architecture remain `PROPOSED` in `DECISION_LOG.md`, despite existing implementation. Do not silently relabel them `CONFIRMED`.
- `DEC-006` schema/indexes, `DEC-008` history write authorization and `DEC-010` ownership/review policy remain `PENDING_DECISION` in that log.
- Data Contract v0 proposes `users/{uid}` **without** a `uid` field, but implemented `FirebaseAuthRepository` and `firestore.rules` require and store `uid`; v1 must reconcile this mismatch.
- `incidents` and history collection shape, immutable fields, status transitions, manager provisioning, deadlines, concurrency and security enforcement are not frozen. Live incident writes remain **BLOCKED** pending contract approval and security validation.
- Remote GitHub inspection cannot verify Android SDK/builds on teammates' computers, `google-services.json` configuration, current Firebase Console state, deployed rules, emulator test logs or local worktree cleanliness.

## Next permitted action (requires task-specific approval for shared files)

1. Review this fact-only DOC-SYNC-01 snapshot via a documentation PR; preserve pending decision labels.
2. Obtain authorized group approvals and freeze `DATA_CONTRACT.md` v1, state/security contracts and shared ownership.
3. Review and test Firestore Rules and repository interface before live incident operations. Member 2 may separately plan RES-01 UI mock on explicitly approved resident-only paths.

## Update at milestones

```text
DATE/TIME:
BASELINE BRANCH+SHA:
ACTIVE PRS:
CONFIRMED DECISIONS SINCE LAST CHECKPOINT:
BUILD/TEST COMMANDS+RESULTS:
DONE:
BLOCKED:
NEXT PERMITTED ACTION:
UPDATED_BY:
```

Do not overwrite this document concurrently from multiple worktrees. Update through PR after integration milestones; per-task details go into individual handoffs.

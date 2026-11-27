# SEC-02A — Firestore Rules baseline regression (emulator only)

**Status:** `TEST_SOURCE_READY / EMULATOR_NOT_RUN / NO_LIVE_FIRESTORE_WRITE`
**Baseline:** `main@51f3b16f0008501bc53c3f649587de68879f8627`.
Tracks [Issue #40](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/40), [CON-01 #10](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/10), [CON-03 #12](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/12).

This standalone Node test package runs TWO isolated suites sequentially: (1) current baseline loaded from the original tracked root Rules, (2) an **explicitly non-production READ-ONLY candidate Rules file** for proposed incident privacy. Each suite independently loads its own rules into the same local emulator; do not run them in parallel. It loads **the original tracked root** `../../firestore.rules` with `@firebase/rules-unit-testing` and a **demo-** project ID. It has *no* Firebase service account, app credential, production Firebase project or SDK administrator.

## Run on Windows (Node.js 22+ recommended, JDK installed)

From the repository root:

```powershell
cd security/rules
npm install
npm test
```

`npm test` invokes `firebase emulators:exec` with the local Firestore emulator on `127.0.0.1:8087` and invokes Node's test runner. The test helper injects fake user tokens (alice, bob, manager). Fixtures are seeded with `withSecurityRulesDisabled` in a **local emulator only**. They never demonstrate that a real client can elevate itself to Manager.

### Current expected verdict
- Profile self-read, self-create with RESIDENT and Auth email, display-name-only update succeed.
- Anonymous/other-resident profile reads, self-assigned MANAGER, protected field changes and delete fail.
- `incidents/{id}`, nested history and top-level history deny **all client reads/writes**, including resident and manager. This reflects current catch-all deny and is **not feature acceptance**.
- Query-scoped resident list and all-manager list are currently denied. This is expected at this gate.

**Important:** A green result only verifies the existing locked-down baseline; it does **not** prove that legitimate incident create/assign/complete/close works. The next security task SEC-02B needs an approved CON-01 history path and exact Rules/transaction strategy plus Emulator tests for authorized transitions, role/owner, time, replay/races and atomic history. Production rollout remains blocked.

## Guardrails
- Do **not** run `firebase deploy` from this package.
- Do **not** replace `firestore.rules`, change Auth/Gradle/UI, migrate user docs or enable Manager self-registration.
- Never interpret `withSecurityRulesDisabled` fixture setup as production access-control behavior.
- If installation or emulator fails, record **BLOCKED** and fix environment, not loosen rules.
- Capture test summary, Node/JDK/CLI versions, exit code and (redacted) logs. Never upload secrets or `google-services.json`.

Official references: https://firebase.google.com/docs/rules/unit-tests and https://firebase.google.com/docs/firestore/security/rules-query

## Proposed read-only candidate (not deployed)

The sibling `incidents.readonly.candidate.rules` preserves the existing `users` permissions, allows the authenticated original resident to read their incident/history and an **already trusted preseeded** Manager to read all incidents/history, while denying all client create/update/delete for incidents/history. Its history subcollection is a **CON-01 candidate, not frozen schema**. Fourteen tests cover allowed/denied reads, owner-filtered queries, manager list, history, and fail-closed writes. A green result permits **only a proposed read-only design review**; it is not secured writable transitions and must never be deployed without explicit role/contract approval and full emulator acceptance.

`run-regressions.mjs` executes both suites **sequentially** to avoid loading competing Rules into a shared emulator at the same time.

## Harness portability fix — project-relative Rules (2026-10-27)

The original `npm test` failed before running suites because Firebase CLI treated `../../firestore.rules` as **outside** the test project `security/rules`. The required command now runs `prepare-emulator.mjs` first to copy the **exact bytes** of the tracked root `firestore.rules` to the gitignored `security/rules/.emulator-runtime/firestore.rules`; `firebase.json` refers only to this **within-project** path. Preparation asserts the root's default-deny markers, compares the copy byte-for-byte and outputs its SHA256. The baseline suite continues to read the **original root Rules directly** and the candidate suite continues to read its separate test-only Rules. They are not combined or relaxed.

The staged file is generated only at test execution and stays ignored. No deployment or credential use is involved. Prior validation: old `npm test` FAILED before tests; a temporary external CLI configuration successfully executed all 38 tests, but this is **not** a PASS for the repaired tracked entry point. Re-run at the new PR head:

```powershell
cd security/rules
npm install --no-package-lock
npm test
```

If the emulator is already bound to port 8087 or a dependency/audit warning requires action, record it separately. Never patch Rules or assert PASS without test reporter output from the exact new SHA.

## SEC-02B — EXPERIMENTAL ATOMIC-WRITE CANDIDATE (NOT DEPLOYABLE)

This branch adds an isolated third suite `writepilot.candidate.rules.test.mjs` and `incidents.writepilot.candidate.rules` under `security/rules/`, leaving the root production `firestore.rules` **unchanged**. The candidate attempts matching atomic incident + nested history updates, with a CREATE event, five selected LOG transitions, protected Manager role/owner and `revision/lastEventId`. It is an **unverified research implementation**, not a safe Rules policy.

`npm test` now executes **three independent sequential** suites:
- SEC-02A baseline: 24 test declarations (root deny-only policy).
- SEC-02A read-only candidate: 14 test declarations.
- SEC-02B atomic-write pilot: **28 test declarations** (allowed creates/transitions and expected-deny attacks).

**66 is an anticipated declaration count, NOT a measured PASS.** Use the exact candidate PR SHA, run the tracked `npm install --no-package-lock` then `npm test` inside a separate Windows worktree, and collect actual exit codes and Node test reporters. A failure in the pilot is a design finding; don't delete/relabel tests or weaken security. The pilot covers a subset of replay, stale revision, field tampering and wrong-actor scenarios, but still needs concurrency tests, bad-history cardinality attacks, Unicode/whitespace validation equivalence, query/index checks, Rules access-call budgets and deliberate trusted-backend fallback criteria. Do not mark SEC-02B security PASS without these.

The existing isolated `prepare-emulator.mjs` still copies the exact root production Rules into a gitignored location for the baseline suite. Each suite loads its own Rules through `initializeTestEnvironment` and uses the demo project/localhost. Never run `firebase deploy` or use a production service account or real privileged Manager accounts.

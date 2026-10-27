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

## SEC-02B-FIX01 — layered adversarial parity patch (REVIEW ONLY; NOT TESTED)

This branch is based **directly on PR #48 HEAD `3375428fef3a7d951d2a66395a9ed36600db2d09`**, not `main`. Open a *Draft PR against PR #48's branch* to avoid deploying or prematurely merging the experimental candidate. The root `firestore.rules` remains unchanged.

- `incidents.writepilot.candidate.rules`: require text fields to be trimmed, nonblank and within contract-specific code-point bounds using RE2 whole-string checks. The 2000-character field is expressed as two regex segments because RE2 repetition counts above 1000 are not supported. Enforce `createdAt DESC` on root incident list queries using `request.query.orderBy`.
- `writepilot.candidate.rules.test.mjs`: preserve the original 28 test declarations; make the 2 originally successful list queries canonical `createdAt DESC` instead of leaving them unsorted.
- `writepilot.fix01.rules.test.mjs`: **23 NEW test declarations** for trimmed/nonblank title/description/location/handler/note, ASCII/non-BMP Unicode boundaries including 2000/2001 code points, resident/manager DESC/ASC query gates, cross-owner denial, extra-history atomic denial and concurrent revision clash.
- `run-regressions.mjs`: execute the fourth suite sequentially, without deleting any baseline/read-only/write-pilot suite.

**Expected declared tests: 24 + 14 + 28 + 23 = 89. This is NOT an observed test result.**
The 19 outside-source Windows probes from Issue #45 were not committed in their original form: FIX01 instead adds reproducible, explicitly numbered overlapping regression tests. Do not describe the new 23 as a bit-for-bit port of the external 19.

### Windows acceptance gate (must run against exact new FIX01 SHA)

```powershell
cd security/rules
npm install --no-package-lock
npm test
```

Record four *actual* Node test reporter summaries, each exact testcase name, totals/failures/skips, both process exit codes, candidate compilation and original root Rules SHA. The original 66 assertions must still pass; each of the 23 new cases must pass with its stated positive/negative expectation.

**Critical unverified details:** Firestore Rules `request.query.orderBy['createdAt'] == 'DESC'` representation and RE2 code-point behavior **must be established in the actual Emulator**. If the candidate rejects the 120-emoji positive or canonical DESC query, report FAIL; investigate without changing v1 Java limits or disabling tests. `String.trim()` semantics must be checked for non-ASCII whitespace parity before production approval.

**Still OPEN even after 89/89 local PASS:** measured Firestore access-call budget for batches/transactions (including caching), additional adversarial race/replay shapes, real client transaction retries/idempotent success, approved composite indexes/cloud parity, GOV-02 independent security/L3 authorization, real trusted Manager provisioning and Android REPO-02 E2E. The new race probe is a smoke check, not a transaction correctness certificate. The Firestore emulator does not prove live composite index behavior.

**Hard guardrails:** candidate-only, demo Firestore Emulator at 127.0.0.1:8087; no `firebase deploy`, root Rules, Java/LOG/Auth/Manager/Resident edits, credential/profile escalation, or permission bypass. Keep PRs Draft and Issue #45 OPEN until full acceptance.

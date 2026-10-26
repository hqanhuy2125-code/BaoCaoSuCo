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

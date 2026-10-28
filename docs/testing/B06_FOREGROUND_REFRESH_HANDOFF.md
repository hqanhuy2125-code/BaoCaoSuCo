# B06 — Resident foreground status refresh (Java-only handoff)

**Scope:** Basic in-app change notification while the Resident screen is visible, **not** Firebase Cloud Messaging / background push / persistent worker / a Firestore listener. Huy owns Java integration. Hưng still owns LOG status rules. Phúc owns UI/XML and actual ID manifest.

**Baseline:** branch created from `main@36ede21d4c3b67c6af6e9398c3e1a0a4030aa0fc`; [PR #57](https://github.com/hqanhuy2125-code/BaoCaoSuCo/pull/57) B01–B05 Java and [PR #58](https://github.com/hqanhuy2125-code/BaoCaoSuCo/pull/58) A08 are separately in review. B06 is deliberately independent and does not change those files.

## Actual implementation
- `resident/ResidentForegroundStatusRefresh.java`: injects the **existing, real `IncidentRepository.Resident`** (CON-03) with `listMine()`; no direct Firebase SDK access, Auth bypass, new Repository API or Firestore writes. The existing Repository checks caller role, current UID, server query limits, Firestore Rules and callback session.
- `resident/AndroidResidentRefreshScheduler.java`: Android main-thread `Handler` implementation of delayed calls and callback removal.
- `ResidentForegroundStatusRefreshTest.java`: pure JVM fake Repo/scheduler tests; no network or Firebase credentials.
- `onStart()`: immediate baseline fetch, schedule **one bounded read every 60 seconds while visible**; after initial baseline, status changes with **strictly higher revision** trigger `View.onStatusChanged(incident)`. Also emits `onPage` when the server page changes.
- `onStop()`: removes scheduled checks, discards cached private page and invalidates delayed callbacks; `close()` is idempotent and prevents restart after logout/destroy.
- Never automatically retries incident writes. Network read failure is displayed via `onError`; next scheduled read may recover. No credential/token, UI/role change or cloud deployment.
- **Limitations:** only the latest **20** incidents from canonical server query; no guarantee of immediate update (up to ~60s after the first sample plus network), no background updates, changes that occur and revert between polls may not be observed. Revisions/status read from server, not from LOG mock sources.

## Java Activity binding after Phúc XML PR
1. After trusted role gate succeeds in Resident Activity, construct `ResidentForegroundStatusRefresh(new FirebaseIncidentRepository().resident(), new AndroidResidentRefreshScheduler(), view)` alongside the B01–B05 coordinator. **Do not initialize before role-gated Auth**.
2. Call `onStart()` when the incident list screen becomes active; `onStop()` when hidden; `close()` on logout/onDestroy. All calls on UI thread.
3. `view.onPage` updates current list if visible and View is still attached; `view.onStatusChanged` displays a lightweight in-app update and triggers `ResidentIncidentCoordinator.loadDetail(id)` only if that detail is actually visible and not busy. Avoid unbounded reads and double requests.
4. `View.onError` should follow B01–B05 error handling. Authorization failure needs session check, not an automatic write retry. Conflict means refresh from server. **Never invent XML IDs** or edit Phúc's layout in this branch.
5. Manager uses existing B04 `onSaved` callback and list refresh; Resident's foreground poll will observe the resulting server revision/status on its next eligible read. No new Hưng LOG transition rules.

## Acceptance/evidence
- [ ] Exact PR head merge-preview CI + JUnit suite and APK PASS (do not claim before GitHub Actions completes).
- [ ] Independent review/merge after #57/#58 dependency checks.
- [ ] Phúc XML PR supplies actual ids/views, then separate Java-only UI wiring PR.
- [ ] On Android emulator and physical phone, keep Resident incident list visible, change status from a Manager session, wait for next foreground refresh, verify new server status displayed **once**. On navigating away/logging out, verify no further UI notifications/read callbacks. Test offline/reconnect, permission denied, owner separation and B07 handoff.
- [ ] Full B06 DONE only after Activity/XML/device demonstration. Until then `JAVA_TEST_PASS` (once CI green), **not** `B06 DONE`.

**No Firebase Rules/indexes/IAM/Billing changes. No Auth, LOG, shared data, manifest, Gradle or XML edits.**

# CP-05A — Integration manifest and Windows acceptance handoff

**Status:** `JAVA_MOCK_SOURCE_COMBINED / WINDOWS_GRADLE_NOT_RUN / DRAFT_ONLY`
**Tracker:** [Issue #37](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/37).
**Base:** `main@28cc9af04918355ad93d3422b9cc6f0d9f4cd445` after CON-02 domain baseline.
**Integration branch:** `integration/log-resident-manager-mock-20261026`.
**Pre-handoff tested candidate SHA:** `eb0a2ea627957091c3285298f3e207ede815a28b` (file addition to this report will create a new SHA; always test actual latest PR head).

## I-01/I-02 — Source graph and exact origin manifest

All nine existing Draft PRs are still untouched: Resident #15, #17, #29, #31 and Manager #19, #21, #23, #25, #27. Their branch graphs are *stacked*, so cherry-picking their full histories would duplicate ancestor files. The integration uses the **18 unique original Git blobs once** without source edits.

| Source PR | Original source/test path | Verified Git blob SHA |
|---|---|---|
| #15 | `app/src/main/java/com/example/baocaosuco/resident/ResidentReportInputValidator.java` | `b553c1e5c079bb9624ada8976f5c6a1af1e6e0c8` |
| #15 | `app/src/test/java/com/example/baocaosuco/resident/ResidentReportInputValidatorTest.java` | `0e6b5cccc71a17c76354ea8822fcc17a87ca4a5d` |
| #17 | `app/src/main/java/com/example/baocaosuco/resident/ResidentIncidentListPreview.java` | `19bf30b5659a402975cae288ef513ef931f8eebf` |
| #17 | `app/src/test/java/com/example/baocaosuco/resident/ResidentIncidentListPreviewTest.java` | `768d49481647073ac5dfa6a114293867d8a675d5` |
| #29 | `app/src/main/java/com/example/baocaosuco/resident/ResidentIncidentDetailPreview.java` | `665db19bb485355fe351c62168d503b07d3115ff` |
| #29 | `app/src/test/java/com/example/baocaosuco/resident/ResidentIncidentDetailPreviewTest.java` | `2b22a33723ace7e41506326ae00fafc019f57b71` |
| #31 | `app/src/main/java/com/example/baocaosuco/resident/ResidentResolutionCoordinator.java` | `7c23a7f83e7aaa714adf4b436f1d21761211a6f2` |
| #31 | `app/src/test/java/com/example/baocaosuco/resident/ResidentResolutionCoordinatorTest.java` | `3bbfe426d2edb8b36293aabbf4977168a9f461fa` |
| #19 | `app/src/main/java/com/example/baocaosuco/manager/ManagerIncidentListPreview.java` | `29ef9a134fc744030c8bce218929d3e9580a0a08` |
| #19 | `app/src/test/java/com/example/baocaosuco/manager/ManagerIncidentListPreviewTest.java` | `a8fff85d23001fd3cc4553e396c35bef792ea1e9` |
| #21 | `app/src/main/java/com/example/baocaosuco/manager/ManagerIncidentListFilterSorter.java` | `25ce20144705e018a89a462a6bc56ded104caaf1` |
| #21 | `app/src/test/java/com/example/baocaosuco/manager/ManagerIncidentListFilterSorterTest.java` | `5a27104c5c23986835d5b1a5a9a3de6e29b15a32` |
| #23 | `app/src/main/java/com/example/baocaosuco/manager/ManagerIncidentDetailDraft.java` | `781eb0412bbc8b0b487f18411823c096069e6c5c` |
| #23 | `app/src/test/java/com/example/baocaosuco/manager/ManagerIncidentDetailDraftTest.java` | `ab806179ae0a1728e453ea10e42dd812df47a6c0` |
| #25 | `app/src/main/java/com/example/baocaosuco/manager/ManagerIncidentActionCoordinator.java` | `e39926e8f2f266d20d3701917d548eeb9952d6c0` |
| #25 | `app/src/test/java/com/example/baocaosuco/manager/ManagerIncidentActionCoordinatorTest.java` | `74a6a89c5abf8cdd46c470cab7cc8375c42544a0` |
| #27 | `app/src/main/java/com/example/baocaosuco/manager/ManagerIncidentOverview.java` | `5419d670104f47f2bfcfd0ed435b51f31c08cf70` |
| #27 | `app/src/test/java/com/example/baocaosuco/manager/ManagerIncidentOverviewTest.java` | `1011f9ed0b611132f718e57878eda70edbaa3408` |

Source counts: Resident four production Java classes + four JUnit files; Manager five production Java classes + five JUnit files. Original `ResidentMainActivity`, `ManagerMainActivity`, XML/Gradle, Firebase/Auth and LOG were not modified.

**Worktree note:** The remote GitHub integration branch is isolated and clean at start. Only Codex on Windows can create and verify an isolated *local* Git worktree; do not count that as already performed in this remote session.

## I-03 — Combined Java mock sources

Exact 18 files above added in source-preserving commit `ca404ad1b2145a2cb00398e7b3716f358a7a7cb7`. All files match their original Git blob SHAs; no PR ancestor commits were copied and no source changes introduced.

## I-04/I-05 — Newly authored preview-only LOG bridges

Under `app/src/main/java/com/example/baocaosuco/integration/mock/`:

- `MockIncidentValueMapper`: canonical English LOG status vs explicitly recognized Vietnamese/ASCII synthetic mock labels. Does **not** relax `IncidentStatusCodec` for Firestore.
- `MockManagerLogicRuleAdapter`: maps Manager action to LOG `IncidentAction` and previews `IncidentTransitionPolicy` with a *simulated* Manager role, which is **not real authorization**.
- `MockResidentLogicRuleAdapter`: maps Cư dân `RESOLVED` / `UNRESOLVED` to canonical status as a state-only preview; no owner verification is possible from its feature API.
- `MockManagerOverduePolicy`: maps statuses to active/terminal; configured MVP `URGENT=4h`, `NORMAL=72h`, `LOW=168h`; `now > dueAt` required for overdue, `COMPLETED/CLOSED` terminal.
- `MockManagerOverviewWiring`: actual MGR-05A DataSource/StatusClassifier setup; takes explicit **synthetic** createdAt map and time source; missing dueAt remains UNKNOWN (no manufactured Firestore data).
- `MockDeniedCommitGateways`: both Manager/Resident mock writes fail closed with `MOCK_ONLY_NO_PERSISTENCE`; no `onCommitted` and no Firebase dependencies.

**Production integration blockers:** existing coordinators' `Command.expectedStatus` may contain Vietnamese UI labels. A real repository must use canonical status only, derive real `auth.uid`/owner/Manager role and latest persisted state, verify transaction/atomic history and idempotency under Firestore Rules. These demo adapters and mock gateways **must never be reused as access-control/persistence implementation**.

## I-06 — JUnit test coverage to run

Original feature test sources copied intact (number of `@Test` declarations, not runtime results):
- Manager: **81** across five JUnit test files.
- Resident: **46** across four JUnit test files.
- New mock LOG bridge: **12** methods in `MockLogIntegrationTest.java`.
- Existing merged LOG tests: **8** in `IncidentLogicTest.java`.
- Existing starter: **1** in `ExampleUnitTest.java`.

Expected **148 discovered tests** *if the Gradle/JUnit runner discovers every `@Test`*, exact count and pass/fail require XML confirmation. The 12 new tests exercise canonical/display mapping, five preview actions (3 Manager + 2 Resident), invalid states, SLA 4h/72h/168h and overflow, terminal/active classification, strict deadline edge, data-source injection without invented timestamps, demo list preservation, and rejection-only mock commit gateways.

## I-07 — Official Windows Android regression — NOT_RUN

A GitHub MCP session cannot execute commands on your Windows Codex machine or access its local SDK/Firebase JSON. **No Gradle PASS is asserted at this integration SHA.** GitHub Actions had no configured runs for earlier checkpoints.

On a clean detached worktree from the **current integration branch head** (not this doc's historical SHA), Codex must:

```powershell
git fetch origin
git worktree add --detach <new-unique-test-directory> <exact-integration-head-sha>
# Use only valid previously configured gitignored app/google-services.json
cd <new-unique-test-directory>
git rev-parse HEAD
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --stacktrace --no-daemon --console=plain
```

Collect JUnit XML counts per class, Gradle exit code, APK existence, compilation errors, environment versions and log SHA256. Verify no source edits or Firebase JSON staging. If FAIL, report exact failing method before any separate corrective code commit. No merge and no Rules or live incident writes.

## Scope and completion

**I-01:** remote branch created; Windows worktree PENDING.
**I-02:** branch graph/blob manifest PASS.
**I-03:** original mock-only Java source/test assembly PASS.
**I-04:** mock LOG rule/display bridges authored, Android test pending.
**I-05:** MGR-05A overdue wiring and explicit SLA authored, Android test pending.
**I-06:** 12 new JUnit methods checked in, execution pending.
**I-07:** official Windows Gradle/JUnit **NOT_RUN** on latest integration SHA.

No Android UI screens were wired, no Manager/Resident real Repository was implemented, and no live Firebase incidents/history writes are enabled. This is a **mock-only integration candidate**.

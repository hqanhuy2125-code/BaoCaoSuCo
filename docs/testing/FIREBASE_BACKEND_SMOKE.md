# A05 → A06 → A07: Android Firebase backend smoke

Task owner: Trần Quốc Huy (integration/testing), under the 2026-10-28 session request
and [Issue #54](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/54).
Independent PR reviewer: pending human assignment. Auth owner Hà, LOG owner Hưng
and UI owner Phúc retain their modules. Review is required before merge.

Allowed changes in this task: the instrumentation test and its fixtures under
`app/src/androidTest/**`, plus this test runbook. Runtime source, XML, Manifest,
Gradle/dependencies, shared contracts, Rules/indexes, IAM, billing and live role
provisioning are protected. The suite calls the existing Java implementations.

## Observed checkpoint — 2026-10-28

- Initial checkout clean: `main@f2d9418da0957c0b5bb7aeaa0fe0e84000f40e9f`;
  status/diff/cached diff empty; origin/main matched after fetch.
- Branch: `codex/a05-a07-backend-smoke`. Existing detached Firebase Review
  worktree preserved.
- Console `baocaosuco-c4ccc/(default)` shows the `incidents` composite
  `residentId ASC, createdAt DESC, __name__ DESC` **Enabled**.
- Console Authentication redirects to its landing page with **Get started**.
  No configured provider or signed-in test identity was demonstrated. No Auth
  setting was changed by this task.
- User confirmed **no provisioned MANAGER**. Live A05/A06/A07 therefore remain
  **BLOCKED**, not DONE. Rules deployment and index status alone do not prove
  authenticated access.
- Checkout initially lacked real `app/google-services.json` and local SDK
  configuration. SDK37 and Android Studio JBR25 are installed. A deliberately
  fake, ignored demo config was used for local build; that APK is not a cloud
  demo/submission build. Replace it with the owner's correct Android config
  before an approved live run; do not commit either file.

## A05 — trusted MANAGER readiness, performed by the authorized owner

1. Firebase Owner confirms the correct project, enables/configures the required
   Email/Password provider under a separately authorized configuration action,
   and records the result. This suite never enables a provider.
2. Owner supplies dedicated test identities: resident A, independent resident B,
   and an already trusted-provisioned Manager. Owner/approved maintainer handles
   provisioning separately; no client self-promotion or new provisioning script
   is supplied here.
3. Verify each identity exists in Auth and has the same UID at `users/{uid}`.
   Required fields: `uid`, `displayName`, `email`, `role`, `createdAt` (Timestamp).
   Keep the private UID/approval audit with the owner; do not publish passwords,
   tokens, service accounts or personal account data in the issue/PR.
4. The authorized operator records who granted the Manager role, when, the
   approval reference and the demo UID privately. A role label on a screen is
   insufficient evidence. The suite must sign in and exercise server access.
5. Provide unused signup credentials for one approved disposable Resident
   identity. Registration is tested through `FirebaseAuthRepository`, never by
   writing a synthetic live profile. One signup email is used once per run.

Missing identities/approval: report `A05 BLOCKED — Firebase Owner`, run only the
local Emulator mode. Do not weaken Rules or change roles to unblock a test.

## What the three tests exercise

| Test | Evidence required for PASS |
|---|---|
| `registrationCreatesResidentProfile` | Auth signup + exact five-field RESIDENT profile; duplicate registration fails without deleting the existing account/profile; logout/login preserves UID |
| `residentLoginProfileAndAuthorization` | Login/current-user; server profile read; displayName edit then restoration; server denies self-promotion; Java denies Manager capability; logout clears session; wrong password rejected; login succeeds again |
| `incidentLifecycleWithTrustedManagerAndSecondResident` | Real `FirebaseIncidentRepository` create/list/get/history, Manager list/assign/start/complete, Resident reopen/confirm; wrong role/owner, blank assignee, skipped transition, stale revision, reused request and repeated close rejected |

Lifecycle: CREATE → ASSIGN → START_WORK → MARK_COMPLETE → REPORT_UNRESOLVED →
MARK_COMPLETE → CONFIRM_RESOLVED. Final state CLOSED, revision 6, exactly seven
ordered history events, matching actors/from/to/revisions, final lastEventId and
server times; reopen retains the original derived 4h URGENT deadline. Repository
callbacks must return on the main thread and once during the observed operation.
Direct SDK probes also require server `PERMISSION_DENIED` for cross-owner reads,
unfiltered Resident lists and self-promotion; Java guards alone are insufficient.

This is bounded backend smoke coverage. It does not prove the full UI journey,
physical-device acceptance, every priority/overdue boundary, offline recovery,
Auth registration rollback under a forced profile-write failure, or arbitrary
concurrency. Existing JUnit/Rules regressions and B07 remain separate evidence.

## Build and local Emulator run

Use PowerShell 7, JDK25 (the checked-in daemon requirement), Android SDK37 and an
isolated Android API37 Emulator. API24 remains the application minimum. These
instructions do not change tracked build configuration or add Android libraries.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
```

For compile-only builds use an ignored dummy `google-services.json`, as in the
existing REPO-02 workflow, with project ID `demo-baocaosuco-smoke`. Never mistake
it for the real app config. The Emulator-mode test creates named FirebaseApp
instances using demo options and disables disk persistence, independently of the
default app's Firebase config.

Start Auth at `127.0.0.1:9099` and Firestore at `127.0.0.1:8087`, with project ID
**`demo-baocaosuco-smoke`**, UI disabled and a byte-for-byte copy of the current
root `firestore.rules`. A local ignored Firebase configuration can be:

```json
{"firestore":{"database":"(default)","rules":"firestore.rules"},"emulators":{"auth":{"host":"127.0.0.1","port":9099},"firestore":{"host":"127.0.0.1","port":8087},"ui":{"enabled":false},"singleProjectMode":true}}
```

Put that file and the Rules copy in `.gradle/backend-smoke/`, then start the
Firebase CLI with `emulators:start --only "auth,firestore" --project
demo-baocaosuco-smoke --config .gradle/backend-smoke/firebase.json`.
Use isolated CLI configuration without live credentials; no deploy/login is
needed for this demo project.

After the Android Emulator boots (replace `emulator-5554` if necessary):

```powershell
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
node app/src/androidTest/fixtures/seed-backend-smoke-emulator.mjs .gradle/backend-smoke/run-01.properties
./app/src/androidTest/fixtures/run-backend-smoke.ps1 -Serial emulator-5554 -Fixture .gradle/backend-smoke/run-01.properties -Mode emulator
```

The seeder has hardcoded **loopback HTTP endpoints and a demo project only**. It
creates fake Emulator accounts/profiles, including a fake Manager fixture. It
cannot grant any cloud role. Use a fresh output filename for each run; it refuses
to overwrite an existing file. The runner uses `adb reverse` for both ports and
`localhost`; API37 permits loopback HTTP by default. The initial `10.0.2.2` attempt
was rejected by network security policy; **no Manifest relaxation was needed**.
On older Android versions, stop if loopback HTTP is disallowed and obtain a
separate approved debug-only configuration task.

Runner uploads the properties through stdin to the debug app's private files
directory, consumes `tee` output privately, and removes only its device credential
copy in `finally`. It refuses to overwrite a pre-existing device fixture. Run on
a dedicated device; use a serial explicitly. Keep the host fixture ignored and
private. It accepts PASS only with all three tests completed without skips.
Default instrumentation with no `backendSmoke` argument skips the suite as
**NOT_RUN**, never as live PASS.

## Live run — only after A05 and environment readiness

1. Owner supplies the real Android `google-services.json`, verifies project
   `baocaosuco-c4ccc` and rebuilds/reinstalls both APKs on the selected test device.
2. Store this fixture privately under ignored `secrets/`, never in Git or runner
   command-line arguments. Properties syntax requires escaping backslashes in
   values; use UTF-8/ASCII-compatible dedicated test credentials.

```properties
projectId=baocaosuco-c4ccc
allowLiveTestWrites=yes
residentEmail=<dedicated-resident-A>
residentPassword=<private>
managerEmail=<already-provisioned-manager>
managerPassword=<private>
otherResidentEmail=<dedicated-resident-B>
otherResidentPassword=<private>
signupEmail=<approved-unused-signup-email>
signupPassword=<private>
```

```powershell
./app/src/androidTest/fixtures/run-backend-smoke.ps1 -Serial DEVICE_SERIAL -Fixture secrets/backend-smoke.properties -Mode live
```

The suite verifies the exact project and expected server profile roles before
incident writes. It creates one synthetic incident and its history, plus one
signup account/profile; it temporarily edits/restores the demo Resident name.
It keeps all audit data, never deletes accounts/incidents/history and never
provisions Managers. Use only owner-approved test identities/data. If a run fails
partway, the owner audits remaining records; do not blindly repeat registration
with the same signup email. Failed mutations must leave revision/history unchanged.

## Evidence and handoff

Record exact tested SHA, device/API, APK config (demo or live), command, test
counts/skips and result. Keep credentials/raw personal data out of published logs.
Use Issue #54 for the daily update and the session handoff template in the PR
comment; leave A05–A07 unchecked until live acceptance is actually demonstrated.

Emulator success does not verify the deployed index or Rules revision. The live
gates remain: Auth/provider readiness, approved Manager identity, correct ignored
Android config, authenticated cloud query/transaction probes and independent
human review. UI/physical-device acceptance remains B07.

References: [Auth Emulator](https://firebase.google.com/docs/emulator-suite/connect_auth),
[Firestore Emulator](https://firebase.google.com/docs/emulator-suite/connect_firestore),
[Android localhost network policy](https://developer.android.com/privacy-and-security/security-config#localhost).

# Test and acceptance checklist

**Status:** `PROPOSED`. Recording planned tests is **not** recording PASS. Use real emulator/phone and Firebase Rules emulator when selected.

## Setup and infrastructure

- [ ] Fresh clone opens in Android Studio; Gradle sync PASS (record versions).
- [ ] `./gradlew testDebugUnitTest` / `gradlew.bat testDebugUnitTest` result recorded if task applicable.
- [ ] `./gradlew assembleDebug` / `gradlew.bat assembleDebug` result recorded.
- [ ] Emulator launch + real phone test with OS version and screenshot.
- [ ] `google-services.json` and credentials handling compliant; no secrets committed.

## Authentication and access

- [ ] Register/login/logout happy and wrong-password paths.
- [ ] Resident cannot see another resident's incident, including direct document ID access.
- [ ] Resident cannot self-escalate role or edit manager-only fields.
- [ ] Manager can access appropriate incidents and cannot fake owner confirmation.
- [ ] Firestore Rules tests reject unauthorized operations; no permissive production rules.

## Resident

- [ ] Required title, description, location and priority validated.
- [ ] Duplicate tap doesn't create unintended duplicate reports.
- [ ] Create → show in own list → detail accurate.
- [ ] Loading/error/empty states usable.
- [ ] Status/timeline update reflects authorized manager actions.
- [ ] Only own `COMPLETED` report exposes resolved/unresolved actions.
- [ ] Failed request does not falsely show success or corrupt timeline.

## Manager and logic

- [ ] Manager list and priority/due sorting correct.
- [ ] Assignment requires handler; state checks reject skips.
- [ ] Due-date computation uses approved priority table and time basis.
- [ ] Overdue warning correct at boundary.
- [ ] Concurrent status updates handled; history consistent.

## E2E demo

Two devices/accounts: resident A submits → manager B receives/assigns → starts → marks completed → resident A confirms / rejects and then confirms. Verify final status and history. Record build SHA, device info, dates, test results and blockers; do not present mock-only demo as cloud integration.

## Evidence template

`TEST_ID | DATE | BRANCH/SHA | DEVICE | STEPS | EXPECTED | ACTUAL | PASS/FAIL/BLOCKED/NOT_RUN | EVIDENCE`

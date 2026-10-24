# Android architecture and integration contracts

**Status:** `PROPOSED` architecture; observed implementation baseline refreshed from remote `main` HEAD `bca86b786804068b60646e30aa3e32a8206d8a89` on 2026-10-24. This is not an instruction to refactor.

## Baseline observed

- Root package `com.example.baocaosuco`; Java source and XML layouts; Gradle uses an AGP version catalog, compile/target SDK 37, min SDK 24, Java compatibility 11.
- `MainActivity.java` is now the session/role router; the former `activity_main.xml` still exists in resources but is not used by that Activity's current `onCreate`.
- Implemented packages: `auth/` (AuthRepository, mock and Firebase implementations, Login/Register), `data/model/User.java`, `resident/ResidentMainActivity.java`, `manager/ManagerMainActivity.java`.
- `AuthRepositories.USE_FIREBASE = true`. Firebase Auth + Firestore user-profile code and `firestore.rules` for `users` were merged via PR #4; remote source does not prove console deployment or Android E2E success.
- Proposed `logic/`, `data/repository/`, `common/` incident contracts and incident workflows are **not present** in the inspected `main` tree. Local build status is unverified.

## Proposed source layout (package-by-feature)

```text
app/src/main/java/com/example/baocaosuco/
  MainActivity.java               # shell/integration; shared
  auth/                           # owner 1
  resident/                       # owner 2
  manager/                        # owner 3
  logic/                          # owner 4; pure business functions
  data/model/                     # shared contracts
  data/repository/                # shared interface + Firebase/mock adapters
  common/                         # constants, errors, utilities
app/src/main/res/layout/
  activity_main.xml
  resident_*.xml                  # resident feature; naming convention proposed
  manager_*.xml
```

## Dependency direction

- UI features depend on shared model/interface; feature UI should not directly depend on another feature package.
- `logic/` owns state validation and due-date calculation; UI calls approved services, not ad hoc hard-coded state strings.
- Repository contract hides Firebase vs mock implementation. Avoid direct Firestore calls from every Activity.
- UI reads signed-in user identity from approved auth interface, never accepts arbitrary resident UID from form input.

## Proposed interfaces (signatures subject to agreement)

```java
interface IncidentRepository {
  void createIncident(NewIncidentInput input, ResultCallback<String> cb);
  void listMyIncidents(String userId, ResultCallback<List<Incident>> cb);
  void getIncident(String incidentId, ResultCallback<Incident> cb);
  void confirmResolution(String incidentId, boolean resolved, ResultCallback<Void> cb);
}
```

This is a **sketch**, not compile-ready code. Decide callback types, listener unsubscribe, null/error handling, and who implements transactional history writes before using in UI.

## Bootstrapping gates

1. Confirm backend choice and secure role provisioning.
2. Approve data/state/security contracts and path ownership.
3. Bootstrap a minimal navigation/data/mock contract that builds on each member's Android Studio.
4. Implement features independently; merge one small PR at a time with build/test evidence.

Avoid introducing MVVM/Navigation Component unless the group and course requirements justify the complexity. Existing scope keeps that decision pending.

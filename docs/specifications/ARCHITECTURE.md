# Android architecture and integration contracts

**Status:** `PROPOSED` based on current Java/XML starter and `docs/ai-review/02_ket_qua_gemini_doi_chieu.md`; not a mandate to refactor.

## Baseline observed

- Root package `com.example.baocaosuco`; Java source and XML layouts.
- `MainActivity` and `activity_main.xml` are starter examples, not an auth/navigation implementation.
- Gradle uses AGP catalog; compile/target SDK 37, min SDK 24, Java compatibility 11 in current main; verify local SDK/Gradle build before feature work.

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

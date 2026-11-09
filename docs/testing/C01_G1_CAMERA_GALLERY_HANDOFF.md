# C01 G1 — Camera + Gallery Java handoff (09/11/2026)

**Owner:** Trần Quốc Huy (Resident Java / Technical Integration)  
**Branch:** `feature/c01-camera-local-20261109`  
**Source:** `app/src/main/java/com/example/baocaosuco/resident/photo/`  
**Status:** `CODE_READY / FILE_PROVIDER_CONFIG_BLOCKED / ANDROID_BUILD_NOT_RUN / DEVICE_NOT_RUN` as of this handoff.  
**Plan:** [C01 3-day execution plan](../planning/C01_CAMERA_LOCAL_3DAY_EXECUTION_PLAN.md).  
**UI dependency:** [#59](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/59) Phúc XML/ID manifest.

## Delivered implementation (G1 only)

1. `IncidentPhotoController.java` — AndroidX Activity Result `TakePicture` for one full-resolution Camera file, `PickVisualMedia(ImageOnly)` for one library image, `Listener.onSelected(uri, source)`, `onCancelled(source)`, `onError(source, reason)`.
2. `IncidentPhotoRequestState.java` — pure Java single-flight gate; blocks repeated taps, wrong/duplicate callback, results after `close()`.
3. `IncidentPhotoRequestStateTest.java` — 8 JUnit4 tests for gate transitions (source authored; not claimed PASS without run).

Successful Camera output is a FileProvider `content://` pointing to an app-private temporary JPEG file. Gallery produces a temporary read-only picker URI; it MUST be copied into app-private storage by G2 before reliance beyond the current result callback. G1 does **not** validate image bytes, compress or persist any media, attach to Firestore or provide a new UI screen.

## How Phúc/B01 can bind it later (illustrative Java, no fabricated XML IDs)

Create the controller **unconditionally in `Activity.onCreate` before STARTED**, in a consistent registration order. Existing authentication/RESIDENT role check must succeed **before buttons can invoke** `capture()` or `pickFromGallery()`. Never create the controller inside an asynchronous Auth callback after the Activity is STARTED.

```java
private IncidentPhotoController photoController;

// Inside Activity.onCreate, before the Activity reaches STARTED:
photoController = new IncidentPhotoController(this, new IncidentPhotoController.Listener() {
    @Override public void onSelected(Uri uri, IncidentPhotoController.Source source) {
        // B01: show preview of uri; G2 will copy/process photo before save.
    }
    @Override public void onCancelled(IncidentPhotoController.Source source) {
        // B01: preserve previous selected image (if any).
    }
    @Override public void onError(IncidentPhotoController.Source source,
                                  IncidentPhotoController.Error reason) {
        // B01: show a user-friendly error (not technical path/exception).
    }
});
// AFTER the RESIDENT role gate, bind using real View IDs from Phúc:
// cameraButton.setOnClickListener(v -> photoController.capture());
// galleryButton.setOnClickListener(v -> photoController.pickFromGallery());
// On logout/onDestroy: photoController.close();
```

`capture()` and `pickFromGallery()` return `false` if controller closed/another request in flight; the caller should disable repeat taps. `close()` invalidates callbacks and releases launchers. G3 still needs rotation/process-death testing; no claim lifecycle complete.

## Shared Android config needed: PROPOSAL ONLY / NOT COMMITTED

No permission to edit shared `AndroidManifest.xml` was recorded for this task. The Java implementation intentionally leaves Camera runtime blocked with `PROVIDER_NOT_CONFIGURED` until a task-specific L2 approval exists. After human approval, add only the following scoped provider inside the existing `<application>` element:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.incidentphoto.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/incident_photo_paths" />
</provider>
```

The required NEW `app/src/main/res/xml/incident_photo_paths.xml` file should contain:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="incident_camera" path="incident_camera/" />
</paths>
```

**Review gate:** exact authority must equal runtime package `com.example.baocaosuco.incidentphoto.fileprovider`; no `external-path`, broad `cache-path="."`, `android:exported="true"`, extra Camera permission or dependency. Check AndroidX camera URI grant behavior on the target emulator/physical device; document any failure rather than weakening provider security.

## G1 test/acceptance checklist

| Case | Requirement | Current evidence |
|---|---|---|
| G1-JVM-01 | Single-flight and close handling | 8 JUnit test methods authored; actual Gradle result `NOT_RUN` |
| G1-ANDROID-01 | Camera returns readable image URI | `BLOCKED_CONFIG` until reviewed FileProvider; emulator `NOT_RUN` |
| G1-ANDROID-02 | Gallery selection returns `content://` | Code ready; emulator `NOT_RUN` |
| G1-ANDROID-03 | Camera/Picker cancel, no crash | Code branches added; emulator `NOT_RUN` |
| G1-ANDROID-04 | No stale callback on logout/destroy | Guard implemented; Android lifecycle tests `NOT_RUN` |
| G1-SAFETY-01 | No Auth/LOG/Manager/Data/Firebase/Phúc XML edits | Verify final PR changed-file list before merge |
| G1-SEC-01 | No new permission/library/cloud deploy | Verify Manifest/Gradle and security diff |

**Completion policy:** G1 can be marked `JAVA_CODE_READY` at this checkpoint, **not `G1_PASS`**. After L2 approval, isolated FileProvider patch, Gradle test/build, and manual Camera+Gallery on emulator are required. G2 validation/compression/local copy and G3 final test/PR remain separate deliverables.

## Known limitations and next action

- FileProvider Manifest/XML L2 authorization is pending; neither file has been edited.
- There is no XML B01 view on main yet, so no user-facing button is wired.
- Pending in-flight Camera capture is not restored across Activity recreation/process death; G3 must address or document as blocker before application acceptance.
- Successful captured cache image is temporarily owned by the consumer; G2 will add deliberate lifecycle/cleanup after processing.
- No live Android build, JUnit, device, Cloud or E2E run should be inferred from committed Java files.

**Next:** obtain explicit L2 approval, implement only the two minimal FileProvider configuration files, then run Gradle and emulator smoke. Do not merge to main before independent review.

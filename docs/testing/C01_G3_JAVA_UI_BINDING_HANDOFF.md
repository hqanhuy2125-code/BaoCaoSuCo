# C01 G3 — Java UI hookup (waiting for actual B01 XML)

**Owner:** Trần Quốc Huy (Resident Java / Technical Integration).  
**Source:** PR #62, `resident/photo/**`. **No B01 UI XML or R.id invented.**  
**Status:** Java binder/Lifecycle implementation in code; CI/Android device acceptance reported in PR; end-user B01 binding **BLOCKED on Phúc's #59 XML handoff**.

## Available Java pieces

- `IncidentPhotoUiBridge`: Activity Result registration; `capture()`, `pickFromGallery()`, `clearSelected()`, `currentPhoto()`, `close()`. Uses local JPEG processing asynchronously. Does **not** attach to Firestore.
- `IncidentPhotoViewBinder`: accepts existing actual `View` references (Camera/Gallery/delete, `ImageView` preview, optional busy indicator), wires clicks, shows preview, keeps all actions disabled until RESIDENT role verified, reports errors to the host. **No duplicate B01 layout or R.id assumptions.**
- `IncidentPhotoController` and bridge preserve an in-flight Camera URI and prepared preview during normal Activity configuration recreation via `SavedStateRegistry`. Close on `onDestroy()` cleans temp files on real finish. An in-progress image *processing* worker is cancelled during recreation: user may reselect; process death/cache eviction still must be tested on device.

## Wiring pattern — to apply only after Phúc supplies exact XML IDs

Create the listener/binder after `setContentView`, create the bridge in `onCreate` after `super.onCreate`, **before Activity STARTED**. The actual `findViewById` IDs must be taken from Phúc's approved B01 XML PR; do not copy fake IDs below.

```java
// Java pseudocode: pass REAL view objects from Phúc's XML (not placeholder R.id).
IncidentPhotoViewBinder binder = new IncidentPhotoViewBinder(
        cameraView, galleryView, removeView, previewImageView, loadingView,
        new IncidentPhotoViewBinder.ErrorHandler() {
            @Override public void onPickerError(IncidentPhotoController.Source s,
                                                IncidentPhotoController.Error e) {
                // Show B01 error state / Toast in the already-owned screen.
            }
            @Override public void onProcessingError(IncidentPhotoProcessor.Failure e) {
                // Show B01 error state / Toast.
            }
        });
IncidentPhotoUiBridge bridge = new IncidentPhotoUiBridge(this, binder);
binder.attach(bridge);
// Only after Resident Auth callback validates current user's RESIDENT role:
binder.setResidentVerified(true);
// On logout/onDestroy: bridge.close().
```

**Note:** camera view actions are disabled while Auth is still pending or wrong role; never treat that client gate as a replacement for Firebase Rules. `currentPhoto()` provides restored preview. A local cache URI is **not** an incident attachment and may be evicted; submitting the existing B01 text report does not upload the photo.

## Phúc handoff required (#59)

Send actual B01 XML path/PR and exact IDs for Camera button, Gallery button, image preview, delete button and optional busy/error indicator. Preserve existing `resident_main_*` IDs; do not edit another owner's file in this C01 branch. A single B01 layout handoff is enough to unblock C01 (no need to wait for all B02–B05).

## Minimal acceptance (not replaceable by JUnit)

Run BaoCaoSuCo with the PR/merged SHA on Android emulator or device, and check **Camera capture / Camera cancel / Gallery select / Gallery cancel**, plus one rotation case during in-flight capture. Record device/API, source SHA, observed URI preview, crash logs, PASS/FAIL. **CI compile and JUnit alone do not prove Camera works on Android.** Independent reviewer must approve; PR #62 should remain Draft until these gates.

## Scope boundaries

Do not add Firebase Storage, imageUrl/schema, CameraX, Camera permissions, XML screens, Auth/LOG/Manager modifications or duplicate test workflows. Finish this one Java binding change set and leave B01 real Activity hook for when the UI owner delivers actual XML.

# Task preparation — Resident module (Member 2)

**Status:** `READY_FOR_UI_PLANNING`, **NOT READY FOR LIVE FIRESTORE WRITES**. No owner name is assumed and no source modifications are authorized by this document.

## Scope from current project plan

- Report form: title, description, location, priority, missing-field validation.
- Own report list and current status.
- Incident detail with timeline/history.
- Completed report action: confirm `Đã xong` or `Chưa xong`, only for the original resident.
- Image attachment **optional, last**.

## Before opening an implementation task

- [ ] Confirm assigned member, reviewer and feature branch from approved baseline.
- [ ] Agree `Incident`, `History`, enum and report-input types.
- [ ] Confirm shared `IncidentRepository` API and mock response/error format.
- [ ] Confirm who owns navigation and how to obtain current Firebase UID.
- [ ] Agree UI labels/layout naming and status display.
- [ ] Identify secure backend path for `COMPLETED → CLOSED` or `IN_PROGRESS` and history creation.
- [ ] Mark allowed paths and leave `data/`, `common/`, `AndroidManifest.xml` and build files protected.

## Suggested small tasks

`RES-01` UI mock list; `RES-02` report form + validation; `RES-03` detail + history timeline; `RES-04` approved repository integration; `RES-05` confirmation workflow + security tests; `RES-06` image (optional).

## Validation examples

- Empty title/description/location shows field-specific feedback, no write.
- A successful report is visible only to its creator.
- `COMPLETED` action hidden for other statuses; unauthorized direct Firestore request rejected by Rules.
- `Chưa xong` returns incident to `IN_PROGRESS`, adds one correct history event and survives app reload.
- Loading, failure, offline and duplicate-submit behavior explicitly handled.

## Never self-define

Collection paths, role bootstrap, due-date algorithm, terminal status, shared error types, Firebase Rules, or manager permissions. Raise contract proposals instead.

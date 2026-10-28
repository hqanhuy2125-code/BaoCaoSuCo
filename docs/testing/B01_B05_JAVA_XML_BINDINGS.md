# B01–B05 — Java integration contract for Phúc's XML (pending UI PR)

**Implementation branch baseline:** `main@f2d9418da0957c0b5bb7aeaa0fe0e84000f40e9f`. **Current verified main:** `36ede21d4c3b67c6af6e9398c3e1a0a4030aa0fc` after **PR #56 MERGED** with eligible reviewer approval. The Firebase live backend smoke tests were run at the #56 tested head; this PR does not fake backend data. PR #58 (A08 error mapping) is a separate review-gated Repository change, not yet merged.

**Ownership:** Huy may edit `resident/**`, `manager/**` Java and their unit tests; **Phúc owns `app/src/main/res/layout/**` and appearance**. Do not independently change `auth/**` (Hà), `logic/**` (Hưng), `data/**` contracts or Firestore Rules/indexes. No XML changes in this Java PR.

## Java coordinator entry points

Both coordinators consume the REAL split interface from `new FirebaseIncidentRepository()`, *not* mock previews:

```java
ResidentIncidentCoordinator coordinator = new ResidentIncidentCoordinator(
    new FirebaseIncidentRepository().resident(), residentView);

ManagerIncidentCoordinator managerCoordinator = new ManagerIncidentCoordinator(
    new FirebaseIncidentRepository().manager(), managerView);
```

Activity/View implementation must call `close()` in `onDestroy()` to ignore late callbacks. Instantiate only **after role-gated Auth** (existing `ResidentMainActivity` / `ManagerMainActivity` already perform this check). Disable submit/buttons on `onBusy(true)`; re-enable on `onBusy(false)`. Do not invent a duplicate Firestore client or write role/status from XML.

| B task | XML owner: Phúc | Java coordinator calls | What the View displays |
|---|---|---|---|
| B01 | `resident_report_form.xml` **not yet in main** | `ResidentIncidentCoordinator.submit(title,description,location,priority)` | Required fields title (120), description (2000), location (160), priority `URGENT/NORMAL/LOW`; loading, validation, saved/error |
| B02 | `resident_main.xml` (existing shell), `resident_incident_item.xml`, `resident_incident_detail.xml`, `resident_history_item.xml` (three missing) | `loadList()`, `loadDetail(id)` | Signed-in Resident's 20 newest incidents, full detail and chronological bounded history; loading/empty/error; no cross-owner data |
| B03 | `manager_main.xml` (existing shell), `manager_incident_item.xml` (missing) | `ManagerIncidentCoordinator.loadList()`, `setStatusFilter("ALL"/status)`, `setDueDateFirst(bool)`, `isOverdue(row,nowMillis)` | Up to 50 server-fetched incidents. **Filtering and due-date sort only within loaded page**, never claim global totals; overdue indicator on active statuses |
| B04 | `manager_incident_detail.xml`, optionally `manager_assign_dialog.xml` (missing) | `select(incidentId)`, `assign(observed,handler)`, `start(observed)`, `complete(observed)` | Selected snapshot from loaded page and one valid action at a time. Manager-specific history/get-by-ID are **not in locked Repository v1** and must not be simulated |
| B05 | Resident detail XML (missing) | `ResidentIncidentCoordinator.confirm(observed,resolved)` only when COMPLETED | “Đã xong” → CLOSED, “Chưa xong” → IN_PROGRESS; actual server status/revision returned |

**Critical IDs already existing in main, keep unchanged**: `resident_main_title`, `resident_main_greeting`, `resident_main_btn_logout`, `manager_main_title`, `manager_main_greeting`, `manager_main_btn_logout`.

**New IDs** must be agreed with Phúc's actual XML before wiring Java Activities. No unresolved `R.id.*` references have been added. Test adapters use injected repository interfaces; they are NOT proof of a human UI tap and do NOT complete B01–B05 alone.

### Errors and refresh
Use `onError(IncidentRepository.Error)` to distinguish AUTH_REQUIRED, PERMISSION_DENIED, VALIDATION, NOT_FOUND, CONFLICT, NETWORK, SECURITY_NOT_READY, UNKNOWN. For `CONFLICT`, reload the incident/list and allow user to retry with a **fresh observed snapshot**. Do not silently retry transitions with stale revision. Once `onSaved` returns, refresh the list/detail/history from server; neither coordinator fabricates a history event. After logout or Activity destruction, `close()`.

### Safe merge order / acceptance gates
1. PR #56 has **MERGED** to verified `main@36ede21d4c3b67c6af6e9398c3e1a0a4030aa0fc` under GitHub approval. Do not rerun used live-signup fixtures.
2. PR #58 is separately under review. Once its changes are merged, reconcile this coordinator PR against the updated `main`, re-run CI and obtain eligible human review before merge.
3. Phúc sends actual XML PR with matching IDs. Huy connects existing Activities to the Java View callbacks in a **separate non-overlapping PR**. Do not edit or duplicate Phúc XML.
4. B07: on authorized Android physical device with actual `app/google-services.json` (ignored), login Resident, report, Manager login/assign/start/complete, Resident confirm/reopen, check persisted history. Capture Android SDK37 build, screenshots/video and redacted device logs. Not yet tested.

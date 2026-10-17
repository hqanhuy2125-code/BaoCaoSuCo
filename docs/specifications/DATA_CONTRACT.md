# Data contract v0 — **PROPOSED, NOT FROZEN**

**Owner to assign:** integration/data lead + module owners 2, 3, 4. No one should implement production Firestore writes against this draft before acceptance. Existing `docs/PROJECT_SCOPE.md` names `users`, `incidents`, `history` but does not define exact field schema.

## Proposed Firebase collections

### `users/{uid}`

| Field | Firestore type | Required | Note |
|---|---|---|---|
| `displayName` | string | yes | Name shown in UI |
| `email` | string | yes | Match verified account as appropriate |
| `role` | string | yes | `RESIDENT` or `MANAGER` (approval pending) |
| `createdAt` | timestamp | yes | Initial signup time |

Document ID = Firebase Auth UID. **Client signup must not self-assign MANAGER**; manager provisioning/review is a separate trusted process.

### `incidents/{incidentId}`

| Field | Firestore type | Required | Note |
|---|---|---|---|
| `residentId` | string | yes | Auth UID of reporter |
| `title` | string | yes | Nonblank, length limit to decide |
| `description` | string | yes | Nonblank, limit to decide |
| `location` | string | yes | Room/building; controlled form or text to decide |
| `priority` | string | yes | `URGENT`, `NORMAL`, `LOW` proposed |
| `status` | string | yes | `NEW`, `ASSIGNED`, `IN_PROGRESS`, `COMPLETED`, `CLOSED` proposed |
| `assignedTo` | string or null | yes | Display name, not account identity in MVP |
| `createdAt` | timestamp | yes | Must have consistent authoritative source |
| `dueAt` | timestamp | yes | Determined by agreed priority rule |
| `updatedAt` | timestamp | yes | Last approved change |
| `imageUrl` | string or null | no | Optional image, no Storage in MVP |

ID = document ID. Avoid also storing a separate inconsistent `id` field unless Android serialization requires it. Choose maximum text lengths, null conventions, ordering/indexes and Firestore timestamps before freeze.

### History — decision required

**Option A (recommended for incident timeline):** `incidents/{incidentId}/history/{eventId}` with fields `fromStatus`, `toStatus`, `actorId`, `createdAt`, `note`.

**Option B:** top-level `history/{eventId}` with `incidentId` field (closer to initial planning document). The group must pick exactly one, document query/index implications and apply authorization rules consistently.

## Invariants to approve

- `residentId`, `createdAt`, initial `status=NEW` immutable after creation.
- Residents can list/read only their reports; managers can list/read all reports.
- State changes occur through controlled operation; history is append-only and consistent with incident state.
- Deadline computed from one agreed table and time basis; do not trust arbitrary client-supplied `dueAt` when privilege matters.
- No raw string variations across modules; enum/constant mapping shared from `common/`.

## Example document (illustrative only)

```json
{
  "residentId": "uid_demo_001",
  "title": "Den hanh lang bi hong",
  "description": "Den tang 2 khong sang",
  "location": "Tang 2 - toa A",
  "priority": "NORMAL",
  "status": "NEW",
  "assignedTo": null,
  "createdAt": "<Firestore Timestamp>",
  "dueAt": "<Firestore Timestamp>",
  "updatedAt": "<Firestore Timestamp>",
  "imageUrl": null
}
```

Date strings above are placeholders, not actual serialized Firestore timestamp data. Use fake/demo identities, never real resident private information.

## Freeze checklist

[ ] Collection paths and history shape approved; [ ] enum names/types; [ ] allowed/required fields and validation limits; [ ] role provisioning; [ ] security model; [ ] timestamp/priority due calculation; [ ] indexes; [ ] sample seed data; [ ] owner/version/decision log entry.

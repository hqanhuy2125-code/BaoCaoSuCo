# CON-01 — Data Contract v1 review candidate (24/10/2026)

**Status: DRAFT_FOR_REVIEW / NOT FROZEN.** Owner coordinating review: Trần Quốc Huy (acting technical integration lead). Affected implementing owners for shared `data/**`, Manager Java and Firestore Security Rules remain unassigned. This document does not replace `DATA_CONTRACT.md` until accepted through a reviewed PR and decision record.

## Sources and boundaries

- Scope: `docs/PROJECT_SCOPE.md` (resident submits; manager assigns/progresses; resident confirms or rejects; 5 statuses).
- Existing proposed contract: `docs/specifications/DATA_CONTRACT.md` (v0, not frozen).
- Implemented code (main SHA `bca86b786804068b60646e30aa3e32a8206d8a89`): `FirebaseAuthRepository`, `User.java`, `firestore.rules`.
- Must **not** modify existing Auth profile shape implicitly. No client incident writes until SEC-02 Rules tests and schema approval.

## Collection A — `users/{uid}` (observed existing implementation)

| Field | Firestore type | Observed implementation | Contract v1 recommendation |
|---|---|---|---|
| `uid` | string | Written by Auth; enforced by Rules = authenticated UID | **Keep** as required; also document ID = same UID |
| `displayName` | string | Written by Auth; owner may update only this field | Required |
| `email` | string | From Firebase Auth; Rules compare with token at creation | Required, immutable from Android client |
| `role` | string | Defaults to `RESIDENT` at signup; `MANAGER` recognized by app | Allowed `RESIDENT` / `MANAGER`; trusted provisioning only |
| `createdAt` | timestamp | `FieldValue.serverTimestamp()` at signup | Required, immutable after creation |

**Observed mismatch:** v0 contract omits `uid`; repository and Rules include it. Preserve source behavior while agreeing on the canonical v1 contract; do not replace user profile records or rewrite existing Auth.

## Collection B — `incidents/{incidentId}` (PROPOSED, not yet implemented)

| Field | Proposed Firestore type | Required | Proposed invariants / semantics |
|---|---|---|---|
| Document ID | auto-generated string | yes | Canonical incident identifier; avoid redundant mutable `id` field |
| `residentId` | string | yes | Equals current Firebase Auth UID on create, immutable |
| `title` | string | yes | Nonblank; recommended maximum **120 chars** (decision needed) |
| `description` | string | yes | Nonblank; recommended maximum **2000 chars** (decision needed) |
| `location` | string | yes | Nonblank; recommended maximum **160 chars** (decision needed) |
| `priority` | string | yes | Candidate enum: `URGENT`, `NORMAL`, `LOW`; immutable after creation for resident |
| `status` | string | yes | Candidate enum: `NEW`, `ASSIGNED`, `IN_PROGRESS`, `COMPLETED`, `CLOSED`; initial `NEW` |
| `assignedTo` | string or null | yes | Initially null; manager assigns handler display name, not a third account role |
| `createdAt` | timestamp | yes | Trusted source required; immutable |
| `dueAt` | timestamp | yes | Computed from confirmed priority SLA; cannot trust arbitrary client deadline |
| `updatedAt` | timestamp | yes | Set during authorized modifications |
| `imageUrl` | string or null | optional | Excluded from MVP write path; no Cloud Storage/billing changes |

Proposed string lengths above are **review candidates**, not course-provided facts. Final decision must define trimming, whitespace, validation error messages, offline behavior and whether `imageUrl` is omitted or null.

## Collection C — incident history (decision required)

- **Candidate A (recommendation, not approved):** `incidents/{incidentId}/history/{eventId}`.
- Candidate B: top-level `history/{eventId}` including `incidentId`.
- Proposed fields: `fromStatus` (string), `toStatus` (string), `actorId` (Firebase UID), `createdAt` (trusted timestamp), `note` (string or null).
- Events append-only; no regular Android update/delete of history; deny fabricated actor IDs and event sequences.
- Creation of state transition and history event must be consistent/atomic (transaction/batch and Rules checks where enforceable, otherwise trusted backend). **Do not assume client batch alone provides security.**

## Query/index candidates, not final

- Resident list: `residentId == currentUid`, ordered newest first (`createdAt` descending). Verify required composite index and access-control behavior.
- Manager list: optional filter `status`, ordering by priority/due date. Define stable sort using approved priority order (lexical enum sorting is insufficient) and determine index strategy.
- History: order `createdAt` ascending per incident. Handle equal timestamps deterministically.
- Decide limits/pagination and listener re-subscription before implementation; avoid unbounded reads.

## Freeze gates — all required

- [ ] DEC-004 backend and DEC-005 role assumptions reconciled with identified approver
- [ ] Collection paths, field types, required/null/optional and max length decisions recorded
- [ ] History storage choice and auth checks decided
- [ ] Priority SLA table, authoritative time, dueAt mutation policy decided
- [ ] Per-field writer permissions and history/incident atomicity approved with SEC-01/SEC-02
- [ ] Index/query strategy and privacy tests reviewed
- [ ] Shared model/repository owners and impacted module reviewers identified
- [ ] Updated `DATA_CONTRACT.md` and `DECISION_LOG.md` merged through approved PR

**Do not label v1 FROZEN or implement production incident writes until every gate has evidence.**

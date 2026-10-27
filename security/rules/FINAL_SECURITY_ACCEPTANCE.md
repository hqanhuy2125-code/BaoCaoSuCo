# SEC-02B — Final security acceptance evidence and release gates

**Candidate-only review document, not production authorization.**
Baseline: PR #49 `b16c76c97d1c6d929a924c9d4449fdc215334375`.
Task: Issue #45; GOV-02 Issue #9.
No modifications to production `firestore.rules`, Firebase Console, Android source, user roles, billing, or deployment.

## Gates and what would actually qualify

| Gate | Observable evidence | Threshold / verdict rule |
|---|---|---|
| Regression | Firebase Emulator `npm test` at exact SHA | 24 baseline + 14 read-only + 28 write-pilot + 23 FIX01 + 16 FINAL; all passing with no disabled assertions |
| Budget | Single paired CREATE and UPDATE plus stress batches 1/2/5/10; Rules review | Calls have **10 per operation / 20 aggregate** cap; 10-pair batch must reject atomically; no hand-waved call count from the coverage report |
| Atomic concurrency | Two different request IDs racing same root revision and real JS transaction read/retry | One winner; loser conflict or denied; one persisted history event with matching revision/lastEventId; only a **prototype**, not Android adapter |
| Replay | Repeat same event ID, stale revision, forged actor | Denied without a second event or incident mutation; idempotent-success behavior of a future Repository is separately unproven |
| Indexes | `firestore.indexes.candidate.json`, schema/query tests | Resident `residentId ASC, createdAt DESC` composite; Manager `createdAt DESC` single-field default; nested History `createdAt ASC` with document-ID tie-break; production index existence/READY status **not proven by Emulator** |
| Security & governance | Named independent review, Firebase project owner, group-lead/L3 approval | **No production/security signoff until actual human approvals are linked** |

### Budget interpretation

Cloud Firestore Rules limits are documented as 10 `get/exists/getAfter` access calls **per operation** and 20 **per batch/transaction**. Cached calls may not count. Emulator allows/denies and `ruleCoverage` expression evaluations **are not an exact per-request uncached access-call meter**. Do not publish an invented count or translate number of expression evaluations into paid document reads.

The expected legitimate write unit is **one incident root + one matching nested history event**, a two-operation transaction/batch. For a conservative source-review count **without relying on cache**:
- CREATE root: protected user profile `get` (1) + history `exists` (1) + `existsAfter` (1) + `getAfter` (1) = **at most 4** on the success path.
- CREATE history: root `existsAfter` (1) + `getAfter` (1) + root `exists` (1) + profile `get` (1) = **at most 4** on the success path.
- Transition root: role profile `get` (1) + event `exists` (1) + `existsAfter` (1) + `getAfter` (1) = **at most 4** on the success path.
- Transition history: root `exists` (1) + `get` (1) + `getAfter` (1) + role profile `get` (1) = **at most 4** on the success path.

**Review-derived upper bound for one paired write: 4 calls per operation and 8 aggregate** before caching, on the known successful policy path; this is a manual upper-bound analysis **not a measured runtime call count**. For arbitrary many pairs, do not multiply these bounds and certify success; actual caching and aggregate limits depend on evaluated paths, and large batches can fail. If exact actual uncached counts are mandatory, this gate remains **OPEN** pending instrumented/request-level evidence that explicitly exposes such a count.

Official limits: https://firebase.google.com/docs/firestore/security/rules-conditions
Emulator limitations (indexes/transactions/limits): https://firebase.google.com/docs/emulator-suite/connect_firestore

### Index scope

The candidate index manifest in this directory is a **non-deployable design input**. It was not added to Firebase's production `firestore.json`, registered in Console or deployed. Emulator does not track composite indexes for Native Firestore. The only credible cloud release index proof is a **named Firebase owner** reporting project/database index definition and READY status, with an authorized integration query against the intended environment. Do not use a production write as an informal test.

History queries must specify `createdAt ASC` and `limit(50)`. Deterministic tie-break is document ID (`__name__`) ascending; no top-level history reads.

### GOV-02 independent ownership and role provisioning

Current governance files under `docs/governance/` record:
- **Trần Quốc Huy:** integration custodian, Resident Java module coordinator. This is **not** independent approval or Firebase Console L3 authority.
- **Java Manager implementer/reviewer:** UNASSIGNED.
- **`data/**` shared-code implementer + independent reviewer:** UNASSIGNED.
- **Firestore candidate Rules/indexes implementer:** work exists under PR #48/#49, but formal L3 author authorization and independent security reviewer: UNASSIGNED.
- **Firebase Console project/database owner:** UNASSIGNED (no verified owner/account permission evidence).
- **Protected Manager role grant actor:** UNASSIGNED; must be trusted administrator only, never client-side `users.role` edit; review/audit procedures pending.
- **Group lead or explicitly delegated L3 approver:** UNASSIGNED; cannot infer identity from GitHub account, module custodian status, or this chat.
- **Branch protection/required reviews:** still PENDING_DECISION.

**Mandatory named independent signoff, all with links:** (1) Rules/index author or delegated implementer; (2) a different security reviewer who reviews actual candidate diff, access boundaries, budget and failure paths; (3) account owner with authorized ability to confirm Firestore indexes and grant MANAGER through a trusted audited procedure; (4) group lead/L3 delegate confirming production deployment scope, rollback and approval; (5) shared-code reviewer for CORE-02/REPO-02 as separate L2 gate.

Approval evidence template:

```text
AREA:
AUTHOR / GITHUB USERNAME:
INDEPENDENT REVIEWER / GITHUB USERNAME:
FIREBASE PROJECT OWNER (for Console/indexes/role):
MANAGER ROLE GRANT METHOD (trusted server/Admin SDK/approved manual admin):
GROUP LEAD / L3 APPROVER:
ALLOWED_PATHS:
PROTECTED_PATHS:
REVIEW PR + EXACT SHA:
INDEX STATUS EVIDENCE URL:
ROLE PROVISIONING/AUDIT EVIDENCE URL:
APPROVAL EVIDENCE URL:
DECISION DATE:
```

**Prohibited shortcuts:** mock `MANAGER` roles in Emulator are only fixtures; no client-set role, unchecked UI flag, implicit owner promotion, project-wide unaudited Rules deploy, unsupported billing changes, self-review or fake group approval. Keep GOV-02 #9 OPEN and DEC-010 PENDING_DECISION until genuine evidence is received.

## Completion boundary

Once technical tests have passed, declare only `CANDIDATE_EMULATOR_REGRESSION_PASS`. Do not mark `FINAL_SECURITY_APPROVED`, merge #48/#49, enable Firestore writes, or implement live REPO-02 until independent/GOV-02/L3 and cloud index gates have actual proof. Emulator concurrency and the JS transaction prototype do **not** establish production retry semantics of the Android Repository.

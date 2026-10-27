# Firebase integration & security contract — CON-01/CON-03 technical MVP v1

**Current status:** `TECHNICAL_MVP_V1_LOCKED` for schema/interface definitions on 2026-10-27. **`L3_RULES_NOT_APPROVED`, `SEC02B_NOT_TESTED`, `PRODUCTION_INCIDENT_WRITES_DENIED`, `FORMAL_GOV02_SIGNOFF_PENDING`.** Decision details: [DATA_CONTRACT.md](DATA_CONTRACT.md), [ARCHITECTURE.md](ARCHITECTURE.md), `DECISION_LOG.md` DEC-015/DEC-016.

## Source-backed current Firebase integration

- Android `FirebaseAuthRepository` implements registration/login/current-user/signout and uses Firebase Auth email/password plus `users/{uid}` Firestore profile.
- Auth registration writes `uid`, `displayName`, `email`, `role=RESIDENT`, `createdAt=serverTimestamp()`. Existing `firestore.rules` allows owner profile reads and displayName-only changes, blocks self-assignment of `MANAGER`, and permits existing trusted Manager profile reads.
- The root `firestore.rules` has a catch-all `allow read, write: if false` for all other collections including `incidents` and nested history. **Do not deploy `security/rules/incidents.readonly.candidate.rules`**: it is a test-only fixture, not production Rules.
- Merged CORE-01 + SEC-02A POST-MERGE at `main@96eb03c65718087280a98548073e187f83781a5f`: local Windows 158/158 JUnit PASS, 38/38 Emulator test PASS on original deny-only/read-only candidate test fixtures. That does **not** test authorized incident/history write transactions or prove the deployed cloud Rules version.
- Firebase Auth + Firestore are technically used by the Android code, but `DEC-004` group administrative approval/Console/ownership remains separately pending. Do not infer Spark/Blaze or runtime cloud deployment from source.

## Selected technical MVP security model (not yet implemented)

- Only authenticated `RESIDENT` who is the `residentId` may create and read their own incident, and only the original owner may `CONFIRM_RESOLVED` / `REPORT_UNRESOLVED` from COMPLETED.
- Only authenticated, **trusted-provisioned** `MANAGER` may list/read all incidents and perform `NEW→ASSIGNED→IN_PROGRESS→COMPLETED` actions. The handler is a **display-name string**, not another authenticated account or technician role.
- Only canonical enums accepted. `status`, `assignedTo`, `residentId`, protected times, revision and history writer identity must be validated against the authoritative persisted state. Never accept client role/owner claims.
- `createdAt`, `updatedAt`, event `createdAt` are trusted server timestamps; deadline is **derived** from persisted creation time plus LOG SLA (4/72/168 hours), never independently user-written in Firestore.
- Every CREATE and status transition must have a matching **append-only nested history event** in the same atomic unit; event ID acts as the command idempotency key; incident `revision` and `lastEventId` bind the new history event. Event UPDATE/DELETE denied.
- **Release-critical proof is pending:** Rules `getAfter()` and transactions/batches support reading post-write state for coupled invariants, but do not automatically guarantee safe single-event authorization. SEC-02B must prove all invariants in the local Emulator within Rules access-call limits or redesign to a trusted backend. Never silently omit history or loosen role/owner checks.
- Strict `users/{uid}` profile behavior is preserved; Manager provisioning is only by a named trusted authorized operator using a separately reviewed process.

## Read and index shape

- Resident query must constrain `residentId == request.auth.uid`, sort by creation descending, and limit to 20.
- Manager list newest-first limit 50; Java sorting by priority/status/derived dueAt applies **within fetched page only**, not globally.
- History nested under one incident, chronological order and bounded `limit(50)`. Independent role/owner checks must hold on history reads even when incidentId is guessed.
- The needed composite index for owner + creation sort must be reviewed; no indexes are deployed or modified in this doc task.
- Firestore Rules are **not filters**. A query missing an owner constraint must be rejected even if the data currently stored belongs to the caller.

## Prerequisites before real Firestore incident access

1. CON-01/03 technical contract defined on main; changes thereafter follow versioned review.
2. Resolve actual L3 accountable security reviewer, Firebase Console permissions and Manager provisioning procedure through GOV-02 [#9](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/9).
3. SEC-02B [#45](https://github.com/hqanhuy2125-code/BaoCaoSuCo/issues/45) candidate Rules Emulator tests: authorized creation and five transitions, unauthorized and cross-owner access, time/field tampering, forged history, stale revision, replay, concurrency, query privacy and access budget. Evidence must have exact SHA, observed test counts and source diff.
4. REPO-02 scoped Android adapter + transactions, unit/emulator test evidence; only then propose explicit change to root `firestore.rules`, index deployment and production role grants with separate approval.
5. Validate full signed-in Resident→Manager→Resident report lifecycle on Android Emulator and a physical device. No push, image Storage, billing or real Firebase privileges are implied by a documentation merge.

**No production Rules, Auth, user profile, credential, index, Java or UI files are modified by PR #44.**

# Firebase integration & security contract

**Status:** `PROPOSED` — user favors shared cloud database for the Android app, but repository still marks Firebase Auth + Firestore `PROPOSED`. Formal team approval and console/project access are required.

## Selected candidate topology

Java/XML app → Firebase Authentication (email/password) → Cloud Firestore collections `users`, `incidents`, `history` (final path to decide). Firestore SDK can provide live listeners while app is running. No separate server is needed for basic data operations **if** Rules can safely enforce requirements.

## Source implementation checkpoint — 2026-10-24 (observed, not approval)

- Remote `main` at `bca86b786804068b60646e30aa3e32a8206d8a89` contains Firebase Auth SDK/Firestore dependencies, `AuthRepositories.USE_FIREBASE = true`, and `FirebaseAuthRepository` for email/password login, registration, session/profile lookup and logout.
- The profile implementation stores `uid`, `displayName`, `email`, `role`, `createdAt` at `users/{uid}`; registration assigns `RESIDENT`. `User.java` and `firestore.rules` agree on the `uid` field, while Data Contract v0 does not yet list it.
- Root `firestore.rules` contains client read/create/update constraints for `users` and a default-deny catch-all for other collections. **No allow rules for `incidents` or history** are implemented in this snapshot.
- PR #4 author reports selected Auth/Rules/build tests PASS. This is not independent evidence that rules were deployed, manager provisioning exists or incident operations pass E2E.
- `DEC-004` is still `PROPOSED` in `DECISION_LOG.md`; reconcile its human approval record separately rather than changing the decision label based solely on source merges.

## Security gate before production writes

- `request.auth != null` is necessary but **not sufficient**.
- Resident may create incident only with `residentId = request.auth.uid` and only allowed initial fields/status; list/read own incidents only.
- Manager may read all incidents and do only approved assignment/progress operations.
- Only owning resident may confirm completed incident, using only the permitted status changes.
- Role claims must come from a trusted source. Never let a client set its own `users/{uid}.role` to `MANAGER`. Decide protected user document provisioning or verified custom claims.
- History must be immutable/append-only to users, and coordinated atomically with status changes. Direct Android-side transitions plus separate history writes are **not** assumed secure until Rules are reviewed and tested.
- Reject changes to protected fields (`residentId`, original creation time, system-owned due time) and invalid enum strings.

## Integration responsibilities

| Asset | Responsible role | Gate |
|---|---|---|
| Firebase project console / members / billing | Group lead | Decide Spark vs Blaze, least-privilege access |
| Android Firebase setup and SDK | Integration owner | Feature request + PR and build |
| `google-services.json` handling | Integration owner | Validate package, avoid secrets and service accounts; set team policy |
| Firestore Rules and indexes | Assigned data/security owner | Emulator tests and approval |
| Auth flow/role bootstrap | Integration + data owner | Confirm manager provisioning |
| Repository interface and mapping | Shared code owner | Confirm `DATA_CONTRACT.md` v1 |

## Cost/privacy constraints

- Prefer Spark for MVP Auth + Firestore with its usage quotas; monitor reads/writes and avoid unbounded listeners/queries.
- Cloud Storage for Firebase image uploads require eligible paid-plan billing. Photo uploads remain optional; **do not turn on billing without explicit authorization**.
- Never store passwords in Firestore; use Firebase Auth. Never publish service account keys or real personal info in fixtures/logs/screenshots.

## Definition of integration ready

[ ] Firebase product/project and billing choice confirmed; [ ] roles bootstrapped securely; [ ] schema and indexes accepted; [ ] rules reviewed; [ ] emulator rules tests PASS; [ ] Android smoke login/create/read/assign/confirm PASS; [ ] cross-role privacy tests PASS; [ ] usage and error/connection-handling documented.

This contract does not supply **approved runnable rules for `incidents`/history** because their collection shape and authorization mechanism are not yet confirmed. The repository does contain an implemented `firestore.rules` for `users`; its deployment state remains unverified. No insecure permissive incident rules are authorized.

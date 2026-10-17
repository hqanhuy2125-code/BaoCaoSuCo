# Scope, contract, and change approval

**Status:** `PROPOSED`. Keep requests lightweight; log significant changes in `DECISION_LOG.md`.

## Request must state

`ID`, proposer, motivation, affected modules/files, alternatives, impact on time/testing, rollback plan, approval owner, decision and decision date.

## Approval matrix

| Change | Review required |
|---|---|
| UI/internal implementation within approved module | Module owner, PR reviewer |
| Shared interface/model/enum/schema/index | Impacted module owners + integration owner |
| Roles, Firestore Security Rules, authentication, billing, data loss | Group lead; security/data reviewers |
| MVP scope/deadline/mandatory mobile feature | Group decision and instructor requirement check |
| Branch history rewrite, force push or destructive recovery | Affected owners + explicit group lead approval |

## Process

`PROPOSED → IMPACT_ASSESSED → APPROVED / REJECTED → IMPLEMENTED → VERIFIED` (these are change-request states, distinct from project decision labels).

- Do not change `CONFIRMED` baseline because an AI suggested another architecture.
- For blocked work, agree on a mock adapter or stub; do not invent a new backend contract.
- Emergency fixes: document reason and get minimum required approval; always follow with tests/PR.
- If change affects `DATA_CONTRACT.md`, `STATE_TRANSITIONS.md` or `FIREBASE_CONTRACT.md`, update dependent tests and the decision log in the same PR.

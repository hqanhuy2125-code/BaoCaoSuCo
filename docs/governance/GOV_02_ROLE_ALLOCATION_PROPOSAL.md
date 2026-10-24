# GOV-02 — Module boundaries and unassigned integration work (2026-10-24)

**Status: CORRECTED_SCOPE / PENDING_DECISION.** This revision supersedes GOV-02's earlier proposal to add unrelated work to Hà Quang Huy and Nguyễn Đức Hưng. It records an explicit correction from the acting Technical Lead / Resident owner in the 2026-10-24 conversation. **No additional module is assigned to either person.**

## Ownership boundaries

| Team member | Module / responsibilities in scope | Not assigned by GOV-02 |
|---|---|---|
| **Trần Quốc Huy (Member 2)** | Technical Lead / Integration Owner, **Resident Java**; custodian of shared paths/contracts and coordinated integration | Shared-code authoring, Manager Java, security deployment or L3 authorization are **not automatically assumed** from integration ownership |
| **Trần Huỳnh Hồng Phúc (Member 3)** | **UI/UX only**: screen designs, user experience; task-approved XML implementation when explicitly allocated | Manager Java, business logic, shared repository or Firestore Rules |
| **Hà Quang Huy (Member 1)** | **Own existing Auth/Firebase Authentication and user-profile module only**, as evidenced by merged PR #3/#4. Continuing feature work/maintenance requires a scoped task | **NOT ASSIGNED:** IncidentRepository, `data/**`, `common/**` incident models, Firestore incident/history Rules, Manager role provisioning or other teams' features |
| **Nguyễn Đức Hưng (Member 4)** | **Own earlier-assigned core logic module only**: state transition/deadline logic and related tests, subject to reconfirmation for new LOG tasks | **NOT ASSIGNED:** Manager Java, independent Rules security tests/reviews, shared Firebase repository or unrelated business modules |

**Clarification:** Previous GOV-02 PR/Issue discussion nominated Hưng for Manager Java and Hà Quang Huy for shared Repository and Rules. Those nominations were **incorrectly extended** beyond the members' scope and are **withdrawn**. Reviewers must not treat the older proposal or issue comment as accepted assignments.

## Path ownership and task boundaries

| Repo path / subsystem | Implementation owner | Technical integration / review | Gate |
|---|---|---|---|
| `app/src/main/java/com/example/baocaosuco/auth/**`, existing Auth-specific UI | Hà Quang Huy's Auth module (historical implementation; continuation task-specific) | Trần Quốc Huy as Integration Owner; actual reviewer assigned per PR | No cross-module feature additions by default |
| `app/src/main/java/com/example/baocaosuco/logic/**`, related unit tests | Nguyễn Đức Hưng, original LOG allocation (new task acceptance pending) | Trần Quốc Huy | Only state/deadline/core logic scope; contract changes reviewed separately |
| `app/src/main/java/com/example/baocaosuco/resident/**`, related Java tests | Trần Quốc Huy | Authorized independent reviewer on Huy-authored PR | RES task paths only |
| UI/UX designs; explicitly assigned layout XML | Trần Huỳnh Hồng Phúc | Integration + consuming module owner | May modify only task-approved XML/layouts, not Java modules |
| `app/src/main/java/com/example/baocaosuco/manager/**`, Manager Java tests | **UNASSIGNED** | Trần Quốc Huy coordinates integration | MGR-01/02/03 blocked until named implementer and reviewer |
| `app/src/main/java/com/example/baocaosuco/data/**`, `common/**`, IncidentRepository and adapters | **UNASSIGNED** | Trần Quốc Huy is custodian, not automatic author | CORE-01/REPO-01/02 blocked until contract, implementer, independent reviewer |
| Root `firestore.rules`, indexes, secure Manager provisioning and deployed access controls | **UNASSIGNED** | Trần Quốc Huy coordinates; independent security reviewer and L3 approver required | SEC-01/02 blocked; no role grants, permissive Rules, deployment or billing |
| Shared configuration / navigation / governance / contracts | Per-task named author; Trần Quốc Huy coordinates | Affected owners and independent reviewer if Huy writes | L2/L3 approval per `CHANGE_CONTROL.md` |

## Remaining decisions (GOV-02 cannot be closed yet)

- [ ] Name **one implementer and an independent reviewer** for Manager Java. Do not assign to Phúc or Hưng by inference.
- [ ] Name author and independent reviewer for shared `IncidentRepository` and incident data models. Do not assign to Hà Quang Huy by inference.
- [ ] Name Firestore Rules/security implementation author, independent security reviewer, and an authorized L3 approver. Technical review is not deployment approval.
- [ ] If additional work is intended for Trần Quốc Huy or AI-assisted implementation, authorize **explicitly by individual task**, bounded by a task's `ALLOWED_PATHS`, reviewer and tests; never enlarge his workload silently.
- [ ] Reconfirm task availability of Hà Quang Huy **only for Auth** and Nguyễn Đức Hưng **only for core logic**.
- [ ] Assign independent reviewer for Huy-authored L2/L3 PRs and decide GitHub branch protection/checks.
- [ ] Record actual approval/acceptance evidence for these slots in `DECISION_LOG.md` and module ownership policy through a reviewed PR before `DEC-010` is `CONFIRMED`.

## Contract and security guardrails

- PR #8 and Issues #10/#11/#12 remain **DRAFT / NOT FROZEN** pending schema, transitions, callback contracts and relevant reviewers.
- No incident/history production writes, Firebase Rules deployment, role escalation or billing changes until separately authorized and tested.
- Existing `MODULE_OWNERSHIP.md`, `CHANGE_CONTROL.md`, `WORKTREE_POLICY.md`, module source and unrelated teammate branches remain untouched by this document.
- One scoped task per branch/PR. No silent merge, overwrite or self-approval.

**GOV-02 status:** Correction documented; missing implementation and security-review roles are explicitly **UNASSIGNED / BLOCKED**, not assigned to other teammates.

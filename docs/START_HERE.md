# START HERE — Project governance and source of truth

**Purpose:** lightweight coordination for four Android students collaborating with AI tools. The current Android codebase is a starter shell, not an implemented incident system.

## Source hierarchy

- Course/instructor requirements and **decisions explicitly recorded by the group** take precedence.
- `docs/PROJECT_SCOPE.md` remains the existing scope baseline; `README.md`, `AGENT.md` and `docs/TEAM_WORKFLOW.md` are existing references.
- New documents in `docs/specifications/` propose concrete implementation contracts. **Do not infer that their examples are approved.**
- `docs/governance/DECISION_LOG.md` records approvals. When documents conflict, stop implementation in affected areas, log the conflict and resolve it with the designated decision owner; do not silently choose one.

## Decision labels

- `CONFIRMED`: documented acceptance by the group or the responsible approver.
- `PROPOSED`: concrete option for review, not permission to implement in shared code.
- `PENDING_DECISION`: question that blocks a dependent action.
- `OUT_OF_SCOPE`: excluded from the approved MVP.

## Document map

| Need | Read |
|---|---|
| Scope and assignments | `docs/PROJECT_SCOPE.md`, `docs/planning/DELIVERY_PLAN.md` |
| Tech and source structure | `docs/specifications/ARCHITECTURE.md` |
| Field names, enums, nullable values | `docs/specifications/DATA_CONTRACT.md` |
| Incident lifecycle and authorization | `docs/specifications/STATE_TRANSITIONS.md` |
| Firebase database/security approach | `docs/specifications/FIREBASE_CONTRACT.md` |
| Human/AI change limits | `AGENTS.md`, `docs/governance/DEVELOPMENT_GUARDRAILS.md` |
| Dirty tree, multiple worktrees | `docs/governance/WORKTREE_POLICY.md` |
| Module edit boundaries | `docs/governance/MODULE_OWNERSHIP.md` |
| Decisions and approvals | `docs/governance/DECISION_LOG.md`, `CHANGE_CONTROL.md` |
| Hand-off | `docs/governance/PROJECT_STATE.md`, `SESSION_HANDOFF_TEMPLATE.md` |
| Visual consistency | `docs/design/UI_GUIDELINES.md` |
| Test evidence | `docs/testing/TEST_CHECKLIST.md` |
| Resident module start | `docs/tasks/RESIDENT_MODULE_START.md` |

## Minimum approvals before parallel implementation

1. Backend choice and account ownership; data and security contract v1.
2. Feature package ownership and shared model/interface owners.
3. State transitions and who may perform them.
4. Repository branch/PR policy and initial working build evidence.
5. Timebox and definition of done.

No file in this overlay is evidence of a successful build, an accepted Firebase project, or a human sign-off.

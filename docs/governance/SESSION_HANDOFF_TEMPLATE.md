# AI / human session handoff template

Create one record per task in `docs/handoffs/<TASK_ID>_<YYYY-MM-DD>.md` **only if the task authorizes writing docs**. Otherwise paste this in the PR/task comment.

```text
TASK_ID / TITLE:
OWNER / REVIEWER:
SESSION DATE:
BASELINE BRANCH / SHA:
CURRENT BRANCH / SHA:
WORKTREE STATUS (tracked, staged, untracked, relevant ignored):
ALLOWED PATHS:
FILES CREATED / MODIFIED / DELETED:
SHARED/PROTECTED FILES TOUCHED (with approval link):
IMPLEMENTATION SUMMARY:
BUILD COMMAND + RESULT: PASS / FAIL / BLOCKED / NOT_RUN
TEST COMMAND + RESULT: PASS / FAIL / BLOCKED / NOT_RUN
MANUAL CHECKS (device/emulator + evidence):
OPEN RISKS / BLOCKERS:
DECISIONS OR CONTRACT CHANGES NEEDED:
COMMIT / PUSH / PR STATUS (never imply completed without evidence):
NEXT SAFE STEP:
```

Handoff is factual evidence, not permission to proceed beyond the current task. Preserve precise paths, relevant SHA references and error messages without exposing secrets.

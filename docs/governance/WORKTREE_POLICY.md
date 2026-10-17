# Git working tree & worktree policy

**Status:** `PROPOSED`. Protects local changes from both humans and AI agents.

## Preflight (read-only)

```bash
git status --short
git branch --show-current
git rev-parse HEAD
git diff --stat
git diff --cached --stat
git worktree list
```

Record outputs in the task/session note. Check untracked files and, when safe and relevant, ignored files (`git status --ignored --short`), taking care not to disclose secret paths/content.

## Baseline rule

- For new work, start from a reviewed commit on an authorized feature branch. Never assume the checkout is clean.
- Dirty files created by someone else: **do not modify, stage, move, stash, restore, or delete them**. Coordinate with their owner.
- If target files are dirty or protected, stop and request a safe baseline. If only unrelated files are dirty, do not include them in the task or stage them accidentally.
- To avoid simultaneous edits: **one active writer/agent per checkout**. Use separate Git worktrees for independent branches if the team knows how; otherwise separate clones are simpler.
- Separate worktrees still share Git repository refs. Coordinate branch, rebase, cherry-pick and ref operations. Do not work on the same branch from two active writers.

## Allowed workflow

1. Read and document the baseline SHA; get scope approval.
2. Create/change only allowed paths in your own feature branch.
3. Review `git diff --check`, `git status --short`, `git diff -- <allowed-path>`.
4. Stage **explicit paths only** (`git add path/to/file`), inspect staged diff, commit only if authorized.
5. Push feature branch when authorized, open PR, wait for reviewer; integration owner handles merge.

## Prohibited without task-specific explicit permission

`git reset --hard`, `git clean -fd`/`-fdx`, force push, destructive checkout/restore, automatic stash/pop, rebasing a shared branch, broad staging `git add .`, cherry-picking unknown commits, overwriting untracked files, changing default branch, deleting worktrees.

## Recovery procedure

- Capture current HEAD, status, branch/worktree list and diff before changes.
- Identify which files are owned by whom and what has been committed/backed up.
- Propose reversible options: create a rescue branch for committed work; make a **consensual** backup of uncommitted changes; isolate in a new checkout.
- Do not run recovery commands until the affected owner approves; record exact commands, expected effect and post-check.

## Postflight/handoff

Repeat preflight, list modified/untracked files, confirm no unexpected changes, state build/test evidence and whether changes are committed, pushed or only local.

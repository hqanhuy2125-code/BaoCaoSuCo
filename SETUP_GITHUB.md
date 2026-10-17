# Applying this governance pack safely

**This is a NEW-FILES-ONLY overlay** for `hqanhuy2125-code/BaoCaoSuCo`, inspected at `main` commit `694545e63e5afe02d39bea7ce8ed23227c3a3b47` on 2026-10-17. The pack intentionally does **not** modify any existing source or documentation.

## Recommended route: feature branch + PR (Windows PowerShell)

1. Clone or open the team's repository. Confirm you have contributor/write permission.
2. Inspect `git status --short`, `git branch --show-current`, `git rev-parse HEAD`, `git worktree list`. Save any unrelated local work with the affected owner's consent; **do not discard it**.
3. Review that the upstream files still match this baseline. If upstream changed, inspect conflicts and adjust the overlay instead of applying blindly.
4. Copy `BaoCaoSuCo_governance.patch` into the repository root (or replace its path in the command with the absolute location). On a clean checkout at the reviewed baseline:

```powershell
git switch -c docs/ai-governance-setup
# Run these commands from the repository root, with the patch copied here:
git apply --check .\BaoCaoSuCo_governance.patch
git apply .\BaoCaoSuCo_governance.patch
git diff --check
git status --short
```

5. Review proposed content and obtain group approvals for decisions in `docs/governance/DECISION_LOG.md`.
6. Stage **the new paths explicitly**, review staged diff, and commit/push the feature branch **only if authorized**. Open a PR into `main` for review; do not push directly to `main`.

If using the ZIP instead of the patch: inspect all ZIP paths first and copy **only files that don't already exist** into the repo. Do not use blind overwrite/extract on top of someone's checkout.

## Agent setup

- `AGENTS.md` is the conventional entry file for Codex-style agents; it points to existing `AGENT.md` and project docs.
- The new contract documents are **proposals** until signed off. Adding files does not grant AI permission to code or modify shared schema.
- This pack does not add Firebase SDK, change Android source, enable billing, configure rules, or run tests.

## If direct GitHub upload fails

Check which GitHub account is connected and whether it has **Write** permission in the repository. The GitHub repository owner can invite the appropriate account from Settings → Collaborators (menu naming may vary). Alternatively, an authorized contributor may apply the patch and open a PR. Do not publish to an unrelated repository or represent a local ZIP as a remote commit.

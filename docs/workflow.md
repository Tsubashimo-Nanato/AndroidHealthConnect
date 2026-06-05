# Workflow

## Branch Model

- `main` is the stable branch.
- Development happens on focused branches such as `feature/*`, `fix/*`, `test/*`, `docs/*`, or `refactor/*`.
- Historical tags are preserved as old project snapshots.
- `legacy/*` branches can point at historical tags to make those snapshots easier to inspect.
- `archaeology/*` branches can hold reconstructed review branches that compare one historical stage to the next.

## Commit Policy

Use small commits with clear scope:

- `docs: ...`
- `test: ...`
- `chore: ...`
- `refactor: ...`
- `fix: ...`
- `feat: ...`

Separate documentation, tests, behavior changes, and mechanical moves where practical.

## Pull Requests

When pushing and opening PRs is explicitly authorized:

- Open PRs into `main`.
- Include a concise summary.
- List test commands and results.
- Include screenshots or logs when UI, build, sync, or upload behavior changes.
- Link relevant issues.
- Keep PRs narrow enough to review.

## Tags

Tags should represent deliberate release milestones, not backup snapshots. The existing tags remain preserved for now as historical snapshots. Future backup needs should use normal commits, branches, exported archives, or external backups.

Do not delete or rename historical tags without a separate explicit backup/export plan and user approval.

## Local-Only Safety Rules

- Do not rewrite history for routine cleanup.
- Do not force-push.
- Do not delete remote branches.
- Do not delete, rename, or move existing tags during archaeology.
- Use read-only GitHub inspection unless an issue, PR, push, or release action is explicitly requested.

# Branch Map

## Existing Branches Before Archaeology

- Local branch: `main`
- Remote branches: `origin/main`, `origin/HEAD -> origin/main`
- Default branch: `main`

## Legacy Branches

These local branches point at existing tags. Tags were not moved or renamed.

| Branch | Points to | Purpose |
| --- | --- | --- |
| `legacy/stage-01-v0-1-0` | `v0.1.0` | inspect the first tagged monorepo stage |
| `legacy/stage-02-v0-1-1` | `v0.1.1` | inspect the second tagged stage |

## Reconstructed Transition Branches

| Branch | Base | Reconstruction target | Commit | Notes |
| --- | --- | --- | --- | --- |
| `archaeology/transition-01-v0-1-0-to-v0-1-1` | `v0.1.0` | `v0.1.1` | `reconstruct: v0.1.0 to v0.1.1` | initial strict `git apply --index` failed due patch application whitespace/line-ending mismatch; retry with `--ignore-whitespace --whitespace=nowarn` succeeded |

The transition branch is synthetic review scaffolding. It is not the original development history.

## Current Refactor Branch

| Branch | Base | Purpose |
| --- | --- | --- |
| `refactor/repository-organization` | `main` at `4a107ae` | current-code documentation, tests, and scoped refactor work |

## Safety Branch

No safety branch was required because the initial working tree was clean.

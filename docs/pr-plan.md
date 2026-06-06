# Local PR Plan

This plan prepares the current local work for review without pushing or opening PRs.

## PR 1: Repository archaeology documentation

- Branch name: `refactor/repository-organization`
- Purpose: add tag inventory, stage logs, transition notes, branch map, and workflow context.
- Files likely changed: `docs/archaeology/**`, `docs/workflow.md`, `docs/pr-plan.md`, `README.md`.
- Risk level: low.
- Test commands: documentation only; verify Markdown paths and branch names.
- Review notes: confirm that observed facts and inferred purpose are clearly separated.

## PR 2: Baseline tests and smoke tests

- Branch name: `test/baseline-characterization`
- Purpose: add characterization tests for current behavior before larger refactors.
- Files likely changed: Android JVM tests, future server test project.
- Risk level: low to medium.
- Test commands: `.\gradlew.bat :app:testDebugUnitTest --no-daemon`, `dotnet build --configuration Release`.
- Review notes: prioritize timezone grouping, upload validation, sync severity, and server security behavior.

## PR 3: Project structure cleanup

- Branch name: `chore/project-structure-cleanup`
- Purpose: clarify docs, templates, and low-risk organization without behavior changes.
- Files likely changed: `.github/**`, `docs/**`, possibly package/module boundaries.
- Risk level: low.
- Test commands: Android unit tests and server build.
- Review notes: avoid broad file moves unless imports and tests are straightforward.

## PR 4: Core/domain extraction

- Branch name: `refactor/core-domain-extraction`
- Purpose: extract pure policies from UI/query services into tested core modules.
- Files likely changed: `AndroidClient/app/src/main/java/.../hc/**`, tests under `app/src/test`.
- Risk level: medium.
- Test commands: Android unit tests, targeted new characterization tests.
- Review notes: do one responsibility at a time, especially around dates, chart windows, and aggregation.

## PR 5: Interface separation

- Branch name: `refactor/interface-separation`
- Purpose: keep Compose screens thin and route behavior through services or view-model-like adapters.
- Files likely changed: `AndroidClient/app/src/main/java/.../ui/**`, service/controller modules.
- Risk level: medium to high.
- Test commands: Android unit tests plus manual app smoke where possible.
- Review notes: preserve UI behavior and avoid bundling visual changes with logic moves.

## PR 6: Documentation and workflow finalization

- Branch name: `docs/workflow-finalization`
- Purpose: finalize README, architecture, testing, and contributor workflow after code changes settle.
- Files likely changed: `README.md`, `docs/**`, `.github/**`.
- Risk level: low.
- Test commands: documentation review plus latest build/test commands.
- Review notes: make sure docs match the actual final branch state.

## Issue and Risk Mapping

Read-only GitHub API inspection on 2026-06-06 returned no issues and no pull requests. The following workstreams are mapped from local code and archaeology findings instead of external issue numbers:

- Daily grouping timezone semantics: map to PR 2 and PR 4. Add tests first, then choose an explicit daily grouping contract.
- Local server API upload contract: current `main` includes `/health/api/v1/status` and `/health/api/v1/ingest/batches`; keep regression tests in PR 2.
- Aggregate sync coverage success semantics: current `main` has aggregate-error-aware severity policy; keep coverage tests in PR 2.
- API keys and sensitive EF logging: current `main` reads keys from config and gates sensitive logging; add server tests before further changes.

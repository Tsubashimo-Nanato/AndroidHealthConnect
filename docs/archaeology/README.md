# Repository Archaeology

## Scope

This archaeology pass is local-only and non-destructive. Existing tags were preserved, local legacy branches were created for inspection, and a synthetic transition branch was created to make the `v0.1.0` to `v0.1.1` change reviewable.

## Inventory Summary

- Primary languages: Kotlin, C#.
- Primary platforms: Android and ASP.NET Core.
- Android framework/build: Gradle Kotlin DSL, Android Gradle Plugin 8.6.1, Kotlin 1.9.25, Jetpack Compose, Room, WorkManager, Health Connect.
- Server framework/build: .NET 8, ASP.NET Core, EF Core, SQLite, Swagger.
- Test systems: Android JVM unit tests through Gradle; no checked-in server test project yet.
- CI: `.github/workflows/ci.yml` compiles Android debug Kotlin and builds the server on push/PR to `main`.
- Package managers/build tools: Gradle wrapper for Android, NuGet through `dotnet` for server.
- Entry points: `AndroidClient/app/src/main/java/com/example/healthconnectandroid/MainActivity.kt`, `AndroidClient/app/src/main/java/com/example/healthconnectandroid/PermissionUsageActivity.kt`, `Server/Program.cs`.
- Existing docs at start: `README.md`, `docs/README.md`.
- Issue/PR templates at start: none observed.
- GitHub CLI: not available locally.
- GitHub issue/PR inspection: `gh` was not available locally; a read-only GitHub API check on 2026-06-06 returned no issues and no pull requests.

## Chronological Stages

1. `v0.1.0`: baseline monorepo release tag for the Android Health Connect app and .NET server.
2. `v0.1.1`: larger Android sync/upload/UI improvement tag, including local debug upload documentation.

Both tags are annotated tags and reachable from current `main`.

## Documents

- [Tag inventory](tag-inventory.md)
- [Branch map](branch-map.md)
- [Stage 1: v0.1.0](stages/stage-01-v0-1-0.md)
- [Stage 2: v0.1.1](stages/stage-02-v0-1-1.md)
- [Transition: v0.1.0 to v0.1.1](transitions/transition-01-v0-1-0-to-v0-1-1.md)

## Issue and PR Context

Read-only GitHub API inspection on 2026-06-06 found:

- No issues returned by `GET /repos/Tsubashimo-Nanato/AndroidHealthConnect/issues?state=all`.
- No pull requests returned by `GET /repos/Tsubashimo-Nanato/AndroidHealthConnect/pulls?state=all`.

The PR plan still maps local technical risks to future workstreams, but it does not rely on external issue numbers.

No GitHub issues or PRs were modified.

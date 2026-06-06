# Stage 1: v0.1.0

## Observed facts

- Annotated tag `v0.1.0` targets commit `de6f474f7168f7a1d13f7aadca6d16c884e01ae8`.
- Tagger date: 2026-05-12 15:27:28 +0900.
- Target commit date: 2026-05-12 15:27:08 +0900.
- Commit subject: `Version 0.1.0`.
- The tagged tree has 165 tracked files.
- Top-level tracked structure: `.github/`, `AndroidClient/`, `Server/`, `docs/`, root config files, `README.md`, and `VERSION`.
- CI exists at `.github/workflows/ci.yml`.
- Android app version is `versionCode = 1`, `versionName = "0.1.0"`.
- README identifies the repository as a monorepo for an Android Health Connect client and a small .NET API server.

## Inferred purpose

This appears to be the first deliberate stable monorepo snapshot. It likely marked the point where the Android client, .NET server, docs skeleton, CI, and ignore rules were considered coherent enough to preserve as a release-like baseline.

## Main components

- Core/domain logic: health data type registry, normalized record models, heart-rate analysis, sleep-session analysis, record paging, sync range/result models.
- CLI layer: none observed.
- Shell layer: Gradle wrapper and .NET CLI commands documented in README.
- GUI layer: Android Compose UI screens for dashboard, data catalog/detail, records, settings, charts, heart-rate dates, and sleep views.
- Mobile layer: Android app with Health Connect, Room, WorkManager, and Compose.
- Platform-specific layer: Health Connect readers, Android permissions, WorkManager workers, preferences, Room database.
- Server layer: ASP.NET Core app with sample health controller, EF Core SQLite context, Swagger, and API-key middleware.
- Tests: Android local unit tests and a basic Android instrumentation test.
- Docs: root README and `docs/README.md`.

## Build/test status

Commands attempted:

```powershell
$env:JAVA_HOME = "E:\Mess\Projects\Programming\HealthConnect\.tools\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd AndroidClient
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Result: passed.

```powershell
cd Server
dotnet build --configuration Release
```

Result: passed with 0 warnings and 0 errors.

Note: the Android test command emitted a Gradle manifest warning that the `package` attribute in `AndroidManifest.xml` is ignored because Gradle namespace is used.

## Notes

- The Android project already has meaningful JVM tests around pure policy logic.
- The server has no checked-in test project.
- Several Android services combine platform access, database access, and orchestration. Future refactors should add characterization tests before moving that code.
- API key handling in this stage should be treated carefully because the project handles health data and later code changed server security behavior.

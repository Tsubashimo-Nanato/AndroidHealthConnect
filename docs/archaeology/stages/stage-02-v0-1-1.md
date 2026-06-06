# Stage 2: v0.1.1

## Observed facts

- Annotated tag `v0.1.1` targets commit `95d9306e896dfd16627ef68f3d5fda0be587e78b`.
- Tagger date: 2026-06-05 22:00:48 +0900.
- Target commit date: 2026-06-05 22:00:35 +0900.
- Commit subject: `v0.1.1`.
- The tagged tree has 183 tracked files.
- Android app version is `versionCode = 2`, `versionName = "0.1.1"`.
- Compared with `v0.1.0`, the transition includes 71 changed files, 4727 insertions, and 573 deletions.
- Three commits exist between `v0.1.0` and `v0.1.1`.
- Added Android upload service/policy/model files, sync window planning, upload acknowledgement data, sync coverage data, UI i18n support, and additional tests.
- README added local NanatoStudio upload instructions and local HTTP debug guidance.

## Inferred purpose

This appears to be a feature-expansion and stabilization stage focused on making the Android app better at syncing, displaying, and uploading Health Connect data. It likely converted the project from a baseline prototype into a more complete local-debug/upload workflow.

## Main components

- Core/domain logic: same baseline as `v0.1.0`, plus sync window planning, upload endpoint policy, upload range policy, and UI text/i18n policy.
- CLI layer: none observed.
- Shell layer: Gradle wrapper and .NET CLI commands.
- GUI layer: Compose UI expanded for settings, data detail, dashboard, theme, i18n, and charts.
- Mobile layer: Android Health Connect app with added upload and LAN debug support.
- Platform-specific layer: Health Connect readers, WorkManager sync/upload workers, Room DAOs/entities, Android network security config.
- Server layer: still present as ASP.NET Core .NET 8 server.
- Tests: additional Android unit tests for upload endpoint policy, upload time ranges, sync window planning, sync models, and UI text.
- Docs: README local upload guidance.

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

Note: the Android test command emitted the same Gradle manifest warning about the ignored manifest `package` attribute.

## Notes

- Upload and sync policies became more explicit and gained tests.
- The transition added significant behavior in one stage, so review is easier through the synthetic transition branch.
- Local archaeology identified risk areas around upload contract compatibility, aggregate sync coverage semantics, daily timezone grouping, and server API-key/logging behavior.
- Current `main` has one additional commit after this tag that appears to address several server and sync/upload concerns.

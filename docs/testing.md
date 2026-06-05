# Testing

## Verified Commands

The following commands were run locally during the archaeology/refactor pass.

### Android unit tests

```powershell
$env:JAVA_HOME = "E:\Mess\Projects\Programming\HealthConnect\.tools\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd AndroidClient
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Results observed:

- `v0.1.0`: passed.
- `v0.1.1`: passed.
- `refactor/repository-organization`: passed after adding timezone date-window characterization tests and extracting the health CSV row downsampler.

Both tagged stages emitted an Android Gradle warning that `package="com.example.healthconnectandroid"` in `AndroidManifest.xml` is ignored because namespace is configured by Gradle.

### Server build

```powershell
cd Server
dotnet build --configuration Release
```

Results observed:

- `v0.1.0`: passed with 0 warnings and 0 errors.
- `v0.1.1`: passed with 0 warnings and 0 errors.

## Current Test Layout

Android local unit tests live under:

```text
AndroidClient/app/src/test/java/com/example/healthconnectandroid/
```

Current tested areas include:

- heart-rate analysis and date selection
- inspector time ranges
- query-layer chart row downsampling
- record paging
- sleep-session analysis and display models
- sync range and result severity policy
- sync window planning
- upload endpoint and upload time-range policy
- UI formatting, chart bucketing/downsampling, matrix gestures, and data-card policies

Android instrumentation tests live under:

```text
AndroidClient/app/src/androidTest/java/com/example/healthconnectandroid/
```

The server currently has no checked-in test project.

## Recommended Next Tests

- Add tests for the open timezone issue around samples within two hours of midnight in UTC, Asia/Tokyo, and a negative-offset timezone.
- Add Room-backed tests for DAO queries that group by day or calculate detail chart windows.
- Add .NET tests for `ApiKeyMiddleware`, upload DTO validation, and upload persistence idempotency.
- Add a minimal server integration test for `GET /health/api/v1/status` and `POST /health/api/v1/ingest/batches`.

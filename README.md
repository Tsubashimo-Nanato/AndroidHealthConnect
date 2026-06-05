# Android Health Connect

Monorepo for an Android Health Connect client and a small .NET API server.

Current version: `0.1.1`

## Repository Layout

```text
HealthConnect/
  AndroidClient/   Android Health Connect client built with Gradle, Kotlin, Room, and Compose
  Server/          .NET 8 API server for local upload/status endpoints
  docs/            Architecture, workflow, testing, and archaeology documentation
```

`Workspace/` contains local planning notes and sample data used during development. It is intentionally ignored by git.

## Project Docs

- [Architecture](docs/architecture.md)
- [Testing](docs/testing.md)
- [Workflow](docs/workflow.md)
- [Repository archaeology](docs/archaeology/README.md)
- [Local PR plan](docs/pr-plan.md)

## Android Client

The Android app reads supported Health Connect data types, stores normalized local records in Room, renders dashboard/detail/sleep/heart-rate views with Compose, exports local data, and can upload batches to a compatible server endpoint.

```powershell
cd AndroidClient
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

The Android build requires a Java 17+ JDK.

Run unit tests:

```powershell
$env:JAVA_HOME = "E:\Mess\Projects\Programming\HealthConnect\.tools\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd AndroidClient
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

### Local NanatoStudio Upload

For a physical Android device on the same Wi-Fi, use the PC LAN IP, not `127.0.0.1`.

```powershell
cd E:\Mess\Projects\Programming\NanatoStudio
npm.cmd run dev:lan
```

In the Android app upload settings:

- Server mode: `Local debug`
- Local URL: `http://<PC-LAN-IP>:8000/health/api/v1/`
- API key: the key generated in NanatoStudio at `/admin/healthconnect/`

Debug builds permit local HTTP for LAN testing. Release builds keep cleartext disabled; use `https://www.tsubashimonanato.com/health/api/v1/` or another HTTPS endpoint for production.

## Server

The server exposes the checked-in upload/status contract at `/health/api/v1`, persists uploaded health items with EF Core and SQLite, and protects API routes with `X-API-Key`.

```powershell
cd Server
dotnet build
dotnet run
```

The server targets .NET 8.

Run a release build:

```powershell
cd Server
dotnet build --configuration Release
```

Configure API keys through `ApiKeys` in configuration or environment-specific secret storage. The checked-in default configuration intentionally contains no production key.

## Development Workflow

- Keep `main` stable.
- Do work on focused branches such as `feature/*`, `fix/*`, `test/*`, `docs/*`, or `refactor/*`.
- Use small commits that separate docs, tests, behavior changes, and refactors.
- Open PRs into `main` when remote work is authorized.
- Use tags only for deliberate release milestones. Historical tags in this repository are preserved as archaeology snapshots.

## Local Files

Generated build outputs, IDE folders, local Android SDK config, local databases, debug logs, and downloaded tool runtimes are ignored by the root `.gitignore`.

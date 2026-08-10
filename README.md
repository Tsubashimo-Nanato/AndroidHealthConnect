# Health Data Sync

English | [日本語](README.ja.md)

[![CI](https://github.com/Tsubashimo-Nanato/AndroidHealthConnect/actions/workflows/ci.yml/badge.svg)](https://github.com/Tsubashimo-Nanato/AndroidHealthConnect/actions/workflows/ci.yml)
![Version](https://img.shields.io/badge/version-0.1.5-0f766e)
![Android](https://img.shields.io/badge/Android-Health%20Connect-3ddc84)
![Server](https://img.shields.io/badge/API-.NET%208-512bd4)
[![License: AGPL-3.0-only](https://img.shields.io/badge/License-AGPL--3.0--only-blue.svg)](LICENSE)

**Health Data Sync** is a local-first Android app for inspecting Health Connect data, exporting it to CSV/ZIP, and optionally uploading normalized batches to a small self-hosted .NET API. The system covers the path from Health Connect ingestion and local Room/SQLite storage to Compose dashboards, explicit exports, an API-key-protected upload server, and a browser graph inspector.

The app focuses on personal observability rather than medical advice. It reads supported Health Connect records, keeps the working copy local, and makes export/upload explicit user-controlled actions.

## What It Does

- Reads Health Connect data into a local Room database for fast inspection and repeatable exports.
- Supports heart rate, sleep sessions, steps, distance, calories, weight, body fat, oxygen saturation, blood pressure, body temperature, respiratory rate, and resting heart rate, with additional data types planned in the registry.
- Shows Compose dashboards, data cards, heart-rate timelines, resting-HR estimates, sleep summaries, sleep quality matrix selection, and raw-record drill-downs.
- Exports heart-rate compatibility CSV, all-data CSV, per-type CSV, and ZIP packages.
- Runs manual, smart, full, and periodic sync flows, using WorkManager where Android allows background work.
- Uploads normalized records, values, aggregates, and optional medicine rows to a self-hosted API using `X-API-Key`.
- Includes a small server graph UI for inspecting uploaded metric rows by local date.
- Adds an optional medicine companion workflow for schedules, quick dose logs, reminders, and adherence history.

## Architecture

```text
Health Connect
      |
      v
Android app (Kotlin, Jetpack Compose)
      |
      +-- Room / SQLite local cache
      +-- Charts, sleep insights, CSV / ZIP export
      +-- WorkManager sync and upload workers
      |
      v
ASP.NET Core 8 API (X-API-Key)
      |
      +-- SQLite uploaded-health tables
      +-- Static Plotly graph inspector
```

Repository layout:

```text
HealthConnect/
  AndroidClient/   Android Gradle project
  Server/          ASP.NET Core 8 API and graph UI
  Server.Tests/    .NET API tests
  docs/            Durable project documentation
```

## Application Scenarios

- **Personal health archive:** keep a local, queryable copy of wearable data before exporting or uploading anything.
- **Wearable data QA:** compare recent Health Connect sync coverage, record counts, and chart quality after device/app changes.
- **Heart-rate trend inspection:** review dense HR samples, visible-range estimates, and resting-HR trends without opening raw CSVs.
- **Sleep review:** inspect sleep-session windows, short/nap labels, and selected sessions from a weekly/monthly matrix.
- **Local-first export:** produce CSV/ZIP packages for spreadsheet review or backup.
- **Self-hosted pipeline:** send normalized health batches to your own .NET/SQLite endpoint for lightweight web inspection.
- **Medicine adherence companion:** track scheduled/as-needed dose logs alongside the Health Connect upload model.

## Tech Stack

- **Android:** Kotlin, Jetpack Compose, Material 3, Health Connect Client, Room, WorkManager, OkHttp, ML Kit Code Scanner.
- **Server:** .NET 8, ASP.NET Core controllers, Entity Framework Core, SQLite, Swagger in development.
- **Visualization:** Compose charts on Android and Plotly for the server-side browser graph.
- **Testing:** JUnit Android unit tests and .NET API/controller tests, wired into GitHub Actions.

## Quick Start

Requirements:

- Java 17+ JDK
- Android SDK / Android Studio for device or emulator work
- .NET 8 SDK

Build and test the Android app:

```powershell
cd AndroidClient

# Point JAVA_HOME at an installed Java 17+ JDK.
$env:JAVA_HOME = "<path-to-jdk-17>"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

Run the server:

```powershell
cd Server
$keyBytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($keyBytes)
$env:ApiKeys__0 = [Convert]::ToBase64String($keyBytes)
dotnet build HC_server.sln
dotnet run --project HC_server.csproj
```

The command above creates a random development key for the current shell. No API key is stored in the repository. Configured keys must contain 32 to 256 characters, and non-development deployments must configure at least one private `ApiKeys` entry. The server validates one immutable key snapshot at startup; configuration reloads do not change the active credentials.

Smoke-test the existing upload API:

```powershell
$base = "http://localhost:5045/health/api/v1"
$headers = @{ "X-API-Key" = $env:ApiKeys__0 }

Invoke-RestMethod "$base/status" -Headers $headers

$body = @{
  schemaVersion = 1
  deviceId = "demo-device"
  batchId = "demo-empty-batch"
  createdAtEpochMillis = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  records = @()
  values = @()
  aggregates = @()
} | ConvertTo-Json -Depth 8

Invoke-RestMethod "$base/ingest/batches" -Method Post -Headers $headers -ContentType "application/json" -Body $body
```

## API Surface

The current public upload surface is intentionally small:

- `GET /health/api/v1/status`
- `POST /health/api/v1/ingest/batches`
- Auth header: `X-API-Key: <key>`

Upload retries are supported by treating local IDs as idempotency keys per device.

## Local Upload Notes

For a physical Android device on the same Wi-Fi, use the PC LAN IP, not `127.0.0.1`.

In the Android app upload settings:

- Server mode: `Local debug`
- Local URL: `http://<PC-LAN-IP>:8000/health/api/v1/`
- API key: the key configured on the compatible backend/admin page

Debug builds permit local HTTP for LAN testing. Release builds keep cleartext disabled; use an HTTPS endpoint for production.

## Privacy Notes

- The Android app reads Health Connect data for local inspection, charting, export, and explicit sync/upload actions.
- It does not write Health Connect records.
- CSV/ZIP exports are created only when the user chooses a destination.
- Uploads require configured endpoint settings and an API key.
- The Android client atomically encrypts its upload configuration, including the API key and endpoint selection, with AES-GCM backed by Android Keystore. Settings saved by an older version are migrated from ordinary preferences on first access and the plaintext key is removed there only after the encrypted configuration is written successfully.
- Temporary secure-storage failures preserve the existing encrypted configuration and defer background uploads instead of clearing credentials or mixing a new key with an older endpoint.
- Android backup is disabled, and every app-private root, file, database, and preferences domain is explicitly excluded from cloud backup and device transfer. This covers health data, the local database, legacy upload fields, the device identifier, and Keystore-backed ciphertext.
- Raw health exports, local databases, API keys, device IDs, and device QA screenshots are not committed.

## Release Verification Gates

The automated JVM and server suites do not replace these device checks before publishing a release:

- Run the Android instrumentation suite on a supported emulator and a physical device.
- Use Android Backup Manager (`bmgr`) on a disposable test install to confirm no app-private preferences, databases, or files enter a backup or restore set.
- Verify a real device-to-device transfer does not restore health data, the local database, upload credentials, or device identifiers.
- Exercise secure-settings behavior while the device is locked and after Keystore invalidation; temporary provider/lock failures must defer access, while permanent invalidation must require credential reentry.

## Suggested GitHub Topics

`android`, `health-connect`, `jetpack-compose`, `dotnet`, `sqlite`, `health-data`, `wearables`, `self-hosted`

## License

Project source code in this repository is licensed under the GNU Affero General Public License v3.0 only (`AGPL-3.0-only`). See [LICENSE](LICENSE).

Files that carry their own license notices remain governed by those notices.

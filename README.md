# Health Data Sync

[![CI](https://github.com/Tsubashimo-Nanato/AndroidHealthConnect/actions/workflows/ci.yml/badge.svg)](https://github.com/Tsubashimo-Nanato/AndroidHealthConnect/actions/workflows/ci.yml)
![Version](https://img.shields.io/badge/version-0.1.5-0f766e)
![Android](https://img.shields.io/badge/Android-Health%20Connect-3ddc84)
![Server](https://img.shields.io/badge/API-.NET%208-512bd4)
[![License: AGPL-3.0-only](https://img.shields.io/badge/License-AGPL--3.0--only-blue.svg)](LICENSE)

**Health Data Sync** is a local-first Android app for inspecting Health Connect data, exporting it to CSV/ZIP, and optionally uploading normalized batches to a small self-hosted .NET API. It is built as a portfolio-grade end-to-end health-data pipeline: Android Health Connect -> local Room/SQLite cache -> Compose dashboards and charts -> API-key protected upload server -> browser graph inspector.

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

`Workspace/` contains local planning notes, smoke-test evidence, and scratch data used during development. It is intentionally ignored by git.

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

# Point JAVA_HOME at any installed Java 17+ JDK.
$env:JAVA_HOME = "E:\Tools\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

Run the server:

```powershell
cd Server
dotnet build HC_server.sln
dotnet run --project HC_server.csproj
```

The development config includes API key `123`. Non-development deployments must configure at least one non-default `ApiKeys` entry.

Smoke-test the existing upload API:

```powershell
$base = "http://localhost:5045/health/api/v1"
$headers = @{ "X-API-Key" = "123" }

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
- Raw health exports, local databases, API keys, device IDs, and device QA screenshots are not committed.

## Suggested GitHub Topics

`android`, `health-connect`, `jetpack-compose`, `dotnet`, `sqlite`, `health-data`, `wearables`, `self-hosted`

## License

Project source code in this repository is licensed under the GNU Affero General Public License v3.0 only (`AGPL-3.0-only`). See [LICENSE](LICENSE).

Files that carry their own license notices remain governed by those notices.

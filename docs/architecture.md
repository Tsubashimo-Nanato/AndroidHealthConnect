# Architecture

## Overview

This repository is a small monorepo with two primary applications:

- `AndroidClient/`: Android app using Kotlin, Gradle, Jetpack Compose, Room, WorkManager, and Android Health Connect.
- `Server/`: ASP.NET Core .NET 8 API using EF Core and SQLite.

The main data flow is:

1. Android Health Connect records are read by Android adapter code.
2. Records are normalized into app-local health models.
3. Room entities store records, values, aggregates, sync runs, coverage, and upload acknowledgements.
4. Query services assemble dashboard/detail/export/upload views over the local database.
5. Compose screens render those query results and dispatch user actions.
6. Upload services send batch payloads to the server contract under `/health/api/v1`.
7. The server validates the API key, accepts batch payloads, and persists uploaded records, values, and aggregates.

## Android Layers

### Core/domain logic

Observed core logic currently lives mostly under `AndroidClient/app/src/main/java/com/example/healthconnectandroid/hc/`:

- health data type registry and descriptors
- normalized health record models
- heart-rate analysis
- sleep-session analysis
- record paging policy
- sync range and result severity policy
- upload endpoint and time-range policy

These modules are mostly testable with local JVM unit tests and should stay independent from Compose where possible.

### Data adapters

`AndroidClient/app/src/main/java/com/example/healthconnectandroid/data/` contains Room entities, DAOs, database wiring, and query row DTOs. This layer should own SQLite details and expose explicit DAO/query methods to services.

### Platform adapters

Health Connect reader classes, WorkManager workers, local preferences, and Android permission intents are platform adapters. They depend on Android APIs and should call into core policies rather than contain business rules directly.

### Interface layer

`AndroidClient/app/src/main/java/com/example/healthconnectandroid/ui/` contains Compose screens and UI components. UI code should remain thin: render state, collect user input, and call service/controller methods. Business rules such as date-window selection, chart downsampling, upload validation, and sync result classification should remain in tested non-UI modules.

## Server Layers

### API layer

`Server/Controllers/` contains ASP.NET Core controllers:

- `HealthController.cs`: legacy/sample health endpoints.
- `UploadController.cs`: current Android upload/status contract at `/health/api/v1`.

### Data layer

`Server/Data/` contains `AppDbContext`, legacy sample DB wiring, and upload schema initialization. `Server/Models/` contains DTOs and persisted models.

### Security adapter

`Server/Security/ApiKeyMiddleware.cs` protects API routes with `X-API-Key` values from configuration. Current `main` disables sensitive EF logging unless the app is in development and `Ef:EnableSensitiveDataLogging` is enabled.

## Dependency Direction

Preferred dependency direction:

- Android UI -> query/sync/upload services -> core policies and data DAOs.
- Android platform adapters -> core policies and data DAOs.
- Core policies -> standard library and pure models only.
- Server controllers -> EF Core context and DTO mapping.
- Server middleware -> configuration and ASP.NET Core abstractions only.

Avoid dependencies from core/domain logic back into Compose, Android widgets, WorkManager, EF, or HTTP controllers.

## Testing Strategy

- Keep pure Kotlin policies under JVM unit tests.
- Add characterization tests before changing query, sync, upload, date-window, or chart behavior.
- Add Room/integration tests when DAO SQL semantics become the refactor target.
- Add server unit or integration tests before changing API-key middleware or upload persistence behavior.
- Keep CI focused on Android compile/unit tests and server build at minimum.

## Known Limitations

- Open GitHub issue #3 reports inconsistent daily grouping timezone semantics. Current code has targeted timezone-aware heart-rate query work, but the repository still needs an explicit end-to-end daily grouping contract.
- Server-side behavior has no checked-in test project yet.
- Some Compose screens remain large and contain orchestration logic that should move behind view-model/service boundaries over time.
- DAO SQL is powerful but dense; high-risk query changes should be covered by Room or integration tests before broad refactors.

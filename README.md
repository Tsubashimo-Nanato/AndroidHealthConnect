# Android Health Connect

Monorepo for an Android Health Connect client and a small .NET API server.

Current version: `0.1.0`

## Repository Layout

```text
HealthConnect/
  AndroidClient/   Android Gradle project
  Server/          .NET 8 API project
  docs/            Project documentation
```

`Workspace/` contains local planning notes and sample data used during development. It is intentionally ignored by git.

## Android Client

```powershell
cd AndroidClient
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

The Android build requires a Java 17+ JDK.

## Server

```powershell
cd Server
dotnet build
dotnet run
```

The server targets .NET 8.

## Local Files

Generated build outputs, IDE folders, local Android SDK config, local databases, debug logs, and downloaded tool runtimes are ignored by the root `.gitignore`.

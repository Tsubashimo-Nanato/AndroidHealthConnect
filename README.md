# Android Health Connect

Monorepo for an Android Health Connect client and a small .NET API server.

Current version: `0.1.5`

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

```powershell
cd Server
dotnet build
dotnet run
```

The server targets .NET 8.

## Local Files

Generated build outputs, IDE folders, local Android SDK config, local databases, debug logs, and downloaded tool runtimes are ignored by the root `.gitignore`.

## License

Project source code in this repository is licensed under the GNU Affero General Public License v3.0 only (`AGPL-3.0-only`). See [LICENSE](LICENSE).

Files that carry their own license notices remain governed by those notices.

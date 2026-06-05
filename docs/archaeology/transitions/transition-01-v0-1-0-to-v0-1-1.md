# Transition: v0.1.0 to v0.1.1

## Summary

The project moved from a baseline Android Health Connect plus .NET server monorepo to a more complete Android sync/upload workflow. The Android app gained upload settings, upload workers/services, sync coverage tracking, expanded detail/query behavior, UI text policy, and more tests.

## Observed file changes

- File count summary: 71 files changed, 4727 insertions, 573 deletions.
- Files added: 18.
- Files deleted: 0 observed in `git diff --name-status v0.1.0 v0.1.1`.
- Files renamed: 0 observed.
- Heavily modified files include:
  - `AndroidClient/app/src/main/java/com/example/healthconnectandroid/hc/upload/HealthUploadService.kt`
  - `AndroidClient/app/src/main/java/com/example/healthconnectandroid/ui/i18n/UiText.kt`
  - `AndroidClient/app/src/main/java/com/example/healthconnectandroid/ui/settings/SettingsScreens.kt`
  - `AndroidClient/app/src/main/java/com/example/healthconnectandroid/ui/theme/Theme.kt`
  - `AndroidClient/app/src/main/java/com/example/healthconnectandroid/ui/data/DataDetailScreen.kt`
  - `AndroidClient/app/src/main/java/com/example/healthconnectandroid/hc/sync/HealthSyncService.kt`
  - `AndroidClient/app/src/main/java/com/example/healthconnectandroid/hc/sync/HealthDataTypeSyncer.kt`
  - `AndroidClient/app/src/main/java/com/example/healthconnectandroid/data/HealthRecordDao.kt`

Selected files added:

- `AndroidClient/app/src/main/java/com/example/healthconnectandroid/hc/upload/HealthUploadService.kt`
- `AndroidClient/app/src/main/java/com/example/healthconnectandroid/hc/upload/HealthUploadWorker.kt`
- `AndroidClient/app/src/main/java/com/example/healthconnectandroid/hc/upload/UploadEndpointPolicy.kt`
- `AndroidClient/app/src/main/java/com/example/healthconnectandroid/hc/upload/UploadModels.kt`
- `AndroidClient/app/src/main/java/com/example/healthconnectandroid/hc/sync/SyncWindowPlanner.kt`
- `AndroidClient/app/src/main/java/com/example/healthconnectandroid/data/HealthUploadDao.kt`
- `AndroidClient/app/src/main/java/com/example/healthconnectandroid/data/HealthUploadAckEntity.kt`
- `AndroidClient/app/src/main/java/com/example/healthconnectandroid/data/HealthSyncCoverageDao.kt`
- `AndroidClient/app/src/main/java/com/example/healthconnectandroid/data/HealthSyncCoverageEntity.kt`
- `AndroidClient/app/src/main/java/com/example/healthconnectandroid/ui/i18n/UiText.kt`
- New tests for upload, sync windows, and UI text.

## Feature-level interpretation

Observed additions indicate:

- Android upload configuration and background upload were introduced.
- Local debug HTTP support was added for Android LAN testing.
- Sync coverage and upload acknowledgement tracking became first-class local database concepts.
- Data detail/query behavior expanded, especially for charting and heart-rate/date views.
- UI text and settings behavior became more centralized.
- More policy logic was covered by unit tests.

## Architecture impact

The transition increased the number of explicit service/policy modules, which is positive for testability. It also increased orchestration complexity in sync, upload, and query services. The main architecture risk is keeping UI, platform workers, Room queries, Health Connect readers, and upload concerns from coupling too tightly.

## Testing impact

Testing improved through new Android JVM tests for:

- sync window planning
- upload endpoint validation
- upload time ranges
- UI text
- expanded sync model behavior

The server still lacks checked-in tests in this transition.

## Risk notes

- Daily grouping and timezone semantics need an explicit contract because local dates can be derived from record offsets, system timezone, or selected display timezone.
- Upload success should only be acknowledged after server-side durable persistence.
- Sync coverage must not hide partial aggregate failures.
- Server API keys and sensitive logging need tests because the project handles health data.

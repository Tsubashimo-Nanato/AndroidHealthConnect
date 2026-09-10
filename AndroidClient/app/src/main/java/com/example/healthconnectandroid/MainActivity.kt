package com.example.healthconnectandroid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.healthconnectandroid.debug.DeviceSmokeDiagnostics
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.hc.LocalHealthStatus
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import com.example.healthconnectandroid.hc.PeriodicHealthSyncWorker
import com.example.healthconnectandroid.hc.PeriodicSyncPreferences
import com.example.healthconnectandroid.hc.export.HealthCsvExporter
import com.example.healthconnectandroid.hc.export.HealthZipExporter
import com.example.healthconnectandroid.hc.local.LocalDataRemovalPhase
import com.example.healthconnectandroid.hc.local.LocalDataRemovalProgress
import com.example.healthconnectandroid.hc.local.LocalDataService
import com.example.healthconnectandroid.hc.query.HealthDashboardQueryService
import com.example.healthconnectandroid.hc.query.CatalogRefreshPolicy
import com.example.healthconnectandroid.hc.query.HealthDataCatalogQueryService
import com.example.healthconnectandroid.hc.query.HealthDetailQueryService
import com.example.healthconnectandroid.hc.query.HealthRecordDetailQueryService
import com.example.healthconnectandroid.hc.retention.HealthRetentionWorker
import com.example.healthconnectandroid.hc.retention.HealthDatabaseCompactionWorker
import com.example.healthconnectandroid.hc.sync.HealthSyncService
import com.example.healthconnectandroid.hc.sync.SyncMode
import com.example.healthconnectandroid.hc.sync.SyncProgress
import com.example.healthconnectandroid.hc.sync.SyncRunStatus
import com.example.healthconnectandroid.hc.sync.syncAllStatusText
import com.example.healthconnectandroid.hc.upload.HealthUploadService
import com.example.healthconnectandroid.hc.upload.HealthUploadWorker
import com.example.healthconnectandroid.hc.upload.ProfilePairingStore
import com.example.healthconnectandroid.hc.upload.UploadAutoQueueDecision
import com.example.healthconnectandroid.hc.upload.UploadAutoQueuePolicy
import com.example.healthconnectandroid.hc.upload.UploadDebugModePolicy
import com.example.healthconnectandroid.hc.upload.UploadEndpointPolicy
import com.example.healthconnectandroid.hc.upload.UploadEndpointValidation
import com.example.healthconnectandroid.hc.upload.UploadPairingCode
import com.example.healthconnectandroid.hc.upload.UploadPairingResult
import com.example.healthconnectandroid.hc.upload.UploadPairingService
import com.example.healthconnectandroid.hc.upload.UploadServerMode
import com.example.healthconnectandroid.hc.upload.UploadPendingCounts
import com.example.healthconnectandroid.hc.upload.UploadProgress
import com.example.healthconnectandroid.hc.upload.UploadResultSeverity
import com.example.healthconnectandroid.hc.upload.UploadRetentionPolicy
import com.example.healthconnectandroid.hc.upload.UploadScanApplyResult
import com.example.healthconnectandroid.hc.upload.UploadScanPolicy
import com.example.healthconnectandroid.hc.upload.UploadSettings
import com.example.healthconnectandroid.hc.upload.UploadTimeRange
import com.example.healthconnectandroid.hc.upload.toStatus
import com.example.healthconnectandroid.medicine.EmptyMedicineSnapshot
import com.example.healthconnectandroid.medicine.MedicineDoseStatus
import com.example.healthconnectandroid.medicine.MedicineLogSource
import com.example.healthconnectandroid.medicine.MedicineReminderIntents
import com.example.healthconnectandroid.medicine.MedicineReminderNotifier
import com.example.healthconnectandroid.medicine.MedicineReminderScheduler
import com.example.healthconnectandroid.medicine.MedicineRepository
import com.example.healthconnectandroid.medicine.MedicineSlot
import com.example.healthconnectandroid.medicine.MedicineSlotPolicy
import com.example.healthconnectandroid.navigation.AppDestination
import com.example.healthconnectandroid.navigation.AppNavigationState
import com.example.healthconnectandroid.navigation.AppTab
import com.example.healthconnectandroid.navigation.SettingsDestination
import com.example.healthconnectandroid.ui.AppTopBar
import com.example.healthconnectandroid.ui.BottomNavigationBar
import com.example.healthconnectandroid.ui.animation.destinationEnterMotion
import com.example.healthconnectandroid.ui.data.DataCatalogScreen
import com.example.healthconnectandroid.ui.data.DataSyncScreen
import com.example.healthconnectandroid.ui.data.HealthDataDetailScreen
import com.example.healthconnectandroid.ui.medicine.MedicineScreen
import com.example.healthconnectandroid.ui.settings.SettingsHealthConnectScreen
import com.example.healthconnectandroid.ui.settings.SettingsMedicineScreen
import com.example.healthconnectandroid.ui.settings.SettingsPreferencesScreen
import com.example.healthconnectandroid.ui.settings.SettingsScreen
import com.example.healthconnectandroid.ui.settings.SettingsStorageToolsScreen
import com.example.healthconnectandroid.ui.settings.LocalDataRemovalDialog
import com.example.healthconnectandroid.ui.format.toDisplayPreferences
import com.example.healthconnectandroid.ui.i18n.LocalAppLanguage
import com.example.healthconnectandroid.ui.i18n.uiText
import com.example.healthconnectandroid.ui.i18n.translateUiText
import com.example.healthconnectandroid.ui.theme.HealthConnectAndroidTheme
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.UploadRetryAction
import com.example.healthconnectandroid.ui.backgroundReadStatusText
import com.example.healthconnectandroid.ui.syncResultsStatusTone
import com.example.healthconnectandroid.ui.uploadCompletionStatus
import com.example.healthconnectandroid.ui.uploadStartStatus
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val HR_PERMISSION = HealthDataTypeRegistry.heartRate.requiredReadPermission
    ?: error("Heart rate record must expose a Health Connect read permission")
private const val TAG = "HealthConnect"
private const val VISIBLE_DATA_CACHE_TTL_MILLIS = 60_000L
private const val MEDICINE_SEED_VERSION = 1

class MainActivity : ComponentActivity() {
    private var pendingTypeExportKey: String? = null
    private var reportExportStatus: ((String) -> Unit)? = null
    private var reportActionBusy: ((Boolean) -> Unit)? = null
    private var reportNotificationPermission: ((Boolean) -> Unit)? = null
    private var reportOverlayPermission: ((Boolean) -> Unit)? = null
    private var reportExactAlarmAccess: ((Boolean) -> Unit)? = null
    private var openMedicineFromIntent: ((MedicineSlot?) -> Unit)? = null
    private var healthConnectEnabledForProfile = true

    private fun hasHrPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, HR_PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun manifestDeclaresHr(): Boolean = try {
        val pm = packageManager
        val requested: Array<String>? =
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                ).requestedPermissions
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
            }
        requested?.contains(HR_PERMISSION) == true
    } catch (_: Exception) { false }

    private fun hasNotificationPermission(): Boolean =
        MedicineReminderNotifier.canNotify(this)

    private fun hasMedicineOverlayPermission(): Boolean =
        AndroidSettings.canDrawOverlays(this)

    private fun hasMedicineExactAlarmAccess(): Boolean =
        MedicineReminderScheduler.canScheduleExactAlarms(this)

    private fun medicineOverlayPermissionIntent(): Intent =
        Intent(
            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )

    @RequiresApi(Build.VERSION_CODES.S)
    private fun medicineExactAlarmAccessIntent(): Intent =
        Intent(
            AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:$packageName")
        )

    private fun medicineSlotFromIntent(intent: Intent?): MedicineSlot? {
        if (intent?.action != MedicineReminderIntents.ACTION_OPEN_MEDICINE) return null
        return intent.getStringExtra(MedicineReminderIntents.EXTRA_SLOT)
            ?.let(MedicineSlot::fromId)
    }

    private var hcClient: HealthConnectClient? = null
    private val hcPermissions = HealthDataTypeRegistry.implementedReadPermissions
    private val requestHcPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) {
            // Health Connect permissions are re-read on resume to keep platform and HC state in one path.
    }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            reportNotificationPermission?.invoke(hasNotificationPermission())
        }

    private val requestOverlayPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            reportOverlayPermission?.invoke(hasMedicineOverlayPermission())
        }

    private val requestExactAlarmAccess =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            reportExactAlarmAccess?.invoke(hasMedicineExactAlarmAccess())
        }

    private val createHrCsv =
        registerForActivityResult(CreateDocument("text/csv")) { uri ->
            if (uri == null) {
                reportExportStatus?.invoke("Heart-rate CSV export cancelled")
                reportActionBusy?.invoke(false)
                return@registerForActivityResult
            }
            lifecycleScope.launch {
                val count = runCatching {
                    HealthCsvExporter(AppDb.get(this@MainActivity))
                        .exportLegacyHeartRateCsv(contentResolver, uri)
                }.onFailure { Log.e(TAG, "CSV export failed", it) }
                    .getOrDefault(-1)
                val message = if (count >= 0) {
                    "Exported heart-rate compatibility CSV: $count rows"
                } else {
                    "Heart-rate CSV export failed"
                }
                reportExportStatus?.invoke(message)
                reportActionBusy?.invoke(false)
                Log.i(TAG, "CSV export rows=$count -> $uri")
            }
        }

    private val createAllCsv =
        registerForActivityResult(CreateDocument("text/csv")) { uri ->
            if (uri == null) {
                reportExportStatus?.invoke("All-data CSV export cancelled")
                reportActionBusy?.invoke(false)
                return@registerForActivityResult
            }
            lifecycleScope.launch {
                val count = runCatching {
                    HealthCsvExporter(AppDb.get(this@MainActivity))
                        .exportAllCsv(contentResolver, uri)
                }.onFailure { Log.e(TAG, "All CSV export failed", it) }
                    .getOrDefault(-1)
                val message = if (count >= 0) {
                    "Exported all Health Connect data CSV: $count rows"
                } else {
                    "All-data CSV export failed"
                }
                reportExportStatus?.invoke(message)
                reportActionBusy?.invoke(false)
                Log.i(TAG, "All CSV export rows=$count -> $uri")
            }
        }

    private val createCsvZip =
        registerForActivityResult(CreateDocument("application/zip")) { uri ->
            if (uri == null) {
                reportExportStatus?.invoke("CSV ZIP export cancelled")
                reportActionBusy?.invoke(false)
                return@registerForActivityResult
            }
            lifecycleScope.launch {
                val count = runCatching {
                    HealthZipExporter(AppDb.get(this@MainActivity))
                        .exportAllCsvZip(contentResolver, uri)
                }.onFailure { Log.e(TAG, "CSV ZIP export failed", it) }
                    .getOrDefault(-1)
                val message = if (count >= 0) {
                    "Exported CSV ZIP package: $count raw rows plus summaries when available"
                } else {
                    "CSV ZIP export failed"
                }
                reportExportStatus?.invoke(message)
                reportActionBusy?.invoke(false)
                Log.i(TAG, "CSV ZIP export rows=$count -> $uri")
            }
        }

    private val createTypeCsv =
        registerForActivityResult(CreateDocument("text/csv")) { uri ->
            val key = pendingTypeExportKey
            pendingTypeExportKey = null
            if (uri == null) {
                reportExportStatus?.invoke("Type CSV export cancelled")
                reportActionBusy?.invoke(false)
                return@registerForActivityResult
            }
            if (key == null) {
                reportExportStatus?.invoke("Type CSV export failed: no selected data type")
                reportActionBusy?.invoke(false)
                return@registerForActivityResult
            }
            lifecycleScope.launch {
                val count = runCatching {
                    HealthCsvExporter(AppDb.get(this@MainActivity))
                        .exportTypeCsv(contentResolver, uri, key)
                }.onFailure { Log.e(TAG, "Type CSV export failed", it) }
                    .getOrDefault(-1)
                val message = if (count >= 0) {
                    "Exported $key CSV: $count rows across all local records for this type"
                } else {
                    "$key CSV export failed"
                }
                reportExportStatus?.invoke(message)
                reportActionBusy?.invoke(false)
                Log.i(TAG, "Type CSV export rows=$count type=$key -> $uri")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activeProfile = LocalProfileStore.activeProfile(this)
        healthConnectEnabledForProfile = activeProfile.ownsHealthConnect
        if (healthConnectEnabledForProfile) {
            hcClient = healthConnectClientOrNull()
            if (!hasHrPermission()) requestHealthConnectPermissions(hcPermissions)
        }

        val db = AppDb.get(this, activeProfile.id)
        val dashboardQueries = HealthDashboardQueryService(db)
        val catalogQueries = HealthDataCatalogQueryService(this, db)
        val detailQueries = HealthDetailQueryService(db)
        val recordQueries = HealthRecordDetailQueryService(db)
        val localDataService = LocalDataService(db)
        val syncService = HealthSyncService(this, db)
        val uploadService = HealthUploadService(db)
        val medicineRepository = MedicineRepository(db)
        setContent {
            App(
                dashboardQueries = dashboardQueries,
                catalogQueries = catalogQueries,
                detailQueries = detailQueries,
                recordQueries = recordQueries,
                localDataService = localDataService,
                syncService = syncService,
                uploadService = uploadService,
                medicineRepository = medicineRepository,
                activeProfile = activeProfile,
                manifestDeclares = ::manifestDeclaresHr,
                hasPlatformPerm = ::hasHrPermission
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == MedicineReminderIntents.ACTION_OPEN_MEDICINE) {
            openMedicineFromIntent?.invoke(medicineSlotFromIntent(intent))
        }
    }

    @Composable
    private fun App(
        dashboardQueries: HealthDashboardQueryService,
        catalogQueries: HealthDataCatalogQueryService,
        detailQueries: HealthDetailQueryService,
        recordQueries: HealthRecordDetailQueryService,
        localDataService: LocalDataService,
        syncService: HealthSyncService,
        uploadService: HealthUploadService,
        medicineRepository: MedicineRepository,
        activeProfile: LocalProfile,
        manifestDeclares: () -> Boolean,
        hasPlatformPerm: () -> Boolean
    ) {
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val pairingService = remember { UploadPairingService() }

        var platformGranted by remember { mutableStateOf(hasPlatformPerm()) }
        val declared = remember { manifestDeclares() }
        var hcGranted by remember { mutableStateOf(false) }
        var hrHcGranted by remember { mutableStateOf(false) }
        var backgroundReadAvailable by remember { mutableStateOf(false) }
        var backgroundReadGranted by remember { mutableStateOf(false) }
        var grantedPermissions by remember {
            mutableStateOf(
                if (activeProfile.ownsHealthConnect) emptySet() else hcPermissions
            )
        }
        var periodicEnabled by remember {
            mutableStateOf(
                activeProfile.ownsHealthConnect &&
                    PeriodicSyncPreferences.isEnabled(this@MainActivity)
            )
        }
        var lastPeriodicSync by remember {
            mutableStateOf(PeriodicSyncPreferences.lastFinishedAt(this@MainActivity))
        }
        var lastPeriodicStatus by remember {
            mutableStateOf(PeriodicSyncPreferences.lastStatus(this@MainActivity))
        }
        var lastPeriodicSummary by remember {
            mutableStateOf(PeriodicSyncPreferences.lastSummary(this@MainActivity))
        }
        var localHealthStatus by remember { mutableStateOf<LocalHealthStatus?>(null) }
        var localHealthStatusLoadedAt by remember { mutableLongStateOf(0L) }
        var dataCatalogRevision by remember { mutableIntStateOf(0) }
        var storageToolsStatus by remember { mutableStateOf("Ready") }
        var syncStatus by remember { mutableStateOf("Ready") }
        var uploadActionStatus by remember { mutableStateOf("Ready") }
        var dashboardStatusTone by remember { mutableStateOf(StatusTone.Neutral) }
        var actionInProgress by remember { mutableStateOf<AppAction?>(null) }
        var syncProgress by remember { mutableStateOf<SyncProgress?>(null) }
        var fullSyncJob by remember { mutableStateOf<Job?>(null) }
        var showLocalDataRemoval by remember { mutableStateOf(false) }
        var localDataRemovalProgress by remember { mutableStateOf<LocalDataRemovalProgress?>(null) }
        var debugEnabled by remember { mutableStateOf(AppPreferences.debugModeEnabled(this@MainActivity)) }
        var themeMode by remember { mutableStateOf(AppPreferences.themeMode(this@MainActivity)) }
        var themePalette by remember { mutableStateOf(AppPreferences.themePalette(this@MainActivity)) }
        var profiles by remember { mutableStateOf(LocalProfileStore.profiles(this@MainActivity)) }
        var userProfile by remember { mutableStateOf(activeProfile.userProfile) }
        var userPreferences by remember { mutableStateOf(AppPreferences.userPreferences(this@MainActivity)) }
        var uploadSettings by remember {
            mutableStateOf(AppPreferences.uploadSettings(this@MainActivity, activeProfile.id))
        }
        var uploadStatus by remember {
            mutableStateOf(AppPreferences.uploadStatus(this@MainActivity, activeProfile.id))
        }
        var uploadPendingCounts by remember { mutableStateOf(UploadPendingCounts.Empty) }
        var uploadPendingCountsLoadedAt by remember { mutableLongStateOf(0L) }
        var uploadPendingCountsSettings by remember { mutableStateOf<UploadSettings?>(null) }
        var uploadProgress by remember { mutableStateOf<UploadProgress?>(null) }
        var medicineSnapshot by remember { mutableStateOf(EmptyMedicineSnapshot) }
        var medicineSnapshotLoadedAt by remember { mutableLongStateOf(0L) }
        var medicineSnapshotZoneId by remember { mutableStateOf<String?>(null) }
        var medicineStatus by remember { mutableStateOf("Medicine ready") }
        var notificationPermissionGranted by remember { mutableStateOf(hasNotificationPermission()) }
        var medicineOverlayReminderEnabled by remember {
            mutableStateOf(AppPreferences.medicineOverlayReminderEnabled(this@MainActivity))
        }
        var medicineOverlayPermissionGranted by remember { mutableStateOf(hasMedicineOverlayPermission()) }
        var medicineExactAlarmAccessGranted by remember {
            mutableStateOf(hasMedicineExactAlarmAccess())
        }
        var medicineDefaultSlot by remember {
            mutableStateOf(medicineSlotFromIntent(intent) ?: MedicineSlot.MORNING)
        }
        var initialLoadFinished by remember { mutableStateOf(false) }
        val diagnostics = remember { DeviceSmokeDiagnostics() }
        val nav = remember { AppNavigationState() }
        val userAge = userProfile.age
        val displayPreferences = remember(userPreferences) { userPreferences.toDisplayPreferences() }
        val systemDark = isSystemInDarkTheme()
        val darkTheme = when (themeMode) {
            AppThemeMode.SYSTEM -> systemDark
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
        }

        DisposableEffect(Unit) {
            reportExportStatus = { storageToolsStatus = it }
            reportActionBusy = { busy -> if (!busy) actionInProgress = null }
            reportNotificationPermission = { granted ->
                notificationPermissionGranted = granted
                medicineStatus = if (granted) {
                    "Medicine notifications enabled"
                } else {
                    "Medicine notifications are not enabled"
                }
            }
            reportOverlayPermission = { granted ->
                medicineOverlayPermissionGranted = granted
                medicineStatus = if (granted) {
                    "Overlay popup enabled"
                } else {
                    "Overlay permission is needed for direct popups."
                }
            }
            reportExactAlarmAccess = { granted ->
                medicineExactAlarmAccessGranted = granted
                medicineStatus = if (granted) {
                    "Exact alarm access enabled"
                } else {
                    "Exact alarm access is needed for Alarm mode."
                }
                if (granted) {
                    scope.launch {
                        MedicineReminderScheduler.scheduleAll(
                            this@MainActivity,
                            medicineRepository,
                            activeProfile.id
                        )
                    }
                }
            }
            onDispose {
                reportExportStatus = null
                reportActionBusy = null
                reportNotificationPermission = null
                reportOverlayPermission = null
                reportExactAlarmAccess = null
            }
        }

        suspend fun loadLocalHealthStatus(force: Boolean) {
            val now = System.currentTimeMillis()
            val cacheIsFresh =
                localHealthStatus != null &&
                    now - localHealthStatusLoadedAt < VISIBLE_DATA_CACHE_TTL_MILLIS
            if (!force && cacheIsFresh) return

            runCatching { dashboardQueries.localHealthStatus() }
                .onSuccess {
                    localHealthStatus = it
                    localHealthStatusLoadedAt = now
                }
                .onFailure { Log.w(TAG, "Local health status refresh failed", it) }
        }

        suspend fun loadMedicineSnapshot(force: Boolean) {
            val now = System.currentTimeMillis()
            val zoneId = displayPreferences.zoneId
            val cacheIsFresh =
                medicineSnapshotLoadedAt > 0L &&
                    medicineSnapshotZoneId == zoneId.id &&
                    now - medicineSnapshotLoadedAt < VISIBLE_DATA_CACHE_TTL_MILLIS
            if (!force && cacheIsFresh) return

            runCatching { medicineRepository.snapshot(zoneId) }
                .onSuccess {
                    medicineSnapshot = it
                    medicineSnapshotLoadedAt = now
                    medicineSnapshotZoneId = zoneId.id
                }
                .onFailure {
                    medicineStatus = "Medicine load failed: ${it.message}"
                    Log.w(TAG, "Medicine snapshot refresh failed", it)
                }
        }

        suspend fun loadUploadPendingCounts(settings: UploadSettings, force: Boolean) {
            val now = System.currentTimeMillis()
            val cacheIsFresh =
                uploadPendingCountsSettings == settings &&
                    now - uploadPendingCountsLoadedAt < VISIBLE_DATA_CACHE_TTL_MILLIS
            if (!force && cacheIsFresh) return

            // Exact pending counts scan the large upload tables; mutations call this with force=true.
            runCatching { uploadService.pendingCounts(settings) }
                .onSuccess { counts ->
                    uploadPendingCounts = counts
                    uploadPendingCountsLoadedAt = now
                    uploadPendingCountsSettings = settings
                }
                .onFailure { throwable ->
                    if (uploadPendingCountsSettings != settings) {
                        uploadPendingCounts = UploadPendingCounts.Empty
                    }
                    Log.w(TAG, "Upload pending-count refresh failed", throwable)
                }
        }

        fun refreshLocalStatus() {
            scope.launch { loadLocalHealthStatus(force = true) }
        }

        fun invalidateDataCatalog(recordTypes: Set<String>? = null) {
            scope.launch {
                catalogQueries.invalidate(recordTypes, displayPreferences.zoneId)
            }
            dataCatalogRevision++
        }

        fun refreshUploadStatus() {
            uploadStatus = AppPreferences.uploadStatus(this@MainActivity, activeProfile.id)
            scope.launch {
                loadUploadPendingCounts(uploadSettings, force = true)
            }
        }

        fun refreshMedicineSnapshot() {
            scope.launch { loadMedicineSnapshot(force = true) }
        }

        fun refreshMedicineAndReminders() {
            scope.launch {
                loadMedicineSnapshot(force = true)
                runCatching {
                    MedicineReminderScheduler.scheduleAll(
                        context = this@MainActivity,
                        repository = medicineRepository,
                        zoneId = displayPreferences.zoneId
                    )
                }.onFailure { throwable ->
                    medicineStatus = "Medicine reminder schedule failed: ${throwable.message}"
                }
            }
        }

        suspend fun refreshHealthConnectAccess() {
            val granted = grantedHealthConnectPermissions()
            grantedPermissions = if (activeProfile.ownsHealthConnect) granted else hcPermissions
            hcGranted = granted.containsAll(hcPermissions)
            hrHcGranted = HR_PERMISSION in granted
            backgroundReadAvailable = syncService.backgroundReadFeatureAvailable()
            backgroundReadGranted = HealthDataTypeRegistry.backgroundReadPermission in granted
        }

        fun ensureBackgroundSyncScheduled() {
            if (!activeProfile.ownsHealthConnect) return
            if (!PeriodicSyncPreferences.isEnabled(this@MainActivity)) {
                PeriodicHealthSyncWorker.suspendSchedule(this@MainActivity)
                return
            }
            if (!backgroundReadAvailable || !backgroundReadGranted) {
                // Keep the user's enabled preference, but do not wake a worker that cannot read data.
                PeriodicHealthSyncWorker.suspendSchedule(this@MainActivity)
                return
            }
            PeriodicHealthSyncWorker.refreshScheduleIfEnabled(this@MainActivity)
            PeriodicHealthSyncWorker.enqueueImmediateIfStale(this@MainActivity)
        }

        suspend fun refreshVisibleDestination(destination: AppDestination) {
            when (destination) {
                AppDestination.Data -> {
                    loadLocalHealthStatus(force = false)
                }
                AppDestination.Medicine -> {
                    loadMedicineSnapshot(force = false)
                    notificationPermissionGranted = hasNotificationPermission()
                }
                is AppDestination.SettingsSection -> when (destination.section) {
                    SettingsDestination.HealthConnect -> {
                        loadUploadPendingCounts(uploadSettings, force = false)
                    }
                    SettingsDestination.Medicine -> {
                        loadMedicineSnapshot(force = false)
                    }
                    SettingsDestination.General,
                    SettingsDestination.StorageAndTools -> Unit
                }
                AppDestination.Dashboard,
                is AppDestination.DataDetail,
                AppDestination.Settings -> Unit
            }
        }

        fun addMedicine(name: String, slots: Set<MedicineSlot>) {
            scope.launch {
                val result = medicineRepository.addMedicine(name, slots)
                medicineStatus = result.message
                if (result.success) refreshMedicineAndReminders()
            }
        }

        fun archiveMedicine(medicineLocalId: Long) {
            scope.launch {
                val result = medicineRepository.archiveMedicine(medicineLocalId)
                medicineStatus = result.message
                if (result.success) refreshMedicineAndReminders()
            }
        }

        fun saveMedicineReminder(
            slot: MedicineSlot,
            rawTime: String,
            enabled: Boolean,
            alarmEnabled: Boolean
        ) {
            scope.launch {
                val result = medicineRepository.saveReminderTime(slot, rawTime, enabled, alarmEnabled)
                medicineStatus = result.message
                if (result.success) refreshMedicineAndReminders()
            }
        }

        fun logMedicineDose(
            slot: MedicineSlot,
            doseStatus: MedicineDoseStatus,
            medicineIds: Set<Long>,
            extraMedicineName: String?
        ) {
            scope.launch {
                val result = medicineRepository.logDose(
                    slot = slot,
                    status = doseStatus,
                    medicineLocalIds = medicineIds,
                    extraMedicineName = extraMedicineName,
                    source = MedicineLogSource.MANUAL,
                    zoneId = displayPreferences.zoneId
                )
                medicineStatus = result.message
                refreshMedicineSnapshot()
            }
        }

        fun deleteMedicineDoseLogs(logIds: Set<Long>) {
            scope.launch {
                val result = medicineRepository.deleteDoseLogs(logIds)
                medicineStatus = result.message
                if (result.success) refreshMedicineSnapshot()
            }
        }

        fun requestMedicineNotifications() {
            if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                notificationPermissionGranted = true
                medicineStatus = "Medicine notifications enabled"
            }
        }

        fun setMedicineOverlayReminder(enabled: Boolean) {
            AppPreferences.setMedicineOverlayReminderEnabled(this@MainActivity, enabled)
            medicineOverlayReminderEnabled = enabled
            medicineOverlayPermissionGranted = hasMedicineOverlayPermission()
            medicineStatus = if (!enabled) {
                "Overlay popup off"
            } else if (medicineOverlayPermissionGranted) {
                "Overlay popup enabled"
            } else {
                "Overlay permission is needed for direct popups."
            }
            if (enabled && !medicineOverlayPermissionGranted) {
                requestOverlayPermission.launch(medicineOverlayPermissionIntent())
            }
        }

        fun requestMedicineOverlayPermission() {
            requestOverlayPermission.launch(medicineOverlayPermissionIntent())
        }

        fun requestMedicineExactAlarmAccess() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                medicineExactAlarmAccessGranted = true
                return
            }
            runCatching {
                requestExactAlarmAccess.launch(medicineExactAlarmAccessIntent())
            }.onFailure { error ->
                Log.w(TAG, "Could not open exact-alarm settings", error)
                medicineStatus = "Exact alarm settings are unavailable"
            }
        }

        fun queueAutoUpload(settings: UploadSettings): String? =
            if (!activeProfile.ownsHealthConnect && settings.profileCredential == null) {
                "Upload is unavailable for this local profile"
            } else {
            when (val decision = UploadAutoQueuePolicy.decide(settings)) {
                UploadAutoQueueDecision.Disabled -> null
                is UploadAutoQueueDecision.Queue -> {
                    HealthUploadWorker.enqueue(this@MainActivity, activeProfile.id)
                    "Auto upload queued"
                }
                is UploadAutoQueueDecision.Invalid ->
                    "Auto upload not queued: ${decision.reason}"
            }
            }

        fun redeemPairing(pairing: UploadPairingCode) {
            scope.launch {
                if (actionInProgress != null) {
                    uploadActionStatus = "Another action is still running"
                    return@launch
                }
                actionInProgress = AppAction.UPLOAD_PAIRING
                uploadActionStatus = "Pairing with server..."
                try {
                    val clientDeviceId = ProfilePairingStore.clientDeviceId(
                        context = this@MainActivity,
                        localProfileId = activeProfile.id,
                        legacyDeviceId = uploadSettings.deviceId,
                        useLegacyId = activeProfile.ownsHealthConnect
                    )
                    when (
                        val result = pairingService.redeem(
                            pairing = pairing,
                            clientDeviceId = clientDeviceId,
                            deviceName = Build.MODEL,
                            appVersion = packageManager
                                .getPackageInfo(packageName, 0)
                                .versionName
                                .orEmpty()
                        )
                    ) {
                        is UploadPairingResult.Success -> {
                            val credential = result.credential
                            ProfilePairingStore.save(this@MainActivity, activeProfile.id, credential)
                            val pairedSettings = uploadSettings.copy(
                                serverMode = credential.serverMode,
                                productionBaseUrl = if (
                                    credential.serverMode == UploadServerMode.PRODUCTION
                                ) {
                                    credential.uploadBaseUrl
                                } else {
                                    uploadSettings.productionBaseUrl
                                },
                                localBaseUrl = if (
                                    credential.serverMode == UploadServerMode.LOCAL_DEBUG
                                ) {
                                    credential.uploadBaseUrl
                                } else {
                                    uploadSettings.localBaseUrl
                                },
                                apiKey = "",
                                profileCredential = credential
                            )
                            uploadSettings = pairedSettings
                            AppPreferences.setUploadSettings(this@MainActivity, pairedSettings, activeProfile.id)
                            if (credential.serverMode == UploadServerMode.LOCAL_DEBUG) {
                                debugEnabled = true
                                AppPreferences.setDebugModeEnabled(this@MainActivity, true)
                            }
                            uploadStatus = uploadStatus.copy(
                                serverMode = credential.serverMode,
                                connectionResult = "Paired ${credential.serverProfileName}",
                                severity = UploadResultSeverity.SUCCESS
                            )
                            AppPreferences.setUploadStatus(this@MainActivity, uploadStatus, activeProfile.id)
                            uploadActionStatus = "Paired ${credential.serverProfileName}"
                            refreshUploadStatus()
                        }
                        is UploadPairingResult.Failure -> {
                            uploadActionStatus = "Pairing failed: ${result.message}"
                        }
                    }
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    Log.e(TAG, "Pairing failed", t)
                    uploadActionStatus = "Pairing failed: ${t.message ?: t.javaClass.simpleName}"
                } finally {
                    actionInProgress = null
                }
            }
        }

        fun applyScannedUploadText(rawText: String) {
            when (val result = UploadScanPolicy.applyScannedText(uploadSettings, rawText)) {
                is UploadScanApplyResult.Success -> {
                    ProfilePairingStore.clear(this@MainActivity, activeProfile.id)
                    val legacyResult = result.copy(
                        settings = result.settings.copy(profileCredential = null)
                    )
                    val update = UploadDebugModePolicy.applyScanSuccess(
                        currentStatus = uploadStatus,
                        debugEnabled = debugEnabled,
                        success = legacyResult
                    )
                    uploadSettings = update.settings
                    uploadStatus = update.status
                    debugEnabled = update.debugEnabled
                    AppPreferences.setUploadSettings(this@MainActivity, update.settings, activeProfile.id)
                    AppPreferences.setUploadStatus(this@MainActivity, update.status, activeProfile.id)
                    AppPreferences.setDebugModeEnabled(this@MainActivity, update.debugEnabled)
                    uploadActionStatus = update.message
                    refreshUploadStatus()
                }
                is UploadScanApplyResult.Redeem -> redeemPairing(result.pairing)
                is UploadScanApplyResult.Invalid -> {
                    uploadActionStatus = "QR scan failed: ${result.message}"
                }
            }
        }

        fun scanUploadQr() {
            uploadActionStatus = "Opening pairing scanner..."
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            GmsBarcodeScanning.getClient(this@MainActivity, options)
                .startScan()
                .addOnSuccessListener { barcode ->
                    val rawValue = barcode.rawValue?.trim()
                    if (rawValue.isNullOrBlank()) {
                        uploadActionStatus = "QR scan failed: empty code"
                    } else {
                        applyScannedUploadText(rawValue)
                    }
                }
                .addOnCanceledListener {
                    uploadActionStatus = "Pairing scan cancelled"
                }
                .addOnFailureListener { throwable ->
                    uploadActionStatus = "QR scan failed: ${throwable.message ?: throwable.javaClass.simpleName}"
                }
        }

        fun togglePeriodicSync() {
            actionInProgress = AppAction.PERIODIC_TOGGLE
            if (!activeProfile.ownsHealthConnect) {
                syncStatus = "Health Connect belongs to ${profiles.firstOrNull { it.ownsHealthConnect }?.displayName ?: "another profile"}"
                actionInProgress = null
                return
            }
            if (periodicEnabled) {
                PeriodicHealthSyncWorker.cancel(this@MainActivity)
                periodicEnabled = false
                syncStatus = "Periodic sync disabled"
            } else if (!backgroundReadAvailable) {
                syncStatus = "Background read is unavailable on this device"
            } else if (!backgroundReadGranted) {
                PeriodicSyncPreferences.setEnabled(this@MainActivity, true)
                periodicEnabled = true
                syncStatus = "Grant sync permissions first"
                requestHealthConnectPermissions(setOf(HealthDataTypeRegistry.backgroundReadPermission))
            } else {
                PeriodicHealthSyncWorker.schedule(this@MainActivity)
                periodicEnabled = true
                lastPeriodicSync = PeriodicSyncPreferences.lastFinishedAt(this@MainActivity)
                lastPeriodicStatus = PeriodicSyncPreferences.lastStatus(this@MainActivity)
                lastPeriodicSummary = PeriodicSyncPreferences.lastSummary(this@MainActivity)
                syncStatus = "Periodic sync scheduled"
            }
            actionInProgress = null
        }

        fun runFullResync() {
            fullSyncJob = scope.launch {
                actionInProgress = AppAction.FULL_RESYNC
                syncProgress = SyncProgress.initial(
                    mode = SyncMode.FULL_HISTORY,
                    totalTypes = HealthDataTypeRegistry.implementedDescriptors.size,
                    isCancellable = true,
                    rangeEnd = Instant.now()
                )
                syncStatus = "Running full historical resync..."
                val results = try {
                    syncService.runFullHistorySync { progress ->
                        syncProgress = progress
                        diagnostics.recordSyncProgress(progress)
                    }
                } catch (t: CancellationException) {
                    invalidateDataCatalog()
                    syncStatus = "Full resync cancelled"
                    diagnostics.recordSyncCancelled(SyncMode.FULL_HISTORY)
                    syncProgress = syncProgress?.copy(
                        isCancellable = false,
                        message = "Full resync cancelled"
                    )
                    actionInProgress = null
                    fullSyncJob = null
                    return@launch
                } catch (t: Throwable) {
                    invalidateDataCatalog()
                    syncStatus = "Full resync failed: ${t.message}"
                    diagnostics.recordSyncFailure(SyncMode.FULL_HISTORY, null, null, null)
                    actionInProgress = null
                    fullSyncJob = null
                    return@launch
                }
                invalidateDataCatalog(CatalogRefreshPolicy.changedRecordTypes(results))
                diagnostics.recordSyncResults(SyncMode.FULL_HISTORY, results)
                syncStatus = syncAllStatusText(results, "Full resync")
                loadLocalHealthStatus(force = true)
                refreshUploadStatus()
                actionInProgress = null
                fullSyncJob = null
            }
        }

        fun runBackgroundSyncNow() {
            scope.launch {
                actionInProgress = AppAction.BACKGROUND_NOW
                syncProgress = SyncProgress.initial(
                    mode = SyncMode.PERIODIC,
                    totalTypes = HealthDataTypeRegistry.implementedDescriptors.size,
                    isCancellable = false,
                    rangeEnd = Instant.now()
                )
                syncStatus = "Running background sync now..."
                val results = runCatching {
                    syncService.runPeriodicSmartSync(
                        requireBackgroundReadPermission = backgroundReadGranted
                    ) { progress ->
                        syncProgress = progress
                        diagnostics.recordSyncProgress(progress)
                    }
                }.getOrElse {
                    invalidateDataCatalog()
                    syncStatus = "Background sync failed: ${it.message}"
                    diagnostics.recordSyncFailure(SyncMode.PERIODIC, null, null, null)
                    actionInProgress = null
                    return@launch
                }
                invalidateDataCatalog(CatalogRefreshPolicy.changedRecordTypes(results))
                diagnostics.recordSyncResults(SyncMode.PERIODIC, results)
                val summary = syncAllStatusText(results, "Background sync now")
                PeriodicSyncPreferences.markFinished(
                    this@MainActivity,
                    Instant.now(),
                    if (results.any { it.errorMessage != null || it.terminalStatus == SyncRunStatus.TIMEOUT }) {
                        "partial_error"
                    } else {
                        "success"
                    },
                    summary
                )
                lastPeriodicSync = PeriodicSyncPreferences.lastFinishedAt(this@MainActivity)
                lastPeriodicStatus = PeriodicSyncPreferences.lastStatus(this@MainActivity)
                lastPeriodicSummary = PeriodicSyncPreferences.lastSummary(this@MainActivity)
                syncStatus = summary
                loadLocalHealthStatus(force = true)
                refreshUploadStatus()
                actionInProgress = null
            }
        }

        fun saveUploadSettings(settings: UploadSettings) {
            uploadSettings = settings
            AppPreferences.setUploadSettings(this@MainActivity, settings, activeProfile.id)
            uploadStatus = uploadStatus.copy(serverMode = settings.serverMode)
            AppPreferences.setUploadStatus(this@MainActivity, uploadStatus, activeProfile.id)
            val autoUploadMessage = queueAutoUpload(settings)
            if (autoUploadMessage?.startsWith("Auto upload not queued:") == true) {
                uploadStatus = uploadStatus.copy(
                    connectionResult = autoUploadMessage,
                    severity = UploadResultSeverity.WARNING,
                    serverMode = settings.serverMode
                )
                AppPreferences.setUploadStatus(this@MainActivity, uploadStatus, activeProfile.id)
            }
            uploadActionStatus = autoUploadMessage?.let { "Upload settings saved. $it" }
                ?: "Upload settings saved"
            refreshUploadStatus()
        }

        fun testUploadConnection(settings: UploadSettings) {
            scope.launch {
                try {
                    actionInProgress = AppAction.UPLOAD_TEST
                    uploadSettings = settings
                    AppPreferences.setUploadSettings(this@MainActivity, settings, activeProfile.id)
                    uploadActionStatus = "Testing upload server..."
                    val counts = runCatching { uploadService.pendingCounts(settings) }
                        .onSuccess { pendingCounts ->
                            uploadPendingCounts = pendingCounts
                            uploadPendingCountsLoadedAt = System.currentTimeMillis()
                            uploadPendingCountsSettings = settings
                        }
                        .onFailure {
                            uploadPendingCounts = UploadPendingCounts.Empty
                        }
                        .getOrDefault(UploadPendingCounts.Empty)
                    val result = uploadService.testConnection(settings)
                    uploadStatus = result.toStatus(uploadStatus, counts.total)
                    AppPreferences.setUploadStatus(this@MainActivity, uploadStatus, activeProfile.id)
                    uploadActionStatus = result.message
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    Log.e(TAG, "Upload connection test failed", t)
                    val message = "Upload test failed: ${t.message ?: t.javaClass.simpleName}"
                    uploadStatus = uploadStatus.copy(
                        connectionResult = message,
                        severity = UploadResultSeverity.ERROR
                    )
                    AppPreferences.setUploadStatus(this@MainActivity, uploadStatus, activeProfile.id)
                    uploadActionStatus = message
                } finally {
                    actionInProgress = null
                }
            }
        }

        fun uploadNow(settings: UploadSettings, range: UploadTimeRange) {
            scope.launch {
                try {
                    actionInProgress = AppAction.UPLOAD
                    uploadProgress = null
                    uploadSettings = settings
                    AppPreferences.setUploadSettings(this@MainActivity, settings, activeProfile.id)
                    uploadActionStatus = uploadStartStatus(range)
                    val result = uploadService.uploadPending(settings, range) { progress ->
                        uploadProgress = progress
                    }
                    uploadStatus = result.toStatus()
                    AppPreferences.setUploadStatus(this@MainActivity, uploadStatus, activeProfile.id)
                    uploadPendingCounts = result.pendingCounts
                    uploadPendingCountsLoadedAt = System.currentTimeMillis()
                    uploadPendingCountsSettings = settings
                    UploadRetentionPolicy.productionServerKey(
                        settings = settings,
                        uploadSucceeded = result.success,
                        profileOwnsHealthConnect = activeProfile.ownsHealthConnect
                    )?.let { serverKey ->
                        HealthRetentionWorker.enqueue(
                            this@MainActivity,
                            serverKey,
                            activeProfile.id
                        )
                    }
                    val completion = uploadCompletionStatus(result, range)
                    if (completion.retryAction == UploadRetryAction.QUEUE_ALL) {
                        HealthUploadWorker.enqueue(this@MainActivity, activeProfile.id)
                    }
                    uploadActionStatus = completion.message
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    Log.e(TAG, "Upload failed", t)
                    val message = "Upload failed: ${t.message ?: t.javaClass.simpleName}"
                    uploadStatus = uploadStatus.copy(
                        lastResult = message,
                        severity = UploadResultSeverity.ERROR
                    )
                    AppPreferences.setUploadStatus(this@MainActivity, uploadStatus, activeProfile.id)
                    uploadActionStatus = message
                } finally {
                    uploadProgress = null
                    actionInProgress = null
                }
            }
        }

        fun runSmartSync() {
            if (actionInProgress != null) {
                syncStatus = if (actionInProgress == AppAction.CLEAR_LOCAL_DATA) {
                    "Local data removal is still running"
                } else {
                    "Another action is still running"
                }
                dashboardStatusTone = StatusTone.Info
                return
            }
            scope.launch {
                actionInProgress = AppAction.SMART_SYNC
                syncProgress = SyncProgress.initial(
                    mode = SyncMode.SMART,
                    totalTypes = HealthDataTypeRegistry.implementedDescriptors.size,
                    isCancellable = false,
                    rangeEnd = Instant.now()
                )
                dashboardStatusTone = StatusTone.Info
                syncStatus = "Checking for new Health Connect data..."
                val results = runCatching {
                    syncService.runSmartSync { progress ->
                        syncProgress = progress
                        diagnostics.recordSyncProgress(progress)
                    }
                }.getOrElse {
                    invalidateDataCatalog()
                    syncStatus = "Sync failed: ${it.message}"
                    diagnostics.recordSyncFailure(SyncMode.SMART, null, null, null)
                    dashboardStatusTone = StatusTone.Error
                    actionInProgress = null
                    return@launch
                }
                invalidateDataCatalog(CatalogRefreshPolicy.changedRecordTypes(results))
                diagnostics.recordSyncResults(SyncMode.SMART, results)
                syncStatus = syncAllStatusText(results, "Sync new data")
                dashboardStatusTone = syncResultsStatusTone(results)
                loadLocalHealthStatus(force = true)
                actionInProgress = null
            }
        }

        LaunchedEffect(Unit) {
            try {
                refreshHealthConnectAccess()
                ensureBackgroundSyncScheduled()
                runCatching { catalogQueries.warmCache() }
                    .onFailure { Log.w(TAG, "Catalog cache warmup failed", it) }
                val medicineSeedPending = activeProfile.ownsHealthConnect &&
                    AppPreferences.medicineSeedVersion(
                        context = this@MainActivity,
                        profileId = activeProfile.id
                    ) < MEDICINE_SEED_VERSION
                if (medicineSeedPending) {
                    runCatching {
                        medicineRepository.seedTestingMedicines()
                    }.onSuccess { result ->
                        AppPreferences.setMedicineSeedVersion(
                            context = this@MainActivity,
                            profileId = activeProfile.id,
                            version = MEDICINE_SEED_VERSION
                        )
                        if (result.changedRows > 0) {
                            Log.i(TAG, "Medicine test catalog applied rows=${result.changedRows}")
                        }
                    }.onFailure { throwable ->
                        medicineStatus = "Medicine seed failed: ${throwable.message}"
                        Log.w(TAG, "Medicine seed failed", throwable)
                    }
                }
                runCatching {
                    MedicineReminderScheduler.scheduleAll(
                        context = this@MainActivity,
                        repository = medicineRepository,
                        zoneId = displayPreferences.zoneId
                    )
                }.onFailure { throwable ->
                    medicineStatus = "Medicine reminder schedule failed: ${throwable.message}"
                    Log.w(TAG, "Medicine reminder schedule failed", throwable)
                }
            } finally {
                initialLoadFinished = true
            }
        }

        LaunchedEffect(
            nav.destination,
            displayPreferences.zoneId,
            uploadSettings,
            initialLoadFinished
        ) {
            if (initialLoadFinished) {
                refreshVisibleDestination(nav.destination)
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event != androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    return@LifecycleEventObserver
                }
                platformGranted = hasPlatformPerm()
                debugEnabled = AppPreferences.debugModeEnabled(this@MainActivity)
                periodicEnabled = activeProfile.ownsHealthConnect &&
                    PeriodicSyncPreferences.isEnabled(this@MainActivity)
                val refreshedPeriodicSync = PeriodicSyncPreferences.lastFinishedAt(this@MainActivity)
                val catalogChangedInBackground = refreshedPeriodicSync != lastPeriodicSync
                lastPeriodicSync = refreshedPeriodicSync
                lastPeriodicStatus = PeriodicSyncPreferences.lastStatus(this@MainActivity)
                lastPeriodicSummary = PeriodicSyncPreferences.lastSummary(this@MainActivity)
                uploadStatus = AppPreferences.uploadStatus(this@MainActivity, activeProfile.id)
                medicineOverlayReminderEnabled =
                    AppPreferences.medicineOverlayReminderEnabled(this@MainActivity)
                medicineOverlayPermissionGranted = hasMedicineOverlayPermission()
                val exactAlarmAccess = hasMedicineExactAlarmAccess()
                if (exactAlarmAccess && !medicineExactAlarmAccessGranted) {
                    scope.launch {
                        MedicineReminderScheduler.scheduleAll(
                            this@MainActivity,
                            medicineRepository,
                            activeProfile.id
                        )
                    }
                }
                medicineExactAlarmAccessGranted = exactAlarmAccess
                if (initialLoadFinished) {
                    scope.launch {
                        refreshHealthConnectAccess()
                        ensureBackgroundSyncScheduled()
                        refreshVisibleDestination(nav.destination)
                    }
                }
                if (catalogChangedInBackground) {
                    dataCatalogRevision++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        DisposableEffect(nav) {
            openMedicineFromIntent = { slot ->
                slot?.let { medicineDefaultSlot = it }
                nav.selectTab(AppTab.Medicine)
                refreshMedicineSnapshot()
            }
            onDispose { openMedicineFromIntent = null }
        }

        LaunchedEffect(Unit) {
            if (intent?.action == MedicineReminderIntents.ACTION_OPEN_MEDICINE) {
                medicineSlotFromIntent(intent)?.let { medicineDefaultSlot = it }
                nav.selectTab(AppTab.Medicine)
            }
        }

        BackHandler(enabled = nav.destination != AppDestination.Dashboard) {
            nav.goBack()
        }

        HealthConnectAndroidTheme(darkTheme = darkTheme, palette = themePalette) {
            CompositionLocalProvider(LocalAppLanguage provides userPreferences.language) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    AppTopBar(
                        nav = nav,
                        profileName = activeProfile.displayName,
                        onBack = { nav.goBack() }
                    )
                },
                bottomBar = {
                    if (nav.showBottomBar) {
                        BottomNavigationBar(
                            selectedTab = nav.selectedTab,
                            onSelectTab = nav::selectTab
                        )
                    }
                }
            ) { pad ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .destinationEnterMotion(nav.destination)
                ) {
                when (val destination = nav.destination) {
                    AppDestination.Settings -> {
                        SettingsScreen(
                            periodicEnabled = periodicEnabled,
                            debugEnabled = debugEnabled,
                            onOpenGeneral = { nav.openSettingsSection(SettingsDestination.General) },
                            onOpenMedicine = { nav.openSettingsSection(SettingsDestination.Medicine) },
                            onOpenHealthConnect = {
                                nav.openSettingsSection(SettingsDestination.HealthConnect)
                            },
                            onOpenStorageAndTools = {
                                nav.openSettingsSection(SettingsDestination.StorageAndTools)
                            },
                            modifier = Modifier.padding(pad)
                        )
                    }
                    is AppDestination.SettingsSection -> {
                        when (destination.section) {
                            SettingsDestination.General -> SettingsPreferencesScreen(
                                profiles = profiles,
                                activeProfileId = activeProfile.id,
                                userProfile = userProfile,
                                userPreferences = userPreferences,
                                themeMode = themeMode,
                                themePalette = themePalette,
                                onUserProfileSave = { profile ->
                                    userProfile = profile
                                    AppPreferences.setUserProfile(this@MainActivity, profile)
                                    profiles = LocalProfileStore.profiles(this@MainActivity)
                                },
                                onSwitchProfile = { profileId ->
                                    if (profileId != activeProfile.id &&
                                        LocalProfileStore.setActive(this@MainActivity, profileId)
                                    ) {
                                        recreate()
                                    }
                                },
                                onCreateProfile = { displayName ->
                                    when (val result = LocalProfileStore.create(this@MainActivity, displayName)) {
                                        is CreateProfileResult.Created -> {
                                            LocalProfileStore.setActive(this@MainActivity, result.profile.id)
                                            recreate()
                                            true
                                        }
                                        is CreateProfileResult.Rejected -> false
                                    }
                                },
                                onUserPreferencesSave = { preferences ->
                                    userPreferences = preferences
                                    AppPreferences.setUserPreferences(this@MainActivity, preferences)
                                },
                                onThemeModeChange = { mode ->
                                    themeMode = mode
                                    AppPreferences.setThemeMode(this@MainActivity, mode)
                                },
                                onThemePaletteChange = { palette ->
                                    themePalette = palette
                                    AppPreferences.setThemePalette(this@MainActivity, palette)
                                },
                                modifier = Modifier.padding(pad)
                            )
                            SettingsDestination.HealthConnect -> SettingsHealthConnectScreen(
                                profileOwnsHealthConnect = activeProfile.ownsHealthConnect,
                                healthConnectOwnerName = profiles
                                    .firstOrNull { it.ownsHealthConnect }
                                    ?.displayName,
                                declaredReadHr = declared,
                                platformGranted = platformGranted,
                                hcGranted = hcGranted,
                                backgroundReadStatus = backgroundReadStatusText(
                                    backgroundReadAvailable,
                                    backgroundReadGranted
                                ),
                                dataPermissionSummary = dataPermissionSummaryText(grantedPermissions),
                                backgroundReadAvailable = backgroundReadAvailable,
                                periodicEnabled = periodicEnabled,
                                backgroundReadGranted = backgroundReadGranted,
                                lastPeriodicSync = lastPeriodicSync,
                                lastPeriodicStatus = lastPeriodicStatus,
                                lastPeriodicSummary = lastPeriodicSummary,
                                syncStatus = syncStatus,
                                uploadActionStatus = uploadActionStatus,
                                syncBusy = actionInProgress?.blocksSyncSettings == true,
                                syncProgress = syncProgress,
                                uploadSettings = uploadSettings,
                                uploadStatus = uploadStatus,
                                uploadPendingCounts = uploadPendingCounts,
                                debugEnabled = debugEnabled,
                                uploadBusy = actionInProgress?.blocksUpload == true,
                                uploadProgress = uploadProgress,
                                onRequestPlatform = {
                                    requestHealthConnectPermissions(setOf(HR_PERMISSION))
                                },
                                onRequestDataPermissions = { requestHealthConnectPermissions(hcPermissions) },
                                onRequestBackgroundRead = {
                                    requestHealthConnectPermissions(setOf(HealthDataTypeRegistry.backgroundReadPermission))
                                },
                                openAppSettings = { startActivity(it) },
                                packageName = packageName,
                                onTogglePeriodic = ::togglePeriodicSync,
                                onFullResync = ::runFullResync,
                                onCancelFullResync = {
                                    syncStatus = "Cancelling full resync..."
                                    fullSyncJob?.cancel()
                                },
                                onRunBackgroundNow = ::runBackgroundSyncNow,
                                onSaveUploadSettings = ::saveUploadSettings,
                                onTestUploadConnection = ::testUploadConnection,
                                onUploadNow = ::uploadNow,
                                onScanPairingQr = ::scanUploadQr,
                                modifier = Modifier.padding(pad)
                            )
                            SettingsDestination.Medicine -> SettingsMedicineScreen(
                                snapshot = medicineSnapshot,
                                status = medicineStatus,
                                overlayReminderEnabled = medicineOverlayReminderEnabled,
                                overlayPermissionGranted = medicineOverlayPermissionGranted,
                                exactAlarmAccessGranted = medicineExactAlarmAccessGranted,
                                onAddMedicine = ::addMedicine,
                                onArchiveMedicine = ::archiveMedicine,
                                onSaveReminder = ::saveMedicineReminder,
                                onOverlayReminderChange = ::setMedicineOverlayReminder,
                                onRequestOverlayPermission = ::requestMedicineOverlayPermission,
                                onRequestExactAlarmAccess = ::requestMedicineExactAlarmAccess,
                                modifier = Modifier.padding(pad)
                            )
                            SettingsDestination.StorageAndTools -> SettingsStorageToolsScreen(
                                modifier = Modifier.padding(pad),
                                debugEnabled = debugEnabled,
                                platformGranted = platformGranted,
                                hrHcGranted = hrHcGranted,
                                status = storageToolsStatus,
                                diagnostics = diagnostics,
                                dataManagementBusy = actionInProgress?.blocksDataManagement == true,
                                onExportHrCsv = {
                                    actionInProgress = AppAction.EXPORT_HR
                                    storageToolsStatus = "Choose heart-rate CSV destination..."
                                    createHrCsv.launch("hr_export_${exportFileStamp()}.csv")
                                },
                                onExportAllCsv = {
                                    actionInProgress = AppAction.EXPORT_ALL
                                    storageToolsStatus = "Choose all-data CSV destination..."
                                    createAllCsv.launch("health_connect_all_${exportFileStamp()}.csv")
                                },
                                onExportZip = {
                                    actionInProgress = AppAction.EXPORT_ZIP
                                    storageToolsStatus = "Choose ZIP destination..."
                                    createCsvZip.launch("health_connect_csv_${exportFileStamp()}.zip")
                                },
                                onToggleDebug = {
                                    val nextDebugEnabled = !debugEnabled
                                    val update = UploadDebugModePolicy.setDebugMode(
                                        currentSettings = uploadSettings,
                                        currentStatus = uploadStatus,
                                        enabled = nextDebugEnabled
                                    )
                                    debugEnabled = update.debugEnabled
                                    uploadSettings = update.settings
                                    uploadStatus = update.status
                                    AppPreferences.setDebugModeEnabled(this@MainActivity, update.debugEnabled)
                                    AppPreferences.setUploadSettings(
                                        this@MainActivity,
                                        update.settings,
                                        activeProfile.id
                                    )
                                    AppPreferences.setUploadStatus(
                                        this@MainActivity,
                                        update.status,
                                        activeProfile.id
                                    )
                                    storageToolsStatus = update.message
                                },
                                onSyncHours = { hours ->
                                    scope.launch {
                                        storageToolsStatus = "Syncing heart rate for last ${hours}h..."
                                        storageToolsStatus = runCatching { syncService.runLegacyHrDebugSync(hours) }
                                            .fold(
                                                onSuccess = {
                                                    diagnostics.recordLegacyHrDebug(it)
                                                    "Synced heart rate for last ${hours}h: $it samples"
                                                },
                                                onFailure = {
                                                    diagnostics.recordSyncFailure(SyncMode.LEGACY_HR_DEBUG, "heart_rate", null, null)
                                                    "Heart-rate sync failed: ${it.message}"
                                                }
                                            )
                                        invalidateDataCatalog(setOf(com.example.healthconnectandroid.hc.HealthDataTypeKeys.HEART_RATE))
                                        refreshLocalStatus()
                                    }
                                },
                                onQuery = { at ->
                                    scope.launch {
                                        storageToolsStatus = "Querying heart rate..."
                                        storageToolsStatus = runCatching { syncService.queryLegacyHeartRate(at) }
                                            .fold(
                                                onSuccess = {
                                                    it?.let { "${it.bpm} bpm at ${it.time}" }
                                                        ?: "No nearby heart-rate sample"
                                                },
                                                onFailure = { "Heart-rate query failed: ${it.message}" }
                                            )
                                    }
                                },
                                onRequestClear = { showLocalDataRemoval = true }
                            )
                        }
                    }
                    AppDestination.Data -> {
                        DataSyncScreen(
                            modifier = Modifier.padding(pad),
                            localHealthStatus = localHealthStatus,
                            grantedPermissions = grantedPermissions,
                            backgroundReadAvailable = backgroundReadAvailable,
                            backgroundReadGranted = backgroundReadGranted,
                            periodicEnabled = periodicEnabled,
                            status = syncStatus,
                            statusTone = dashboardStatusTone,
                            syncing = actionInProgress == AppAction.SMART_SYNC,
                            syncProgress = syncProgress,
                            displayPreferences = displayPreferences,
                            onSyncAll = ::runSmartSync
                        )
                    }
                    AppDestination.Medicine -> {
                        MedicineScreen(
                            snapshot = medicineSnapshot,
                            zoneId = displayPreferences.zoneId,
                            weekStart = displayPreferences.weekStart,
                            defaultSlot = medicineDefaultSlot,
                            notificationPermissionGranted = notificationPermissionGranted,
                            status = medicineStatus,
                            onLogDose = ::logMedicineDose,
                            onDeleteDoseLogs = ::deleteMedicineDoseLogs,
                            onRequestNotificationPermission = ::requestMedicineNotifications,
                            modifier = Modifier.padding(pad)
                        )
                    }
                    is AppDestination.DataDetail -> {
                        HealthDataDetailScreen(
                            detailQueries = detailQueries,
                            recordQueries = recordQueries,
                            dataTypeKey = destination.dataTypeKey,
                            grantedPermissions = grantedPermissions,
                            userAge = userAge,
                            userPreferences = userPreferences,
                            diagnostics = diagnostics,
                            showDiagnostics = debugEnabled,
                            onExportType = { key ->
                                actionInProgress = AppAction.EXPORT_TYPE
                                storageToolsStatus = "Choose ${key} CSV destination..."
                                pendingTypeExportKey = key
                                createTypeCsv.launch("${key}_${exportFileStamp()}.csv")
                            },
                            runSelectedTypeSync = syncService::runSelectedTypeSync,
                            onLocalDataChanged = {
                                invalidateDataCatalog(setOf(destination.dataTypeKey))
                                refreshLocalStatus()
                            },
                            modifier = Modifier.padding(pad)
                        )
                    }
                    AppDestination.Dashboard -> {
                        DataCatalogScreen(
                            catalogQueries = catalogQueries,
                            grantedPermissions = grantedPermissions,
                            displayPreferences = displayPreferences,
                            dataRevision = dataCatalogRevision,
                            onOpenDetail = nav::openDataDetail,
                            modifier = Modifier.padding(pad),
                        )
                    }
                }
                }
            }

            if (showLocalDataRemoval) {
                LocalDataRemovalDialog(
                    busy = actionInProgress == AppAction.CLEAR_LOCAL_DATA,
                    progress = localDataRemovalProgress,
                    onDismiss = { showLocalDataRemoval = false },
                    onConfirm = { retention ->
                        actionInProgress = AppAction.CLEAR_LOCAL_DATA
                        localDataRemovalProgress = LocalDataRemovalProgress(LocalDataRemovalPhase.PREPARING)
                        scope.launch {
                            try {
                                val result = localDataService.removeHealthData(
                                    retention = retention,
                                    zoneId = displayPreferences.zoneId,
                                    onProgress = { progress ->
                                        withContext(Dispatchers.Main.immediate) {
                                            localDataRemovalProgress = progress
                                        }
                                    }
                                )
                                invalidateDataCatalog()
                                loadLocalHealthStatus(force = true)
                                HealthDatabaseCompactionWorker.enqueue(this@MainActivity)
                                val keptRange = translateUiText(retention.label, userPreferences.language)
                                storageToolsStatus = if (userPreferences.language == AppLanguagePreference.CHINESE_SIMPLIFIED) {
                                    "本地数据已释放。保留范围：$keptRange。已删除 ${result.recordsRemoved} 条健康记录。"
                                } else {
                                    "Local data released. Kept range: $keptRange. " +
                                        "Removed ${result.recordsRemoved} health records."
                                }
                                showLocalDataRemoval = false
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                storageToolsStatus = if (userPreferences.language == AppLanguagePreference.CHINESE_SIMPLIFIED) {
                                    "释放本地数据失败：${error.message ?: "未知错误"}"
                                } else {
                                    "Local data removal failed: ${error.message ?: "unknown error"}"
                                }
                            } finally {
                                actionInProgress = null
                                localDataRemovalProgress = null
                            }
                        }
                    }
                )
            }
            }
        }
    }

    private fun exportFileStamp(): String =
        java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))

    private fun requestHealthConnectPermissions(permissions: Set<String>) {
        if (permissions.isEmpty()) return
        if (healthConnectClientOrNull() == null) return
        requestHcPermissions.launch(permissions)
    }

    private suspend fun grantedHealthConnectPermissions(): Set<String> {
        val client = healthConnectClientOrNull() ?: return emptySet()
        return runCatching { client.permissionController.getGrantedPermissions() }
            .onFailure { Log.e(TAG, "Health Connect permission check failed", it) }
            .getOrDefault(emptySet())
    }

    private fun healthConnectClientOrNull(): HealthConnectClient? {
        if (!healthConnectEnabledForProfile) return null
        hcClient?.let { return it }
        return runCatching { HealthConnectClient.getOrCreate(this) }
            .onFailure { Log.e(TAG, "Health Connect client unavailable", it) }
            .getOrNull()
            ?.also { hcClient = it }
    }

    private fun dataPermissionSummaryText(grantedPermissions: Set<String>): String {
        val total = hcPermissions.size
        val granted = hcPermissions.count { it in grantedPermissions }
        val missing = total - granted
        return "$granted of $total supported data permissions granted; $missing missing"
    }

}

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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.healthconnectandroid.debug.DeviceSmokeDiagnostics
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.hc.LocalHealthStatus
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import com.example.healthconnectandroid.hc.PeriodicHealthSyncWorker
import com.example.healthconnectandroid.hc.PeriodicSyncPreferences
import com.example.healthconnectandroid.hc.buildHcPermissionIntent
import com.example.healthconnectandroid.hc.export.HealthCsvExporter
import com.example.healthconnectandroid.hc.export.HealthZipExporter
import com.example.healthconnectandroid.hc.local.LocalDataService
import com.example.healthconnectandroid.hc.query.HealthDashboardQueryService
import com.example.healthconnectandroid.hc.query.HealthDataCatalogQueryService
import com.example.healthconnectandroid.hc.query.HealthDetailQueryService
import com.example.healthconnectandroid.hc.query.HealthRecordDetailQueryService
import com.example.healthconnectandroid.hc.sync.HealthSyncService
import com.example.healthconnectandroid.hc.sync.SyncMode
import com.example.healthconnectandroid.hc.sync.SyncProgress
import com.example.healthconnectandroid.hc.sync.SyncRunStatus
import com.example.healthconnectandroid.hc.sync.syncAllStatusText
import com.example.healthconnectandroid.hc.upload.HealthUploadService
import com.example.healthconnectandroid.hc.upload.HealthUploadWorker
import com.example.healthconnectandroid.hc.upload.UploadAutoQueueDecision
import com.example.healthconnectandroid.hc.upload.UploadAutoQueuePolicy
import com.example.healthconnectandroid.hc.upload.UploadDebugModePolicy
import com.example.healthconnectandroid.hc.upload.UploadPendingCounts
import com.example.healthconnectandroid.hc.upload.UploadProgress
import com.example.healthconnectandroid.hc.upload.UploadResultSeverity
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
import com.example.healthconnectandroid.ui.data.DataCatalogScreen
import com.example.healthconnectandroid.ui.data.HealthDataDetailScreen
import com.example.healthconnectandroid.ui.dashboard.DashboardScreen
import com.example.healthconnectandroid.ui.medicine.MedicineScreen
import com.example.healthconnectandroid.ui.settings.SettingsAdvancedScreen
import com.example.healthconnectandroid.ui.settings.SettingsDataFlowScreen
import com.example.healthconnectandroid.ui.settings.SettingsMedicineScreen
import com.example.healthconnectandroid.ui.settings.SettingsPreferencesScreen
import com.example.healthconnectandroid.ui.settings.SettingsScreen
import com.example.healthconnectandroid.ui.format.toDisplayPreferences
import com.example.healthconnectandroid.ui.i18n.LocalAppLanguage
import com.example.healthconnectandroid.ui.i18n.uiText
import com.example.healthconnectandroid.ui.theme.HealthConnectAndroidTheme
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.UploadRetryAction
import com.example.healthconnectandroid.ui.syncResultsStatusTone
import com.example.healthconnectandroid.ui.uploadCompletionStatus
import com.example.healthconnectandroid.ui.uploadStartStatus
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val HR_PERMISSION = HealthDataTypeRegistry.heartRate.requiredReadPermission
    ?: error("Heart rate record must expose a Health Connect read permission")
private const val TAG = "HealthConnect"

class MainActivity : ComponentActivity() {
    private var pendingTypeExportKey: String? = null
    private var reportExportStatus: ((String) -> Unit)? = null
    private var reportActionBusy: ((Boolean) -> Unit)? = null
    private var reportNotificationPermission: ((Boolean) -> Unit)? = null
    private var reportOverlayPermission: ((Boolean) -> Unit)? = null
    private var openMedicineFromIntent: ((MedicineSlot?) -> Unit)? = null

    private val requestHrPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Permission dialogs can return before Health Connect state settles; resume refresh is the stable source.
        }

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
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || AndroidSettings.canDrawOverlays(this)

    private fun medicineOverlayPermissionIntent(): Intent =
        Intent(
            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )

    private fun medicineSlotFromIntent(intent: Intent?): MedicineSlot? {
        if (intent?.action != MedicineReminderIntents.ACTION_OPEN_MEDICINE) return null
        return intent.getStringExtra(MedicineReminderIntents.EXTRA_SLOT)
            ?.let(MedicineSlot::fromId)
    }

    private var hcClient: HealthConnectClient? = null
    private val hcPermissions = HealthDataTypeRegistry.implementedReadPermissions
    private val requestHcPermissions =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
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

        hcClient = healthConnectClientOrNull()
        if (!hasHrPermission()) requestHrPermission.launch(HR_PERMISSION)

        val db = AppDb.get(this)
        val dashboardQueries = HealthDashboardQueryService(db)
        val catalogQueries = HealthDataCatalogQueryService(db)
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
        manifestDeclares: () -> Boolean,
        hasPlatformPerm: () -> Boolean
    ) {
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current

        var platformGranted by remember { mutableStateOf(hasPlatformPerm()) }
        val declared = remember { manifestDeclares() }
        var hcGranted by remember { mutableStateOf(false) }
        var hrHcGranted by remember { mutableStateOf(false) }
        var backgroundReadAvailable by remember { mutableStateOf(false) }
        var backgroundReadGranted by remember { mutableStateOf(false) }
        var grantedPermissions by remember { mutableStateOf<Set<String>>(emptySet()) }
        var periodicEnabled by remember {
            mutableStateOf(PeriodicSyncPreferences.isEnabled(this@MainActivity))
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
        var status by remember { mutableStateOf("Ready") }
        var dashboardStatusTone by remember { mutableStateOf(StatusTone.Neutral) }
        var actionInProgress by remember { mutableStateOf<AppAction?>(null) }
        var syncProgress by remember { mutableStateOf<SyncProgress?>(null) }
        var fullSyncJob by remember { mutableStateOf<Job?>(null) }
        var showClearConfirm by remember { mutableStateOf(false) }
        var debugEnabled by remember { mutableStateOf(AppPreferences.debugModeEnabled(this@MainActivity)) }
        var themeMode by remember { mutableStateOf(AppPreferences.themeMode(this@MainActivity)) }
        var themePalette by remember { mutableStateOf(AppPreferences.themePalette(this@MainActivity)) }
        var userProfile by remember { mutableStateOf(AppPreferences.userProfile(this@MainActivity)) }
        var userPreferences by remember { mutableStateOf(AppPreferences.userPreferences(this@MainActivity)) }
        var uploadSettings by remember { mutableStateOf(AppPreferences.uploadSettings(this@MainActivity)) }
        var uploadStatus by remember { mutableStateOf(AppPreferences.uploadStatus(this@MainActivity)) }
        var uploadPendingCounts by remember { mutableStateOf(UploadPendingCounts.Empty) }
        var uploadProgress by remember { mutableStateOf<UploadProgress?>(null) }
        var medicineSnapshot by remember { mutableStateOf(EmptyMedicineSnapshot) }
        var medicineStatus by remember { mutableStateOf("Medicine ready") }
        var notificationPermissionGranted by remember { mutableStateOf(hasNotificationPermission()) }
        var medicineOverlayReminderEnabled by remember {
            mutableStateOf(AppPreferences.medicineOverlayReminderEnabled(this@MainActivity))
        }
        var medicineOverlayPermissionGranted by remember { mutableStateOf(hasMedicineOverlayPermission()) }
        var medicineDefaultSlot by remember {
            mutableStateOf(medicineSlotFromIntent(intent) ?: MedicineSlot.MORNING)
        }
        val diagnostics = remember { DeviceSmokeDiagnostics() }
        val userAge = userProfile.age
        val displayPreferences = remember(userPreferences) { userPreferences.toDisplayPreferences() }
        val systemDark = isSystemInDarkTheme()
        val darkTheme = when (themeMode) {
            AppThemeMode.SYSTEM -> systemDark
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
        }

        DisposableEffect(Unit) {
            reportExportStatus = { status = it }
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
            onDispose {
                reportExportStatus = null
                reportActionBusy = null
                reportNotificationPermission = null
                reportOverlayPermission = null
            }
        }

        fun refreshLocalStatus() {
            scope.launch {
                localHealthStatus = runCatching { dashboardQueries.localHealthStatus() }.getOrNull()
            }
        }

        fun refreshUploadStatus() {
            uploadStatus = AppPreferences.uploadStatus(this@MainActivity)
            scope.launch {
                uploadPendingCounts = runCatching { uploadService.pendingCounts(uploadSettings) }
                    .getOrDefault(UploadPendingCounts.Empty)
            }
        }

        fun refreshMedicineSnapshot() {
            scope.launch {
                medicineSnapshot = runCatching {
                    medicineRepository.snapshot(displayPreferences.zoneId)
                }.getOrElse { throwable ->
                    medicineStatus = "Medicine load failed: ${throwable.message}"
                    EmptyMedicineSnapshot
                }
            }
        }

        fun refreshMedicineAndReminders() {
            scope.launch {
                medicineSnapshot = runCatching {
                    medicineRepository.snapshot(displayPreferences.zoneId)
                }.getOrElse { throwable ->
                    medicineStatus = "Medicine load failed: ${throwable.message}"
                    EmptyMedicineSnapshot
                }
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

        fun addMedicine(name: String, slots: Set<MedicineSlot>) {
            scope.launch {
                val result = medicineRepository.addMedicine(name, slots)
                medicineStatus = result.message
                status = result.message
                if (result.success) refreshMedicineAndReminders()
            }
        }

        fun archiveMedicine(medicineLocalId: Long) {
            scope.launch {
                val result = medicineRepository.archiveMedicine(medicineLocalId)
                medicineStatus = result.message
                status = result.message
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
                status = result.message
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
                status = result.message
                refreshMedicineSnapshot()
            }
        }

        fun deleteMedicineDoseLogs(logIds: Set<Long>) {
            scope.launch {
                val result = medicineRepository.deleteDoseLogs(logIds)
                medicineStatus = result.message
                status = result.message
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
            status = medicineStatus
            if (enabled && !medicineOverlayPermissionGranted) {
                requestOverlayPermission.launch(medicineOverlayPermissionIntent())
            }
        }

        fun requestMedicineOverlayPermission() {
            requestOverlayPermission.launch(medicineOverlayPermissionIntent())
        }

        fun queueAutoUpload(settings: UploadSettings): String? =
            when (val decision = UploadAutoQueuePolicy.decide(settings)) {
                UploadAutoQueueDecision.Disabled -> null
                is UploadAutoQueueDecision.Queue -> {
                    HealthUploadWorker.enqueue(this@MainActivity)
                    "Auto upload queued"
                }
                is UploadAutoQueueDecision.Invalid ->
                    "Auto upload not queued: ${decision.reason}"
            }

        fun applyScannedUploadText(rawText: String) {
            when (val result = UploadScanPolicy.applyScannedText(uploadSettings, rawText)) {
                is UploadScanApplyResult.Success -> {
                    val update = UploadDebugModePolicy.applyScanSuccess(
                        currentStatus = uploadStatus,
                        debugEnabled = debugEnabled,
                        success = result
                    )
                    uploadSettings = update.settings
                    uploadStatus = update.status
                    debugEnabled = update.debugEnabled
                    AppPreferences.setUploadSettings(this@MainActivity, update.settings)
                    AppPreferences.setUploadStatus(this@MainActivity, update.status)
                    AppPreferences.setDebugModeEnabled(this@MainActivity, update.debugEnabled)
                    status = update.message
                    refreshUploadStatus()
                }
                is UploadScanApplyResult.Invalid -> {
                    status = "QR scan failed: ${result.message}"
                }
            }
        }

        fun scanUploadQr() {
            status = "Opening pairing scanner..."
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            GmsBarcodeScanning.getClient(this@MainActivity, options)
                .startScan()
                .addOnSuccessListener { barcode ->
                    val rawValue = barcode.rawValue?.trim()
                    if (rawValue.isNullOrBlank()) {
                        status = "QR scan failed: empty code"
                    } else {
                        applyScannedUploadText(rawValue)
                    }
                }
                .addOnCanceledListener {
                    status = "Pairing scan cancelled"
                }
                .addOnFailureListener { throwable ->
                    status = "QR scan failed: ${throwable.message ?: throwable.javaClass.simpleName}"
                }
        }

        fun togglePeriodicSync() {
            actionInProgress = AppAction.PERIODIC_TOGGLE
            if (periodicEnabled) {
                PeriodicHealthSyncWorker.cancel(this@MainActivity)
                periodicEnabled = false
                status = "Periodic sync disabled"
            } else if (!backgroundReadAvailable) {
                status = "Background read is unavailable on this device"
            } else if (!backgroundReadGranted) {
                status = "Grant sync permissions first"
                requestHealthConnectPermissions(setOf(HealthDataTypeRegistry.backgroundReadPermission))
            } else {
                PeriodicHealthSyncWorker.schedule(this@MainActivity)
                periodicEnabled = true
                lastPeriodicSync = PeriodicSyncPreferences.lastFinishedAt(this@MainActivity)
                lastPeriodicStatus = PeriodicSyncPreferences.lastStatus(this@MainActivity)
                lastPeriodicSummary = PeriodicSyncPreferences.lastSummary(this@MainActivity)
                status = "Periodic sync scheduled"
            }
            actionInProgress = null
        }

        fun runFullResync() {
            fullSyncJob = scope.launch {
                actionInProgress = AppAction.FULL_RESYNC
                syncProgress = null
                status = "Running full historical resync..."
                val results = try {
                    syncService.runFullHistorySync { progress ->
                        syncProgress = progress
                        diagnostics.recordSyncProgress(progress)
                    }
                } catch (t: CancellationException) {
                    status = "Full resync cancelled"
                    diagnostics.recordSyncCancelled(SyncMode.FULL_HISTORY)
                    syncProgress = syncProgress?.copy(
                        isCancellable = false,
                        message = "Full resync cancelled"
                    )
                    actionInProgress = null
                    fullSyncJob = null
                    return@launch
                } catch (t: Throwable) {
                    status = "Full resync failed: ${t.message}"
                    diagnostics.recordSyncFailure(SyncMode.FULL_HISTORY, null, null, null)
                    actionInProgress = null
                    fullSyncJob = null
                    return@launch
                }
                diagnostics.recordSyncResults(SyncMode.FULL_HISTORY, results)
                status = syncAllStatusText(results, "Full resync")
                localHealthStatus = runCatching { dashboardQueries.localHealthStatus() }.getOrNull()
                refreshUploadStatus()
                actionInProgress = null
                fullSyncJob = null
            }
        }

        fun runBackgroundSyncNow() {
            scope.launch {
                actionInProgress = AppAction.BACKGROUND_NOW
                syncProgress = null
                status = "Running background sync now..."
                val results = runCatching {
                    syncService.runPeriodicSmartSync(
                        requireBackgroundReadPermission = backgroundReadGranted
                    ) { progress ->
                        syncProgress = progress
                        diagnostics.recordSyncProgress(progress)
                    }
                }.getOrElse {
                    status = "Background sync failed: ${it.message}"
                    diagnostics.recordSyncFailure(SyncMode.PERIODIC, null, null, null)
                    actionInProgress = null
                    return@launch
                }
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
                status = summary
                localHealthStatus = runCatching { dashboardQueries.localHealthStatus() }.getOrNull()
                refreshUploadStatus()
                actionInProgress = null
            }
        }

        fun saveUploadSettings(settings: UploadSettings) {
            uploadSettings = settings
            AppPreferences.setUploadSettings(this@MainActivity, settings)
            uploadStatus = uploadStatus.copy(serverMode = settings.serverMode)
            AppPreferences.setUploadStatus(this@MainActivity, uploadStatus)
            val autoUploadMessage = queueAutoUpload(settings)
            if (autoUploadMessage?.startsWith("Auto upload not queued:") == true) {
                uploadStatus = uploadStatus.copy(
                    connectionResult = autoUploadMessage,
                    severity = UploadResultSeverity.WARNING,
                    serverMode = settings.serverMode
                )
                AppPreferences.setUploadStatus(this@MainActivity, uploadStatus)
            }
            status = autoUploadMessage?.let { "Upload settings saved. $it" } ?: "Upload settings saved"
            refreshUploadStatus()
        }

        fun testUploadConnection(settings: UploadSettings) {
            scope.launch {
                try {
                    actionInProgress = AppAction.UPLOAD_TEST
                    uploadSettings = settings
                    AppPreferences.setUploadSettings(this@MainActivity, settings)
                    status = "Testing upload server..."
                    val counts = runCatching { uploadService.pendingCounts(settings) }
                        .getOrDefault(UploadPendingCounts.Empty)
                    uploadPendingCounts = counts
                    val result = uploadService.testConnection(settings)
                    uploadStatus = result.toStatus(uploadStatus, counts.total)
                    AppPreferences.setUploadStatus(this@MainActivity, uploadStatus)
                    status = result.message
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    Log.e(TAG, "Upload connection test failed", t)
                    val message = "Upload test failed: ${t.message ?: t.javaClass.simpleName}"
                    uploadStatus = uploadStatus.copy(
                        connectionResult = message,
                        severity = UploadResultSeverity.ERROR
                    )
                    AppPreferences.setUploadStatus(this@MainActivity, uploadStatus)
                    status = message
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
                    AppPreferences.setUploadSettings(this@MainActivity, settings)
                    status = uploadStartStatus(range)
                    val result = uploadService.uploadPending(settings, range) { progress ->
                        uploadProgress = progress
                    }
                    uploadStatus = result.toStatus()
                    AppPreferences.setUploadStatus(this@MainActivity, uploadStatus)
                    uploadPendingCounts = result.pendingCounts
                    val completion = uploadCompletionStatus(result, range)
                    if (completion.retryAction == UploadRetryAction.QUEUE_ALL) {
                        HealthUploadWorker.enqueue(this@MainActivity)
                    }
                    status = completion.message
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    Log.e(TAG, "Upload failed", t)
                    val message = "Upload failed: ${t.message ?: t.javaClass.simpleName}"
                    uploadStatus = uploadStatus.copy(
                        lastResult = message,
                        severity = UploadResultSeverity.ERROR
                    )
                    AppPreferences.setUploadStatus(this@MainActivity, uploadStatus)
                    status = message
                } finally {
                    uploadProgress = null
                    actionInProgress = null
                }
            }
        }

        fun runSmartSync() {
            scope.launch {
                actionInProgress = AppAction.SMART_SYNC
                syncProgress = null
                dashboardStatusTone = StatusTone.Info
                status = "Smart syncing recent Health Connect data..."
                val results = runCatching {
                    syncService.runSmartSync { progress ->
                        syncProgress = progress
                        diagnostics.recordSyncProgress(progress)
                    }
                }.getOrElse {
                    status = "Smart sync failed: ${it.message}"
                    diagnostics.recordSyncFailure(SyncMode.SMART, null, null, null)
                    dashboardStatusTone = StatusTone.Error
                    actionInProgress = null
                    return@launch
                }
                diagnostics.recordSyncResults(SyncMode.SMART, results)
                status = syncAllStatusText(results, "Smart sync")
                dashboardStatusTone = syncResultsStatusTone(results)
                localHealthStatus = runCatching { dashboardQueries.localHealthStatus() }.getOrNull()
                refreshUploadStatus()
                actionInProgress = null
            }
        }

        LaunchedEffect(Unit) {
            val granted = grantedHealthConnectPermissions()
            grantedPermissions = granted
            hcGranted = granted.containsAll(hcPermissions)
            hrHcGranted = HR_PERMISSION in granted
            backgroundReadAvailable = syncService.backgroundReadFeatureAvailable()
            backgroundReadGranted = HealthDataTypeRegistry.backgroundReadPermission in granted
            localHealthStatus = runCatching { dashboardQueries.localHealthStatus() }.getOrNull()
            uploadPendingCounts = runCatching { uploadService.pendingCounts(uploadSettings) }
                .getOrDefault(UploadPendingCounts.Empty)
            runCatching {
                medicineRepository.seedTestingMedicines()
            }.onSuccess { result ->
                if (result.changedRows > 0) {
                    medicineStatus = result.message
                }
            }.onFailure { throwable ->
                medicineStatus = "Medicine seed failed: ${throwable.message}"
            }
            medicineSnapshot = runCatching { medicineRepository.snapshot(displayPreferences.zoneId) }
                .getOrDefault(EmptyMedicineSnapshot)
            MedicineReminderScheduler.scheduleAll(
                context = this@MainActivity,
                repository = medicineRepository,
                zoneId = displayPreferences.zoneId
            )
        }

        DisposableEffect(lifecycleOwner) {
            val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    platformGranted = hasPlatformPerm()
                    debugEnabled = AppPreferences.debugModeEnabled(this@MainActivity)
                    periodicEnabled = PeriodicSyncPreferences.isEnabled(this@MainActivity)
                    lastPeriodicSync = PeriodicSyncPreferences.lastFinishedAt(this@MainActivity)
                    lastPeriodicStatus = PeriodicSyncPreferences.lastStatus(this@MainActivity)
                    lastPeriodicSummary = PeriodicSyncPreferences.lastSummary(this@MainActivity)
                    uploadStatus = AppPreferences.uploadStatus(this@MainActivity)
                    medicineOverlayReminderEnabled =
                        AppPreferences.medicineOverlayReminderEnabled(this@MainActivity)
                    medicineOverlayPermissionGranted = hasMedicineOverlayPermission()
                    scope.launch {
                        val granted = grantedHealthConnectPermissions()
                        grantedPermissions = granted
                        hcGranted = granted.containsAll(hcPermissions)
                        hrHcGranted = HR_PERMISSION in granted
                        backgroundReadAvailable = syncService.backgroundReadFeatureAvailable()
                        backgroundReadGranted = HealthDataTypeRegistry.backgroundReadPermission in granted
                        localHealthStatus = runCatching { dashboardQueries.localHealthStatus() }.getOrNull()
                        uploadPendingCounts = runCatching { uploadService.pendingCounts(uploadSettings) }
                            .getOrDefault(UploadPendingCounts.Empty)
                        medicineSnapshot = runCatching { medicineRepository.snapshot(displayPreferences.zoneId) }
                            .getOrDefault(EmptyMedicineSnapshot)
                        notificationPermissionGranted = hasNotificationPermission()
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(obs)
            onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
        }

        val nav = remember { AppNavigationState() }

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
                when (val destination = nav.destination) {
                    AppDestination.Settings -> {
                        SettingsScreen(
                            periodicEnabled = periodicEnabled,
                            debugEnabled = debugEnabled,
                            status = status,
                            onOpenPreferences = { nav.openSettingsSection(SettingsDestination.Preferences) },
                            onOpenMedicine = { nav.openSettingsSection(SettingsDestination.Medicine) },
                            onOpenDataFlow = { nav.openSettingsSection(SettingsDestination.DataFlow) },
                            onOpenAdvanced = { nav.openSettingsSection(SettingsDestination.Advanced) },
                            modifier = Modifier.padding(pad)
                        )
                    }
                    is AppDestination.SettingsSection -> {
                        when (destination.section) {
                            SettingsDestination.Preferences -> SettingsPreferencesScreen(
                                userProfile = userProfile,
                                userPreferences = userPreferences,
                                themeMode = themeMode,
                                themePalette = themePalette,
                                onUserProfileSave = { profile ->
                                    userProfile = profile
                                    AppPreferences.setUserProfile(this@MainActivity, profile)
                                    status = "Profile saved"
                                },
                                onUserPreferencesSave = { preferences ->
                                    userPreferences = preferences
                                    AppPreferences.setUserPreferences(this@MainActivity, preferences)
                                    status = "Preferences saved"
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
                            SettingsDestination.DataFlow -> SettingsDataFlowScreen(
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
                                settingsStatus = status,
                                syncBusy = actionInProgress?.blocksSyncSettings == true,
                                syncProgress = syncProgress,
                                uploadSettings = uploadSettings,
                                uploadStatus = uploadStatus,
                                uploadPendingCounts = uploadPendingCounts,
                                debugEnabled = debugEnabled,
                                uploadBusy = actionInProgress?.blocksUpload == true,
                                uploadProgress = uploadProgress,
                                exportBusy = actionInProgress?.isExport == true,
                                onRequestPlatform = { requestHrPermission.launch(HR_PERMISSION) },
                                onRequestDataPermissions = { requestHealthConnectPermissions(hcPermissions) },
                                onRequestBackgroundRead = {
                                    requestHealthConnectPermissions(setOf(HealthDataTypeRegistry.backgroundReadPermission))
                                },
                                openAppSettings = { startActivity(it) },
                                packageName = packageName,
                                onTogglePeriodic = ::togglePeriodicSync,
                                onFullResync = ::runFullResync,
                                onCancelFullResync = {
                                    status = "Cancelling full resync..."
                                    fullSyncJob?.cancel()
                                },
                                onRunBackgroundNow = ::runBackgroundSyncNow,
                                onSaveUploadSettings = ::saveUploadSettings,
                                onTestUploadConnection = ::testUploadConnection,
                                onUploadNow = ::uploadNow,
                                onScanPairingQr = ::scanUploadQr,
                                onExportHrCsv = {
                                    actionInProgress = AppAction.EXPORT_HR
                                    status = "Choose heart-rate CSV destination..."
                                    createHrCsv.launch("hr_export_${exportFileStamp()}.csv")
                                },
                                onExportAllCsv = {
                                    actionInProgress = AppAction.EXPORT_ALL
                                    status = "Choose all-data CSV destination..."
                                    createAllCsv.launch("health_connect_all_${exportFileStamp()}.csv")
                                },
                                onExportZip = {
                                    actionInProgress = AppAction.EXPORT_ZIP
                                    status = "Choose ZIP destination..."
                                    createCsvZip.launch("health_connect_csv_${exportFileStamp()}.zip")
                                },
                                onRequestClear = { showClearConfirm = true },
                                modifier = Modifier.padding(pad)
                            )
                            SettingsDestination.Medicine -> SettingsMedicineScreen(
                                snapshot = medicineSnapshot,
                                status = medicineStatus,
                                overlayReminderEnabled = medicineOverlayReminderEnabled,
                                overlayPermissionGranted = medicineOverlayPermissionGranted,
                                onAddMedicine = ::addMedicine,
                                onArchiveMedicine = ::archiveMedicine,
                                onSaveReminder = ::saveMedicineReminder,
                                onOverlayReminderChange = ::setMedicineOverlayReminder,
                                onRequestOverlayPermission = ::requestMedicineOverlayPermission,
                                modifier = Modifier.padding(pad)
                            )
                            SettingsDestination.Advanced -> SettingsAdvancedScreen(
                                modifier = Modifier.padding(pad),
                                debugEnabled = debugEnabled,
                                platformGranted = platformGranted,
                                hrHcGranted = hrHcGranted,
                                status = status,
                                diagnostics = diagnostics,
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
                                    AppPreferences.setUploadSettings(this@MainActivity, update.settings)
                                    AppPreferences.setUploadStatus(this@MainActivity, update.status)
                                    status = update.message
                                },
                                onSyncHours = { hours ->
                                    scope.launch {
                                        status = "Syncing heart rate for last ${hours}h..."
                                        status = runCatching { syncService.runLegacyHrDebugSync(hours) }
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
                                        refreshLocalStatus()
                                    }
                                },
                                onQuery = { at ->
                                    scope.launch {
                                        status = "Querying heart rate..."
                                        status = runCatching { syncService.queryLegacyHeartRate(at) }
                                            .fold(
                                                onSuccess = {
                                                    it?.let { "${it.bpm} bpm at ${it.time}" }
                                                        ?: "No nearby heart-rate sample"
                                                },
                                                onFailure = { "Heart-rate query failed: ${it.message}" }
                                            )
                                    }
                                },
                                onRequestClear = { showClearConfirm = true }
                            )
                        }
                    }
                    AppDestination.Data -> {
                        DataCatalogScreen(
                            catalogQueries = catalogQueries,
                            grantedPermissions = grantedPermissions,
                            displayPreferences = displayPreferences,
                            onOpenDetail = {
                                nav.openDataDetail(it)
                            },
                            modifier = Modifier.padding(pad)
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
                            onExportType = { key ->
                                actionInProgress = AppAction.EXPORT_TYPE
                                status = "Choose ${key} CSV destination..."
                                pendingTypeExportKey = key
                                createTypeCsv.launch("${key}_${exportFileStamp()}.csv")
                            },
                            runSelectedTypeSync = syncService::runSelectedTypeSync,
                            onLocalDataChanged = {
                                refreshLocalStatus()
                                refreshUploadStatus()
                            },
                            modifier = Modifier.padding(pad)
                        )
                    }
                    AppDestination.Dashboard -> {
                        DashboardScreen(
                            modifier = Modifier.padding(pad),
                            localHealthStatus = localHealthStatus,
                            grantedPermissions = grantedPermissions,
                            backgroundReadAvailable = backgroundReadAvailable,
                            backgroundReadGranted = backgroundReadGranted,
                            periodicEnabled = periodicEnabled,
                            status = status,
                            statusTone = dashboardStatusTone,
                            syncing = actionInProgress == AppAction.SMART_SYNC,
                            syncProgress = syncProgress,
                            displayPreferences = displayPreferences,
                            onSyncAll = ::runSmartSync
                        )
                    }
                }
            }

            if (showClearConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearConfirm = false },
                    title = { Text(uiText("Remove local data?")) },
                    text = {
                        Text(
                            uiText(
                                "This removes cached records, aggregates, older heart-rate rows, and sync history " +
                                    "from this app. Health Connect data itself is not deleted."
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showClearConfirm = false
                                scope.launch {
                                    val removed = localDataService.clearDb()
                                    status = "Removed local data ($removed older heart-rate rows)"
                                    localHealthStatus = runCatching { dashboardQueries.localHealthStatus() }.getOrNull()
                                    uploadPendingCounts = runCatching { uploadService.pendingCounts(uploadSettings) }
                                        .getOrDefault(UploadPendingCounts.Empty)
                                }
                            }
                        ) { Text(uiText("Remove")) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirm = false }) { Text(uiText("Cancel")) }
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
        val client = healthConnectClientOrNull() ?: return
        val intent = buildHcPermissionIntent(client, permissions)
        requestHcPermissions.launch(intent)
    }

    private suspend fun grantedHealthConnectPermissions(): Set<String> {
        val client = healthConnectClientOrNull() ?: return emptySet()
        return runCatching { client.permissionController.getGrantedPermissions() }
            .onFailure { Log.e(TAG, "Health Connect permission check failed", it) }
            .getOrDefault(emptySet())
    }

    private fun healthConnectClientOrNull(): HealthConnectClient? {
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

    private fun backgroundReadStatusText(available: Boolean, granted: Boolean): String = when {
        !available -> "not available on this device"
        granted -> "available and granted"
        else -> "available, permission missing"
    }
}

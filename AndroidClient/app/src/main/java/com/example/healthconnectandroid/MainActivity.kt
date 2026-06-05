@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.healthconnectandroid

import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.healthconnectandroid.debug.DeviceSmokeDiagnostics
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.hc.DemoStatus
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
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
import com.example.healthconnectandroid.hc.sync.SyncRangePolicy
import com.example.healthconnectandroid.hc.sync.SyncResultSeverity
import com.example.healthconnectandroid.hc.sync.SyncResultSeverityPolicy
import com.example.healthconnectandroid.hc.sync.SyncRunStatus
import com.example.healthconnectandroid.hc.upload.HealthUploadService
import com.example.healthconnectandroid.hc.upload.HealthUploadWorker
import com.example.healthconnectandroid.hc.upload.UploadPendingCounts
import com.example.healthconnectandroid.hc.upload.UploadProgress
import com.example.healthconnectandroid.hc.upload.UploadTimeRange
import com.example.healthconnectandroid.hc.upload.toStatus
import com.example.healthconnectandroid.navigation.AppDestination
import com.example.healthconnectandroid.navigation.AppNavigationState
import com.example.healthconnectandroid.navigation.AppTab
import com.example.healthconnectandroid.navigation.SettingsDestination
import com.example.healthconnectandroid.ui.data.DataCatalogScreen
import com.example.healthconnectandroid.ui.data.HealthDataDetailScreen
import com.example.healthconnectandroid.ui.dashboard.DashboardScreen
import com.example.healthconnectandroid.ui.settings.DebugScreen
import com.example.healthconnectandroid.ui.settings.SettingsAppearanceScreen
import com.example.healthconnectandroid.ui.settings.SettingsDataScreen
import com.example.healthconnectandroid.ui.settings.SettingsPermissionsScreen
import com.example.healthconnectandroid.ui.settings.SettingsPreferencesScreen
import com.example.healthconnectandroid.ui.settings.SettingsProfileScreen
import com.example.healthconnectandroid.ui.settings.SettingsScreen
import com.example.healthconnectandroid.ui.settings.SettingsSyncScreen
import com.example.healthconnectandroid.ui.settings.SettingsUploadScreen
import com.example.healthconnectandroid.ui.format.toDisplayPreferences
import com.example.healthconnectandroid.ui.i18n.LocalAppLanguage
import com.example.healthconnectandroid.ui.i18n.uiText
import com.example.healthconnectandroid.ui.theme.HealthConnectAndroidTheme
import com.example.healthconnectandroid.ui.StatusTone
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val HR_PERMISSION = HealthDataTypeRegistry.heartRate.requiredReadPermission
    ?: error("Heart rate record must expose a Health Connect read permission")
private const val TAG = "HCHRDemo"

class MainActivity : ComponentActivity() {
    private var pendingTypeExportKey: String? = null
    private var reportExportStatus: ((String) -> Unit)? = null
    private var reportActionBusy: ((Boolean) -> Unit)? = null

    private val requestHrPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* refresh on resume */ }

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

    private var hcClient: HealthConnectClient? = null
    private val hcPermissions = HealthDataTypeRegistry.implementedReadPermissions
    private val requestHcPermissions =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { /* refresh on resume */ }

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
                    "Exported legacy heart-rate CSV: $count rows"
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
        setContent {
            App(
                dashboardQueries = dashboardQueries,
                catalogQueries = catalogQueries,
                detailQueries = detailQueries,
                recordQueries = recordQueries,
                localDataService = localDataService,
                syncService = syncService,
                uploadService = uploadService,
                manifestDeclares = ::manifestDeclaresHr,
                hasPlatformPerm = ::hasHrPermission
            )
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
        var demoStatus by remember { mutableStateOf<DemoStatus?>(null) }
        var status by remember { mutableStateOf("Ready") }
        var dashboardStatusTone by remember { mutableStateOf(StatusTone.Neutral) }
        var actionInProgress by remember { mutableStateOf<String?>(null) }
        var syncProgress by remember { mutableStateOf<SyncProgress?>(null) }
        var fullSyncJob by remember { mutableStateOf<Job?>(null) }
        var showClearConfirm by remember { mutableStateOf(false) }
        var themeMode by remember { mutableStateOf(AppPreferences.themeMode(this@MainActivity)) }
        var themePalette by remember { mutableStateOf(AppPreferences.themePalette(this@MainActivity)) }
        var userProfile by remember { mutableStateOf(AppPreferences.userProfile(this@MainActivity)) }
        var userPreferences by remember { mutableStateOf(AppPreferences.userPreferences(this@MainActivity)) }
        var uploadSettings by remember { mutableStateOf(AppPreferences.uploadSettings(this@MainActivity)) }
        var uploadStatus by remember { mutableStateOf(AppPreferences.uploadStatus(this@MainActivity)) }
        var uploadPendingCounts by remember { mutableStateOf(UploadPendingCounts.Empty) }
        var uploadProgress by remember { mutableStateOf<UploadProgress?>(null) }
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
            onDispose {
                reportExportStatus = null
                reportActionBusy = null
            }
        }

        fun refreshLocalStatus() {
            scope.launch {
                demoStatus = runCatching { dashboardQueries.demoStatus() }.getOrNull()
            }
        }

        fun refreshUploadStatus() {
            uploadStatus = AppPreferences.uploadStatus(this@MainActivity)
            scope.launch {
                uploadPendingCounts = runCatching { uploadService.pendingCounts(uploadSettings) }
                    .getOrDefault(UploadPendingCounts.Empty)
            }
        }

        LaunchedEffect(Unit) {
            val granted = grantedHealthConnectPermissions()
            grantedPermissions = granted
            hcGranted = granted.containsAll(hcPermissions)
            hrHcGranted = HR_PERMISSION in granted
            backgroundReadAvailable = syncService.backgroundReadFeatureAvailable()
            backgroundReadGranted = HealthDataTypeRegistry.backgroundReadPermission in granted
            demoStatus = runCatching { dashboardQueries.demoStatus() }.getOrNull()
            uploadPendingCounts = runCatching { uploadService.pendingCounts(uploadSettings) }
                .getOrDefault(UploadPendingCounts.Empty)
        }

        DisposableEffect(lifecycleOwner) {
            val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    platformGranted = hasPlatformPerm()
                    periodicEnabled = PeriodicSyncPreferences.isEnabled(this@MainActivity)
                    lastPeriodicSync = PeriodicSyncPreferences.lastFinishedAt(this@MainActivity)
                    lastPeriodicStatus = PeriodicSyncPreferences.lastStatus(this@MainActivity)
                    lastPeriodicSummary = PeriodicSyncPreferences.lastSummary(this@MainActivity)
                    uploadStatus = AppPreferences.uploadStatus(this@MainActivity)
                    scope.launch {
                        val granted = grantedHealthConnectPermissions()
                        grantedPermissions = granted
                        hcGranted = granted.containsAll(hcPermissions)
                        hrHcGranted = HR_PERMISSION in granted
                        backgroundReadAvailable = syncService.backgroundReadFeatureAvailable()
                        backgroundReadGranted = HealthDataTypeRegistry.backgroundReadPermission in granted
                        demoStatus = runCatching { dashboardQueries.demoStatus() }.getOrNull()
                        uploadPendingCounts = runCatching { uploadService.pendingCounts(uploadSettings) }
                            .getOrDefault(UploadPendingCounts.Empty)
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(obs)
            onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
        }

        val nav = remember { AppNavigationState() }

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
                            userProfile = userProfile,
                            periodicEnabled = periodicEnabled,
                            status = status,
                            onOpenProfile = { nav.openSettingsSection(SettingsDestination.Profile) },
                            onOpenPreferences = { nav.openSettingsSection(SettingsDestination.Preferences) },
                            onOpenPermissions = { nav.openSettingsSection(SettingsDestination.Permissions) },
                            onOpenSync = { nav.openSettingsSection(SettingsDestination.Sync) },
                            onOpenUpload = { nav.openSettingsSection(SettingsDestination.Upload) },
                            onOpenDataSettings = { nav.openSettingsSection(SettingsDestination.DataSettings) },
                            onOpenAppearance = { nav.openSettingsSection(SettingsDestination.Appearance) },
                            onOpenDebug = { nav.openSettingsSection(SettingsDestination.Debug) },
                            modifier = Modifier.padding(pad)
                        )
                    }
                    is AppDestination.SettingsSection -> {
                        when (destination.section) {
                            SettingsDestination.Profile -> SettingsProfileScreen(
                                userProfile = userProfile,
                                onUserProfileSave = { profile ->
                                    userProfile = profile
                                    AppPreferences.setUserProfile(this@MainActivity, profile)
                                    status = "Profile saved"
                                },
                                modifier = Modifier.padding(pad)
                            )
                            SettingsDestination.Preferences -> SettingsPreferencesScreen(
                                userPreferences = userPreferences,
                                onUserPreferencesSave = { preferences ->
                                    userPreferences = preferences
                                    AppPreferences.setUserPreferences(this@MainActivity, preferences)
                                    status = "Preferences saved"
                                },
                                modifier = Modifier.padding(pad)
                            )
                            SettingsDestination.Permissions -> SettingsPermissionsScreen(
                                declaredReadHr = declared,
                                platformGranted = platformGranted,
                                hcGranted = hcGranted,
                                backgroundReadStatus = backgroundReadStatusText(
                                    backgroundReadAvailable,
                                    backgroundReadGranted
                                ),
                                dataPermissionSummary = dataPermissionSummaryText(grantedPermissions),
                                backgroundReadAvailable = backgroundReadAvailable,
                                onRequestPlatform = { requestHrPermission.launch(HR_PERMISSION) },
                                onRequestDataPermissions = { requestHealthConnectPermissions(hcPermissions) },
                                onRequestBackgroundRead = {
                                    requestHealthConnectPermissions(setOf(HealthDataTypeRegistry.backgroundReadPermission))
                                },
                                openAppSettings = { startActivity(it) },
                                packageName = packageName,
                                modifier = Modifier.padding(pad)
                            )
                            SettingsDestination.Sync -> SettingsSyncScreen(
                                periodicEnabled = periodicEnabled,
                                backgroundReadAvailable = backgroundReadAvailable,
                                backgroundReadGranted = backgroundReadGranted,
                                lastPeriodicSync = lastPeriodicSync,
                                lastPeriodicStatus = lastPeriodicStatus,
                                lastPeriodicSummary = lastPeriodicSummary,
                                status = status,
                                busy = actionInProgress in setOf("periodic_toggle", "full_resync", "background_now"),
                                syncProgress = syncProgress,
                                onTogglePeriodic = {
                                    actionInProgress = "periodic_toggle"
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
                                },
                                onFullResync = {
                                    fullSyncJob = scope.launch {
                                        actionInProgress = "full_resync"
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
                                        demoStatus = runCatching { dashboardQueries.demoStatus() }.getOrNull()
                                        refreshUploadStatus()
                                        actionInProgress = null
                                        fullSyncJob = null
                                    }
                                },
                                onCancelFullResync = {
                                    status = "Cancelling full resync..."
                                    fullSyncJob?.cancel()
                                },
                                onRunBackgroundNow = {
                                    scope.launch {
                                        actionInProgress = "background_now"
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
                                        demoStatus = runCatching { dashboardQueries.demoStatus() }.getOrNull()
                                        refreshUploadStatus()
                                        actionInProgress = null
                                    }
                                },
                                modifier = Modifier.padding(pad)
                            )
                            SettingsDestination.Upload -> SettingsUploadScreen(
                                settings = uploadSettings,
                                uploadStatus = uploadStatus,
                                pendingCounts = uploadPendingCounts,
                                busy = actionInProgress == "upload" || actionInProgress == "upload_test",
                                progress = uploadProgress,
                                onSaveSettings = { settings ->
                                    uploadSettings = settings
                                    AppPreferences.setUploadSettings(this@MainActivity, settings)
                                    uploadStatus = uploadStatus.copy(serverMode = settings.serverMode)
                                    AppPreferences.setUploadStatus(this@MainActivity, uploadStatus)
                                    status = "Upload settings saved"
                                    refreshUploadStatus()
                                },
                                onTestConnection = { settings ->
                                    scope.launch {
                                        actionInProgress = "upload_test"
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
                                        actionInProgress = null
                                    }
                                },
                                onUploadNow = { settings, range ->
                                    scope.launch {
                                        actionInProgress = "upload"
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
                                        status = result.message
                                        if (!result.success && result.retryable && range == UploadTimeRange.ALL) {
                                            HealthUploadWorker.enqueue(this@MainActivity)
                                            status = "${result.message}. Retry queued."
                                        } else if (!result.success && result.retryable) {
                                            status = "${result.message}. Retry ${range.label} manually."
                                        }
                                        uploadProgress = null
                                        actionInProgress = null
                                    }
                                },
                                modifier = Modifier.padding(pad)
                            )
                            SettingsDestination.DataSettings -> SettingsDataScreen(
                                status = status,
                                busy = actionInProgress?.startsWith("export") == true,
                                onExportHrCsv = {
                                    actionInProgress = "export_hr"
                                    status = "Choose heart-rate CSV destination..."
                                    createHrCsv.launch("hr_export_${exportFileStamp()}.csv")
                                },
                                onExportAllCsv = {
                                    actionInProgress = "export_all"
                                    status = "Choose all-data CSV destination..."
                                    createAllCsv.launch("health_connect_all_${exportFileStamp()}.csv")
                                },
                                onExportZip = {
                                    actionInProgress = "export_zip"
                                    status = "Choose ZIP destination..."
                                    createCsvZip.launch("health_connect_csv_${exportFileStamp()}.zip")
                                },
                                onRequestClear = { showClearConfirm = true },
                                modifier = Modifier.padding(pad)
                            )
                            SettingsDestination.Appearance -> SettingsAppearanceScreen(
                                themeMode = themeMode,
                                themePalette = themePalette,
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
                            SettingsDestination.Debug -> DebugScreen(
                                modifier = Modifier.padding(pad),
                                platformGranted = platformGranted,
                                hrHcGranted = hrHcGranted,
                                status = status,
                                diagnostics = diagnostics,
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
                                actionInProgress = "export_type"
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
                            demoStatus = demoStatus,
                            grantedPermissions = grantedPermissions,
                            backgroundReadAvailable = backgroundReadAvailable,
                            backgroundReadGranted = backgroundReadGranted,
                            periodicEnabled = periodicEnabled,
                            status = status,
                            statusTone = dashboardStatusTone,
                            syncing = actionInProgress == "smart_sync",
                            syncProgress = syncProgress,
                            displayPreferences = displayPreferences,
                            onSyncAll = {
                                scope.launch {
                                    actionInProgress = "smart_sync"
                                    syncProgress = null
                                    dashboardStatusTone = StatusTone.Info
                                    status = "Smart syncing recent Health Connect data..."
                                    val results = runCatching {
                                        syncService.runSmartSync { progress ->
                                            syncProgress = progress
                                            diagnostics.recordSyncProgress(progress)
                                        }
                                    }
                                        .getOrElse {
                                            status = "Smart sync failed: ${it.message}"
                                            diagnostics.recordSyncFailure(SyncMode.SMART, null, null, null)
                                            dashboardStatusTone = StatusTone.Error
                                            actionInProgress = null
                                            return@launch
                                        }
                                    diagnostics.recordSyncResults(SyncMode.SMART, results)
                                    status = syncAllStatusText(results, "Smart sync")
                                    dashboardStatusTone = syncResultsStatusTone(results)
                                    demoStatus = runCatching { dashboardQueries.demoStatus() }.getOrNull()
                                    refreshUploadStatus()
                                    actionInProgress = null
                                }
                            }
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
                                "This removes cached records, aggregates, legacy heart-rate rows, and sync history " +
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
                                    status = "Removed local data ($removed legacy heart-rate rows)"
                                    demoStatus = runCatching { dashboardQueries.demoStatus() }.getOrNull()
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

    @Composable
    private fun AppTopBar(
        nav: AppNavigationState,
        onBack: () -> Unit
    ) {
        TopAppBar(
            title = {
                Text(uiText(nav.title()), fontWeight = FontWeight.SemiBold)
            },
            navigationIcon = {
                if (nav.destination != AppDestination.Dashboard) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = {},
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
    }

    @Composable
    private fun BottomNavigationBar(
        selectedTab: AppTab,
        onSelectTab: (AppTab) -> Unit
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 10.dp
        ) {
            NavigationBarItem(
                selected = selectedTab == AppTab.Dashboard,
                onClick = { onSelectTab(AppTab.Dashboard) },
                icon = { AnimatedNavIcon(selected = selectedTab == AppTab.Dashboard) {
                    Icon(Icons.Default.Home, contentDescription = null)
                } },
                label = { Text(uiText(AppTab.Dashboard.label)) },
                colors = studioNavigationItemColors()
            )
            NavigationBarItem(
                selected = selectedTab == AppTab.Data,
                onClick = { onSelectTab(AppTab.Data) },
                icon = { AnimatedNavIcon(selected = selectedTab == AppTab.Data) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                } },
                label = { Text(uiText(AppTab.Data.label)) },
                colors = studioNavigationItemColors()
            )
            NavigationBarItem(
                selected = selectedTab == AppTab.Settings,
                onClick = { onSelectTab(AppTab.Settings) },
                icon = { AnimatedNavIcon(selected = selectedTab == AppTab.Settings) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                } },
                label = { Text(uiText(AppTab.Settings.label)) },
                colors = studioNavigationItemColors()
            )
        }
    }

    @Composable
    private fun studioNavigationItemColors() = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    @Composable
    private fun AnimatedNavIcon(
        selected: Boolean,
        content: @Composable () -> Unit
    ) {
        val scale by animateFloatAsState(
            targetValue = if (selected) 1.08f else 1f,
            animationSpec = tween(180),
            label = "bottom-nav-icon-scale"
        )
        Row(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
            content()
        }
    }

    private fun exportFileStamp(): String =
        java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))

    private fun uploadStartStatus(range: UploadTimeRange): String =
        if (range == UploadTimeRange.ALL) {
            "Uploading local data..."
        } else {
            "Uploading local data (${range.label})..."
        }

    private fun syncAllStatusText(
        results: List<HealthDataTypeSyncResult>,
        label: String = "Sync"
    ): String {
        val inserted = results.sumOf { it.recordsInserted }
        val updated = results.sumOf { it.recordsUpdated }
        val duplicates = results.sumOf { it.recordsSkippedDuplicate }
        val summaries = results.sumOf { it.aggregateRowsStored }
        val skipped = results.count { it.skippedReason != null }
        val timeouts = results.count { it.terminalStatus == SyncRunStatus.TIMEOUT }
        val cancelled = results.count { it.terminalStatus == SyncRunStatus.CANCELLED }
        val errors = results.count {
            it.errorMessage != null && it.terminalStatus !in setOf(SyncRunStatus.TIMEOUT, SyncRunStatus.CANCELLED)
        }
        val start = results.mapNotNull { it.requestedStart }.minOrNull()
        val end = results.mapNotNull { it.requestedEnd }.maxOrNull()
        val range = if (start != null && end != null) {
            ", ${syncRangeText(start, end)}"
        } else {
            ""
        }
        return "$label complete: types ${results.size}, inserted $inserted, updated $updated, duplicates $duplicates, " +
            "summaries $summaries, skipped $skipped, timeouts $timeouts, cancelled $cancelled, errors $errors$range"
    }

    private fun syncResultsStatusTone(results: List<HealthDataTypeSyncResult>): StatusTone =
        when (SyncResultSeverityPolicy.fromResults(results)) {
            SyncResultSeverity.NEUTRAL -> StatusTone.Neutral
            SyncResultSeverity.SUCCESS -> StatusTone.Success
            SyncResultSeverity.WARNING -> StatusTone.Warning
            SyncResultSeverity.ERROR -> StatusTone.Error
        }

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

    private fun shortInstant(value: Instant): String =
        java.time.format.DateTimeFormatter.ofPattern("M/d HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
            .format(value)

    private fun syncRangeText(start: Instant, end: Instant): String {
        val startText = if (start == SyncRangePolicy.FULL_HISTORY_START) {
            "range full history (${start})"
        } else {
            "range ${shortInstant(start)}"
        }
        return "$startText to ${shortInstant(end)}"
    }
}

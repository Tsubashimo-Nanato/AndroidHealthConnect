package com.example.healthconnectandroid.hc.sync

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.room.withTransaction
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.HealthAggregateDao
import com.example.healthconnectandroid.data.HealthAggregateEntity
import com.example.healthconnectandroid.data.HealthChangeTokenEntity
import com.example.healthconnectandroid.data.HealthRecordEntity
import com.example.healthconnectandroid.data.HealthSyncRunEntity
import com.example.healthconnectandroid.data.HealthValueEntity
import com.example.healthconnectandroid.hc.HealthAggregateSummary
import com.example.healthconnectandroid.hc.HealthDataTypeDescriptor
import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import com.example.healthconnectandroid.hc.HeartRateRecordReader
import com.example.healthconnectandroid.hc.HrSample
import com.example.healthconnectandroid.hc.NormalizedHealthRecord
import com.example.healthconnectandroid.hc.NormalizedHealthValue
import com.example.healthconnectandroid.hc.dedupeKey
import com.example.healthconnectandroid.hc.localDateString
import com.example.healthconnectandroid.hc.normalizeHealthConnectRecord
import com.example.healthconnectandroid.hc.toHeartRateEntities
import com.example.healthconnectandroid.hc.toNormalizedHeartRate
import com.example.healthconnectandroid.hc.valueKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

private const val TAG = "HealthConnectSync"

private data class StoreNormalizedResult(
    val inserted: Int,
    val updated: Int,
    val skippedDuplicate: Int,
    val valuesStored: Int,
    val localBytesWritten: Long
)

private enum class ExistingRecordPolicy {
    KEEP,
    REPLACE_IF_CHANGED
}

private data class StoreChangePageResult(
    val inserted: Int,
    val updated: Int,
    val deleted: Int,
    val skippedDuplicate: Int,
    val valuesStored: Int,
    val localBytesWritten: Long,
    val changedStart: Instant?,
    val changedEnd: Instant?
)

internal sealed interface HealthChangeSyncOutcome {
    data class Applied(val result: HealthDataTypeSyncResult) : HealthChangeSyncOutcome
    data object TokenExpired : HealthChangeSyncOutcome
}

private data class StoreAggregateResult(
    val rowsStored: Int,
    val localBytesWritten: Long
)

private data class SyncAggregateResult(
    val rowsRead: Int = 0,
    val rowsStored: Int = 0,
    val localBytesWritten: Long = 0,
    val errorMessage: String? = null
)

class HealthDataTypeSyncer(
    context: Context,
    private val db: AppDb
) {
    private val appContext = context.applicationContext
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(appContext) }
    private val dao = db.heartRateDao()
    private val healthDao = db.healthRecordDao()
    private val syncDao = db.healthSyncRunDao()
    private val aggregateDao = db.healthAggregateDao()
    private val retentionDao = db.healthRetentionDao()
    private val changeTokenDao = db.healthChangeTokenDao()

    private fun ensureAvailable(): String? = try {
        val status = HealthConnectClient.getSdkStatus(appContext)
        if (status != HealthConnectClient.SDK_AVAILABLE)
            "Health Connect not available (status=$status). Install/Update the 'Health Connect by Android' app."
        else null
    } catch (t: Throwable) {
        Log.e(TAG, "Availability check failed", t)
        "Availability check failed: ${t.message ?: t.javaClass.simpleName}"
    }

    fun backgroundReadFeatureAvailable(): Boolean = try {
        client.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    } catch (t: Throwable) {
        Log.e(TAG, "Background read feature check failed", t)
        false
    }

    private suspend fun readAccessIssue(
        descriptor: HealthDataTypeDescriptor,
        requireBackgroundReadPermission: Boolean
    ): String? {
        ensureAvailable()?.let { return it }
        val grantedPermissions = client.permissionController.getGrantedPermissions()
        if (requireBackgroundReadPermission) {
            if (!backgroundReadFeatureAvailable()) {
                return "Health Connect background reads are not available on this device"
            }
            if (HealthDataTypeRegistry.backgroundReadPermission !in grantedPermissions) {
                return "Missing permission ${HealthDataTypeRegistry.backgroundReadPermission}"
            }
        }

        val readPermission = descriptor.requiredReadPermission
            ?: return "No Health Connect read permission is available for this data type"
        return if (readPermission in grantedPermissions) null else "Missing permission $readPermission"
    }

    /** Legacy heart-rate sync path kept for single-type tools; multi-type sync uses [syncDataType]. */
    suspend fun syncLastHours(hours: Long): Int {
        ensureAvailable()?.let { throw IllegalStateException(it) }

        val end = Instant.now()
        val start = end.minus(hours, ChronoUnit.HOURS)
        val syncStartedAt = Instant.now()

        val records = HeartRateRecordReader.readRecords(client, start, end)
        val batch = records.toHeartRateEntities()
        if (batch.isNotEmpty()) dao.insertReplace(batch)
        val normalizedRecords = records.map { it.toNormalizedHeartRate() }
        val storeResult = storeNormalizedRecords(
            typeKey = HealthDataTypeKeys.HEART_RATE,
            records = normalizedRecords
        )
        val sourceBytesRead = normalizedRecords.sumOf { it.approxBytes() }
        insertSyncRun(
            result = HealthDataTypeSyncResult(
                key = HealthDataTypeKeys.HEART_RATE,
                requestedStart = start,
                requestedEnd = end,
                recordsRead = records.size,
                recordsInserted = storeResult.inserted,
                recordsUpdated = storeResult.updated,
                recordsSkippedDuplicate = storeResult.skippedDuplicate,
                valuesStored = storeResult.valuesStored,
                sourceBytesRead = sourceBytesRead,
                localBytesWritten = storeResult.localBytesWritten
            ),
            startedAt = syncStartedAt
        )

        return batch.size
    }

    suspend fun syncDataType(
        key: String,
        start: Instant,
        end: Instant,
        requireBackgroundReadPermission: Boolean = false,
        replaceChangedRecords: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault(),
        onProgress: (SyncTypeProgress) -> Unit = {}
    ): HealthDataTypeSyncResult {
        val startedAt = Instant.now()

        val result = try {
            val descriptor = HealthDataTypeRegistry.require(key)
            readAccessIssue(descriptor, requireBackgroundReadPermission)?.let {
                return syncResultAndLog(
                    result = HealthDataTypeSyncResult(
                        key = key,
                        requestedStart = start,
                        requestedEnd = end,
                        skippedReason = it
                    ),
                    startedAt = startedAt
                )
            }
            val reader = descriptor.reader ?: return syncResultAndLog(
                result = HealthDataTypeSyncResult(
                    key = key,
                    requestedStart = start,
                    requestedEnd = end,
                    skippedReason = "No reader registered"
                ),
                startedAt = startedAt
            )

            val archivedBefore = retentionDao.archivedBeforeEpochMillis(key)
                ?.let(Instant::ofEpochMilli)
            // Archived rows already have compact local history; clamping prevents full sync from restoring purged raw data.
            val readStart = archivedBefore?.takeIf { it.isAfter(start) } ?: start
            if (!readStart.isBefore(end)) {
                return syncResultAndLog(
                    result = HealthDataTypeSyncResult(
                        key = key,
                        requestedStart = start,
                        requestedEnd = end,
                        skippedReason = "Requested raw range is already archived locally"
                    ),
                    startedAt = startedAt
                )
            }

            onProgress(
                SyncTypeProgress(
                    phase = SyncProgressPhase.FETCHING,
                    message = "Fetching ${descriptor.displayName}"
                )
            )
            var recordsRead = 0
            var sourceBytesRead = 0L
            var sourceStart: Instant? = null
            var sourceEnd: Instant? = null
            var inserted = 0
            var updated = 0
            var skippedDuplicate = 0
            var valuesStored = 0
            var localBytesWritten = 0L

            reader.readPages(client, readStart, end) { page ->
                val pageSourceBytes = page.sumOf { it.approxBytes() }
                recordsRead += page.size
                sourceBytesRead += pageSourceBytes
                sourceStart = minInstantOrNull(sourceStart, page.minOfOrNull { it.startTime })
                sourceEnd = maxInstantOrNull(
                    sourceEnd,
                    page.mapNotNull { it.endTime ?: it.startTime }.maxOrNull()
                )
                onProgress(
                    SyncTypeProgress(
                        phase = SyncProgressPhase.STORING,
                        recordsRead = recordsRead,
                        inserted = inserted,
                        updated = updated,
                        duplicates = skippedDuplicate,
                        sourceBytesRead = sourceBytesRead,
                        localBytesWritten = localBytesWritten,
                        message = "Fetched $recordsRead records"
                    )
                )
                val pageStoreResult = storeNormalizedRecords(
                    typeKey = key,
                    records = page,
                    existingRecordPolicy = if (replaceChangedRecords) {
                        ExistingRecordPolicy.REPLACE_IF_CHANGED
                    } else {
                        ExistingRecordPolicy.KEEP
                    }
                )
                inserted += pageStoreResult.inserted
                updated += pageStoreResult.updated
                skippedDuplicate += pageStoreResult.skippedDuplicate
                valuesStored += pageStoreResult.valuesStored
                localBytesWritten += pageStoreResult.localBytesWritten
                onProgress(
                    SyncTypeProgress(
                        phase = SyncProgressPhase.STORING,
                        recordsRead = recordsRead,
                        inserted = inserted,
                        updated = updated,
                        duplicates = skippedDuplicate,
                        sourceBytesRead = sourceBytesRead,
                        localBytesWritten = localBytesWritten,
                        message = "Stored $recordsRead records"
                    )
                )
            }
            if (recordsRead == 0) {
                onProgress(
                    SyncTypeProgress(
                        phase = SyncProgressPhase.NO_SOURCE_DATA,
                        message = "No Health Connect records returned"
                    )
                )
            }
            if (descriptor.aggregateReader != null) {
                onProgress(
                    SyncTypeProgress(
                        phase = SyncProgressPhase.AGGREGATING,
                        recordsRead = recordsRead,
                        inserted = inserted,
                        updated = updated,
                        duplicates = skippedDuplicate,
                        sourceBytesRead = sourceBytesRead,
                        localBytesWritten = localBytesWritten,
                        message = "Updating summaries"
                    )
                )
            }
            val aggregateResult = syncDailyAggregatesIfAvailable(
                descriptor = descriptor,
                start = readStart,
                end = end,
                zoneId = zoneId
            )
            HealthDataTypeSyncResult(
                key = key,
                requestedStart = start,
                requestedEnd = end,
                recordsRead = recordsRead,
                recordsInserted = inserted,
                recordsUpdated = updated,
                recordsSkippedDuplicate = skippedDuplicate,
                valuesStored = valuesStored,
                aggregateRowsRead = aggregateResult.rowsRead,
                aggregateRowsStored = aggregateResult.rowsStored,
                sourceBytesRead = sourceBytesRead,
                localBytesWritten = localBytesWritten + aggregateResult.localBytesWritten,
                sourceStart = sourceStart,
                sourceEnd = sourceEnd,
                aggregateErrorMessage = aggregateResult.errorMessage
            )
        } catch (t: CancellationException) {
            Log.i(TAG, "Sync cancelled for type=$key start=$start end=$end")
            throw t
        } catch (t: Throwable) {
            Log.e(TAG, "Sync failed for type=$key start=$start end=$end", t)
            HealthDataTypeSyncResult(
                key = key,
                requestedStart = start,
                requestedEnd = end,
                errorMessage = t.message ?: t.javaClass.simpleName
            )
        }

        return syncResultAndLog(result, startedAt)
    }

    internal suspend fun createChangesToken(descriptor: HealthDataTypeDescriptor): String {
        readAccessIssue(descriptor, requireBackgroundReadPermission = false)?.let {
            throw IllegalStateException(it)
        }
        return client.getChangesToken(
            ChangesTokenRequest(recordTypes = setOf(descriptor.recordClass))
        )
    }

    internal suspend fun syncChanges(
        descriptor: HealthDataTypeDescriptor,
        initialToken: String,
        requireBackgroundReadPermission: Boolean,
        onProgress: (SyncTypeProgress) -> Unit = {}
    ): HealthChangeSyncOutcome {
        val startedAt = Instant.now()
        val accessIssue = readAccessIssue(descriptor, requireBackgroundReadPermission)
        if (accessIssue != null) {
            return HealthChangeSyncOutcome.Applied(
                syncResultAndLog(
                    result = HealthDataTypeSyncResult(
                        key = descriptor.key,
                        skippedReason = accessIssue
                    ),
                    startedAt = startedAt
                )
            )
        }

        return try {
            var token = initialToken
            var inserted = 0
            var updated = 0
            var deleted = 0
            var duplicates = 0
            var valuesStored = 0
            var recordsRead = 0
            var sourceBytesRead = 0L
            var localBytesWritten = 0L
            var changedStart: Instant? = null
            var changedEnd: Instant? = null

            do {
                onProgress(
                    SyncTypeProgress(
                        phase = SyncProgressPhase.FETCHING,
                        recordsRead = recordsRead,
                        inserted = inserted,
                        updated = updated,
                        duplicates = duplicates,
                        sourceBytesRead = sourceBytesRead,
                        localBytesWritten = localBytesWritten,
                        message = if (recordsRead == 0) {
                            "Checking Health Connect changes"
                        } else {
                            "Fetching the next changes page"
                        }
                    )
                )
                val response = client.getChanges(token)
                if (response.changesTokenExpired) {
                    changeTokenDao.delete(descriptor.key)
                    return HealthChangeSyncOutcome.TokenExpired
                }

                val upsertions = response.changes.filterIsInstance<UpsertionChange>()
                    .map { change ->
                        normalizeHealthConnectRecord(change.record)
                            ?.takeIf { it.typeKey == descriptor.key }
                            ?: error("Unsupported changed record for type=${descriptor.key}")
                    }
                val deletions = response.changes.filterIsInstance<DeletionChange>()
                    .map { it.recordId }
                val pageSourceBytes = upsertions.sumOf { it.approxBytes() }
                onProgress(
                    SyncTypeProgress(
                        phase = SyncProgressPhase.STORING,
                        recordsRead = recordsRead + response.changes.size,
                        inserted = inserted,
                        updated = updated,
                        duplicates = duplicates,
                        sourceBytesRead = sourceBytesRead + pageSourceBytes,
                        localBytesWritten = localBytesWritten,
                        message = "Applying ${response.changes.size} changes"
                    )
                )
                val page = storeChangePage(
                    typeKey = descriptor.key,
                    records = upsertions,
                    deletedRecordUids = deletions,
                    nextToken = response.nextChangesToken
                )

                recordsRead += response.changes.size
                sourceBytesRead += pageSourceBytes
                inserted += page.inserted
                updated += page.updated
                deleted += page.deleted
                duplicates += page.skippedDuplicate
                valuesStored += page.valuesStored
                localBytesWritten += page.localBytesWritten
                changedStart = minInstantOrNull(changedStart, page.changedStart)
                changedEnd = maxInstantOrNull(changedEnd, page.changedEnd)
                token = response.nextChangesToken
                onProgress(
                    SyncTypeProgress(
                        phase = SyncProgressPhase.STORING,
                        recordsRead = recordsRead,
                        inserted = inserted,
                        updated = updated,
                        duplicates = duplicates,
                        sourceBytesRead = sourceBytesRead,
                        localBytesWritten = localBytesWritten,
                        message = "Applied $recordsRead changes"
                    )
                )
            } while (response.hasMore)

            val aggregateResult = if (changedStart != null && changedEnd != null) {
                onProgress(
                    SyncTypeProgress(
                        phase = SyncProgressPhase.AGGREGATING,
                        recordsRead = recordsRead,
                        inserted = inserted,
                        updated = updated,
                        duplicates = duplicates,
                        sourceBytesRead = sourceBytesRead,
                        localBytesWritten = localBytesWritten,
                        message = "Updating changed-day summaries"
                    )
                )
                syncDailyAggregatesIfAvailable(
                    descriptor = descriptor,
                    start = changedStart,
                    end = changedEnd.plusMillis(1),
                    zoneId = ZoneId.systemDefault()
                )
            } else {
                SyncAggregateResult()
            }
            val result = HealthDataTypeSyncResult(
                key = descriptor.key,
                requestedStart = changedStart,
                requestedEnd = changedEnd,
                recordsRead = recordsRead,
                recordsInserted = inserted,
                recordsUpdated = updated,
                recordsDeleted = deleted,
                recordsSkippedDuplicate = duplicates,
                valuesStored = valuesStored,
                aggregateRowsRead = aggregateResult.rowsRead,
                aggregateRowsStored = aggregateResult.rowsStored,
                sourceBytesRead = sourceBytesRead,
                localBytesWritten = localBytesWritten + aggregateResult.localBytesWritten,
                sourceStart = changedStart,
                sourceEnd = changedEnd,
                aggregateErrorMessage = aggregateResult.errorMessage
            )
            HealthChangeSyncOutcome.Applied(syncResultAndLog(result, startedAt))
        } catch (t: CancellationException) {
            Log.i(TAG, "Change sync cancelled for type=${descriptor.key}")
            throw t
        } catch (t: Throwable) {
            Log.e(TAG, "Change sync failed for type=${descriptor.key}", t)
            HealthChangeSyncOutcome.Applied(
                syncResultAndLog(
                    result = HealthDataTypeSyncResult(
                        key = descriptor.key,
                        errorMessage = t.message ?: t.javaClass.simpleName
                    ),
                    startedAt = startedAt
                )
            )
        }
    }

    suspend fun recordSyntheticSyncResult(
        result: HealthDataTypeSyncResult,
        startedAt: Instant
    ): HealthDataTypeSyncResult =
        syncResultAndLog(result, startedAt)


    suspend fun getHrAt(at: Instant, toleranceSec: Long = 120): HrSample? {
        ensureAvailable()?.let { throw IllegalStateException(it) }

        val atSec = at.epochSecond
        val lo = atSec - toleranceSec
        val hi = atSec + toleranceSec

        // The tolerance window prevents an old cached sample from answering a nearby HR query.
        val local = dao.nearestInWindow(atSec = atSec, lo = lo, hi = hi)
        val picked = local ?: run {
            val e = HeartRateRecordReader.readEntities(
                client = client,
                start = Instant.ofEpochSecond(lo),
                end = Instant.ofEpochSecond(hi),
                pageSize = 200
            ).minByOrNull { abs(it.epochSecond - atSec) } ?: return null

            dao.insertReplace(listOf(e))
            e
        }

        return HrSample(
            time = Instant.ofEpochSecond(picked.epochSecond),
            bpm = picked.bpm
        )
    }

    private suspend fun storeNormalizedRecords(
        typeKey: String,
        records: List<NormalizedHealthRecord>,
        existingRecordPolicy: ExistingRecordPolicy = ExistingRecordPolicy.KEEP
    ): StoreNormalizedResult = db.withTransaction {
        storeNormalizedRecordsInTransaction(typeKey, records, existingRecordPolicy)
    }

    private suspend fun storeChangePage(
        typeKey: String,
        records: List<NormalizedHealthRecord>,
        deletedRecordUids: List<String>,
        nextToken: String
    ): StoreChangePageResult = db.withTransaction {
        val stored = storeNormalizedRecordsInTransaction(
            typeKey = typeKey,
            records = records,
            existingRecordPolicy = ExistingRecordPolicy.REPLACE_IF_CHANGED
        )
        var deleted = 0
        var changedStart = if (stored.inserted + stored.updated > 0) {
            records.minOfOrNull { it.startTime }
        } else {
            null
        }
        var changedEnd = if (stored.inserted + stored.updated > 0) {
            records.mapNotNull { it.endTime ?: it.startTime }.maxOrNull()
        } else {
            null
        }

        for (recordUid in deletedRecordUids.distinct()) {
            val existing = healthDao.findByRecordUid(typeKey, recordUid) ?: continue
            changedStart = minInstantOrNull(changedStart, Instant.ofEpochMilli(existing.startEpochMillis))
            changedEnd = maxInstantOrNull(
                changedEnd,
                Instant.ofEpochMilli(existing.endEpochMillis ?: existing.startEpochMillis)
            )
            val localIds = listOf(existing.localId)
            retentionDao.deleteValueAcks(localIds)
            retentionDao.deleteRecordAcks(localIds)
            healthDao.deleteValuesForRecord(existing.localId)
            healthDao.deleteRecord(existing.localId)
            deleted++
        }

        changeTokenDao.upsert(
            HealthChangeTokenEntity(
                recordType = typeKey,
                token = nextToken,
                updatedAtEpochMillis = Instant.now().toEpochMilli()
            )
        )
        StoreChangePageResult(
            inserted = stored.inserted,
            updated = stored.updated,
            deleted = deleted,
            skippedDuplicate = stored.skippedDuplicate,
            valuesStored = stored.valuesStored,
            localBytesWritten = stored.localBytesWritten,
            changedStart = changedStart,
            changedEnd = changedEnd
        )
    }

    private suspend fun storeNormalizedRecordsInTransaction(
        typeKey: String,
        records: List<NormalizedHealthRecord>,
        existingRecordPolicy: ExistingRecordPolicy
    ): StoreNormalizedResult {
        val now = Instant.now().toEpochMilli()
        val incomingRecords = records.map { it.toEntity(now) }
        val existingByDedupe = healthDao.findByDedupeKeys(
            recordType = typeKey,
            dedupeKeys = incomingRecords.map { it.dedupeKey }
        ).associateByTo(HashMap()) { it.dedupeKey }
        val recordUids = incomingRecords.mapNotNull { it.recordUid }.distinct()
        val existingByUid = if (
            existingRecordPolicy == ExistingRecordPolicy.REPLACE_IF_CHANGED && recordUids.isNotEmpty()
        ) {
            healthDao.findByRecordUids(
                recordType = typeKey,
                recordUids = recordUids
            ).associateByTo(HashMap()) { it.recordUid.orEmpty() }
        } else {
            HashMap()
        }
        val existingIds = (existingByDedupe.values + existingByUid.values)
            .map { it.localId }
            .distinct()
        val valueKeysByRecordId = if (
            existingRecordPolicy == ExistingRecordPolicy.REPLACE_IF_CHANGED && existingIds.isNotEmpty()
        ) {
            healthDao.valuesForRecords(existingIds)
                .groupBy({ it.recordLocalId }, { it.valueKey })
                .toMutableMap()
        } else {
            HashMap()
        }
        var valuesStored = 0
        var inserted = 0
        var updated = 0
        var skippedDuplicate = 0
        var localBytesWritten = 0L
        for (index in records.indices) {
            val record = records[index]
            val incoming = incomingRecords[index]
            val incomingValueKeys = record.values.map { it.valueKey() }
            val existing = incoming.recordUid?.let(existingByUid::get)
                ?: existingByDedupe[incoming.dedupeKey]
            if (existing == null) {
                val localId = healthDao.insertRecord(incoming)
                val values = record.values.map { it.toEntity(localId, record) }
                if (values.isNotEmpty()) {
                    healthDao.insertValues(values)
                    valuesStored += values.size
                }
                val insertedRecord = incoming.copy(localId = localId)
                existingByDedupe[incoming.dedupeKey] = insertedRecord
                incoming.recordUid?.let { existingByUid[it] = insertedRecord }
                valueKeysByRecordId[localId] = incomingValueKeys
                localBytesWritten += record.approxBytes()
                inserted++
                continue
            }

            if (
                existingRecordPolicy == ExistingRecordPolicy.KEEP ||
                storedRecordMatches(
                    existing = existing,
                    incoming = incoming,
                    incomingValueKeys = incomingValueKeys,
                    storedValueKeys = valueKeysByRecordId[existing.localId].orEmpty()
                )
            ) {
                skippedDuplicate++
                continue
            }

            val localIds = listOf(existing.localId)
            retentionDao.deleteValueAcks(localIds)
            retentionDao.deleteRecordAcks(localIds)
            healthDao.deleteValuesForRecord(existing.localId)
            healthDao.updateRecord(
                incoming.copy(
                    localId = existing.localId,
                    createdEpochMillis = existing.createdEpochMillis,
                    syncStatus = "local",
                    exportStatus = "pending",
                    syncedEpochMillis = null,
                    exportedEpochMillis = null
                )
            )
            val values = record.values.map { it.toEntity(existing.localId, record) }
            if (values.isNotEmpty()) {
                healthDao.insertValues(values)
                valuesStored += values.size
            }
            val updatedRecord = incoming.copy(
                localId = existing.localId,
                createdEpochMillis = existing.createdEpochMillis
            )
            existingByDedupe[incoming.dedupeKey] = updatedRecord
            incoming.recordUid?.let { existingByUid[it] = updatedRecord }
            valueKeysByRecordId[existing.localId] = incomingValueKeys
            localBytesWritten += record.approxBytes()
            updated++
        }
        return StoreNormalizedResult(
            inserted = inserted,
            updated = updated,
            skippedDuplicate = skippedDuplicate,
            valuesStored = valuesStored,
            localBytesWritten = localBytesWritten
        )
    }

    private fun storedRecordMatches(
        existing: HealthRecordEntity,
        incoming: HealthRecordEntity,
        incomingValueKeys: List<String>,
        storedValueKeys: List<String>
    ): Boolean {
        if (!existing.sameHealthPayload(incoming)) return false
        if (storedValueKeys.size != incomingValueKeys.size) return false
        return storedValueKeys.toHashSet() == incomingValueKeys.toHashSet()
    }

    private fun HealthRecordEntity.sameHealthPayload(other: HealthRecordEntity): Boolean =
        recordUid == other.recordUid &&
            dedupeKey == other.dedupeKey &&
            recordType == other.recordType &&
            recordKind == other.recordKind &&
            startEpochMillis == other.startEpochMillis &&
            endEpochMillis == other.endEpochMillis &&
            localDate == other.localDate &&
            startZoneOffsetSeconds == other.startZoneOffsetSeconds &&
            endZoneOffsetSeconds == other.endZoneOffsetSeconds &&
            sourcePackage == other.sourcePackage &&
            metadataJson == other.metadataJson &&
            rawJson == other.rawJson

    private suspend fun syncDailyAggregatesIfAvailable(
        descriptor: HealthDataTypeDescriptor,
        start: Instant,
        end: Instant,
        zoneId: ZoneId
    ): SyncAggregateResult {
        val aggregateReader = descriptor.aggregateReader ?: return SyncAggregateResult()
        val startDate = start.atZone(zoneId).toLocalDate()
        val endDate = end.atZone(zoneId).toLocalDate()

        return runCatching {
            val summaries = aggregateReader.readDaily(
                client = client,
                startDate = startDate,
                endDate = endDate,
                zoneId = zoneId
            )
            val stored = storeAggregateSummaries(
                recordType = descriptor.key,
                source = HealthAggregateDao.SOURCE_HEALTH_CONNECT_AGGREGATE,
                startDate = startDate,
                endDate = endDate,
                requestedStart = start,
                requestedEnd = end,
                summaries = summaries
            )
            SyncAggregateResult(
                rowsRead = summaries.size,
                rowsStored = stored.rowsStored,
                localBytesWritten = stored.localBytesWritten
            )
        }.getOrElse { throwable ->
            Log.e(TAG, "Aggregate sync failed for type=${descriptor.key} start=$start end=$end", throwable)
            SyncAggregateResult(
                errorMessage = throwable.message ?: throwable.javaClass.simpleName
            )
        }
    }

    private suspend fun storeAggregateSummaries(
        recordType: String,
        source: String,
        startDate: LocalDate,
        endDate: LocalDate,
        requestedStart: Instant,
        requestedEnd: Instant,
        summaries: List<HealthAggregateSummary>
    ): StoreAggregateResult {
        val now = Instant.now().toEpochMilli()
        val entities = summaries.map { summary ->
            HealthAggregateEntity(
                recordType = summary.recordType,
                metric = summary.metric,
                bucketPeriod = summary.bucketPeriod,
                bucketStartEpochMillis = summary.bucketStart.toEpochMilli(),
                bucketEndEpochMillis = summary.bucketEnd.toEpochMilli(),
                localDate = summary.localDate.toString(),
                timezoneId = summary.timezoneId,
                value = summary.value,
                unit = summary.unit,
                source = summary.source,
                computedEpochMillis = now,
                requestedStartEpochMillis = requestedStart.toEpochMilli(),
                requestedEndEpochMillis = requestedEnd.toEpochMilli(),
                rawJson = null
            )
        }
        db.withTransaction {
            aggregateDao.deleteForTypeDateRange(
                recordType = recordType,
                source = source,
                startDate = startDate.toString(),
                endDate = endDate.toString()
            )
            if (entities.isNotEmpty()) {
                aggregateDao.insertAll(entities)
            }
        }
        return StoreAggregateResult(
            rowsStored = summaries.size,
            localBytesWritten = entities.sumOf { it.approxBytes() }
        )
    }

    private fun NormalizedHealthRecord.toEntity(lastReadEpochMillis: Long): HealthRecordEntity =
        HealthRecordEntity(
            recordUid = uid,
            dedupeKey = dedupeKey(),
            recordType = typeKey,
            recordKind = kind.id,
            startEpochMillis = startTime.toEpochMilli(),
            endEpochMillis = endTime?.toEpochMilli(),
            localDate = localDateString(),
            startZoneOffsetSeconds = startZoneOffsetSeconds,
            endZoneOffsetSeconds = endZoneOffsetSeconds,
            sourcePackage = sourcePackage,
            metadataJson = null,
            rawJson = null,
            createdEpochMillis = lastReadEpochMillis,
            updatedEpochMillis = lastReadEpochMillis,
            lastReadEpochMillis = lastReadEpochMillis
        )

    private fun NormalizedHealthValue.toEntity(
        recordLocalId: Long,
        record: NormalizedHealthRecord
    ): HealthValueEntity {
        val startMillis = sampleTime?.toEpochMilli() ?: record.startTime.toEpochMilli()
        val endMillis = endTime?.toEpochMilli()
        return HealthValueEntity(
            recordLocalId = recordLocalId,
            valueKey = valueKey(),
            metric = metric,
            unit = unit,
            label = label,
            category = category,
            numericValue = valueFloat ?: valueInt?.toDouble(),
            secondaryNumericValue = secondaryValueFloat,
            valueFloat = valueFloat,
            valueInt = valueInt,
            valueText = valueText,
            valueJson = valueJson,
            startEpochMillis = startMillis,
            endEpochMillis = endMillis,
            localDate = localDateString(record),
            sampleEpochMillis = sampleTime?.toEpochMilli(),
            sequence = sequence
        )
    }

    private fun NormalizedHealthRecord.approxBytes(): Long =
        160L +
            uid.utf8ByteCount() +
            typeKey.utf8ByteCount() +
            kind.id.utf8ByteCount() +
            sourcePackage.utf8ByteCount() +
            metadataJson.utf8ByteCount() +
            rawJson.utf8ByteCount() +
            values.sumOf { it.approxBytes() }

    private fun NormalizedHealthValue.approxBytes(): Long =
        96L +
            metric.utf8ByteCount() +
            unit.utf8ByteCount() +
            label.utf8ByteCount() +
            category.utf8ByteCount() +
            valueText.utf8ByteCount() +
            valueJson.utf8ByteCount()

    private fun HealthAggregateEntity.approxBytes(): Long =
        128L +
            recordType.utf8ByteCount() +
            metric.utf8ByteCount() +
            bucketPeriod.utf8ByteCount() +
            localDate.utf8ByteCount() +
            timezoneId.utf8ByteCount() +
            unit.utf8ByteCount() +
            source.utf8ByteCount() +
            rawJson.utf8ByteCount()

    private fun String?.utf8ByteCount(): Long =
        this?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L

    private fun minInstantOrNull(current: Instant?, candidate: Instant?): Instant? =
        when {
            current == null -> candidate
            candidate == null -> current
            candidate.isBefore(current) -> candidate
            else -> current
        }

    private fun maxInstantOrNull(current: Instant?, candidate: Instant?): Instant? =
        when {
            current == null -> candidate
            candidate == null -> current
            candidate.isAfter(current) -> candidate
            else -> current
        }

    private suspend fun syncResultAndLog(
        result: HealthDataTypeSyncResult,
        startedAt: Instant
    ): HealthDataTypeSyncResult {
        insertSyncRun(result, startedAt)
        val status = result.runStatus().id
        Log.i(
            TAG,
            "Sync result type=${result.key} status=$status " +
                "start=${result.requestedStart} end=${result.requestedEnd} " +
                "read=${result.recordsRead} inserted=${result.recordsInserted} " +
                "updated=${result.recordsUpdated} deleted=${result.recordsDeleted} " +
                "duplicates=${result.recordsSkippedDuplicate} " +
                "values=${result.valuesStored} aggregateRead=${result.aggregateRowsRead} " +
                "aggregateStored=${result.aggregateRowsStored} " +
                "sourceBytes=${result.sourceBytesRead} localBytes=${result.localBytesWritten} " +
                "aggregateError=${result.aggregateErrorMessage} " +
                "skipped=${result.skippedReason} error=${result.errorMessage}"
        )
        return result
    }

    private suspend fun insertSyncRun(
        result: HealthDataTypeSyncResult,
        startedAt: Instant
    ) {
        val finishedAt = Instant.now()
        syncDao.insert(
            HealthSyncRunEntity(
                recordType = result.key,
                requestedStartEpochMillis = result.requestedStart?.toEpochMilli() ?: 0L,
                requestedEndEpochMillis = result.requestedEnd?.toEpochMilli() ?: 0L,
                startedEpochMillis = startedAt.toEpochMilli(),
                finishedEpochMillis = finishedAt.toEpochMilli(),
                status = result.runStatus().id,
                recordsRead = result.recordsRead,
                recordsInserted = result.recordsInserted,
                recordsUpdated = result.recordsUpdated,
                recordsDeleted = result.recordsDeleted,
                recordsSkippedDuplicate = result.recordsSkippedDuplicate,
                valuesStored = result.valuesStored,
                errorMessage = result.errorMessage ?: result.aggregateErrorMessage ?: result.skippedReason
            )
        )
    }

    private fun HealthDataTypeSyncResult.runStatus(): SyncRunStatus =
        terminalStatus ?: when {
            errorMessage != null || aggregateErrorMessage != null -> SyncRunStatus.ERROR
            skippedReason != null -> SyncRunStatus.SKIPPED
            else -> SyncRunStatus.SUCCESS
        }




}

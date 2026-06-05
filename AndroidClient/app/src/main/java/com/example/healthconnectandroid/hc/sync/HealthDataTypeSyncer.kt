package com.example.healthconnectandroid.hc.sync

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.room.withTransaction
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.HealthAggregateDao
import com.example.healthconnectandroid.data.HealthAggregateEntity
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
import com.example.healthconnectandroid.hc.toHeartRateEntities
import com.example.healthconnectandroid.hc.toNormalizedHeartRate
import com.example.healthconnectandroid.hc.valueKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

private const val TAG = "HCHRDemo"

private data class StoreNormalizedResult(
    val inserted: Int,
    val updated: Int,
    val skippedDuplicate: Int,
    val valuesStored: Int,
    val localBytesWritten: Long
)

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

    /** Read ALL pages within [hours] and cache to Room. Returns number of *samples* saved. */
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
        zoneId: ZoneId = ZoneId.systemDefault(),
        onProgress: (SyncTypeProgress) -> Unit = {}
    ): HealthDataTypeSyncResult {
        val startedAt = Instant.now()

        val result = try {
            ensureAvailable()?.let {
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

            val descriptor = HealthDataTypeRegistry.require(key)
            val reader = descriptor.reader ?: return syncResultAndLog(
                result = HealthDataTypeSyncResult(
                    key = key,
                    requestedStart = start,
                    requestedEnd = end,
                    skippedReason = "No reader registered"
                ),
                startedAt = startedAt
            )

            val grantedPermissions = client.permissionController.getGrantedPermissions()
            if (requireBackgroundReadPermission) {
                if (!backgroundReadFeatureAvailable()) {
                    return syncResultAndLog(
                        result = HealthDataTypeSyncResult(
                            key = key,
                            requestedStart = start,
                            requestedEnd = end,
                            skippedReason = "Health Connect background reads are not available on this device"
                        ),
                        startedAt = startedAt
                    )
                }
                if (HealthDataTypeRegistry.backgroundReadPermission !in grantedPermissions) {
                    return syncResultAndLog(
                        result = HealthDataTypeSyncResult(
                            key = key,
                            requestedStart = start,
                            requestedEnd = end,
                            skippedReason = "Missing permission ${HealthDataTypeRegistry.backgroundReadPermission}"
                        ),
                        startedAt = startedAt
                    )
                }
            }
            val requiredReadPermission = descriptor.requiredReadPermission
            if (requiredReadPermission == null) {
                return syncResultAndLog(
                    result = HealthDataTypeSyncResult(
                        key = key,
                        requestedStart = start,
                        requestedEnd = end,
                        skippedReason = "No Health Connect read permission is available for this data type"
                    ),
                    startedAt = startedAt
                )
            }
            if (requiredReadPermission !in grantedPermissions) {
                return syncResultAndLog(
                    result = HealthDataTypeSyncResult(
                        key = key,
                        requestedStart = start,
                        requestedEnd = end,
                        skippedReason = "Missing permission $requiredReadPermission"
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

            reader.readPages(client, start, end) { page ->
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
                    records = page
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
                start = start,
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

        // 1) try local cache but only inside the window
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
        records: List<NormalizedHealthRecord>
    ): StoreNormalizedResult {
        val now = Instant.now().toEpochMilli()
        var valuesStored = 0
        var inserted = 0
        var updated = 0
        var skippedDuplicate = 0
        var localBytesWritten = 0L
        db.withTransaction {
            for (record in records) {
                val incoming = record.toEntity(now)
                val existing = healthDao.findByDedupeKey(typeKey, incoming.dedupeKey)
                if (existing == null) {
                    val localId = healthDao.insertRecord(incoming)
                    val values = record.values.map { it.toEntity(localId, record) }
                    if (values.isNotEmpty()) {
                        healthDao.insertValues(values)
                        valuesStored += values.size
                    }
                    localBytesWritten += record.approxBytes()
                    inserted++
                } else {
                    skippedDuplicate++
                }
            }
        }
        return StoreNormalizedResult(
            inserted = inserted,
            updated = updated,
            skippedDuplicate = skippedDuplicate,
            valuesStored = valuesStored,
            localBytesWritten = localBytesWritten
        )
    }

    private suspend fun syncDailyAggregatesIfAvailable(
        descriptor: HealthDataTypeDescriptor,
        start: Instant,
        end: Instant,
        zoneId: ZoneId
    ): SyncAggregateResult {
        val aggregateReader = descriptor.aggregateReader ?: return SyncAggregateResult()
        val startDate = LocalDate.ofInstant(start, zoneId)
        val endDate = LocalDate.ofInstant(end, zoneId)

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
                "updated=${result.recordsUpdated} duplicates=${result.recordsSkippedDuplicate} " +
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

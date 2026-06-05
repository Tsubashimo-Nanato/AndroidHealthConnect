package com.example.healthconnectandroid.hc.query

import com.example.healthconnectandroid.UnitSystemPreference
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.HealthCsvRow
import com.example.healthconnectandroid.hc.HealthDataTypeDescriptor
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import com.example.healthconnectandroid.hc.HealthDisplayFormatter
import com.example.healthconnectandroid.hc.RecordFullDetails
import com.example.healthconnectandroid.hc.RecordListItem
import com.example.healthconnectandroid.hc.RecordListPage
import com.example.healthconnectandroid.hc.RecordPagingPolicy
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HealthRecordDetailQueryService(
    private val db: AppDb
) {
    private val healthDao = db.healthRecordDao()

    suspend fun inspectorRecordListPage(
        key: String,
        start: Instant,
        end: Instant,
        limit: Int,
        offset: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
        unitSystem: UnitSystemPreference = UnitSystemPreference.METRIC,
        totalCountOverride: Int? = null
    ): RecordListPage = withContext(Dispatchers.Default) {
        val descriptor = HealthDataTypeRegistry.require(key)
        val safeLimit = RecordPagingPolicy.sanitizeLimit(limit)
        val total = totalCountOverride ?: healthDao.countInspectorRowsForTypeRange(
            recordType = key,
            startEpochMillis = start.toEpochMilli(),
            endEpochMillis = end.toEpochMilli()
        )
        val rows = healthDao.inspectorRowsForTypeRange(
            recordType = key,
            startEpochMillis = start.toEpochMilli(),
            endEpochMillis = end.toEpochMilli(),
            limit = safeLimit,
            offset = offset.coerceAtLeast(0)
        ).distinctBy { "${it.localRecordId}:${it.valueKey}" }
        val nextOffset = RecordPagingPolicy.nextOffset(offset, rows.size, total)
        RecordListPage(
            items = rows.map { it.toRecordListItem(descriptor, zoneId, unitSystem) },
            totalCount = total,
            nextOffset = nextOffset,
            limit = safeLimit
        )
    }

    suspend fun inspectorRecordDetails(
        key: String,
        localRecordId: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        unitSystem: UnitSystemPreference = UnitSystemPreference.METRIC
    ): RecordFullDetails = withContext(Dispatchers.Default) {
        val descriptor = HealthDataTypeRegistry.require(key)
        val rows = healthDao.inspectorRowsForRecord(localRecordId)
            .filter { it.recordType == key }
            .distinctBy { "${it.localRecordId}:${it.valueKey}" }
            .map {
                HealthDisplayFormatter.toReadable(
                    row = it,
                    descriptor = descriptor,
                    includeRawDetails = true,
                    zoneId = zoneId,
                    unitSystem = unitSystem
                )
            }
        RecordFullDetails(
            localRecordId = localRecordId,
            recordType = key,
            readableFields = rows.flatMap { it.detailFields }.distinctBy { it.label to it.value },
            valueRows = rows,
            source = rows.mapNotNull { it.sourceText }.distinct().joinToString().ifBlank { null },
            metadata = rows.mapNotNull { it.metadataText }.distinct().joinToString("\n\n").ifBlank { null },
            rawDetails = rows.mapNotNull { it.rawDetailsText }.distinct().joinToString("\n\n").ifBlank { null }
        )
    }

    private fun HealthCsvRow.toRecordListItem(
        descriptor: HealthDataTypeDescriptor,
        zoneId: ZoneId,
        unitSystem: UnitSystemPreference
    ): RecordListItem {
        val readable = HealthDisplayFormatter.toReadable(
            row = this,
            descriptor = descriptor,
            includeRawDetails = false,
            zoneId = zoneId,
            unitSystem = unitSystem
        )
        return RecordListItem(
            rowKey = readable.rowKey,
            localRecordId = readable.localRecordId,
            recordType = readable.recordTypeKey,
            primaryText = readable.primaryText,
            secondaryText = readable.secondaryText,
            timestamp = readable.startTime,
            compactValue = readable.durationText ?: readable.primaryText,
            hasDetails = true
        )
    }


}

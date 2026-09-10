package com.example.healthconnectandroid.hc.upload

import androidx.room.withTransaction
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.HealthAggregateEntity
import com.example.healthconnectandroid.data.HealthRecordEntity
import com.example.healthconnectandroid.data.HealthUploadAckEntity
import com.example.healthconnectandroid.data.HealthValueEntity
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HealthUploadService(
    private val db: AppDb,
    private val client: OkHttpClient = defaultClient()
) {
    private val uploadDao = db.healthUploadDao()

    suspend fun pendingCounts(
        settings: UploadSettings,
        range: UploadTimeRange = UploadTimeRange.ALL
    ): UploadPendingCounts = withContext(Dispatchers.IO) {
        val endpoint = when (
            val validation = UploadEndpointPolicy.validate(settings, requireAuthentication = false)
        ) {
            is UploadEndpointValidation.Valid -> validation.endpoint
            is UploadEndpointValidation.Invalid -> return@withContext UploadPendingCounts.Empty
        }
        pendingCountsForServer(endpoint.serverKey, range.startEpochMillis())
    }

    suspend fun testConnection(settings: UploadSettings): UploadConnectionResult = withContext(Dispatchers.IO) {
        val endpoint = when (val validation = UploadEndpointPolicy.validate(settings)) {
            is UploadEndpointValidation.Valid -> validation.endpoint
            is UploadEndpointValidation.Invalid -> {
                return@withContext UploadConnectionResult(
                    success = false,
                    retryable = false,
                    failureKind = UploadFailureKind.INVALID_URL,
                    message = validation.reason,
                    serverMode = settings.serverMode
                )
            }
        }

        val request = Request.Builder()
            .url(endpoint.statusUrl)
            .applyAuthorization(endpoint.authorization)
            .get()
            .build()

        // NanatoStudio keeps /status public; real key validation happens on ingest.
        runCatching { client.newCall(request).execute() }
            .fold(
                onSuccess = { response ->
                    response.use {
                        if (response.isSuccessful) {
                            val message = if (endpoint.profileIdentity == null) {
                                "Server reachable (${response.code}). API key is checked when uploading data."
                            } else {
                                "Paired profile reachable (${response.code})."
                            }
                            UploadConnectionResult(
                                success = true,
                                retryable = false,
                                message = message,
                                serverMode = settings.serverMode
                            )
                        } else {
                            val failure = UploadHttpFailurePolicy.fromResponse(
                                action = UploadHttpAction.STATUS,
                                url = endpoint.statusUrl,
                                code = response.code,
                                responseBody = response.readText()
                            )
                            UploadConnectionResult(
                                success = false,
                                retryable = failure.retryable,
                                failureKind = failure.failureKind,
                                message = failure.message,
                                serverMode = settings.serverMode
                            )
                        }
                    }
                },
                onFailure = { throwable ->
                    UploadConnectionResult(
                        success = false,
                        retryable = true,
                        failureKind = UploadFailureKind.NETWORK,
                        message = "Network unavailable: ${throwable.shortName()}",
                        serverMode = settings.serverMode
                    )
                }
            )
    }

    suspend fun uploadPending(
        settings: UploadSettings,
        range: UploadTimeRange = UploadTimeRange.ALL,
        maxBatches: Int? = null,
        onProgress: suspend (UploadProgress) -> Unit = {}
    ): UploadRunResult = withContext(Dispatchers.IO) {
        val endpoint = when (val validation = UploadEndpointPolicy.validate(settings)) {
            is UploadEndpointValidation.Valid -> validation.endpoint
            is UploadEndpointValidation.Invalid -> {
                return@withContext UploadRunResult(
                    success = false,
                    retryable = false,
                    failureKind = UploadFailureKind.INVALID_URL,
                    message = validation.reason,
                    errors = 1,
                    pendingCounts = UploadPendingCounts.Empty,
                    serverMode = settings.serverMode
                )
            }
        }

        val startEpochMillis = range.startEpochMillis()
        var pending = pendingCountsForServer(endpoint.serverKey, startEpochMillis)
        if (pending.total == 0) {
            val allPending = if (startEpochMillis == null) {
                pending
            } else {
                pendingCountsForServer(endpoint.serverKey, startEpochMillis = null)
            }
            return@withContext UploadRunResult(
                success = true,
                retryable = false,
                message = uploadMessage("No pending upload rows", range),
                pendingCounts = allPending,
                serverMode = settings.serverMode
            )
        }
        val batchProfile = resolveUploadProfile(endpoint)

        var batchNumber = 0
        var uploadedRecords = 0
        var uploadedValues = 0
        var uploadedAggregates = 0
        var requestBodyBytesSent = 0L
        var responseBodyBytesReceived = 0L
        var readCursor = UploadReadCursor()
        var stoppedBecauseBatchWasEmpty = false
        val totalAtStart = pending.total
        onProgress(
            UploadProgress(
                phase = "Preparing upload",
                currentBatch = batchNumber,
                uploadedItems = 0,
                totalPendingItems = totalAtStart
            )
        )

        while (pending.total > 0 && (maxBatches == null || batchNumber < maxBatches)) {
            coroutineContext.ensureActive()
            batchNumber += 1
            val rows = loadPendingBatch(endpoint.serverKey, startEpochMillis, batchProfile, readCursor)
            if (rows.isEmpty) {
                stoppedBecauseBatchWasEmpty = true
                break
            }
            val batch = UploadBatch.fromRows(
                schemaVersion = SCHEMA_VERSION,
                deviceId = endpoint.profileIdentity?.clientDeviceId ?: settings.deviceId,
                batchId = UUID.randomUUID().toString(),
                createdAtEpochMillis = Instant.now().toEpochMilli(),
                rows = rows
            )

            onProgress(
                UploadProgress(
                    phase = "Uploading batch",
                    currentBatch = batchNumber,
                    uploadedItems = uploadedRecords + uploadedValues + uploadedAggregates,
                    totalPendingItems = totalAtStart,
                    currentType = rows.primaryKind
                )
            )

            when (val postResult = postBatch(endpoint, batch, batchProfile.requestCompression)) {
                is PostBatchResult.Success -> {
                    requestBodyBytesSent += postResult.requestBodyBytes
                    responseBodyBytesReceived += postResult.responseBodyBytes
                    markUploaded(endpoint.serverKey, rows.ackItems, batch.batchId)
                    uploadedRecords += rows.records.size
                    uploadedValues += rows.values.size
                    uploadedAggregates += rows.aggregates.size
                    readCursor = readCursor.advance(rows)
                    pending = pending.minusUploaded(
                        recordsUploaded = rows.records.size,
                        valuesUploaded = rows.values.size,
                        aggregatesUploaded = rows.aggregates.size
                    )
                    onProgress(
                        UploadProgress(
                            phase = "Uploaded batch",
                            currentBatch = batchNumber,
                            uploadedItems = uploadedRecords + uploadedValues + uploadedAggregates,
                            totalPendingItems = totalAtStart,
                            currentType = rows.primaryKind
                        )
                    )
                }
                is PostBatchResult.Failure -> {
                    requestBodyBytesSent += postResult.requestBodyBytes
                    responseBodyBytesReceived += postResult.responseBodyBytes
                    val remaining = pendingCountsForServer(endpoint.serverKey, startEpochMillis = null)
                    return@withContext UploadRunResult(
                        success = false,
                        retryable = postResult.retryable,
                        failureKind = postResult.failureKind,
                        message = postResult.message,
                        uploadedRecords = uploadedRecords,
                        uploadedValues = uploadedValues,
                        uploadedAggregates = uploadedAggregates,
                        requestBodyBytesSent = requestBodyBytesSent,
                        responseBodyBytesReceived = responseBodyBytesReceived,
                        errors = 1,
                        lastUploadTime = Instant.now(),
                        pendingCounts = remaining,
                        serverMode = settings.serverMode
                    )
                }
            }
        }

        val finishedAt = Instant.now()
        val scopedRemaining = pendingCountsForServer(endpoint.serverKey, startEpochMillis)
        val remaining = if (startEpochMillis == null) {
            scopedRemaining
        } else {
            pendingCountsForServer(endpoint.serverKey, startEpochMillis = null)
        }
        val completion = UploadRunCompletionPolicy.resolve(
            pendingRows = scopedRemaining.total,
            uploadedRows = uploadedRecords + uploadedValues + uploadedAggregates,
            stoppedBecauseBatchWasEmpty = stoppedBecauseBatchWasEmpty,
            reachedBatchLimit = maxBatches != null && batchNumber >= maxBatches
        )
        UploadRunResult(
            success = completion.success,
            retryable = completion.retryable,
            failureKind = completion.failureKind,
            message = uploadMessage(completion.message, range),
            uploadedRecords = uploadedRecords,
            uploadedValues = uploadedValues,
            uploadedAggregates = uploadedAggregates,
            requestBodyBytesSent = requestBodyBytesSent,
            responseBodyBytesReceived = responseBodyBytesReceived,
            errors = completion.errors,
            lastUploadTime = finishedAt,
            pendingCounts = remaining,
            serverMode = settings.serverMode
        )
    }

    private suspend fun pendingCountsForServer(
        serverKey: String,
        startEpochMillis: Long?
    ): UploadPendingCounts {
        val healthCounts = if (startEpochMillis == null) {
            UploadPendingCounts(
                records = uploadDao.pendingAllRecordCount(serverKey),
                values = uploadDao.pendingAllValueCount(serverKey),
                aggregates = uploadDao.pendingAllAggregateCount(serverKey)
            )
        } else {
            UploadPendingCounts(
                records = uploadDao.pendingRecentRecordCount(serverKey, startEpochMillis),
                values = uploadDao.pendingRecentValueCount(serverKey, startEpochMillis),
                aggregates = uploadDao.pendingRecentAggregateCount(serverKey, startEpochMillis)
            )
        }

        val medicineCounts = MedicineUploadRows.counts(
            itemCount = uploadDao.pendingMedicineItemCount(
                serverKey = serverKey,
                itemKind = MedicineUploadRows.ITEM_MEDICINE_ITEM
            ),
            doseLogCount = uploadDao.pendingMedicineDoseLogCount(
                serverKey = serverKey,
                itemKind = MedicineUploadRows.ITEM_MEDICINE_DOSE_LOG,
                startEpochMillis = startEpochMillis
            )
        )

        return healthCounts + medicineCounts
    }

    private suspend fun loadPendingBatch(
        serverKey: String,
        startEpochMillis: Long?,
        profile: UploadBatchProfile,
        cursor: UploadReadCursor
    ): PendingUploadRows {
        // Keyset reads keep later batches from rescanning every acknowledged row in multi-gigabyte databases.
        val records = uploadDao.pendingRecords(
            serverKey,
            startEpochMillis,
            cursor.recordLocalId,
            profile.recordLimit
        )
        val values = uploadDao.pendingValues(
            serverKey,
            startEpochMillis,
            cursor.valueLocalId,
            profile.valueLimit
        )
        val aggregates = uploadDao.pendingAggregates(
            serverKey,
            startEpochMillis,
            cursor.aggregateLocalId,
            profile.aggregateLimit
        )
        val healthRows = PendingUploadRows(
            records = records,
            values = values,
            aggregates = aggregates,
            ackItems = healthAcks(records, values, aggregates)
        )

        return healthRows + loadPendingMedicineRows(serverKey, startEpochMillis, healthRows, profile)
    }

    private suspend fun loadPendingMedicineRows(
        serverKey: String,
        startEpochMillis: Long?,
        healthRows: PendingUploadRows,
        profile: UploadBatchProfile
    ): PendingUploadRows {
        var recordSlots = (profile.recordLimit - healthRows.records.size).coerceAtLeast(0)
        var valueSlots = (profile.valueLimit - healthRows.values.size).coerceAtLeast(0)
        if (recordSlots == 0 || valueSlots == 0) return PendingUploadRows()

        val itemLimit = minOf(recordSlots, valueSlots / MedicineUploadRows.MEDICINE_ITEM_VALUE_COUNT)
        val items = if (itemLimit > 0) {
            uploadDao.pendingMedicineItems(
                serverKey = serverKey,
                itemKind = MedicineUploadRows.ITEM_MEDICINE_ITEM,
                limit = itemLimit
            )
        } else {
            emptyList()
        }

        recordSlots -= items.size
        valueSlots -= items.size * MedicineUploadRows.MEDICINE_ITEM_VALUE_COUNT

        val doseLogLimit = minOf(recordSlots, valueSlots / MedicineUploadRows.MEDICINE_DOSE_LOG_VALUE_COUNT)
        val doseLogs = if (doseLogLimit > 0) {
            uploadDao.pendingMedicineDoseLogs(
                serverKey = serverKey,
                itemKind = MedicineUploadRows.ITEM_MEDICINE_DOSE_LOG,
                startEpochMillis = startEpochMillis,
                limit = doseLogLimit
            )
        } else {
            emptyList()
        }

        if (items.isEmpty() && doseLogs.isEmpty()) return PendingUploadRows()

        val schedules = if (items.isEmpty()) {
            emptyList()
        } else {
            uploadDao.schedulesForMedicineUpload(items.map { it.localId })
        }

        return MedicineUploadRows.fromRows(
            items = items,
            schedules = schedules,
            doseLogs = doseLogs
        )
    }

    private fun uploadMessage(message: String, range: UploadTimeRange): String =
        if (range == UploadTimeRange.ALL) message else "$message (${range.label})"

    private fun resolveUploadProfile(endpoint: UploadEndpoint): UploadBatchProfile {
        if (endpoint.profileIdentity != null) return UploadCapabilityPolicy.Legacy
        val request = Request.Builder()
            .url(endpoint.statusUrl)
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) UploadCapabilityPolicy.Legacy
                else UploadCapabilityPolicy.fromStatusJson(response.body?.string())
            }
        } catch (_: IOException) {
            // Capability discovery is optional; legacy upload remains available when status cannot be read.
            UploadCapabilityPolicy.Legacy
        }
    }

    private fun postBatch(
        endpoint: UploadEndpoint,
        batch: UploadBatch,
        compression: UploadRequestCompression
    ): PostBatchResult {
        val jsonBody = batch.toJson(endpoint.profileIdentity).toString().toRequestBody(JSON_MEDIA_TYPE)
        val requestBody = when (compression) {
            UploadRequestCompression.NONE -> jsonBody
            UploadRequestCompression.GZIP -> GzipRequestBody(jsonBody)
        }
        val requestBuilder = Request.Builder()
            .url(endpoint.ingestBatchesUrl)
            .applyAuthorization(endpoint.authorization)
            .post(requestBody)
        if (compression == UploadRequestCompression.GZIP) {
            requestBuilder.addHeader("Content-Encoding", "gzip")
        }
        val request = requestBuilder.build()
        val requestBodyBytes = requestBody.contentLength().coerceAtLeast(0L)

        return try {
            client.newCall(request).execute().use { response ->
                val responseBytes = response.body?.bytes() ?: byteArrayOf()
                if (response.isSuccessful) {
                    PostBatchResult.Success(
                        requestBodyBytes = requestBodyBytes,
                        responseBodyBytes = responseBytes.size.toLong()
                    )
                } else {
                    val failure = UploadHttpFailurePolicy.fromResponse(
                        action = UploadHttpAction.INGEST,
                        url = endpoint.ingestBatchesUrl,
                        code = response.code,
                        responseBody = responseBytes.toString(Charsets.UTF_8)
                    )
                    PostBatchResult.Failure(
                        retryable = failure.retryable,
                        failureKind = failure.failureKind,
                        message = failure.message,
                        requestBodyBytes = requestBodyBytes,
                        responseBodyBytes = responseBytes.size.toLong()
                    )
                }
            }
        } catch (exception: IOException) {
            PostBatchResult.Failure(
                retryable = true,
                failureKind = UploadFailureKind.NETWORK,
                message = "Network unavailable: ${exception.shortName()}",
                requestBodyBytes = 0,
                responseBodyBytes = 0
            )
        }
    }

    private fun Request.Builder.applyAuthorization(
        authorization: UploadAuthorization
    ): Request.Builder = apply {
        when (authorization) {
            is UploadAuthorization.ApiKey -> addHeader(API_KEY_HEADER, authorization.value)
            is UploadAuthorization.Bearer -> addHeader("Authorization", "Bearer ${authorization.token}")
        }
    }

    private fun healthAcks(
        records: List<HealthRecordEntity>,
        values: List<HealthValueEntity>,
        aggregates: List<HealthAggregateEntity>
    ): List<PendingUploadAck> =
        buildList {
            records.forEach { add(PendingUploadAck(ITEM_RECORD, it.localId)) }
            values.forEach { add(PendingUploadAck(ITEM_VALUE, it.localId)) }
            aggregates.forEach { add(PendingUploadAck(ITEM_AGGREGATE, it.localId)) }
        }

    private suspend fun markUploaded(
        serverKey: String,
        ackItems: List<PendingUploadAck>,
        batchId: String
    ) {
        val now = Instant.now().toEpochMilli()
        val acks = ackItems.map { ack(serverKey, it.itemKind, it.localId, batchId, now) }
        if (acks.isEmpty()) return
        db.withTransaction {
            uploadDao.insertAcks(acks)
        }
    }

    private fun ack(
        serverKey: String,
        kind: String,
        localId: Long,
        batchId: String,
        uploadedAtEpochMillis: Long
    ): HealthUploadAckEntity =
        HealthUploadAckEntity(
            serverKey = serverKey,
            itemKind = kind,
            localId = localId,
            batchId = batchId,
            uploadedAtEpochMillis = uploadedAtEpochMillis
        )

    private sealed interface PostBatchResult {
        data class Success(
            val requestBodyBytes: Long,
            val responseBodyBytes: Long
        ) : PostBatchResult
        data class Failure(
            val retryable: Boolean,
            val failureKind: UploadFailureKind,
            val message: String,
            val requestBodyBytes: Long,
            val responseBodyBytes: Long
        ) : PostBatchResult
    }

    private fun Throwable.shortName(): String {
        val detail = message ?: javaClass.simpleName
        return if (detail.contains("CLEARTEXT", ignoreCase = true)) {
            "HTTP cleartext blocked by Android network security config"
        } else {
            detail
        }
    }

    private fun okhttp3.Response.readText(): String? =
        runCatching { body?.string() }.getOrNull()

    companion object {
        private const val API_KEY_HEADER = "X-API-Key"
        private const val SCHEMA_VERSION = 1
        const val BACKGROUND_MAX_BATCHES_PER_RUN = 200
        private const val ITEM_RECORD = "record"
        private const val ITEM_VALUE = "value"
        private const val ITEM_AGGREGATE = "aggregate"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .build()
    }
}

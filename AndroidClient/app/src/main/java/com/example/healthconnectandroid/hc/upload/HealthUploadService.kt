package com.example.healthconnectandroid.hc.upload

import androidx.room.withTransaction
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.HealthUploadAckEntity
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
        val endpoint = when (val validation = UploadEndpointPolicy.validate(settings, requireApiKey = false)) {
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
            .addHeader(API_KEY_HEADER, settings.apiKey.trim())
            .get()
            .build()

        // Status checks reachability and the configured key; ingest remains the payload-shape check.
        runCatching { client.newCall(request).execute() }
            .fold(
                onSuccess = { response ->
                    response.use {
                        if (response.isSuccessful) {
                            UploadConnectionResult(
                                success = true,
                                retryable = false,
                                message = "Connection OK (${response.code}). Upload payloads are checked when sending data.",
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
            return@withContext UploadRunResult(
                success = true,
                retryable = false,
                message = uploadMessage("No pending upload rows", range),
                pendingCounts = pendingCountsForServer(endpoint.serverKey, startEpochMillis = null),
                serverMode = settings.serverMode
            )
        }

        var batchNumber = 0
        var uploadedRecords = 0
        var uploadedValues = 0
        var uploadedAggregates = 0
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
            val rows = loadPendingBatch(endpoint.serverKey, startEpochMillis)
            if (rows.isEmpty) break
            val batch = UploadBatch.fromRows(
                schemaVersion = SCHEMA_VERSION,
                deviceId = settings.deviceId,
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

            when (val postResult = postBatch(endpoint, settings.apiKey, batch)) {
                is PostBatchResult.Success -> {
                    markUploaded(endpoint.serverKey, batch)
                    uploadedRecords += rows.records.size
                    uploadedValues += rows.values.size
                    uploadedAggregates += rows.aggregates.size
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
                    val remaining = pendingCountsForServer(endpoint.serverKey, startEpochMillis = null)
                    return@withContext UploadRunResult(
                        success = false,
                        retryable = postResult.retryable,
                        failureKind = postResult.failureKind,
                        message = postResult.message,
                        uploadedRecords = uploadedRecords,
                        uploadedValues = uploadedValues,
                        uploadedAggregates = uploadedAggregates,
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
        val remaining = pendingCountsForServer(endpoint.serverKey, startEpochMillis = null)
        UploadRunResult(
            success = scopedRemaining.total == 0,
            retryable = scopedRemaining.total > 0,
            failureKind = if (scopedRemaining.total == 0) UploadFailureKind.NONE else UploadFailureKind.SERVER,
            message = if (scopedRemaining.total == 0) {
                uploadMessage("Upload complete: ${uploadedRecords + uploadedValues + uploadedAggregates} rows", range)
            } else {
                uploadMessage("Upload paused with ${scopedRemaining.total} rows pending", range)
            },
            uploadedRecords = uploadedRecords,
            uploadedValues = uploadedValues,
            uploadedAggregates = uploadedAggregates,
            errors = if (scopedRemaining.total == 0) 0 else 1,
            lastUploadTime = finishedAt,
            pendingCounts = remaining,
            serverMode = settings.serverMode
        )
    }

    private suspend fun pendingCountsForServer(
        serverKey: String,
        startEpochMillis: Long?
    ): UploadPendingCounts {
        return if (startEpochMillis == null) {
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
    }

    private suspend fun loadPendingBatch(
        serverKey: String,
        startEpochMillis: Long?
    ): PendingUploadRows {
        return PendingUploadRows(
            records = uploadDao.pendingRecords(serverKey, startEpochMillis, RECORD_LIMIT),
            values = uploadDao.pendingValues(serverKey, startEpochMillis, VALUE_LIMIT),
            aggregates = uploadDao.pendingAggregates(serverKey, startEpochMillis, AGGREGATE_LIMIT)
        )
    }

    private fun uploadMessage(message: String, range: UploadTimeRange): String =
        if (range == UploadTimeRange.ALL) message else "$message (${range.label})"

    private fun postBatch(
        endpoint: UploadEndpoint,
        apiKey: String,
        batch: UploadBatch
    ): PostBatchResult {
        val request = Request.Builder()
            .url(endpoint.ingestBatchesUrl)
            .addHeader(API_KEY_HEADER, apiKey.trim())
            .post(batch.toJson().toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    PostBatchResult.Success
                } else {
                    val failure = UploadHttpFailurePolicy.fromResponse(
                        action = UploadHttpAction.INGEST,
                        url = endpoint.ingestBatchesUrl,
                        code = response.code,
                        responseBody = response.readText()
                    )
                    PostBatchResult.Failure(
                        retryable = failure.retryable,
                        failureKind = failure.failureKind,
                        message = failure.message
                    )
                }
            }
        } catch (exception: IOException) {
            PostBatchResult.Failure(
                retryable = true,
                failureKind = UploadFailureKind.NETWORK,
                message = "Network unavailable: ${exception.shortName()}"
            )
        }
    }

    private suspend fun markUploaded(serverKey: String, batch: UploadBatch) {
        val now = Instant.now().toEpochMilli()
        val acks = buildList {
            batch.records.forEach { add(ack(serverKey, ITEM_RECORD, it.localId, batch.batchId, now)) }
            batch.values.forEach { add(ack(serverKey, ITEM_VALUE, it.localId, batch.batchId, now)) }
            batch.aggregates.forEach { add(ack(serverKey, ITEM_AGGREGATE, it.localId, batch.batchId, now)) }
        }
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
        data object Success : PostBatchResult
        data class Failure(
            val retryable: Boolean,
            val failureKind: UploadFailureKind,
            val message: String
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
        private const val RECORD_LIMIT = 1000
        private const val VALUE_LIMIT = 5000
        private const val AGGREGATE_LIMIT = 1000
        const val BACKGROUND_MAX_BATCHES_PER_RUN = 100
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

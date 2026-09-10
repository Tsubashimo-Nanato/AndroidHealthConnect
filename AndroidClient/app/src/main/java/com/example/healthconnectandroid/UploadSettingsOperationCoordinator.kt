package com.example.healthconnectandroid

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class UploadSettingsOperationLease internal constructor(
    val generation: Long
)

/**
 * Single-flight boundary for every UI action that can persist upload settings.
 *
 * A lease is acquired synchronously before launching a coroutine or QR scanner, so two taps
 * cannot enqueue competing writes. The coroutine Mutex then provides one common write boundary.
 */
internal class UploadSettingsOperationCoordinator {
    private val writeMutex = Mutex()
    private val nextGeneration = AtomicLong(0L)
    private val activeLease = AtomicReference<UploadSettingsOperationLease?>(null)

    fun tryBegin(): UploadSettingsOperationLease? {
        val lease = UploadSettingsOperationLease(nextGeneration.incrementAndGet())
        return if (activeLease.compareAndSet(null, lease)) lease else null
    }

    suspend fun <T> runExclusive(
        lease: UploadSettingsOperationLease,
        block: suspend () -> T
    ): T = writeMutex.withLock {
        check(isCurrent(lease)) { "Upload settings operation is no longer current" }
        block()
    }

    fun isCurrent(lease: UploadSettingsOperationLease): Boolean = activeLease.get() == lease

    fun finish(lease: UploadSettingsOperationLease): Boolean =
        activeLease.compareAndSet(lease, null)

    val isBusy: Boolean
        get() = activeLease.get() != null
}

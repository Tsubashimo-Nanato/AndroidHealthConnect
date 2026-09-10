package com.example.healthconnectandroid

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadSettingsOperationCoordinatorTest {
    @Test
    fun onlyOneOperationCanOwnTheWriteMutexAtATime() = runBlocking {
        val coordinator = UploadSettingsOperationCoordinator()
        val first = requireNotNull(coordinator.tryBegin())

        assertTrue(coordinator.isBusy)
        assertNull(coordinator.tryBegin())

        var ran = false
        coordinator.runExclusive(first) { ran = true }

        assertTrue(ran)
        assertTrue(coordinator.finish(first))
        assertFalse(coordinator.isBusy)
    }

    @Test
    fun staleLeaseCannotFinishANewerGeneration() {
        val coordinator = UploadSettingsOperationCoordinator()
        val first = requireNotNull(coordinator.tryBegin())
        assertTrue(coordinator.finish(first))
        val second = requireNotNull(coordinator.tryBegin())

        assertTrue(second.generation > first.generation)
        assertFalse(coordinator.finish(first))
        assertTrue(coordinator.isCurrent(second))
        assertTrue(coordinator.finish(second))
    }
}

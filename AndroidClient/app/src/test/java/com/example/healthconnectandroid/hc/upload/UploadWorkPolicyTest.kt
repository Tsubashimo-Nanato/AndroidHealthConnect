package com.example.healthconnectandroid.hc.upload

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadWorkPolicyTest {
    @Test
    fun smallIncrementUsesNormalConnectedWork() {
        assertFalse(
            UploadWorkPolicy.shouldDeferToConstrainedWork(
                pendingRows = UploadWorkPolicy.LARGE_BACKLOG_ROWS,
                constrainedRun = false
            )
        )
    }

    @Test
    fun largeBacklogMovesToConstrainedWork() {
        assertTrue(
            UploadWorkPolicy.shouldDeferToConstrainedWork(
                pendingRows = UploadWorkPolicy.LARGE_BACKLOG_ROWS + 1,
                constrainedRun = false
            )
        )
    }

    @Test
    fun constrainedWorkCanUploadLargeBacklog() {
        assertFalse(
            UploadWorkPolicy.shouldDeferToConstrainedWork(
                pendingRows = UploadWorkPolicy.LARGE_BACKLOG_ROWS + 1,
                constrainedRun = true
            )
        )
    }

    @Test
    fun workNamesAreScopedToProfile() {
        assertNotEquals(
            HealthUploadWorker.workName("profile-a"),
            HealthUploadWorker.workName("profile-b")
        )
        assertNotEquals(
            HealthUploadWorker.constrainedWorkName("profile-a"),
            HealthUploadWorker.constrainedWorkName("profile-b")
        )
    }
}

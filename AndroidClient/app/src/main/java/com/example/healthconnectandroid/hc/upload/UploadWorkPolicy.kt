package com.example.healthconnectandroid.hc.upload

internal object UploadWorkPolicy {
    const val LARGE_BACKLOG_ROWS = 50_000

    fun shouldDeferToConstrainedWork(
        pendingRows: Int,
        constrainedRun: Boolean
    ): Boolean = pendingRows > LARGE_BACKLOG_ROWS && !constrainedRun
}

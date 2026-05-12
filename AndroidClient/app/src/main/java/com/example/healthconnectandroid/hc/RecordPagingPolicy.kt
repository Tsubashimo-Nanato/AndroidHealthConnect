package com.example.healthconnectandroid.hc

object RecordPagingPolicy {
    const val DEFAULT_PAGE_SIZE = 100
    const val MAX_PAGE_SIZE = 200

    fun sanitizeLimit(limit: Int): Int =
        limit.coerceIn(1, MAX_PAGE_SIZE)

    fun nextOffset(currentOffset: Int, loadedCount: Int, totalCount: Int): Int? {
        if (loadedCount <= 0) return null
        val next = currentOffset.coerceAtLeast(0) + loadedCount
        return next.takeIf { it < totalCount }
    }
}

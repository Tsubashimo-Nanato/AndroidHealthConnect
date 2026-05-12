package com.example.healthconnectandroid.hc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordPagingPolicyTest {
    @Test
    fun sanitizeLimitCapsLargePages() {
        assertEquals(RecordPagingPolicy.MAX_PAGE_SIZE, RecordPagingPolicy.sanitizeLimit(10_000))
        assertEquals(1, RecordPagingPolicy.sanitizeLimit(0))
    }

    @Test
    fun nextOffsetAdvancesUntilTotalCount() {
        assertEquals(100, RecordPagingPolicy.nextOffset(currentOffset = 0, loadedCount = 100, totalCount = 250))
        assertEquals(200, RecordPagingPolicy.nextOffset(currentOffset = 100, loadedCount = 100, totalCount = 250))
        assertNull(RecordPagingPolicy.nextOffset(currentOffset = 200, loadedCount = 50, totalCount = 250))
    }

    @Test
    fun nextOffsetStopsWhenNoRowsLoaded() {
        assertNull(RecordPagingPolicy.nextOffset(currentOffset = 0, loadedCount = 0, totalCount = 100))
    }
}

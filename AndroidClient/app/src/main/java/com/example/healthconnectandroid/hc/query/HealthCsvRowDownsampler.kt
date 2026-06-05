package com.example.healthconnectandroid.hc.query

import com.example.healthconnectandroid.data.HealthCsvRow
import kotlin.math.ceil

internal class HealthCsvRowMinMaxDownsampler(
    private val startEpochMillis: Long,
    endEpochMillis: Long,
    targetCount: Int
) {
    private val bucketCount = ((targetCount.coerceAtLeast(3) - 2) / 2).coerceAtLeast(1)
    private val bucketMillis = ceil(
        (endEpochMillis - startEpochMillis).coerceAtLeast(1).toDouble() / bucketCount.toDouble()
    ).toLong().coerceAtLeast(1L)
    private val buckets = Array<Bucket?>(bucketCount) { null }
    private var first: HealthCsvRow? = null
    private var last: HealthCsvRow? = null

    fun offer(row: HealthCsvRow) {
        val epoch = row.effectiveEpochMillis()
        if (first == null) first = row
        last = row
        val bucketIndex = ((epoch - startEpochMillis) / bucketMillis)
            .toInt()
            .coerceIn(0, bucketCount - 1)
        val bucket = buckets[bucketIndex] ?: Bucket().also { buckets[bucketIndex] = it }
        bucket.offer(row)
    }

    fun rows(): List<HealthCsvRow> {
        val result = ArrayList<HealthCsvRow>(bucketCount * 2 + 2)
        first?.let(result::addIfNew)
        buckets.forEach { bucket ->
            bucket?.rows()?.forEach(result::addIfNew)
        }
        last?.let(result::addIfNew)
        return result.sortedBy { it.effectiveEpochMillis() }
    }

    private class Bucket {
        private var low: HealthCsvRow? = null
        private var high: HealthCsvRow? = null

        fun offer(row: HealthCsvRow) {
            val value = row.numericValue ?: return
            if ((low?.numericValue ?: Double.POSITIVE_INFINITY) > value) {
                low = row
            }
            if ((high?.numericValue ?: Double.NEGATIVE_INFINITY) < value) {
                high = row
            }
        }

        fun rows(): List<HealthCsvRow> =
            listOfNotNull(low, high)
                .distinctBy { "${it.localRecordId}:${it.valueKey}:${it.numericValue}" }
                .sortedBy { it.effectiveEpochMillis() }
    }
}

private fun MutableList<HealthCsvRow>.addIfNew(row: HealthCsvRow) {
    val last = lastOrNull()
    if (last == null ||
        last.localRecordId != row.localRecordId ||
        last.valueKey != row.valueKey ||
        last.numericValue != row.numericValue
    ) {
        add(row)
    }
}

private fun HealthCsvRow.effectiveEpochMillis(): Long =
    valueStartEpochMillis ?: recordStartEpochMillis

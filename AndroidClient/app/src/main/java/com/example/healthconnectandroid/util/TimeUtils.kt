package com.example.healthconnectandroid.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Parse either strict ISO-8601 (UTC) or some common local formats. */
fun parseInstantFlexible(text: String): Instant? {
    val t = text.trim()
    try { return Instant.parse(t) } catch (_: Exception) {}

    val zone = ZoneId.systemDefault()
    val fmts = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm",
        "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm"
    ).map { DateTimeFormatter.ofPattern(it) }
    for (f in fmts) {
        try {
            val ldt = LocalDateTime.parse(t, f)
            return ldt.atZone(zone).toInstant()
        } catch (_: DateTimeParseException) {}
    }
    return null
}

fun nowIsoUtc(): String = Instant.now().toString()

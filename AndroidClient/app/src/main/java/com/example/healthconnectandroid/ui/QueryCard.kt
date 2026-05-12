package com.example.healthconnectandroid.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Card UI to pick a local date & time (using platform dialogs) and query HR.
 */
@Composable
fun QueryCard(
    onQuery: (Instant) -> Unit,
    nowInstant: Instant = Instant.now()
) {
    val context = LocalContext.current

    val zone = remember { ZoneId.systemDefault() }
    val nowZ = remember(nowInstant) { nowInstant.atZone(zone) }
    var localDate by remember { mutableStateOf(nowZ.toLocalDate()) }
    var localTime by remember { mutableStateOf(nowZ.toLocalTime().withSecond(0).withNano(0)) }

    fun toInstant(): Instant =
        ZonedDateTime.of(localDate, localTime, zone).toInstant()

    fun openDateDialog() {
        DatePickerDialog(
            context,
            { _, y, m, d -> localDate = LocalDate.of(y, m + 1, d) },
            localDate.year,
            localDate.monthValue - 1, // platform uses 0-based month
            localDate.dayOfMonth
        ).show()
    }

    fun openTimeDialog() {
        TimePickerDialog(
            context,
            { _, h, min -> localTime = LocalTime.of(h, min) },
            localTime.hour,
            localTime.minute,
            true /* 24h */
        ).show()
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Query heart rate at a specific time",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { openDateDialog() }) {
                    Text(localDate.toString())
                }
                Button(onClick = { openTimeDialog() }) {
                    Text(localTime.toString())
                }
                Button(onClick = { onQuery(toInstant()) }) {
                    Text("Get HR")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Local timezone: ${zone.id}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

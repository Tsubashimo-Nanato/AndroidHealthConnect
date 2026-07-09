package com.example.healthconnectandroid.medicine

import java.time.LocalDate

data class MedicineDoseEvent(
    val logIds: List<Long>,
    val slot: MedicineSlot,
    val localDate: LocalDate,
    val recordedEpochMillis: Long,
    val status: MedicineDoseStatus,
    val source: MedicineLogSource,
    val medicineNames: List<String>
)

fun List<MedicineDoseLog>.toDoseEvents(): List<MedicineDoseEvent> =
    groupBy { log ->
        MedicineDoseEventKey(
            slot = log.slot,
            localDate = log.localDate,
            recordedEpochMillis = log.recordedEpochMillis,
            status = log.status,
            source = log.source
        )
    }
        .map { (key, logs) ->
            MedicineDoseEvent(
                logIds = logs.map { it.localId },
                slot = key.slot,
                localDate = key.localDate,
                recordedEpochMillis = key.recordedEpochMillis,
                status = key.status,
                source = key.source,
                medicineNames = logs
                    .map { it.medicineName }
                    .distinctBy { it.lowercase() }
                    .sortedWith(String.CASE_INSENSITIVE_ORDER)
            )
        }
        .sortedByDescending { it.recordedEpochMillis }

private data class MedicineDoseEventKey(
    val slot: MedicineSlot,
    val localDate: LocalDate,
    val recordedEpochMillis: Long,
    val status: MedicineDoseStatus,
    val source: MedicineLogSource
)

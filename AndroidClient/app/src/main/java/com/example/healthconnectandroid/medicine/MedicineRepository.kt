package com.example.healthconnectandroid.medicine

import androidx.room.withTransaction
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.MedicineDoseLogEntity
import com.example.healthconnectandroid.data.MedicineItemEntity
import com.example.healthconnectandroid.data.MedicineReminderSettingEntity
import com.example.healthconnectandroid.data.MedicineScheduleEntity
import com.example.healthconnectandroid.data.MedicineScheduleRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class MedicineMutationResult(
    val success: Boolean,
    val message: String,
    val changedRows: Int = 0
)

class MedicineRepository(
    private val db: AppDb
) {
    private val dao = db.medicineDao()

    suspend fun snapshot(
        zoneId: ZoneId,
        now: Instant = Instant.now()
    ): MedicineSnapshot {
        val today = now.atZone(zoneId).toLocalDate()
        val yesterday = today.minusDays(1)
        val calendarStart = today.minusDays(41)
        val reminders = reminderTimes()
        val schedules = dao.activeSchedules()
        val schedulesByMedicine = schedules.groupBy { it.medicineLocalId }
        val medicines = dao.allMedicines().map { entity ->
            entity.toMedicineItem(schedulesByMedicine[entity.localId].orEmpty())
        }
        val activeBySlot = schedules
            .groupBy { MedicineSlot.fromId(it.slot) ?: MedicineSlot.AS_NEEDED }
            .mapValues { entry ->
                entry.value.map { row -> row.toMedicineItem(entry.key) }
            }
        val logs = dao.logsBetween(calendarStart.toString(), today.toString()).mapNotNull { it.toDoseLog() }
        return MedicineSnapshot(
            medicines = medicines,
            schedules = activeBySlot,
            reminderTimes = reminders,
            todayLogs = logs.filter { it.localDate == today },
            yesterdayLogs = logs.filter { it.localDate == yesterday },
            recentDaySummaries = logs.toDaySummaries()
        )
    }

    suspend fun reminderTimes(): Map<MedicineSlot, MedicineReminderTime> {
        val overrides = dao.reminderSettings()
            .mapNotNull { entity ->
                val slot = MedicineSlot.fromId(entity.slot)?.takeIf { it.supportsReminder } ?: return@mapNotNull null
                slot to MedicineReminderTime(
                    slot = slot,
                    hour = entity.hour,
                    minute = entity.minute,
                    enabled = entity.enabled,
                    alarmEnabled = entity.alarmEnabled
                )
            }
            .toMap()
        return MedicineSlotPolicy.defaultReminderTimes + overrides
    }

    suspend fun activeScheduleCount(slot: MedicineSlot): Int =
        dao.activeSchedulesForSlot(slot.id).size

    suspend fun activeScheduledMedicines(slot: MedicineSlot): List<MedicineItem> =
        dao.activeSchedulesForSlot(slot.id).map { row -> row.toMedicineItem(slot) }

    suspend fun addMedicine(
        rawName: String,
        slots: Set<MedicineSlot>,
        now: Instant = Instant.now()
    ): MedicineMutationResult {
        val name = rawName.trim()
        if (name.isBlank()) {
            return MedicineMutationResult(success = false, message = "Medicine name is required")
        }
        val normalizedSlots = slots.ifEmpty { setOf(MedicineSlot.AS_NEEDED) }
        val duplicate = dao.activeMedicines().any { it.name.equals(name, ignoreCase = true) }
        if (duplicate) {
            return MedicineMutationResult(success = false, message = "$name is already active")
        }

        db.withTransaction {
            val id = dao.insertMedicine(
                MedicineItemEntity(
                    name = name,
                    notes = null,
                    doseText = null,
                    summary = null,
                    details = null,
                    active = true,
                    createdEpochMillis = now.toEpochMilli(),
                    updatedEpochMillis = now.toEpochMilli()
                )
            )
            dao.upsertSchedules(
                normalizedSlots.map { slot ->
                    MedicineScheduleEntity(
                        medicineLocalId = id,
                        slot = slot.id,
                        createdEpochMillis = now.toEpochMilli()
                    )
                }
            )
        }
        return MedicineMutationResult(
            success = true,
            message = "Added $name",
            changedRows = 1
        )
    }

    suspend fun seedTestingMedicines(now: Instant = Instant.now()): MedicineMutationResult {
        val nowMillis = now.toEpochMilli()
        val existingByName = dao.allMedicines()
            .associateBy { it.name.normalizedMedicineName() }
        val usedMedicineIds = mutableSetOf<Long>()
        var inserted = 0
        var updated = 0

        db.withTransaction {
            MedicineSeedCatalog.testingMedicines.forEach { seed ->
                val existing = seed.matchingNames()
                    .asSequence()
                    .mapNotNull { existingByName[it] }
                    .firstOrNull { it.localId !in usedMedicineIds }

                if (existing == null) {
                    insertSeedMedicine(seed, nowMillis)
                    inserted += 1
                    return@forEach
                }

                usedMedicineIds += existing.localId
                if (updateSeedMedicine(existing, seed, nowMillis)) {
                    updated += 1
                }
            }
        }

        val changedRows = inserted + updated
        if (changedRows == 0) {
            return MedicineMutationResult(success = true, message = "Medicine seed already present")
        }

        return MedicineMutationResult(
            success = true,
            message = "Seeded $inserted medicines, updated $updated",
            changedRows = changedRows
        )
    }

    suspend fun archiveMedicine(
        medicineLocalId: Long,
        now: Instant = Instant.now()
    ): MedicineMutationResult {
        val item = dao.medicineById(medicineLocalId)
            ?: return MedicineMutationResult(success = false, message = "Medicine not found")
        if (!item.active) {
            return MedicineMutationResult(success = true, message = "${item.name} is already archived")
        }
        dao.updateMedicine(item.copy(active = false, updatedEpochMillis = now.toEpochMilli()))
        return MedicineMutationResult(success = true, message = "Archived ${item.name}", changedRows = 1)
    }

    suspend fun saveReminderTime(
        slot: MedicineSlot,
        rawTime: String,
        enabled: Boolean,
        alarmEnabled: Boolean,
        now: Instant = Instant.now()
    ): MedicineMutationResult {
        if (!slot.supportsReminder) {
            return MedicineMutationResult(success = false, message = "${slot.label} does not use reminders")
        }
        val parsed = MedicineSlotPolicy.parseClockTime(rawTime)
            ?: return MedicineMutationResult(success = false, message = "${slot.label} time must use HH:mm")
        dao.upsertReminderSetting(
            MedicineReminderSettingEntity(
                slot = slot.id,
                hour = parsed.first,
                minute = parsed.second,
                enabled = enabled,
                alarmEnabled = alarmEnabled,
                updatedEpochMillis = now.toEpochMilli()
            )
        )
        return MedicineMutationResult(
            success = true,
            message = "${slot.label} reminder saved",
            changedRows = 1
        )
    }

    suspend fun logDose(
        slot: MedicineSlot,
        status: MedicineDoseStatus,
        medicineLocalIds: Set<Long>,
        extraMedicineName: String?,
        source: MedicineLogSource,
        zoneId: ZoneId,
        now: Instant = Instant.now()
    ): MedicineMutationResult {
        val date = now.atZone(zoneId).toLocalDate()
        val reminders = reminderTimes()
        val scheduledAt = MedicineSlotPolicy.scheduledInstant(slot, date, zoneId, reminders)?.toEpochMilli()
        val scheduled = dao.activeSchedulesForSlot(slot.id)
            .filter { it.medicineLocalId in medicineLocalIds }
        val extra = extraMedicineName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val logs = scheduled.map { row ->
            row.toDoseLog(
                slot = slot,
                date = date,
                scheduledAt = scheduledAt,
                status = status,
                source = source,
                now = now
            )
        } + extra.orEmpty().let { name ->
            if (name.isBlank()) {
                emptyList()
            } else {
                listOf(
                    MedicineDoseLogEntity(
                        medicineLocalId = null,
                        medicineName = name,
                        slot = slot.id,
                        localDate = date.toString(),
                        scheduledEpochMillis = scheduledAt,
                        recordedEpochMillis = now.toEpochMilli(),
                        status = status.id,
                        source = source.id,
                        note = null,
                        createdEpochMillis = now.toEpochMilli()
                    )
                )
            }
        }

        if (logs.isEmpty()) {
            return MedicineMutationResult(success = false, message = "Select at least one medicine")
        }
        val inserted = dao.insertDoseLogs(logs).size
        val timeText = now.atZone(zoneId).toLocalTime().format(LogTimeFormatter)
        return MedicineMutationResult(
            success = true,
            message = "Logged ${slot.label} ${status.label.lowercase()} at $timeText ($inserted ${if (inserted == 1) "medicine" else "medicines"})",
            changedRows = inserted
        )
    }

    suspend fun logReminderResponse(
        slot: MedicineSlot,
        status: MedicineDoseStatus,
        zoneId: ZoneId,
        medicineLocalIds: Set<Long>? = null,
        now: Instant = Instant.now()
    ): MedicineMutationResult {
        val date = now.atZone(zoneId).toLocalDate()
        val alreadyLogged = dao.logsForDate(date.toString())
            .asSequence()
            .filter { it.slot == slot.id && it.source == MedicineLogSource.REMINDER.id }
            .mapNotNull { it.medicineLocalId }
            .toSet()
        val scheduledForSlot = dao.activeSchedulesForSlot(slot.id)
        val selectedIds = medicineLocalIds?.filter { it > 0L }?.toSet()
        val scheduled = scheduledForSlot
            .filterNot { it.medicineLocalId in alreadyLogged }
            .filter { selectedIds == null || it.medicineLocalId in selectedIds }
        if (scheduled.isEmpty()) {
            return MedicineMutationResult(success = true, message = "No pending ${slot.label.lowercase()} doses")
        }
        val reminders = reminderTimes()
        val scheduledAt = MedicineSlotPolicy.scheduledInstant(slot, date, zoneId, reminders)?.toEpochMilli()
        val logs = scheduled.map { row ->
            row.toDoseLog(
                slot = slot,
                date = date,
                scheduledAt = scheduledAt,
                status = status,
                source = MedicineLogSource.REMINDER,
                now = now
            )
        }
        val inserted = dao.insertDoseLogs(logs).size
        return MedicineMutationResult(
            success = true,
            message = "Logged $inserted ${slot.label.lowercase()} doses",
            changedRows = inserted
        )
    }

    suspend fun deleteDoseLogs(logIds: Set<Long>): MedicineMutationResult {
        val ids = logIds.filter { it > 0L }.distinct()
        if (ids.isEmpty()) {
            return MedicineMutationResult(success = false, message = "No medicine log selected")
        }
        val removed = dao.deleteDoseLogs(ids)
        return MedicineMutationResult(
            success = true,
            message = "Deleted $removed medicine log ${if (removed == 1) "row" else "rows"}",
            changedRows = removed
        )
    }

    private fun MedicineItemEntity.toMedicineItem(scheduleRows: List<MedicineScheduleRow>): MedicineItem =
        MedicineItem(
            localId = localId,
            name = name,
            notes = notes,
            doseText = doseText,
            summary = summary,
            details = details,
            active = active,
            slots = scheduleRows.mapNotNull { MedicineSlot.fromId(it.slot) }.distinct()
        )

    private fun MedicineScheduleRow.toMedicineItem(slot: MedicineSlot): MedicineItem =
        MedicineItem(
            localId = medicineLocalId,
            name = medicineName,
            notes = null,
            doseText = medicineDoseText,
            summary = medicineSummary,
            details = medicineDetails,
            active = true,
            slots = listOf(slot)
        )

    private suspend fun insertSeedMedicine(seed: MedicineSeed, nowMillis: Long) {
        val id = dao.insertMedicine(
            MedicineItemEntity(
                name = seed.name,
                notes = null,
                doseText = seed.doseText,
                summary = seed.summary,
                details = seed.details,
                active = true,
                createdEpochMillis = nowMillis,
                updatedEpochMillis = nowMillis
            )
        )
        replaceSeedSchedules(id, seed.slots, nowMillis)
    }

    private suspend fun updateSeedMedicine(
        existing: MedicineItemEntity,
        seed: MedicineSeed,
        nowMillis: Long
    ): Boolean {
        val medicineChanged = existing.name != seed.name ||
            existing.doseText != seed.doseText ||
            existing.summary != seed.summary ||
            existing.details != seed.details
        if (medicineChanged) {
            dao.updateMedicine(
                existing.copy(
                    name = seed.name,
                    doseText = seed.doseText,
                    summary = seed.summary,
                    details = seed.details,
                    updatedEpochMillis = nowMillis
                )
            )
        }

        val scheduleChanged = if (existing.active) {
            val currentSlots = dao.schedulesForMedicine(existing.localId)
                .mapNotNull { MedicineSlot.fromId(it.slot) }
                .toSet()
            if (currentSlots == seed.slots) {
                false
            } else {
                replaceSeedSchedules(existing.localId, seed.slots, nowMillis)
                true
            }
        } else {
            false
        }

        return medicineChanged || scheduleChanged
    }

    private suspend fun replaceSeedSchedules(
        medicineLocalId: Long,
        slots: Set<MedicineSlot>,
        nowMillis: Long
    ) {
        dao.deleteSchedulesForMedicine(medicineLocalId)
        dao.upsertSchedules(
            slots.map { slot ->
                MedicineScheduleEntity(
                    medicineLocalId = medicineLocalId,
                    slot = slot.id,
                    createdEpochMillis = nowMillis
                )
            }
        )
    }

    private fun String.normalizedMedicineName(): String =
        trim().lowercase()

    private fun MedicineSeed.matchingNames(): Set<String> =
        (aliases + name).map { it.normalizedMedicineName() }.toSet()

    private fun List<MedicineDoseLog>.toDaySummaries(): List<MedicineDayLogSummary> =
        groupBy { it.localDate }
            .map { (date, logs) ->
                MedicineDayLogSummary(
                    date = date,
                    takenCount = logs.count { it.status == MedicineDoseStatus.TAKEN },
                    missedCount = logs.count { it.status == MedicineDoseStatus.MISSED },
                    skippedCount = logs.count { it.status == MedicineDoseStatus.SKIPPED }
                )
            }
            .sortedByDescending { it.date }

    private fun MedicineScheduleRow.toDoseLog(
        slot: MedicineSlot,
        date: LocalDate,
        scheduledAt: Long?,
        status: MedicineDoseStatus,
        source: MedicineLogSource,
        now: Instant
    ): MedicineDoseLogEntity =
        MedicineDoseLogEntity(
            medicineLocalId = medicineLocalId,
            medicineName = medicineName,
            slot = slot.id,
            localDate = date.toString(),
            scheduledEpochMillis = scheduledAt,
            recordedEpochMillis = now.toEpochMilli(),
            status = status.id,
            source = source.id,
            note = null,
            createdEpochMillis = now.toEpochMilli()
        )

    private fun MedicineDoseLogEntity.toDoseLog(): MedicineDoseLog? {
        val slot = MedicineSlot.fromId(slot) ?: return null
        return MedicineDoseLog(
            localId = localId,
            medicineLocalId = medicineLocalId,
            medicineName = medicineName,
            slot = slot,
            localDate = runCatching { LocalDate.parse(localDate) }.getOrNull() ?: return null,
            scheduledEpochMillis = scheduledEpochMillis,
            recordedEpochMillis = recordedEpochMillis,
            status = MedicineDoseStatus.fromId(status),
            source = MedicineLogSource.fromId(source),
            note = note
        )
    }

    private companion object {
        val LogTimeFormatter: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    }
}

package com.example.healthconnectandroid.ui.sleep

import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import com.example.healthconnectandroid.hc.ReadableHealthRecord
import com.example.healthconnectandroid.hc.SleepSessionAnalysis
import com.example.healthconnectandroid.hc.SleepSessionAnalyzer
import com.example.healthconnectandroid.hc.SleepSessionInput
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SleepSessionUiModel(
    val session: ReadableHealthRecord,
    val stages: List<ReadableHealthRecord>,
    val analysisStart: Instant?,
    val analysisEnd: Instant?,
    val analysis: SleepSessionAnalysis
)

fun sleepSessionModels(
    rows: List<ReadableHealthRecord>,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<SleepSessionUiModel> =
    rows
        .filter { it.recordTypeKey == HealthDataTypeKeys.SLEEP_SESSION }
        .groupBy { it.localRecordId }
        .values
        .mapNotNull { group ->
            val session = group.firstOrNull { it.metric == "duration" } ?: group.firstOrNull()
                ?: return@mapNotNull null
            val stages = group.filter { it.metric == "sleep_stage" }
                .sortedBy { it.startTime ?: Instant.EPOCH }
            val inferredStart = session.startTime ?: stages.mapNotNull { it.startTime }.minOrNull()
            val inferredEnd = session.endTime
                ?.takeIf { end -> inferredStart == null || end.isAfter(inferredStart) }
                ?: stages.mapNotNull { it.endTime }.maxOrNull()
            SleepSessionUiModel(
                session = session,
                stages = stages,
                analysisStart = inferredStart,
                analysisEnd = inferredEnd,
                analysis = SleepSessionAnalyzer.analyze(
                    start = inferredStart,
                    end = inferredEnd,
                    stageCount = stages.size,
                    now = LocalDate.now(zoneId),
                    zoneId = zoneId
                )
            )
        }
        .sortedByDescending { it.session.startTime ?: Instant.EPOCH }

fun SleepSessionUiModel.toSleepInput(): SleepSessionInput =
    SleepSessionInput(
        start = analysisStart,
        end = analysisEnd,
        stageCount = stages.size
    )

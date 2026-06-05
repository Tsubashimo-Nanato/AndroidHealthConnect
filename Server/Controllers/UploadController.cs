using HC_server.Data;
using HC_server.Models;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HC_server.Controllers;

[ApiController]
[Route("health/api/v1")]
public class UploadController : ControllerBase
{
    [HttpGet("status")]
    public IActionResult Status()
    {
        return Ok(new
        {
            ok = true,
            service = "AndroidHealthConnect",
            schemaVersion = 1,
            serverTimeEpochMillis = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        });
    }

    [HttpPost("ingest/batches")]
    public async Task<IActionResult> IngestBatch([FromBody] UploadBatchDto body, [FromServices] AppDbContext db)
    {
        if (string.IsNullOrWhiteSpace(body.DeviceId))
        {
            return BadRequest(new { error = "deviceId is required" });
        }
        if (string.IsNullOrWhiteSpace(body.BatchId))
        {
            return BadRequest(new { error = "batchId is required" });
        }
        if (body.SchemaVersion != 1)
        {
            return BadRequest(new { error = $"Unsupported schemaVersion {body.SchemaVersion}" });
        }

        var uploadedAt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        var recordIds = body.Records.Select(x => x.LocalId).Distinct().ToList();
        var valueIds = body.Values.Select(x => x.LocalId).Distinct().ToList();
        var aggregateIds = body.Aggregates.Select(x => x.LocalId).Distinct().ToList();

        var existingRecordIds = await db.UploadedHealthRecords
            .Where(x => x.DeviceId == body.DeviceId && recordIds.Contains(x.LocalId))
            .Select(x => x.LocalId)
            .ToListAsync();
        var existingValueIds = await db.UploadedHealthValues
            .Where(x => x.DeviceId == body.DeviceId && valueIds.Contains(x.LocalId))
            .Select(x => x.LocalId)
            .ToListAsync();
        var existingAggregateIds = await db.UploadedHealthAggregates
            .Where(x => x.DeviceId == body.DeviceId && aggregateIds.Contains(x.LocalId))
            .Select(x => x.LocalId)
            .ToListAsync();

        var existingRecords = existingRecordIds.ToHashSet();
        var existingValues = existingValueIds.ToHashSet();
        var existingAggregates = existingAggregateIds.ToHashSet();

        var records = body.Records
            .Where(x => existingRecords.Add(x.LocalId))
            .Select(x => x.ToEntity(body.DeviceId, body.BatchId, uploadedAt))
            .ToList();
        var values = body.Values
            .Where(x => existingValues.Add(x.LocalId))
            .Select(x => x.ToEntity(body.DeviceId, body.BatchId, uploadedAt))
            .ToList();
        var aggregates = body.Aggregates
            .Where(x => existingAggregates.Add(x.LocalId))
            .Select(x => x.ToEntity(body.DeviceId, body.BatchId, uploadedAt))
            .ToList();

        await using var tx = await db.Database.BeginTransactionAsync();
        db.ChangeTracker.AutoDetectChangesEnabled = false;
        await db.UploadedHealthRecords.AddRangeAsync(records);
        await db.UploadedHealthValues.AddRangeAsync(values);
        await db.UploadedHealthAggregates.AddRangeAsync(aggregates);
        await db.SaveChangesAsync();
        await tx.CommitAsync();

        return Ok(new
        {
            accepted = true,
            batchId = body.BatchId,
            records = body.Records.Count,
            values = body.Values.Count,
            aggregates = body.Aggregates.Count,
            insertedRecords = records.Count,
            insertedValues = values.Count,
            insertedAggregates = aggregates.Count
        });
    }
}

internal static class UploadDtoMapping
{
    public static UploadedHealthRecord ToEntity(
        this UploadRecordDto source,
        string deviceId,
        string batchId,
        long uploadedAtEpochMillis
    ) => new()
    {
        DeviceId = deviceId,
        BatchId = batchId,
        LocalId = source.LocalId,
        RecordUid = source.RecordUid,
        DedupeKey = source.DedupeKey,
        RecordType = source.RecordType,
        RecordKind = source.RecordKind,
        StartEpochMillis = source.StartEpochMillis,
        EndEpochMillis = source.EndEpochMillis,
        LocalDate = source.LocalDate,
        StartZoneOffsetSeconds = source.StartZoneOffsetSeconds,
        EndZoneOffsetSeconds = source.EndZoneOffsetSeconds,
        SourcePackage = source.SourcePackage,
        CreatedEpochMillis = source.CreatedEpochMillis,
        UpdatedEpochMillis = source.UpdatedEpochMillis,
        LastReadEpochMillis = source.LastReadEpochMillis,
        UploadedAtEpochMillis = uploadedAtEpochMillis
    };

    public static UploadedHealthValue ToEntity(
        this UploadValueDto source,
        string deviceId,
        string batchId,
        long uploadedAtEpochMillis
    ) => new()
    {
        DeviceId = deviceId,
        BatchId = batchId,
        LocalId = source.LocalId,
        RecordLocalId = source.RecordLocalId,
        ValueKey = source.ValueKey,
        Metric = source.Metric,
        Unit = source.Unit,
        Label = source.Label,
        Category = source.Category,
        NumericValue = source.NumericValue,
        SecondaryNumericValue = source.SecondaryNumericValue,
        ValueFloat = source.ValueFloat,
        ValueInt = source.ValueInt,
        ValueText = source.ValueText,
        ValueJson = source.ValueJson,
        StartEpochMillis = source.StartEpochMillis,
        EndEpochMillis = source.EndEpochMillis,
        LocalDate = source.LocalDate,
        SampleEpochMillis = source.SampleEpochMillis,
        Sequence = source.Sequence,
        UploadedAtEpochMillis = uploadedAtEpochMillis
    };

    public static UploadedHealthAggregate ToEntity(
        this UploadAggregateDto source,
        string deviceId,
        string batchId,
        long uploadedAtEpochMillis
    ) => new()
    {
        DeviceId = deviceId,
        BatchId = batchId,
        LocalId = source.LocalId,
        RecordType = source.RecordType,
        Metric = source.Metric,
        BucketPeriod = source.BucketPeriod,
        BucketStartEpochMillis = source.BucketStartEpochMillis,
        BucketEndEpochMillis = source.BucketEndEpochMillis,
        LocalDate = source.LocalDate,
        TimezoneId = source.TimezoneId,
        Value = source.Value,
        Unit = source.Unit,
        Source = source.Source,
        ComputedEpochMillis = source.ComputedEpochMillis,
        RequestedStartEpochMillis = source.RequestedStartEpochMillis,
        RequestedEndEpochMillis = source.RequestedEndEpochMillis,
        UploadedAtEpochMillis = uploadedAtEpochMillis
    };
}

using HC_server.Data;
using HC_server.Models;
using Microsoft.EntityFrameworkCore;

namespace HC_server.Controllers;

internal sealed record UploadIngestPlan(
    List<UploadedHealthRecord> Records,
    List<UploadedHealthValue> Values,
    List<UploadedHealthAggregate> Aggregates
);

internal static class UploadIngestPlanner
{
    public static async Task<UploadIngestPlan> BuildAsync(
        UploadBatchDto body,
        AppDbContext db,
        long uploadedAtEpochMillis
    )
    {
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
            .Select(x => x.ToEntity(body.DeviceId, body.BatchId, uploadedAtEpochMillis))
            .ToList();
        var values = body.Values
            .Where(x => existingValues.Add(x.LocalId))
            .Select(x => x.ToEntity(body.DeviceId, body.BatchId, uploadedAtEpochMillis))
            .ToList();
        var aggregates = body.Aggregates
            .Where(x => existingAggregates.Add(x.LocalId))
            .Select(x => x.ToEntity(body.DeviceId, body.BatchId, uploadedAtEpochMillis))
            .ToList();

        return new UploadIngestPlan(records, values, aggregates);
    }
}

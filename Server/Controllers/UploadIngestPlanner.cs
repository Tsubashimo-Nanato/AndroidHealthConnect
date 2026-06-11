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
        // Upload workers may retry the same batch, so local ids are treated as idempotency keys per device.
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

        var records = MapNewItems(
            body.Records,
            existingRecords,
            x => x.LocalId,
            x => x.ToEntity(body.DeviceId, body.BatchId, uploadedAtEpochMillis)
        );
        var values = MapNewItems(
            body.Values,
            existingValues,
            x => x.LocalId,
            x => x.ToEntity(body.DeviceId, body.BatchId, uploadedAtEpochMillis)
        );
        var aggregates = MapNewItems(
            body.Aggregates,
            existingAggregates,
            x => x.LocalId,
            x => x.ToEntity(body.DeviceId, body.BatchId, uploadedAtEpochMillis)
        );

        return new UploadIngestPlan(records, values, aggregates);
    }

    private static List<TTarget> MapNewItems<TSource, TTarget>(
        IEnumerable<TSource> sources,
        HashSet<long> seenLocalIds,
        Func<TSource, long> localId,
        Func<TSource, TTarget> map
    )
    {
        var items = new List<TTarget>();
        foreach (var source in sources)
        {
            if (!seenLocalIds.Add(localId(source)))
            {
                continue;
            }

            items.Add(map(source));
        }

        return items;
    }
}

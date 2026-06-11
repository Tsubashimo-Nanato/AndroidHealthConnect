using HC_server.Controllers;
using HC_server.Models;
using Xunit;

namespace Server.Tests;

public sealed class UploadIngestPlannerTests
{
    [Fact]
    public async Task keeps_only_new_local_ids_for_the_same_device()
    {
        await using var store = await TestDb.CreateAsync();
        store.Db.UploadedHealthRecords.Add(UploadedRecord(localId: 1));
        store.Db.UploadedHealthValues.Add(UploadedValue(localId: 10));
        store.Db.UploadedHealthAggregates.Add(UploadedAggregate(localId: 20));
        await store.Db.SaveChangesAsync();

        var body = new UploadBatchDto
        {
            SchemaVersion = 1,
            DeviceId = "phone-a",
            BatchId = "batch-new",
            Records =
            {
                UploadRecord(localId: 1),
                UploadRecord(localId: 2),
                UploadRecord(localId: 2)
            },
            Values =
            {
                UploadValue(localId: 10),
                UploadValue(localId: 11),
                UploadValue(localId: 11)
            },
            Aggregates =
            {
                UploadAggregate(localId: 20),
                UploadAggregate(localId: 21),
                UploadAggregate(localId: 21)
            }
        };

        var plan = await UploadIngestPlanner.BuildAsync(body, store.Db, uploadedAtEpochMillis: 9000);

        var record = Assert.Single(plan.Records);
        var value = Assert.Single(plan.Values);
        var aggregate = Assert.Single(plan.Aggregates);
        Assert.Equal(2, record.LocalId);
        Assert.Equal(11, value.LocalId);
        Assert.Equal(21, aggregate.LocalId);
        Assert.Equal("phone-a", record.DeviceId);
        Assert.Equal("batch-new", value.BatchId);
        Assert.Equal(9000, aggregate.UploadedAtEpochMillis);
    }

    private static UploadedHealthRecord UploadedRecord(long localId) => new()
    {
        DeviceId = "phone-a",
        BatchId = "batch-old",
        LocalId = localId,
        DedupeKey = $"record-{localId}",
        RecordType = "heart_rate",
        RecordKind = "sample",
        LocalDate = "2026-06-11"
    };

    private static UploadedHealthValue UploadedValue(long localId) => new()
    {
        DeviceId = "phone-a",
        BatchId = "batch-old",
        LocalId = localId,
        RecordLocalId = 1,
        ValueKey = $"value-{localId}",
        Metric = "heart_rate"
    };

    private static UploadedHealthAggregate UploadedAggregate(long localId) => new()
    {
        DeviceId = "phone-a",
        BatchId = "batch-old",
        LocalId = localId,
        RecordType = "steps",
        Metric = "steps",
        BucketPeriod = "day",
        LocalDate = "2026-06-11",
        Source = "test"
    };

    private static UploadRecordDto UploadRecord(long localId) => new()
    {
        LocalId = localId,
        DedupeKey = $"record-{localId}",
        RecordType = "heart_rate",
        RecordKind = "sample",
        LocalDate = "2026-06-11"
    };

    private static UploadValueDto UploadValue(long localId) => new()
    {
        LocalId = localId,
        RecordLocalId = 1,
        ValueKey = $"value-{localId}",
        Metric = "heart_rate"
    };

    private static UploadAggregateDto UploadAggregate(long localId) => new()
    {
        LocalId = localId,
        RecordType = "steps",
        Metric = "steps",
        BucketPeriod = "day",
        LocalDate = "2026-06-11",
        Source = "test"
    };
}

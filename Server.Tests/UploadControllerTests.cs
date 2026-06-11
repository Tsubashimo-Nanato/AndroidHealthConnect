using System.Text.Json;
using HC_server.Controllers;
using HC_server.Models;
using Microsoft.AspNetCore.Mvc;
using Xunit;

namespace Server.Tests;

public sealed class UploadControllerTests
{
    [Fact]
    public void status_keeps_public_response_shape()
    {
        var controller = new UploadController();

        var result = controller.Status();

        var ok = Assert.IsType<OkObjectResult>(result);
        using var json = JsonDocument.Parse(JsonSerializer.Serialize(ok.Value));
        var root = json.RootElement;
        Assert.True(root.GetProperty("ok").GetBoolean());
        Assert.Equal("AndroidHealthConnect", root.GetProperty("service").GetString());
        Assert.Equal(1, root.GetProperty("schemaVersion").GetInt32());
        Assert.True(root.TryGetProperty("serverTimeEpochMillis", out _));
    }

    [Fact]
    public async Task ingest_reports_received_and_inserted_counts()
    {
        await using var store = await TestDb.CreateAsync();
        var controller = new UploadController();
        var body = BatchWithOneRecord();

        var first = await controller.IngestBatch(body, store.Db);
        var second = await controller.IngestBatch(body, store.Db);

        AssertCounts(first, insertedRecords: 1);
        AssertCounts(second, insertedRecords: 0);
    }

    private static UploadBatchDto BatchWithOneRecord() => new()
    {
        SchemaVersion = 1,
        DeviceId = "phone-a",
        BatchId = "batch-a",
        Records =
        {
            new UploadRecordDto
            {
                LocalId = 1,
                DedupeKey = "heart-rate-1",
                RecordType = "heart_rate",
                RecordKind = "sample",
                StartEpochMillis = 1_781_126_400_000,
                LocalDate = "2026-06-11"
            }
        }
    };

    private static void AssertCounts(IActionResult result, int insertedRecords)
    {
        var ok = Assert.IsType<OkObjectResult>(result);
        using var json = JsonDocument.Parse(JsonSerializer.Serialize(ok.Value));
        var root = json.RootElement;
        Assert.True(root.GetProperty("accepted").GetBoolean());
        Assert.Equal("batch-a", root.GetProperty("batchId").GetString());
        Assert.Equal(1, root.GetProperty("records").GetInt32());
        Assert.Equal(0, root.GetProperty("values").GetInt32());
        Assert.Equal(0, root.GetProperty("aggregates").GetInt32());
        Assert.Equal(insertedRecords, root.GetProperty("insertedRecords").GetInt32());
        Assert.Equal(0, root.GetProperty("insertedValues").GetInt32());
        Assert.Equal(0, root.GetProperty("insertedAggregates").GetInt32());
    }
}

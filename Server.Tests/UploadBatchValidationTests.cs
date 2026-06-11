using HC_server.Controllers;
using HC_server.Models;
using Xunit;

namespace Server.Tests;

public sealed class UploadBatchValidationTests
{
    [Fact]
    public void rejects_missing_device_id()
    {
        var body = ValidBatch();
        body.DeviceId = " ";

        var error = UploadBatchValidation.ErrorFor(body);

        Assert.Equal("deviceId is required", error);
    }

    [Fact]
    public void rejects_missing_batch_id()
    {
        var body = ValidBatch();
        body.BatchId = "";

        var error = UploadBatchValidation.ErrorFor(body);

        Assert.Equal("batchId is required", error);
    }

    [Fact]
    public void rejects_unsupported_schema_version()
    {
        var body = ValidBatch();
        body.SchemaVersion = 2;

        var error = UploadBatchValidation.ErrorFor(body);

        Assert.Equal("Unsupported schemaVersion 2", error);
    }

    [Fact]
    public void accepts_required_batch_fields()
    {
        var error = UploadBatchValidation.ErrorFor(ValidBatch());

        Assert.Null(error);
    }

    private static UploadBatchDto ValidBatch() => new()
    {
        SchemaVersion = 1,
        DeviceId = "phone-a",
        BatchId = "batch-a"
    };
}

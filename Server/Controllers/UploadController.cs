using HC_server.Data;
using HC_server.Models;
using Microsoft.AspNetCore.Mvc;

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
        var validationError = UploadBatchValidation.ErrorFor(body);
        if (validationError is not null)
        {
            return BadRequest(new { error = validationError });
        }

        var uploadedAt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        var ingestPlan = await UploadIngestPlanner.BuildAsync(body, db, uploadedAt);

        await using var tx = await db.Database.BeginTransactionAsync();
        db.ChangeTracker.AutoDetectChangesEnabled = false;
        await db.UploadedHealthRecords.AddRangeAsync(ingestPlan.Records);
        await db.UploadedHealthValues.AddRangeAsync(ingestPlan.Values);
        await db.UploadedHealthAggregates.AddRangeAsync(ingestPlan.Aggregates);
        await db.SaveChangesAsync();
        await tx.CommitAsync();

        return Ok(new
        {
            accepted = true,
            batchId = body.BatchId,
            records = body.Records.Count,
            values = body.Values.Count,
            aggregates = body.Aggregates.Count,
            insertedRecords = ingestPlan.Records.Count,
            insertedValues = ingestPlan.Values.Count,
            insertedAggregates = ingestPlan.Aggregates.Count
        });
    }
}

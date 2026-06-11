using HC_server.Data;
using HC_server.Models;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HC_server.Controllers;

[ApiController]
public class HealthController : ControllerBase
{
    [HttpPost("/ingest")]
    public async Task<IActionResult> Ingest([FromBody] IngestBatchDto body, [FromServices] AppDbContext db)
    {
        var rows = new List<Sample>();
        foreach (var s in body.Samples ?? Enumerable.Empty<SampleDto>())
        {
            rows.Add(new Sample
            {
                DeviceId = body.DeviceId ?? "unknown",
                Metric = s.Metric,
                EpochSecond = s.EpochSecond,
                ValueFloat = s.ValueFloat,
                ValueInt = s.ValueInt
            });
        }

        db.ChangeTracker.AutoDetectChangesEnabled = false;
        await db.Samples.AddRangeAsync(rows);
        await db.SaveChangesAsync();
        return Ok(new { inserted = rows.Count });
    }

    [HttpGet("/samples/day")]
    public async Task<IActionResult> GetDay([FromQuery] string metric, [FromQuery] DateTime date, [FromServices] AppDbContext db)
    {
        var start = new DateTime(date.Year, date.Month, date.Day, 0, 0, 0, DateTimeKind.Utc);
        var end = start.AddDays(1);

        var list = await db.Samples
            .Where(x => x.Metric == metric && x.EpochSecond >= new DateTimeOffset(start).ToUnixTimeSeconds()
                                         && x.EpochSecond < new DateTimeOffset(end).ToUnixTimeSeconds())
            .OrderBy(x => x.EpochSecond)
            .Select(x => new { t = x.EpochSecond, vF = x.ValueFloat, vI = x.ValueInt })
            .ToListAsync();

        return Ok(list);
    }

    [HttpGet("/samples/dayLocal")]
    public async Task<IActionResult> GetDayLocal([FromQuery] string metric, [FromQuery] DateTime date, [FromQuery] int tzOffsetMinutes,
                                                 [FromServices] AppDbContext db)
    {
        var startLocal = new DateTime(date.Year, date.Month, date.Day, 0, 0, 0, DateTimeKind.Unspecified);
        var startUtc = startLocal.AddMinutes(-tzOffsetMinutes);
        var endUtc = startUtc.AddDays(1);

        var start = new DateTimeOffset(DateTime.SpecifyKind(startUtc, DateTimeKind.Utc)).ToUnixTimeSeconds();
        var end = new DateTimeOffset(DateTime.SpecifyKind(endUtc, DateTimeKind.Utc)).ToUnixTimeSeconds();

        var list = await db.Samples
            .Where(x => x.Metric == metric && x.EpochSecond >= start && x.EpochSecond < end)
            .OrderBy(x => x.EpochSecond)
            .Select(x => new { t = x.EpochSecond, vF = x.ValueFloat, vI = x.ValueInt })
            .ToListAsync();

        return Ok(list);
    }
}

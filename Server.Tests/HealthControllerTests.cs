using System.Text.Json;
using HC_server.Controllers;
using HC_server.Models;
using Microsoft.AspNetCore.Mvc;
using Xunit;

namespace Server.Tests;

public sealed class HealthControllerTests
{
    [Fact]
    public async Task day_local_uses_requested_timezone_window()
    {
        await using var store = await TestDb.CreateAsync();
        store.Db.Samples.AddRange(
            new Sample
            {
                DeviceId = "phone-a",
                Metric = "heart_rate",
                EpochSecond = 1_781_126_400,
                ValueFloat = 72.0
            },
            new Sample
            {
                DeviceId = "phone-a",
                Metric = "heart_rate",
                EpochSecond = 1_781_212_800,
                ValueFloat = 80.0
            },
            new Sample
            {
                DeviceId = "phone-a",
                Metric = "steps",
                EpochSecond = 1_781_126_400,
                ValueInt = 100
            }
        );
        await store.Db.SaveChangesAsync();

        var controller = new HealthController();
        var result = await controller.GetDayLocal("heart_rate", new DateTime(2026, 6, 11), 540, store.Db);

        var ok = Assert.IsType<OkObjectResult>(result);
        var rows = JsonSerializer.Deserialize<List<SampleRow>>(JsonSerializer.Serialize(ok.Value));
        var row = Assert.Single(rows!);
        Assert.Equal(1_781_126_400, row.t);
        Assert.Equal(72.0, row.vF);
        Assert.Null(row.vI);
    }

    private sealed record SampleRow(long t, double? vF, int? vI);
}

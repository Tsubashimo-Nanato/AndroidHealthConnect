namespace HcServer.Models;

/// <summary>
/// Generic sample storage kept for the older sample routes.
/// New HealthConnect uploads use the normalized uploaded-health tables.
/// </summary>
public class SampleRecord
{
    public string Metric { get; set; } = default!;
    public long EpochSecond { get; set; }

    public float? ValueFloat { get; set; }
    public int? ValueInt { get; set; }
    public string? ValueJson { get; set; }

    public string? DeviceId { get; set; }
    public string? Source { get; set; }
}

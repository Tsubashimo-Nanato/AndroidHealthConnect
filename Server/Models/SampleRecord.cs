namespace HcServer.Models;

/// <summary>
/// Generic Health Connect sample storage.
/// Use Metric + EpochSecond as natural unique key.
/// Store numeric values in ValueFloat/ValueInt; structured payloads in ValueJson.
/// </summary>
public class SampleRecord
{
    public string Metric { get; set; } = default!;  // e.g., "heart_rate", "steps", "sleep_stage"
    public long EpochSecond { get; set; }         // UTC seconds

    // Values (pick one depending on the metric)
    public float? ValueFloat { get; set; }         // e.g., bpm, kcal, temperature
    public int? ValueInt { get; set; }         // e.g., steps count
    public string? ValueJson { get; set; }         // e.g., sleep segment object

    // Optional provenance
    public string? DeviceId { get; set; }
    public string? Source { get; set; }         // app/source package if you want
}

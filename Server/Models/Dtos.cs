namespace HC_server.Models
{
    public class IngestBatchDto
    {
        public string DeviceId { get; set; } = "";
        public List<SampleDto> Samples { get; set; } = new();
    }

    public class SampleDto
    {
        public string Metric { get; set; } = "";
        public long EpochSecond { get; set; }
        public double? ValueFloat { get; set; }
        public int? ValueInt { get; set; }
    }
}

namespace HC_server.Models;

public class UploadBatchDto
{
    public int SchemaVersion { get; set; }
    public string DeviceId { get; set; } = "";
    public string BatchId { get; set; } = "";
    public long CreatedAtEpochMillis { get; set; }
    public List<UploadRecordDto> Records { get; set; } = new();
    public List<UploadValueDto> Values { get; set; } = new();
    public List<UploadAggregateDto> Aggregates { get; set; } = new();
}

public class UploadRecordDto
{
    public long LocalId { get; set; }
    public string? RecordUid { get; set; }
    public string DedupeKey { get; set; } = "";
    public string RecordType { get; set; } = "";
    public string RecordKind { get; set; } = "";
    public long StartEpochMillis { get; set; }
    public long? EndEpochMillis { get; set; }
    public string LocalDate { get; set; } = "";
    public int? StartZoneOffsetSeconds { get; set; }
    public int? EndZoneOffsetSeconds { get; set; }
    public string? SourcePackage { get; set; }
    public long CreatedEpochMillis { get; set; }
    public long UpdatedEpochMillis { get; set; }
    public long LastReadEpochMillis { get; set; }
}

public class UploadValueDto
{
    public long LocalId { get; set; }
    public long RecordLocalId { get; set; }
    public string ValueKey { get; set; } = "";
    public string Metric { get; set; } = "";
    public string? Unit { get; set; }
    public string? Label { get; set; }
    public string? Category { get; set; }
    public double? NumericValue { get; set; }
    public double? SecondaryNumericValue { get; set; }
    public double? ValueFloat { get; set; }
    public long? ValueInt { get; set; }
    public string? ValueText { get; set; }
    public string? ValueJson { get; set; }
    public long? StartEpochMillis { get; set; }
    public long? EndEpochMillis { get; set; }
    public string? LocalDate { get; set; }
    public long? SampleEpochMillis { get; set; }
    public int? Sequence { get; set; }
}

public class UploadAggregateDto
{
    public long LocalId { get; set; }
    public string RecordType { get; set; } = "";
    public string Metric { get; set; } = "";
    public string BucketPeriod { get; set; } = "";
    public long BucketStartEpochMillis { get; set; }
    public long BucketEndEpochMillis { get; set; }
    public string LocalDate { get; set; } = "";
    public string? TimezoneId { get; set; }
    public double Value { get; set; }
    public string? Unit { get; set; }
    public string Source { get; set; } = "";
    public long ComputedEpochMillis { get; set; }
    public long RequestedStartEpochMillis { get; set; }
    public long RequestedEndEpochMillis { get; set; }
}

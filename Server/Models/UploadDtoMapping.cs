namespace HC_server.Models;

internal static class UploadDtoMapping
{
    public static UploadedHealthRecord ToEntity(
        this UploadRecordDto source,
        string deviceId,
        string batchId,
        long uploadedAtEpochMillis
    ) => new()
    {
        DeviceId = deviceId,
        BatchId = batchId,
        LocalId = source.LocalId,
        RecordUid = source.RecordUid,
        DedupeKey = source.DedupeKey,
        RecordType = source.RecordType,
        RecordKind = source.RecordKind,
        StartEpochMillis = source.StartEpochMillis,
        EndEpochMillis = source.EndEpochMillis,
        LocalDate = source.LocalDate,
        StartZoneOffsetSeconds = source.StartZoneOffsetSeconds,
        EndZoneOffsetSeconds = source.EndZoneOffsetSeconds,
        SourcePackage = source.SourcePackage,
        CreatedEpochMillis = source.CreatedEpochMillis,
        UpdatedEpochMillis = source.UpdatedEpochMillis,
        LastReadEpochMillis = source.LastReadEpochMillis,
        UploadedAtEpochMillis = uploadedAtEpochMillis
    };

    public static UploadedHealthValue ToEntity(
        this UploadValueDto source,
        string deviceId,
        string batchId,
        long uploadedAtEpochMillis
    ) => new()
    {
        DeviceId = deviceId,
        BatchId = batchId,
        LocalId = source.LocalId,
        RecordLocalId = source.RecordLocalId,
        ValueKey = source.ValueKey,
        Metric = source.Metric,
        Unit = source.Unit,
        Label = source.Label,
        Category = source.Category,
        NumericValue = source.NumericValue,
        SecondaryNumericValue = source.SecondaryNumericValue,
        ValueFloat = source.ValueFloat,
        ValueInt = source.ValueInt,
        ValueText = source.ValueText,
        ValueJson = source.ValueJson,
        StartEpochMillis = source.StartEpochMillis,
        EndEpochMillis = source.EndEpochMillis,
        LocalDate = source.LocalDate,
        SampleEpochMillis = source.SampleEpochMillis,
        Sequence = source.Sequence,
        UploadedAtEpochMillis = uploadedAtEpochMillis
    };

    public static UploadedHealthAggregate ToEntity(
        this UploadAggregateDto source,
        string deviceId,
        string batchId,
        long uploadedAtEpochMillis
    ) => new()
    {
        DeviceId = deviceId,
        BatchId = batchId,
        LocalId = source.LocalId,
        RecordType = source.RecordType,
        Metric = source.Metric,
        BucketPeriod = source.BucketPeriod,
        BucketStartEpochMillis = source.BucketStartEpochMillis,
        BucketEndEpochMillis = source.BucketEndEpochMillis,
        LocalDate = source.LocalDate,
        TimezoneId = source.TimezoneId,
        Value = source.Value,
        Unit = source.Unit,
        Source = source.Source,
        ComputedEpochMillis = source.ComputedEpochMillis,
        RequestedStartEpochMillis = source.RequestedStartEpochMillis,
        RequestedEndEpochMillis = source.RequestedEndEpochMillis,
        UploadedAtEpochMillis = uploadedAtEpochMillis
    };
}

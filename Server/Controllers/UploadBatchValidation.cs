using HC_server.Models;

namespace HC_server.Controllers;

internal static class UploadBatchValidation
{
    public static string? ErrorFor(UploadBatchDto body)
    {
        if (string.IsNullOrWhiteSpace(body.DeviceId))
        {
            return "deviceId is required";
        }

        if (string.IsNullOrWhiteSpace(body.BatchId))
        {
            return "batchId is required";
        }

        if (body.SchemaVersion != 1)
        {
            return $"Unsupported schemaVersion {body.SchemaVersion}";
        }

        return null;
    }
}

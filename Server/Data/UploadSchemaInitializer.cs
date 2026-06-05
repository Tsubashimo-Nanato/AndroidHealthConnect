using Microsoft.EntityFrameworkCore;

namespace HC_server.Data;

public static class UploadSchemaInitializer
{
    public static async Task EnsureUploadTablesAsync(AppDbContext db)
    {
        await db.Database.ExecuteSqlRawAsync(
            """
            CREATE TABLE IF NOT EXISTS "UploadedHealthRecords" (
                "Id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                "DeviceId" TEXT NOT NULL,
                "BatchId" TEXT NOT NULL,
                "LocalId" INTEGER NOT NULL,
                "RecordUid" TEXT NULL,
                "DedupeKey" TEXT NOT NULL,
                "RecordType" TEXT NOT NULL,
                "RecordKind" TEXT NOT NULL,
                "StartEpochMillis" INTEGER NOT NULL,
                "EndEpochMillis" INTEGER NULL,
                "LocalDate" TEXT NOT NULL,
                "StartZoneOffsetSeconds" INTEGER NULL,
                "EndZoneOffsetSeconds" INTEGER NULL,
                "SourcePackage" TEXT NULL,
                "CreatedEpochMillis" INTEGER NOT NULL,
                "UpdatedEpochMillis" INTEGER NOT NULL,
                "LastReadEpochMillis" INTEGER NOT NULL,
                "UploadedAtEpochMillis" INTEGER NOT NULL
            )
            """
        );
        await db.Database.ExecuteSqlRawAsync(
            """
            CREATE TABLE IF NOT EXISTS "UploadedHealthValues" (
                "Id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                "DeviceId" TEXT NOT NULL,
                "BatchId" TEXT NOT NULL,
                "LocalId" INTEGER NOT NULL,
                "RecordLocalId" INTEGER NOT NULL,
                "ValueKey" TEXT NOT NULL,
                "Metric" TEXT NOT NULL,
                "Unit" TEXT NULL,
                "Label" TEXT NULL,
                "Category" TEXT NULL,
                "NumericValue" REAL NULL,
                "SecondaryNumericValue" REAL NULL,
                "ValueFloat" REAL NULL,
                "ValueInt" INTEGER NULL,
                "ValueText" TEXT NULL,
                "ValueJson" TEXT NULL,
                "StartEpochMillis" INTEGER NULL,
                "EndEpochMillis" INTEGER NULL,
                "LocalDate" TEXT NULL,
                "SampleEpochMillis" INTEGER NULL,
                "Sequence" INTEGER NULL,
                "UploadedAtEpochMillis" INTEGER NOT NULL
            )
            """
        );
        await db.Database.ExecuteSqlRawAsync(
            """
            CREATE TABLE IF NOT EXISTS "UploadedHealthAggregates" (
                "Id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                "DeviceId" TEXT NOT NULL,
                "BatchId" TEXT NOT NULL,
                "LocalId" INTEGER NOT NULL,
                "RecordType" TEXT NOT NULL,
                "Metric" TEXT NOT NULL,
                "BucketPeriod" TEXT NOT NULL,
                "BucketStartEpochMillis" INTEGER NOT NULL,
                "BucketEndEpochMillis" INTEGER NOT NULL,
                "LocalDate" TEXT NOT NULL,
                "TimezoneId" TEXT NULL,
                "Value" REAL NOT NULL,
                "Unit" TEXT NULL,
                "Source" TEXT NOT NULL,
                "ComputedEpochMillis" INTEGER NOT NULL,
                "RequestedStartEpochMillis" INTEGER NOT NULL,
                "RequestedEndEpochMillis" INTEGER NOT NULL,
                "UploadedAtEpochMillis" INTEGER NOT NULL
            )
            """
        );
        await db.Database.ExecuteSqlRawAsync(
            """CREATE UNIQUE INDEX IF NOT EXISTS "IX_UploadedHealthRecords_DeviceId_LocalId" ON "UploadedHealthRecords" ("DeviceId", "LocalId")"""
        );
        await db.Database.ExecuteSqlRawAsync(
            """CREATE INDEX IF NOT EXISTS "IX_UploadedHealthRecords_DeviceId_RecordType_StartEpochMillis" ON "UploadedHealthRecords" ("DeviceId", "RecordType", "StartEpochMillis")"""
        );
        await db.Database.ExecuteSqlRawAsync(
            """CREATE UNIQUE INDEX IF NOT EXISTS "IX_UploadedHealthValues_DeviceId_LocalId" ON "UploadedHealthValues" ("DeviceId", "LocalId")"""
        );
        await db.Database.ExecuteSqlRawAsync(
            """CREATE INDEX IF NOT EXISTS "IX_UploadedHealthValues_DeviceId_Metric_StartEpochMillis" ON "UploadedHealthValues" ("DeviceId", "Metric", "StartEpochMillis")"""
        );
        await db.Database.ExecuteSqlRawAsync(
            """CREATE UNIQUE INDEX IF NOT EXISTS "IX_UploadedHealthAggregates_DeviceId_LocalId" ON "UploadedHealthAggregates" ("DeviceId", "LocalId")"""
        );
        await db.Database.ExecuteSqlRawAsync(
            """CREATE INDEX IF NOT EXISTS "IX_UploadedHealthAggregates_DeviceId_RecordType_LocalDate" ON "UploadedHealthAggregates" ("DeviceId", "RecordType", "LocalDate")"""
        );
    }
}

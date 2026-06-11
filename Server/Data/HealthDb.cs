using HcServer.Models;
using Microsoft.EntityFrameworkCore;

namespace HcServer.Data;

public class HealthDb : DbContext
{
    public HealthDb(DbContextOptions<HealthDb> options) : base(options) { }

    public DbSet<SampleRecord> Samples => Set<SampleRecord>();

    protected override void OnModelCreating(ModelBuilder b)
    {
        b.Entity<SampleRecord>()
            .HasKey(x => new { x.Metric, x.EpochSecond });

        b.Entity<SampleRecord>()
            .HasIndex(x => new { x.Metric, x.EpochSecond })
            .IsUnique();

        b.Entity<SampleRecord>()
            .Property(x => x.Metric).HasMaxLength(64);
        b.Entity<SampleRecord>()
            .Property(x => x.Source).HasMaxLength(128);
        b.Entity<SampleRecord>()
            .Property(x => x.DeviceId).HasMaxLength(128);
    }
}

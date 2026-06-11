using HC_server.Models;
using Microsoft.EntityFrameworkCore;

namespace HC_server.Data
{
    public class AppDbContext : DbContext
    {
        public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) { }

        public DbSet<Sample> Samples => Set<Sample>();
        public DbSet<UploadedHealthRecord> UploadedHealthRecords => Set<UploadedHealthRecord>();
        public DbSet<UploadedHealthValue> UploadedHealthValues => Set<UploadedHealthValue>();
        public DbSet<UploadedHealthAggregate> UploadedHealthAggregates => Set<UploadedHealthAggregate>();

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            var e = modelBuilder.Entity<Sample>();

            e.HasKey(x => x.Id);
            e.Property(x => x.Id)
             .HasColumnType("INTEGER")
             .ValueGeneratedOnAdd()
             .HasAnnotation("Sqlite:Autoincrement", true);

            e.Property(x => x.Metric).IsRequired();
            e.Property(x => x.DeviceId).IsRequired();

            e.HasIndex(x => new { x.DeviceId, x.Metric, x.EpochSecond }).IsUnique();

            modelBuilder.Entity<UploadedHealthRecord>(record =>
            {
                record.HasKey(x => x.Id);
                record.HasIndex(x => new { x.DeviceId, x.LocalId }).IsUnique();
                record.HasIndex(x => new { x.DeviceId, x.RecordType, x.StartEpochMillis });
                record.Property(x => x.DeviceId).HasMaxLength(128).IsRequired();
                record.Property(x => x.BatchId).HasMaxLength(64).IsRequired();
                record.Property(x => x.RecordType).HasMaxLength(96).IsRequired();
                record.Property(x => x.RecordKind).HasMaxLength(64).IsRequired();
                record.Property(x => x.LocalDate).HasMaxLength(16).IsRequired();
            });

            modelBuilder.Entity<UploadedHealthValue>(value =>
            {
                value.HasKey(x => x.Id);
                value.HasIndex(x => new { x.DeviceId, x.LocalId }).IsUnique();
                value.HasIndex(x => new { x.DeviceId, x.Metric, x.StartEpochMillis });
                value.Property(x => x.DeviceId).HasMaxLength(128).IsRequired();
                value.Property(x => x.BatchId).HasMaxLength(64).IsRequired();
                value.Property(x => x.Metric).HasMaxLength(96).IsRequired();
                value.Property(x => x.ValueKey).HasMaxLength(128).IsRequired();
            });

            modelBuilder.Entity<UploadedHealthAggregate>(aggregate =>
            {
                aggregate.HasKey(x => x.Id);
                aggregate.HasIndex(x => new { x.DeviceId, x.LocalId }).IsUnique();
                aggregate.HasIndex(x => new { x.DeviceId, x.RecordType, x.LocalDate });
                aggregate.Property(x => x.DeviceId).HasMaxLength(128).IsRequired();
                aggregate.Property(x => x.BatchId).HasMaxLength(64).IsRequired();
                aggregate.Property(x => x.RecordType).HasMaxLength(96).IsRequired();
                aggregate.Property(x => x.Metric).HasMaxLength(96).IsRequired();
                aggregate.Property(x => x.BucketPeriod).HasMaxLength(32).IsRequired();
                aggregate.Property(x => x.LocalDate).HasMaxLength(16).IsRequired();
            });
        }
    }
}

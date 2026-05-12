// File: Data/AppDbContext.cs
using HC_server.Models;
using Microsoft.EntityFrameworkCore;

namespace HC_server.Data
{
    public class AppDbContext : DbContext
    {
        public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) { }

        public DbSet<Sample> Samples => Set<Sample>();

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            var e = modelBuilder.Entity<Sample>();

            // Force SQLite to treat Id as store-generated rowid
            e.HasKey(x => x.Id);
            e.Property(x => x.Id)
             .HasColumnType("INTEGER")                 // SQLite rowid affinity
             .ValueGeneratedOnAdd()
             .HasAnnotation("Sqlite:Autoincrement", true);

            e.Property(x => x.Metric).IsRequired();
            e.Property(x => x.DeviceId).IsRequired();

            // Optional uniqueness to prevent duplicate datapoints from same device
            e.HasIndex(x => new { x.DeviceId, x.Metric, x.EpochSecond }).IsUnique();
        }
    }
}

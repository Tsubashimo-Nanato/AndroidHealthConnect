// File: Models/Sample.cs
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace HC_server.Models
{
    public class Sample
    {
        // SQLite needs INTEGER PRIMARY KEY (rowid) to auto-generate.
        // The fluent config below will also set AUTOINCREMENT.
        [Key]
        [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
        public long Id { get; set; }

        [Required] public string DeviceId { get; set; } = "";
        [Required] public string Metric { get; set; } = "";     // e.g., "heart_rate"
        [Required] public long EpochSecond { get; set; }      // unix seconds (UTC)

        public double? ValueFloat { get; set; }                 // floats (HR, calories)
        public int? ValueInt { get; set; }                 // ints (steps)
    }
}

using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace HC_server.Models
{
    public class Sample
    {
        [Key]
        [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
        public long Id { get; set; }

        [Required] public string DeviceId { get; set; } = "";
        [Required] public string Metric { get; set; } = "";
        [Required] public long EpochSecond { get; set; }

        public double? ValueFloat { get; set; }
        public int? ValueInt { get; set; }
    }
}

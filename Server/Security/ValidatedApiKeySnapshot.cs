using System.Security.Cryptography;
using System.Text;

namespace HC_server.Security
{
    /// <summary>Startup-validated, immutable credentials used for the lifetime of the process.</summary>
    public sealed class ValidatedApiKeySnapshot
    {
        public const int MinimumLength = 32;
        public const int MaximumLength = 256;
        private readonly byte[][] apiKeys;

        private ValidatedApiKeySnapshot(byte[][] apiKeys)
        {
            this.apiKeys = apiKeys.Select(key => key.ToArray()).ToArray();
        }

        public bool IsEmpty => apiKeys.Length == 0;

        public static ValidatedApiKeySnapshot Create(
            IEnumerable<string>? configuredKeys,
            bool requireAtLeastOne
        )
        {
            var normalized = configuredKeys
                ?.Where(key => !string.IsNullOrWhiteSpace(key))
                .Select(key => key.Trim())
                .Distinct(StringComparer.Ordinal)
                .ToArray()
                ?? Array.Empty<string>();

            if (normalized.Any(key => key.Length is < MinimumLength or > MaximumLength))
            {
                throw new InvalidOperationException(
                    $"Each configured API key must contain {MinimumLength} to {MaximumLength} characters."
                );
            }
            if (requireAtLeastOne && normalized.Length == 0)
            {
                throw new InvalidOperationException(
                    "At least one ApiKeys entry is required outside Development."
                );
            }

            return new ValidatedApiKeySnapshot(
                normalized.Select(Encoding.UTF8.GetBytes).ToArray()
            );
        }

        public bool Matches(string candidate)
        {
            var candidateBytes = Encoding.UTF8.GetBytes(candidate);
            return apiKeys.Any(expected =>
                candidateBytes.Length == expected.Length &&
                CryptographicOperations.FixedTimeEquals(candidateBytes, expected)
            );
        }
    }
}

using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Configuration;
using System.Security.Cryptography;
using System.Text;

namespace HC_server.Security
{
    public sealed class ApiKeyMiddleware : IMiddleware
    {
        private const string HeaderName = "X-API-Key";
        private readonly IReadOnlyList<string> apiKeys;

        public ApiKeyMiddleware(IConfiguration configuration)
        {
            apiKeys = configuration
                .GetSection("ApiKeys")
                .Get<string[]>()
                ?.Where(key => !string.IsNullOrWhiteSpace(key))
                .Select(key => key.Trim())
                .Distinct(StringComparer.Ordinal)
                .ToArray()
                ?? Array.Empty<string>();
        }

        public async Task InvokeAsync(HttpContext ctx, RequestDelegate next)
        {
            var path = ctx.Request.Path.Value ?? "";

            if (HttpMethods.IsGet(ctx.Request.Method))
            {
                if (path == "/" ||
                    path.StartsWith("/swagger") ||
                    path.StartsWith("/favicon") ||
                    path.StartsWith("/index") ||
                    path.StartsWith("/css") ||
                    path.StartsWith("/js") ||
                    path.StartsWith("/lib") ||
                    path.StartsWith("/assets") ||
                    Path.HasExtension(path))
                {
                    await next(ctx);
                    return;
                }
            }

            if (apiKeys.Count == 0)
            {
                ctx.Response.StatusCode = StatusCodes.Status503ServiceUnavailable;
                await ctx.Response.WriteAsync("Server API key is not configured.");
                return;
            }

            if (!ctx.Request.Headers.TryGetValue(HeaderName, out var key) || !IsValidKey(key.ToString()))
            {
                ctx.Response.StatusCode = StatusCodes.Status401Unauthorized;
                await ctx.Response.WriteAsync("Missing or invalid API key.");
                return;
            }

            await next(ctx);
        }

        private bool IsValidKey(string candidate)
        {
            var candidateBytes = Encoding.UTF8.GetBytes(candidate);
            return apiKeys.Any(expected =>
            {
                var expectedBytes = Encoding.UTF8.GetBytes(expected);
                return candidateBytes.Length == expectedBytes.Length &&
                    CryptographicOperations.FixedTimeEquals(candidateBytes, expectedBytes);
            });
        }
    }
}

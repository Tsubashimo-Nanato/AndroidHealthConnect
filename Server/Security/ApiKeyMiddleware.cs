using Microsoft.AspNetCore.Http;

namespace HC_server.Security
{
    public sealed class ApiKeyMiddleware : IMiddleware
    {
        private const string HeaderName = "X-API-Key";
        private readonly ValidatedApiKeySnapshot apiKeys;

        public ApiKeyMiddleware(ValidatedApiKeySnapshot apiKeys)
        {
            this.apiKeys = apiKeys;
        }

        public async Task InvokeAsync(HttpContext ctx, RequestDelegate next)
        {
            var path = ctx.Request.Path;

            if (HttpMethods.IsGet(ctx.Request.Method) && IsPublicAssetPath(path))
            {
                await next(ctx);
                return;
            }

            if (apiKeys.IsEmpty)
            {
                ctx.Response.StatusCode = StatusCodes.Status503ServiceUnavailable;
                await ctx.Response.WriteAsync("Server API key is not configured.");
                return;
            }

            if (!ctx.Request.Headers.TryGetValue(HeaderName, out var key) || !apiKeys.Matches(key.ToString()))
            {
                ctx.Response.StatusCode = StatusCodes.Status401Unauthorized;
                await ctx.Response.WriteAsync("Missing or invalid API key.");
                return;
            }

            // Protected responses can contain health data. A custom API-key header does not
            // automatically prevent shared proxies from caching a successful GET response.
            ctx.Response.Headers.CacheControl = "no-store";
            await next(ctx);
        }

        private static bool IsPublicAssetPath(PathString path) =>
            path == "/" ||
            path.StartsWithSegments("/swagger") ||
            path == "/favicon.ico" ||
            path == "/index.html" ||
            path.StartsWithSegments("/css") ||
            path.StartsWithSegments("/js") ||
            path.StartsWithSegments("/lib") ||
            path.StartsWithSegments("/assets");
    }
}

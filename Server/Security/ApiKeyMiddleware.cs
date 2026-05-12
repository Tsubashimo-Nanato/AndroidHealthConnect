using Microsoft.AspNetCore.Http;

namespace HC_server.Security
{
    // Using IMiddleware => must be registered in DI: builder.Services.AddTransient<ApiKeyMiddleware>();
    public sealed class ApiKeyMiddleware : IMiddleware
    {
        private const string HeaderName = "X-API-Key";
        private const string Expected = "123"; // move to config later

        public async Task InvokeAsync(HttpContext ctx, RequestDelegate next)
        {
            var path = ctx.Request.Path.Value ?? "";

            // --- Allow static & swagger (no API key required) ---
            // If it's a GET to root, swagger, or a file request (/css/app.css, /index.html, etc.), let it through.
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
                    System.IO.Path.HasExtension(path))    // e.g., .html, .js, .css
                {
                    await next(ctx);
                    return;
                }
            }

            // --- Protect everything else ---
            if (!ctx.Request.Headers.TryGetValue(HeaderName, out var key) || key != Expected)
            {
                ctx.Response.StatusCode = StatusCodes.Status401Unauthorized;
                await ctx.Response.WriteAsync("Missing or invalid API key.");
                return;
            }

            await next(ctx);
        }
    }
}

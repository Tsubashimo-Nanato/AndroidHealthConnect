using HC_server.Security;
using Microsoft.AspNetCore.Http;
using Xunit;

namespace Server.Tests;

public sealed class ApiKeyMiddlewareTests
{
    [Fact]
    public void snapshot_rejects_keys_outside_the_shared_length_policy()
    {
        Assert.Throws<InvalidOperationException>(() =>
            ValidatedApiKeySnapshot.Create(new[] { new string('a', 31) }, requireAtLeastOne: false)
        );
        Assert.Throws<InvalidOperationException>(() =>
            ValidatedApiKeySnapshot.Create(new[] { new string('a', 257) }, requireAtLeastOne: false)
        );
    }

    [Fact]
    public void production_snapshot_requires_a_key_at_startup()
    {
        Assert.Throws<InvalidOperationException>(() =>
            ValidatedApiKeySnapshot.Create(Array.Empty<string>(), requireAtLeastOne: true)
        );
    }

    [Fact]
    public async Task middleware_uses_the_immutable_startup_snapshot()
    {
        var original = new string('a', ValidatedApiKeySnapshot.MinimumLength);
        var source = new List<string> { original };
        var snapshot = ValidatedApiKeySnapshot.Create(source, requireAtLeastOne: true);
        var middleware = new ApiKeyMiddleware(snapshot);

        source[0] = new string('b', 1);
        source.Add(new string('c', ValidatedApiKeySnapshot.MinimumLength));

        var accepted = false;
        var acceptedContext = ProtectedRequest(original);
        await middleware.InvokeAsync(acceptedContext, _ =>
        {
            accepted = true;
            return Task.CompletedTask;
        });

        Assert.True(accepted);
        Assert.Equal(StatusCodes.Status200OK, acceptedContext.Response.StatusCode);

        var rejectedContext = ProtectedRequest(source[1]);
        await middleware.InvokeAsync(rejectedContext, _ => Task.CompletedTask);
        Assert.Equal(StatusCodes.Status401Unauthorized, rejectedContext.Response.StatusCode);
    }

    [Fact]
    public async Task a_dotted_api_path_does_not_bypass_authentication_as_a_static_file()
    {
        var snapshot = ValidatedApiKeySnapshot.Create(
            new[] { new string('a', ValidatedApiKeySnapshot.MinimumLength) },
            requireAtLeastOne: true
        );
        var middleware = new ApiKeyMiddleware(snapshot);
        var context = new DefaultHttpContext();
        context.Request.Method = HttpMethods.Get;
        context.Request.Path = "/health/api/v1/private.json";
        context.Response.Body = new MemoryStream();

        await middleware.InvokeAsync(context, _ => Task.CompletedTask);

        Assert.Equal(StatusCodes.Status401Unauthorized, context.Response.StatusCode);
    }

    private static DefaultHttpContext ProtectedRequest(string key)
    {
        var context = new DefaultHttpContext();
        context.Request.Method = HttpMethods.Post;
        context.Request.Path = "/health/api/v1/ingest/batches";
        context.Request.Headers["X-API-Key"] = key;
        context.Response.Body = new MemoryStream();
        return context;
    }
}

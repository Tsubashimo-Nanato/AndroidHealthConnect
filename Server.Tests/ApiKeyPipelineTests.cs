using System.Net;
using HC_server.Security;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Xunit;

namespace Server.Tests;

[CollectionDefinition(EnvironmentVariableCollection.Name, DisableParallelization = true)]
public sealed class EnvironmentVariableCollection
{
    public const string Name = "Process environment";
}

[Collection(EnvironmentVariableCollection.Name)]
public sealed class ApiKeyPipelineTests
{
    private static readonly string ApiKey = new('k', ValidatedApiKeySnapshot.MinimumLength);

    [Fact]
    public async Task static_files_cross_the_same_explicit_authentication_boundary_as_the_real_pipeline()
    {
        var testRoot = Path.Combine(Path.GetTempPath(), $"android-health-connect-{Guid.NewGuid():N}");
        var webRoot = Path.Combine(testRoot, "wwwroot");
        var assetsRoot = Path.Combine(webRoot, "assets");
        var databasePath = Path.Combine(testRoot, "pipeline.db");
        Directory.CreateDirectory(assetsRoot);
        await File.WriteAllTextAsync(Path.Combine(assetsRoot, "public.txt"), "public asset");
        await File.WriteAllTextAsync(Path.Combine(webRoot, "private.json"), "{\"private\":true}");
        var previousApiKey = Environment.GetEnvironmentVariable("ApiKeys__0");
        var previousConnectionString = Environment.GetEnvironmentVariable("ConnectionStrings__HealthDb");
        Environment.SetEnvironmentVariable("ApiKeys__0", ApiKey);
        Environment.SetEnvironmentVariable(
            "ConnectionStrings__HealthDb",
            $"Data Source={databasePath};Pooling=False"
        );

        try
        {
            await AssertPipelineBoundary(webRoot);
        }
        finally
        {
            Environment.SetEnvironmentVariable("ApiKeys__0", previousApiKey);
            Environment.SetEnvironmentVariable("ConnectionStrings__HealthDb", previousConnectionString);
            if (Directory.Exists(testRoot)) Directory.Delete(testRoot, recursive: true);
        }
    }

    private static async Task AssertPipelineBoundary(string webRoot)
    {
        using var factory = new WebApplicationFactory<global::Program>()
            .WithWebHostBuilder(builder =>
            {
                builder.UseEnvironment("Development");
                builder.UseWebRoot(webRoot);
            });
        using var client = factory.CreateClient(
            new WebApplicationFactoryClientOptions { AllowAutoRedirect = false }
        );

        using var publicResponse = await client.GetAsync("/assets/public.txt");
        Assert.Equal(HttpStatusCode.OK, publicResponse.StatusCode);
        Assert.False(publicResponse.Headers.CacheControl?.NoStore ?? false);

        using var unauthenticatedResponse = await client.GetAsync("/private.json");
        Assert.Equal(HttpStatusCode.Unauthorized, unauthenticatedResponse.StatusCode);

        using var authorizedRequest = new HttpRequestMessage(HttpMethod.Get, "/private.json");
        authorizedRequest.Headers.Add("X-API-Key", ApiKey);
        using var authorizedResponse = await client.SendAsync(authorizedRequest);
        Assert.Equal(HttpStatusCode.OK, authorizedResponse.StatusCode);
        Assert.True(authorizedResponse.Headers.CacheControl?.NoStore);

        using var protectedApiRequest = new HttpRequestMessage(HttpMethod.Get, "/health/api/v1/status");
        protectedApiRequest.Headers.Add("X-API-Key", ApiKey);
        using var protectedApiResponse = await client.SendAsync(protectedApiRequest);
        Assert.Equal(HttpStatusCode.OK, protectedApiResponse.StatusCode);
        Assert.True(protectedApiResponse.Headers.CacheControl?.NoStore);
    }
}

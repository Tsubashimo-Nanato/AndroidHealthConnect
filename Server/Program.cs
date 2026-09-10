using HC_server.Data;
using HC_server.Security;
using Microsoft.EntityFrameworkCore;
using Microsoft.OpenApi.Models;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddDbContext<AppDbContext>(opt =>
{
    var connectionString = builder.Configuration.GetConnectionString("HealthDb") ?? "Data Source=hc.db";
    opt.UseSqlite(connectionString);
    if (builder.Environment.IsDevelopment() &&
        builder.Configuration.GetValue<bool>("Ef:EnableSensitiveDataLogging"))
    {
        opt.EnableDetailedErrors();
        opt.EnableSensitiveDataLogging();
    }
});

var configuredApiKeys = builder.Configuration
    .GetSection("ApiKeys")
    .Get<string[]>()
    ?.Where(key => !string.IsNullOrWhiteSpace(key))
    .ToArray()
    ?? Array.Empty<string>();
var apiKeySnapshot = ValidatedApiKeySnapshot.Create(
    configuredApiKeys,
    requireAtLeastOne: !builder.Environment.IsDevelopment()
);

builder.Services.AddControllers();

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    c.SwaggerDoc("v1", new OpenApiInfo { Title = "HC_server", Version = "v1" });
    c.AddSecurityDefinition("ApiKey", new OpenApiSecurityScheme
    {
        Description = "Header: X-API-Key: {your key}",
        Name = "X-API-Key",
        In = ParameterLocation.Header,
        Type = SecuritySchemeType.ApiKey
    });
    c.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        { new OpenApiSecurityScheme{ Reference = new OpenApiReference{ Type=ReferenceType.SecurityScheme, Id="ApiKey" } }, Array.Empty<string>() }
    });
});

builder.Services.AddSingleton(apiKeySnapshot);
builder.Services.AddTransient<ApiKeyMiddleware>();
var app = builder.Build();

using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    await db.Database.EnsureCreatedAsync();
    await UploadSchemaInitializer.EnsureUploadTablesAsync(db);
}

// Authenticate before StaticFileMiddleware can short-circuit the pipeline. The middleware's
// explicit GET allowlist keeps the public shell/assets available without making every file
// placed under wwwroot public by accident.
app.UseMiddleware<ApiKeyMiddleware>();

app.UseDefaultFiles();
app.UseStaticFiles();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.MapControllers();
app.Run();

public partial class Program { }

using HC_server.Data;
using HC_server.Security;
using Microsoft.EntityFrameworkCore;
using Microsoft.OpenApi.Models;

var builder = WebApplication.CreateBuilder(args);

// DB
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
    .Select(key => key.Trim())
    .ToArray()
    ?? Array.Empty<string>();
if (!builder.Environment.IsDevelopment())
{
    if (configuredApiKeys.Length == 0)
    {
        throw new InvalidOperationException("At least one ApiKeys entry is required outside Development.");
    }
    if (configuredApiKeys.Contains("123", StringComparer.Ordinal))
    {
        throw new InvalidOperationException("The default development API key cannot be used outside Development.");
    }
}

// Controllers
builder.Services.AddControllers();

// Swagger + API key
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

builder.Services.AddTransient<ApiKeyMiddleware>();
var app = builder.Build();

// Ensure DB exists
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    await db.Database.EnsureCreatedAsync();
    await UploadSchemaInitializer.EnsureUploadTablesAsync(db);
}

// Static files (index.html)
app.UseDefaultFiles();
app.UseStaticFiles();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

// API key on everything except swagger & static
app.UseMiddleware<ApiKeyMiddleware>();

app.MapControllers();
app.Run();

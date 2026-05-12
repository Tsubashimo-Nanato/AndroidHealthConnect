using HC_server.Data;
using HC_server.Security;
using Microsoft.EntityFrameworkCore;
using Microsoft.OpenApi.Models;

var builder = WebApplication.CreateBuilder(args);

// DB
builder.Services.AddDbContext<AppDbContext>(opt =>
{
    opt.UseSqlite("Data Source=hc.db");
    // helpful while debugging
    opt.EnableDetailedErrors();
    opt.EnableSensitiveDataLogging();
});

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

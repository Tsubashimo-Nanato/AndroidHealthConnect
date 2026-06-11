using HC_server.Data;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;

namespace Server.Tests;

internal sealed class TestDb : IAsyncDisposable
{
    private readonly SqliteConnection connection;

    private TestDb(SqliteConnection connection, AppDbContext db)
    {
        this.connection = connection;
        Db = db;
    }

    public AppDbContext Db { get; }

    public static async Task<TestDb> CreateAsync()
    {
        var connection = new SqliteConnection("Data Source=:memory:");
        await connection.OpenAsync();

        var options = new DbContextOptionsBuilder<AppDbContext>()
            .UseSqlite(connection)
            .Options;
        var db = new AppDbContext(options);
        await db.Database.EnsureCreatedAsync();
        await UploadSchemaInitializer.EnsureUploadTablesAsync(db);

        return new TestDb(connection, db);
    }

    public async ValueTask DisposeAsync()
    {
        await Db.DisposeAsync();
        await connection.DisposeAsync();
    }
}

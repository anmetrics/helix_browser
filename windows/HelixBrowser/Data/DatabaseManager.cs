using System;
using System.Collections.Generic;
using System.Data.SQLite;
using System.IO;

namespace HelixBrowser;

public class DatabaseManager
{
    private static DatabaseManager? _instance;
    public static DatabaseManager Instance => _instance ??= new DatabaseManager();

    private readonly string _dbPath;

    private DatabaseManager()
    {
        var appData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        var helixDir = Path.Combine(appData, "HelixBrowser");
        Directory.CreateDirectory(helixDir);
        _dbPath = Path.Combine(helixDir, "helix.db");
        InitializeDatabase();
    }

    private void InitializeDatabase()
    {
        using var conn = GetConnection();
        conn.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = @"
            CREATE TABLE IF NOT EXISTS history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT, url TEXT NOT NULL, timestamp REAL NOT NULL
            );
            CREATE TABLE IF NOT EXISTS bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT, url TEXT NOT NULL UNIQUE, favicon TEXT, timestamp REAL
            );
            CREATE TABLE IF NOT EXISTS downloads (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                filename TEXT, url TEXT, localPath TEXT, progress REAL, status TEXT, timestamp REAL
            );";
        cmd.ExecuteNonQuery();
    }

    private SQLiteConnection GetConnection() => new($"Data Source={_dbPath};Version=3;");

    // MARK: - History

    public void AddHistory(string title, string url)
    {
        using var conn = GetConnection();
        conn.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "INSERT INTO history (title, url, timestamp) VALUES (@t, @u, @ts)";
        cmd.Parameters.AddWithValue("@t", title);
        cmd.Parameters.AddWithValue("@u", url);
        cmd.Parameters.AddWithValue("@ts", DateTimeOffset.UtcNow.ToUnixTimeSeconds());
        cmd.ExecuteNonQuery();
    }

    public List<Dictionary<string, string>> GetHistory(int limit = 500)
    {
        var result = new List<Dictionary<string, string>>();
        using var conn = GetConnection();
        conn.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "SELECT title, url, timestamp FROM history ORDER BY timestamp DESC LIMIT @l";
        cmd.Parameters.AddWithValue("@l", limit);
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            result.Add(new Dictionary<string, string>
            {
                ["title"] = reader.GetString(0),
                ["url"] = reader.GetString(1),
                ["timestamp"] = reader.GetDouble(2).ToString()
            });
        }
        return result;
    }

    public void ClearHistory()
    {
        using var conn = GetConnection();
        conn.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "DELETE FROM history";
        cmd.ExecuteNonQuery();
    }

    // MARK: - Bookmarks

    public void AddBookmark(string title, string url)
    {
        using var conn = GetConnection();
        conn.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "INSERT OR REPLACE INTO bookmarks (title, url, timestamp) VALUES (@t, @u, @ts)";
        cmd.Parameters.AddWithValue("@t", title);
        cmd.Parameters.AddWithValue("@u", url);
        cmd.Parameters.AddWithValue("@ts", DateTimeOffset.UtcNow.ToUnixTimeSeconds());
        cmd.ExecuteNonQuery();
    }

    public void RemoveBookmark(string url)
    {
        using var conn = GetConnection();
        conn.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "DELETE FROM bookmarks WHERE url = @u";
        cmd.Parameters.AddWithValue("@u", url);
        cmd.ExecuteNonQuery();
    }

    public bool IsBookmarked(string url)
    {
        using var conn = GetConnection();
        conn.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "SELECT COUNT(*) FROM bookmarks WHERE url = @u";
        cmd.Parameters.AddWithValue("@u", url);
        return Convert.ToInt32(cmd.ExecuteScalar()) > 0;
    }

    public List<Dictionary<string, string>> GetBookmarks()
    {
        var result = new List<Dictionary<string, string>>();
        using var conn = GetConnection();
        conn.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "SELECT title, url FROM bookmarks ORDER BY timestamp DESC";
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            result.Add(new Dictionary<string, string>
            {
                ["title"] = reader.GetString(0),
                ["url"] = reader.GetString(1)
            });
        }
        return result;
    }
}

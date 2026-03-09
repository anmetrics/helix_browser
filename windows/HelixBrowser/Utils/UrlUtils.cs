using System;
using System.Web;

namespace HelixBrowser;

public static class UrlUtils
{
    public static bool IsUrl(string input)
    {
        var trimmed = input.Trim();
        return trimmed.StartsWith("http://") || trimmed.StartsWith("https://") ||
               trimmed.StartsWith("ftp://") || (trimmed.Contains('.') && !trimmed.Contains(' '));
    }

    public static string FormatUrl(string input, string searchEngine = "google")
    {
        var trimmed = input.Trim();
        if (string.IsNullOrEmpty(trimmed)) return "helix://start";
        if (trimmed.StartsWith("helix://")) return trimmed;
        if (trimmed.StartsWith("http://") || trimmed.StartsWith("https://")) return trimmed;
        if (trimmed.StartsWith("file://") || trimmed.StartsWith("about:")) return trimmed;
        if (IsUrl(trimmed)) return "https://" + trimmed;
        return BuildSearchQuery(trimmed, searchEngine);
    }

    public static string BuildSearchQuery(string query, string engine)
    {
        var encoded = Uri.EscapeDataString(query);
        return engine.ToLower() switch
        {
            "bing" => $"https://www.bing.com/search?q={encoded}",
            "duckduckgo" => $"https://duckduckgo.com/?q={encoded}",
            "yahoo" => $"https://search.yahoo.com/search?p={encoded}",
            "brave" => $"https://search.brave.com/search?q={encoded}",
            _ => $"https://www.google.com/search?q={encoded}"
        };
    }

    public static string GetDisplayUrl(string url)
    {
        try
        {
            var uri = new Uri(url);
            var host = uri.Host;
            return host.StartsWith("www.") ? host[4..] : host;
        }
        catch { return url; }
    }

    public static string GetFaviconUrl(string url)
    {
        try
        {
            var host = new Uri(url).Host;
            return $"https://www.google.com/s2/favicons?domain={host}&sz=64";
        }
        catch { return ""; }
    }
}

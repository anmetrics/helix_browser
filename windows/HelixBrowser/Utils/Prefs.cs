namespace HelixBrowser;

public class Prefs
{
    private static Prefs? _instance;
    public static Prefs Instance => _instance ??= new Prefs();

    private readonly Windows.Storage.ApplicationDataContainer _settings;

    private Prefs()
    {
        _settings = Windows.Storage.ApplicationData.Current.LocalSettings;
    }

    public string SearchEngine { get => Get("search_engine", "google"); set => Set("search_engine", value); }
    public string Homepage { get => Get("homepage", "https://www.google.com"); set => Set("homepage", value); }
    public bool IsAdBlockEnabled { get => GetBool("ad_block", true); set => Set("ad_block", value); }
    public bool IsSaveHistoryEnabled { get => GetBool("save_history", true); set => Set("save_history", value); }
    public bool IsDesktopMode { get => GetBool("desktop_mode", false); set => Set("desktop_mode", value); }
    public bool IsBlockTrackersEnabled { get => GetBool("block_trackers", true); set => Set("block_trackers", value); }
    public bool IsBlockThirdPartyCookies { get => GetBool("block_third_party_cookies", true); set => Set("block_third_party_cookies", value); }
    public bool IsDoNotTrackEnabled { get => GetBool("do_not_track", true); set => Set("do_not_track", value); }
    public bool IsHttpsUpgradeEnabled { get => GetBool("https_upgrade", true); set => Set("https_upgrade", value); }
    public bool IsBlockFingerprintingEnabled { get => GetBool("block_fingerprinting", true); set => Set("block_fingerprinting", value); }
    public bool IsBlockPopupsEnabled { get => GetBool("block_popups", true); set => Set("block_popups", value); }
    public bool IsRestoreTabsEnabled { get => GetBool("restore_tabs", true); set => Set("restore_tabs", value); }
    public int DefaultZoom { get => GetInt("default_zoom", 100); set => Set("default_zoom", value); }

    private string Get(string key, string defaultValue) =>
        _settings.Values.TryGetValue(key, out var v) ? v as string ?? defaultValue : defaultValue;

    private bool GetBool(string key, bool defaultValue) =>
        _settings.Values.TryGetValue(key, out var v) ? v is bool b ? b : defaultValue : defaultValue;

    private int GetInt(string key, int defaultValue) =>
        _settings.Values.TryGetValue(key, out var v) ? v is int i ? i : defaultValue : defaultValue;

    private void Set(string key, object value) => _settings.Values[key] = value;
}

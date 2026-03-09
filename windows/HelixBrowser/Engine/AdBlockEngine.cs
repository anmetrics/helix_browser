using System.Collections.Generic;
using System.Linq;

namespace HelixBrowser;

public class AdBlockEngine
{
    private static readonly HashSet<string> BlockedDomains = new()
    {
        "googlesyndication.com", "doubleclick.net", "google-analytics.com", "googletagmanager.com",
        "facebook.com/tr", "adservice.google.com", "pagead2.googlesyndication.com",
        "amazon-adsystem.com", "ads.yahoo.com", "adnxs.com", "outbrain.com", "taboola.com",
        "criteo.com", "scorecardresearch.com", "quantserve.com", "hotjar.com", "mixpanel.com",
        "segment.io", "optimizely.com", "zedo.com", "rubiconproject.com", "pubmatic.com",
        "moatads.com", "mediavine.com", "adsrvr.org", "smartadserver.com", "bidswitch.net",
        "sharethrough.com", "indexexchange.com", "openx.net", "casalemedia.com", "advertising.com",
        "newrelic.com", "nr-data.net", "clarity.ms", "fullstory.com", "heapanalytics.com",
        "amplitude.com", "mouseflow.com", "crazyegg.com", "luckyorange.com", "inspectlet.com",
        "cookiebot.com", "onetrust.com", "consensu.org"
    };

    private static readonly string[] BlockedPaths = { "/ads/", "/ad.js", "/advert", "/ad-" };

    public bool ShouldBlock(string url)
    {
        if (string.IsNullOrEmpty(url)) return false;
        var lower = url.ToLowerInvariant();
        return BlockedDomains.Any(d => lower.Contains(d)) || BlockedPaths.Any(p => lower.Contains(p));
    }
}

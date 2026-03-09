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
        "cookiebot.com", "onetrust.com", "consensu.org",
        // YouTube ad serving
        "googleads.g.doubleclick.net", "static.doubleclick.net", "securepubads.g.doubleclick.net",
        "imasdk.googleapis.com", "tpc.googlesyndication.com", "s0.2mdn.net",
        "jnn-pa.googleapis.com", "fundingchoicesmessages.google.com",
        // Popup/redirect ad networks
        "popads.net", "popcash.net", "propellerads.com", "juicyads.com",
        "exoclick.com", "trafficjunky.net", "revcontent.com", "mgid.com",
        "adsterra.com", "hilltopads.net", "clickadu.com", "ad-maven.com",
        "richpush.co", "trafficstars.com"
    };

    private static readonly string[] BlockedPaths = { "/ads/", "/ad.js", "/advert", "/ad-banner", "/popunder", "/popup" };

    private static readonly string[] YoutubeAdPaths = {
        "/api/stats/ads", "/pagead/", "/get_midroll_info", "/ptracking",
        "/api/stats/atr", "/error_204", "/generate_204", "/youtubei/v1/log_event"
    };

    public bool ShouldBlock(string url)
    {
        if (string.IsNullOrEmpty(url)) return false;
        var lower = url.ToLowerInvariant();

        if (BlockedDomains.Any(d => lower.Contains(d))) return true;
        if (lower.Contains("youtube.com") && YoutubeAdPaths.Any(p => lower.Contains(p))) return true;
        return BlockedPaths.Any(p => lower.Contains(p));
    }

    public bool IsPopupAd(string url)
    {
        if (string.IsNullOrEmpty(url)) return false;
        var lower = url.ToLowerInvariant();
        var popupDomains = new[] {
            "popads.net", "popcash.net", "propellerads.com", "juicyads.com",
            "exoclick.com", "trafficjunky.net", "revcontent.com", "mgid.com",
            "adsterra.com", "hilltopads.net", "clickadu.com", "ad-maven.com",
            "doubleclick.net", "googlesyndication.com"
        };
        return popupDomains.Any(d => lower.Contains(d));
    }
}

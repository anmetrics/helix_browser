"""Ad blocking engine for Helix Browser"""


class AdBlockEngine:
    BLOCKED_DOMAINS = {
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
    }

    BLOCKED_PATHS = ["/ads/", "/ad.js", "/advert", "/ad-"]

    def should_block(self, url: str) -> bool:
        if not url:
            return False
        lower = url.lower()
        for domain in self.BLOCKED_DOMAINS:
            if domain in lower:
                return True
        for path in self.BLOCKED_PATHS:
            if path in lower:
                return True
        return False

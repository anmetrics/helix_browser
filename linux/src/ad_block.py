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
        # YouTube ad serving
        "googleads.g.doubleclick.net", "static.doubleclick.net", "securepubads.g.doubleclick.net",
        "imasdk.googleapis.com", "tpc.googlesyndication.com", "s0.2mdn.net",
        "jnn-pa.googleapis.com", "fundingchoicesmessages.google.com",
        # Popup/redirect ad networks
        "popads.net", "popcash.net", "propellerads.com", "juicyads.com",
        "exoclick.com", "trafficjunky.net", "revcontent.com", "mgid.com",
        "adsterra.com", "hilltopads.net", "clickadu.com", "ad-maven.com",
        "richpush.co", "trafficstars.com",
    }

    BLOCKED_PATHS = ["/ads/", "/ad.js", "/advert", "/ad-banner", "/popunder", "/popup"]

    YOUTUBE_AD_PATHS = [
        "/api/stats/ads", "/pagead/", "/get_midroll_info", "/ptracking",
        "/api/stats/atr", "/error_204", "/generate_204", "/youtubei/v1/log_event",
    ]

    POPUP_AD_DOMAINS = {
        "popads.net", "popcash.net", "propellerads.com", "juicyads.com",
        "exoclick.com", "trafficjunky.net", "revcontent.com", "mgid.com",
        "adsterra.com", "hilltopads.net", "clickadu.com", "ad-maven.com",
        "richpush.co", "trafficstars.com", "doubleclick.net", "googlesyndication.com",
    }

    def should_block(self, url: str) -> bool:
        if not url:
            return False
        lower = url.lower()
        for domain in self.BLOCKED_DOMAINS:
            if domain in lower:
                return True
        # YouTube-specific ad patterns
        if "youtube.com" in lower:
            for path in self.YOUTUBE_AD_PATHS:
                if path in lower:
                    return True
        for path in self.BLOCKED_PATHS:
            if path in lower:
                return True
        return False

    def is_popup_ad(self, url: str) -> bool:
        if not url:
            return False
        lower = url.lower()
        return any(domain in lower for domain in self.POPUP_AD_DOMAINS)

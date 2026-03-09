package com.helix.browser.engine

import android.net.Uri

object AdBlockEngine {
    private val adDomains = setOf(
        // Google ads
        "doubleclick.net",
        "google-analytics.com",
        "googletagservices.com",
        "googlesyndication.com",
        "googleadservices.com",
        "ads.google.com",
        "adservice.google.com",
        "analytics.google.com",
        "pagead2.googlesyndication.com",
        "googleads.g.doubleclick.net",
        "static.doubleclick.net",
        "securepubads.g.doubleclick.net",
        "tpc.googlesyndication.com",
        "fundingchoicesmessages.google.com",
        // YouTube ad serving
        "imasdk.googleapis.com",
        "jnn-pa.googleapis.com",
        "s0.2mdn.net",
        // General ad networks
        "adnxs.com",
        "advertising.com",
        "adtechus.com",
        "quantserve.com",
        "scorecardresearch.com",
        "zedo.com",
        "outbrain.com",
        "taboola.com",
        "moatads.com",
        "mediavine.com",
        "adsrvr.org",
        "smartadserver.com",
        "bidswitch.net",
        "sharethrough.com",
        "indexexchange.com",
        "openx.net",
        "casalemedia.com",
        "buysellads.com",
        "carbonads.net",
        // Social media ads
        "ads-twitter.com",
        "static.ads-twitter.com",
        "ads.linkedin.com",
        "facebook.net",
        "connect.facebook.net",
        // Programmatic
        "criteo.com",
        "rubiconproject.com",
        "pubmatic.com",
        "appnexus.com",
        "contextweb.com",
        "liadm.com",
        "ads.yahoo.com",
        "mads.amazon-adsystem.com",
        "amazon-adsystem.com",
        // Popup/redirect ad networks
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "juicyads.com",
        "exoclick.com",
        "trafficjunky.net",
        "revcontent.com",
        "mgid.com",
        "adsterra.com",
        "hilltopads.net",
        "clickadu.com",
        "ad-maven.com",
        "richpush.co",
        "trafficstars.com",
        // Consent/cookie banners
        "cookiebot.com",
        "onetrust.com",
        "consensu.org"
    )

    // YouTube-specific ad URL patterns
    private val youtubeAdPaths = listOf(
        "/api/stats/ads",
        "/pagead/",
        "/get_midroll_info",
        "/ptracking",
        "/api/stats/atr",
        "/error_204?",
        "/generate_204?",
        "/youtubei/v1/log_event"
    )

    private val blockedPathPatterns = listOf(
        "/ads/",
        "/ad.js",
        "/advert",
        "/ad-banner",
        "/popunder",
        "/popup"
    )

    fun isAd(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false

            // Check if host exactly matches or ends with an ad domain
            val domainMatch = adDomains.any { domain ->
                host == domain || host.endsWith(".$domain")
            }
            if (domainMatch) return true

            // Check YouTube-specific ad URL patterns
            if (host.contains("youtube.com") || host.contains("youtube-nocookie.com")) {
                val fullUrl = url.lowercase()
                if (youtubeAdPaths.any { pattern -> fullUrl.contains(pattern) }) return true
            }

            // Check URL path patterns
            val path = uri.path?.lowercase() ?: return false
            blockedPathPatterns.any { pattern -> path.contains(pattern) }
        } catch (e: Exception) {
            false
        }
    }

    /** Check if a URL is from a known popup/redirect ad network */
    fun isPopupAd(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: return false
            val popupDomains = setOf(
                "popads.net", "popcash.net", "propellerads.com", "juicyads.com",
                "exoclick.com", "trafficjunky.net", "revcontent.com", "mgid.com",
                "adsterra.com", "hilltopads.net", "clickadu.com", "ad-maven.com",
                "richpush.co", "trafficstars.com", "doubleclick.net", "googlesyndication.com"
            )
            popupDomains.any { host.contains(it) }
        } catch (e: Exception) {
            false
        }
    }
}

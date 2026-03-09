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
        // Consent/cookie banners
        "cookiebot.com",
        "onetrust.com",
        "consensu.org"
    )

    private val blockedPathPatterns = listOf(
        "/ads/",
        "/ad.js",
        "/advert"
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

            // Check URL path patterns
            val path = uri.path?.lowercase() ?: return false
            blockedPathPatterns.any { pattern -> path.contains(pattern) }
        } catch (e: Exception) {
            false
        }
    }
}

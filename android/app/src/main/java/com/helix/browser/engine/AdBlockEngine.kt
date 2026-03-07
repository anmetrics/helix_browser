package com.helix.browser.engine

import android.net.Uri

object AdBlockEngine {
    private val adDomains = setOf(
        "doubleclick.net",
        "google-analytics.com",
        "googletagservices.com",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "ads.google.com",
        "adservice.google.com",
        "analytics.google.com",
        "adnxs.com",
        "advertising.com",
        "adtechus.com",
        "quantserve.com",
        "scorecardresearch.com",
        "zedo.com",
        "outbrain.com",
        "taboola.com",
        "moatads.com",
        "buysellads.com",
        "carbonads.net",
        "ads-twitter.com",
        "static.ads-twitter.com",
        "ads.linkedin.com",
        "facebook.net",
        "connect.facebook.net",
        "criteo.com",
        "casalemedia.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "appnexus.com",
        "smartadserver.com",
        "bidswitch.net",
        "contextweb.com",
        "liadm.com",
        "ads.yahoo.com",
        "mads.amazon-adsystem.com",
        "amazon-adsystem.com"
    )

    fun isAd(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false
            
            // Check if host exactly matches or ends with an ad domain
            adDomains.any { domain ->
                host == domain || host.endsWith(".$domain")
            }
        } catch (e: Exception) {
            false
        }
    }
}

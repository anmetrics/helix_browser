package com.helix.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewDatabase

@SuppressLint("SetJavaScriptEnabled")
class HelixWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    init {
        setupSettings()
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupSettings() {
        settings.apply {
            // JavaScript
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true

            // Storage
            domStorageEnabled = true
            databaseEnabled = true

            // Caching
            cacheMode = WebSettings.LOAD_DEFAULT

            // Zoom
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // Media
            mediaPlaybackRequiresUserGesture = false

            // Mixed content
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            // Load images
            loadsImagesAutomatically = true
            blockNetworkImage = false

            // File access
            allowFileAccess = true
            allowContentAccess = true

            // Layout
            useWideViewPort = true
            loadWithOverviewMode = true

            // Text encoding
            defaultTextEncodingName = "UTF-8"

            // User agent (Chrome-like)
            userAgentString = getDefaultUserAgent()
        }

        // Hardware acceleration
        setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Scrollbars
        isScrollbarFadingEnabled = true
        scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
    }

    fun setDesktopMode(enabled: Boolean) {
        if (enabled) {
            settings.userAgentString = DESKTOP_USER_AGENT
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        } else {
            settings.userAgentString = getDefaultUserAgent()
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        }
        reload()
    }

    fun setIncognitoMode(enabled: Boolean) {
        if (enabled) {
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            CookieManager.getInstance().setAcceptCookie(false)
        } else {
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            CookieManager.getInstance().setAcceptCookie(true)
        }
        clearHistory()
    }

    fun clearAllData(context: Context) {
        clearCache(true)
        clearHistory()
        clearFormData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebViewDatabase.getInstance(context).clearFormData()
        WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
    }

    private fun getDefaultUserAgent(): String {
        return "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
    }

    companion object {
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
    }
}

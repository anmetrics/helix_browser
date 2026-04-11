package com.helix.browser.engine

import android.app.AlertDialog
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri

class HelixWebViewClient(
    private val onPageStarted: (url: String, favicon: Bitmap?) -> Unit,
    private val onPageFinished: (url: String) -> Unit,
    private val onPageError: (url: String, errorCode: Int, description: String) -> Unit,
    private val shouldOverrideUrl: ((url: String) -> Boolean)? = null,
    private val isAdBlockEnabled: () -> Boolean = { false },
    private val isTrackerBlockEnabled: () -> Boolean = { false },
    private val isHttpsUpgradeEnabled: () -> Boolean = { false },
    private val getPrivacyScripts: () -> String = { "" },
    private val onTrackerBlocked: () -> Unit = {}
) : WebViewClient() {

    private var _trackersBlockedCount = 0
    val trackersBlockedCount: Int get() = _trackersBlockedCount

    fun resetTrackerCount() {
        _trackersBlockedCount = 0
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()

        // Block ads
        if (isAdBlockEnabled() && AdBlockEngine.isAd(url)) {
            return WebResourceResponse("text/plain", "UTF-8", null)
        }

        // Block trackers
        if (isTrackerBlockEnabled() && PrivacyManager.isTracker(url)) {
            _trackersBlockedCount++
            onTrackerBlocked()
            return WebResourceResponse("text/plain", "UTF-8", null)
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)

        // Inject privacy scripts at page start
        val scripts = getPrivacyScripts()
        if (scripts.isNotEmpty()) {
            view.evaluateJavascript(scripts, null)
        }

        onPageStarted(url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)

        // Re-inject privacy/ad-block scripts on page finish
        // Critical for YouTube SPA navigation where page doesn't fully reload
        val scripts = getPrivacyScripts()
        if (scripts.isNotEmpty()) {
            view.evaluateJavascript(scripts, null)
        }

        onPageFinished(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        if (request.isForMainFrame) {
            val errorCode = error.errorCode
            val description = error.description?.toString() ?: "Unknown error"
            onPageError(request.url.toString(), errorCode, description)
            val errorHtml = buildErrorPage(view.context, request.url.toString(), description)
            view.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        // Only handle main frame errors
        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
            super.onReceivedHttpError(view, request, errorResponse)
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        // Show a dialog asking user whether to proceed
        val context = view.context
        val errorMessage = when (error.primaryError) {
            SslError.SSL_UNTRUSTED -> context.getString(com.helix.browser.R.string.ssl_untrusted)
            SslError.SSL_EXPIRED -> context.getString(com.helix.browser.R.string.ssl_expired)
            SslError.SSL_IDMISMATCH -> context.getString(com.helix.browser.R.string.ssl_id_mismatch)
            SslError.SSL_NOTYETVALID -> context.getString(com.helix.browser.R.string.ssl_not_yet_valid)
            SslError.SSL_DATE_INVALID -> context.getString(com.helix.browser.R.string.ssl_date_invalid)
            SslError.SSL_INVALID -> context.getString(com.helix.browser.R.string.ssl_invalid)
            else -> context.getString(com.helix.browser.R.string.ssl_unknown)
        }

        try {
            AlertDialog.Builder(context)
                .setTitle(context.getString(com.helix.browser.R.string.ssl_title))
                .setMessage(context.getString(com.helix.browser.R.string.ssl_message, errorMessage, error.url))
                .setPositiveButton(context.getString(com.helix.browser.R.string.continue_button)) { _, _ ->
                    handler.proceed()
                }
                .setNegativeButton(context.getString(com.helix.browser.R.string.go_back_button)) { _, _ ->
                    handler.cancel()
                }
                .setOnCancelListener {
                    handler.cancel()
                }
                .setCancelable(true)
                .show()
        } catch (e: Exception) {
            // If dialog can't be shown (e.g., activity destroyed), cancel
            handler.cancel()
            val errorHtml = buildSslErrorPage(context, error.url, error.primaryError)
            view.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()

        // Block ad redirect URLs
        if (isAdBlockEnabled() && (AdBlockEngine.isAd(url) || AdBlockEngine.isPopupAd(url))) {
            return true
        }

        // HTTPS upgrade: redirect http to https
        if (isHttpsUpgradeEnabled()) {
            val upgraded = PrivacyManager.upgradeToHttps(url)
            if (upgraded != null) {
                view.loadUrl(upgraded)
                return true
            }
        }

        // Handle special schemes
        return when (url.toUri().scheme) {
            "http", "https", "about", "data", "blob" -> {
                shouldOverrideUrl?.invoke(url) ?: false
            }
            "intent", "market" -> {
                // Android intent URLs - handled externally
                true
            }
            else -> {
                // Tel, mailto, etc.
                false
            }
        }
    }

    private fun buildErrorPage(context: android.content.Context, url: String, description: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body { font-family: -apple-system, sans-serif; background: #000; color: #fff;
                           display: flex; align-items: center; justify-content: center;
                           min-height: 100vh; margin: 0; flex-direction: column; text-align: center; padding: 24px; }
                    .icon { font-size: 64px; margin-bottom: 16px; }
                    h1 { font-size: 22px; font-weight: 600; color: #fff; margin-bottom: 8px; }
                    p { color: #999; font-size: 14px; margin: 4px 0; max-width: 320px; }
                    .url { color: #0095F6; word-break: break-all; margin-top: 12px; font-size: 13px; }
                    button { margin-top: 24px; padding: 12px 32px; border-radius: 24px; border: none;
                             background: #0095F6; color: white; font-size: 16px; font-weight: 600; cursor: pointer; }
                </style>
            </head>
            <body>
                <div class="icon">⚠️</div>
                <h1>${context.getString(com.helix.browser.R.string.error_page_title)}</h1>
                <p>$description</p>
                <p class="url">$url</p>
                <button onclick="history.back()">${context.getString(com.helix.browser.R.string.error_page_go_back)}</button>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildSslErrorPage(context: android.content.Context, url: String, errorCode: Int): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body { font-family: -apple-system, sans-serif; background: #000; color: #fff;
                           display: flex; align-items: center; justify-content: center;
                           min-height: 100vh; margin: 0; flex-direction: column; text-align: center; padding: 24px; }
                    .icon { font-size: 64px; margin-bottom: 16px; }
                    h1 { font-size: 22px; font-weight: 600; color: #FF3B30; margin-bottom: 8px; }
                    p { color: #999; font-size: 14px; max-width: 320px; }
                    .url { color: #FF3B30; word-break: break-all; margin-top: 12px; font-size: 13px; }
                    button { margin-top: 24px; padding: 12px 32px; border-radius: 24px; border: none;
                             background: #FF3B30; color: white; font-size: 16px; font-weight: 600; cursor: pointer; }
                </style>
            </head>
            <body>
                <div class="icon">🔒</div>
                <h1>${context.getString(com.helix.browser.R.string.ssl_error_page_title)}</h1>
                <p>${context.getString(com.helix.browser.R.string.ssl_error_page_message, errorCode)}</p>
                <p class="url">$url</p>
                <button onclick="history.back()">${context.getString(com.helix.browser.R.string.ssl_error_page_go_back)}</button>
            </body>
            </html>
        """.trimIndent()
    }
}

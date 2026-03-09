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
            val errorHtml = buildErrorPage(request.url.toString(), description)
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
            SslError.SSL_UNTRUSTED -> "Chứng chỉ bảo mật không được tin cậy."
            SslError.SSL_EXPIRED -> "Chứng chỉ bảo mật đã hết hạn."
            SslError.SSL_IDMISMATCH -> "Tên miền không khớp với chứng chỉ."
            SslError.SSL_NOTYETVALID -> "Chứng chỉ bảo mật chưa có hiệu lực."
            SslError.SSL_DATE_INVALID -> "Ngày trên chứng chỉ không hợp lệ."
            SslError.SSL_INVALID -> "Chứng chỉ bảo mật không hợp lệ."
            else -> "Lỗi SSL không xác định."
        }

        try {
            AlertDialog.Builder(context)
                .setTitle("Kết nối không an toàn")
                .setMessage("$errorMessage\n\nURL: ${error.url}\n\nBạn có muốn tiếp tục không?")
                .setPositiveButton("Tiếp tục") { _, _ ->
                    handler.proceed()
                }
                .setNegativeButton("Quay lại") { _, _ ->
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
            val errorHtml = buildSslErrorPage(error.url, error.primaryError)
            view.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()

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

    private fun buildErrorPage(url: String, description: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body { font-family: 'Segoe UI', sans-serif; background: #1a1a2e; color: #eee;
                           display: flex; align-items: center; justify-content: center;
                           min-height: 100vh; margin: 0; flex-direction: column; text-align: center; padding: 24px; }
                    .icon { font-size: 64px; margin-bottom: 16px; }
                    h1 { font-size: 24px; font-weight: 700; color: #e0e0ff; margin-bottom: 8px; }
                    p { color: #aaa; font-size: 14px; margin: 4px 0; max-width: 320px; }
                    .url { color: #7c7cff; word-break: break-all; margin-top: 12px; font-size: 13px; }
                    button { margin-top: 24px; padding: 12px 32px; border-radius: 24px; border: none;
                             background: #7c7cff; color: white; font-size: 16px; cursor: pointer; }
                </style>
            </head>
            <body>
                <div class="icon">⚠️</div>
                <h1>Không thể kết nối</h1>
                <p>$description</p>
                <p class="url">$url</p>
                <button onclick="history.back()">Quay lại</button>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildSslErrorPage(url: String, errorCode: Int): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body { font-family: 'Segoe UI', sans-serif; background: #1a0000; color: #eee;
                           display: flex; align-items: center; justify-content: center;
                           min-height: 100vh; margin: 0; flex-direction: column; text-align: center; padding: 24px; }
                    .icon { font-size: 64px; margin-bottom: 16px; }
                    h1 { font-size: 24px; font-weight: 700; color: #ff8080; margin-bottom: 8px; }
                    p { color: #aaa; font-size: 14px; max-width: 320px; }
                    .url { color: #ff4444; word-break: break-all; margin-top: 12px; font-size: 13px; }
                    button { margin-top: 24px; padding: 12px 32px; border-radius: 24px; border: none;
                             background: #ff4444; color: white; font-size: 16px; cursor: pointer; }
                </style>
            </head>
            <body>
                <div class="icon">🔒</div>
                <h1>Kết nối không an toàn</h1>
                <p>Trang web này có chứng chỉ bảo mật không hợp lệ (Lỗi SSL $errorCode).</p>
                <p class="url">$url</p>
                <button onclick="history.back()">Quay lại an toàn</button>
            </body>
            </html>
        """.trimIndent()
    }
}

package com.helix.browser.engine

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.net.Uri
import android.os.Build
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HelixWebViewClient(
    private val onPageStarted: (url: String, favicon: Bitmap?) -> Unit,
    private val onPageFinished: (url: String) -> Unit,
    private val onPageError: (url: String, errorCode: Int, description: String) -> Unit,
    private val shouldOverrideUrl: ((url: String) -> Boolean)? = null,
    private val isAdBlockEnabled: () -> Boolean = { false },
    private val isTrackerBlockEnabled: () -> Boolean = { false },
    private val isHttpsUpgradeEnabled: () -> Boolean = { false },
    private val isHttpsOnlyModeEnabled: () -> Boolean = { false },
    private val getPrivacyScripts: () -> String = { "" },
    private val onTrackerBlocked: () -> Unit = {}
) : WebViewClient() {

    private companion object {
        const val TAG = "HelixWebViewClient"
        // Hard cap on server-controlled host/realm text shown in the auth dialog.
        const val MAX_DISPLAY_LEN = 256
    }

    private val _trackersBlockedCount = java.util.concurrent.atomic.AtomicInteger(0)
    val trackersBlockedCount: Int get() = _trackersBlockedCount.get()

    fun resetTrackerCount() {
        _trackersBlockedCount.set(0)
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
            _trackersBlockedCount.incrementAndGet()
            onTrackerBlocked()
            return WebResourceResponse("text/plain", "UTF-8", null)
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        injectPrivacyScripts(view)
        onPageStarted(url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        // Re-inject for SPA navigations (e.g. YouTube) where no full reload occurs.
        injectPrivacyScripts(view)
        onPageFinished(url)
    }

    private fun injectPrivacyScripts(view: WebView) {
        val scripts = try { getPrivacyScripts() } catch (e: Exception) {
            Log.w(TAG, "getPrivacyScripts threw", e); return
        }
        if (scripts.isEmpty()) return
        try {
            view.evaluateJavascript(scripts) { /* result ignored */ }
        } catch (e: IllegalStateException) {
            // WebView destroyed mid-evaluation — safe to ignore.
            Log.d(TAG, "evaluateJavascript on destroyed WebView: ${e.message}")
        } catch (e: Throwable) {
            Log.w(TAG, "evaluateJavascript failed", e)
        }
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        if (!request.isForMainFrame) return

        val errorCode = error.errorCode
        val description = error.description?.toString() ?: "Unknown error"

        // Ignore aborted/transient failures. ERROR_UNKNOWN (-1) is what WebView
        // reports for user-cancelled / superseded navigations (e.g. tapping a new
        // link before the previous load committed). "ERR_ABORTED" appears in the
        // Chromium description for the same class of cancellations, and
        // ERROR_TIMEOUT is transient and worth a silent retry rather than an
        // interstitial that destroys whatever is currently shown.
        if (errorCode == WebViewClient.ERROR_UNKNOWN ||
            errorCode == WebViewClient.ERROR_TIMEOUT ||
            description.contains("ERR_ABORTED", ignoreCase = true)
        ) {
            return
        }

        // Only render the interstitial when the failing request is the document
        // the WebView is actually committing. Comparing against view.url avoids
        // wiping a perfectly good page when a stale/secondary main-frame request
        // fails. view.url is null/blank before the first commit, in which case we
        // have no good page to protect and should show the error.
        val failingUrl = request.url.toString()
        val committedUrl = view.url
        if (!committedUrl.isNullOrBlank() && committedUrl != failingUrl) {
            return
        }

        onPageError(failingUrl, errorCode, description)
        // loadDataWithBaseURL adds a back-stack entry, so the interstitial's
        // history.back() walks back past the failed navigation to the previous
        // page, which is the desired behaviour here.
        val errorHtml = buildErrorPage(view.context, failingUrl, description)
        view.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
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

    @android.annotation.SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        val context = view.context
        // Strict HTTPS-Only mode refuses any cert override; cancel without
        // prompting and show the interstitial.
        if (isHttpsOnlyModeEnabled()) {
            handler.cancel()
            view.loadDataWithBaseURL(null, buildSslErrorPage(context, error.url, error.primaryError),
                "text/html", "UTF-8", null)
            return
        }
        // For the high-risk certificate problems (untrusted root / hostname
        // mismatch) there is no safe one-tap override: a single AlertDialog tap
        // normalises bypassing active MITM. Match Chrome by refusing the
        // connection and rendering a full-page interstitial that names the exact
        // host, instead of a dialog tied to a possibly-stale Activity context.
        if (error.primaryError == SslError.SSL_UNTRUSTED ||
            error.primaryError == SslError.SSL_IDMISMATCH
        ) {
            handler.cancel()
            val errorHtml = buildSslErrorPage(context, error.url, error.primaryError)
            view.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
            return
        }
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

    // Handles HTTP Basic/Digest auth challenges (401/407). Mirrors Chrome: shows
    // a dialog with the requesting host + realm and username/password fields,
    // optionally remembering the credentials in the WebView auth store. The
    // request MUST be resolved on every path — proceeding or cancelling — or the
    // load hangs forever, so any failure to surface UI cancels the challenge.
    override fun onReceivedHttpAuthRequest(
        view: WebView,
        handler: HttpAuthHandler,
        host: String?,
        realm: String?
    ) {
        // Resolve a live Activity window token the same defensive way the SSL/JS
        // dialogs do. Without one we cannot show UI, so cancel rather than strand
        // the request (and avoid a WindowManager BadTokenException).
        val activity = view.context.findAliveActivity()
        if (activity == null) {
            handler.cancel()
            return
        }

        // Credentials are NEVER prefilled from or written to the shared auth
        // store for incognito sessions — that would leak/persist private creds.
        val incognito = (view as? HelixWebView)?.isIncognito == true

        // Prefill from the persisted WebView auth store when available. Suppressed
        // in incognito so private sessions never surface stored credentials.
        val saved: Pair<String?, String?> = if (incognito) {
            null to null
        } else {
            lookupSavedCredentials(view, host, realm)
        }

        val context: Context = activity
        val usernameField = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
            hint = context.getString(com.helix.browser.R.string.http_auth_username_hint)
            setText(saved.first ?: "")
            setSingleLine(true)
        }
        val passwordField = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = context.getString(com.helix.browser.R.string.http_auth_password_hint)
            setText(saved.second ?: "")
            setSingleLine(true)
        }

        // "Remember" is only offered (and defaults off) for non-incognito
        // sessions and only on API levels where we can actually persist.
        val canRemember = !incognito && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        val rememberCheck = if (canRemember) {
            CheckBox(activity).apply {
                text = context.getString(com.helix.browser.R.string.http_auth_remember)
                isChecked = false
            }
        } else null

        val pad = dp(context, 20)
        val gap = dp(context, 8)
        val realmView = TextView(activity).apply {
            text = context.getString(
                com.helix.browser.R.string.http_auth_realm_label,
                sanitizeForDisplay(host),
                sanitizeForDisplay(realm)
            )
            setPadding(0, 0, 0, gap)
        }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, gap, pad, 0)
            addView(realmView)
            addView(usernameField)
            addView(passwordField)
            rememberCheck?.let { addView(it) }
        }

        try {
            MaterialAlertDialogBuilder(activity)
                .setTitle(context.getString(com.helix.browser.R.string.http_auth_title))
                .setView(container)
                .setPositiveButton(context.getString(com.helix.browser.R.string.http_auth_sign_in)) { _, _ ->
                    val user = usernameField.text?.toString() ?: ""
                    val pass = passwordField.text?.toString() ?: ""
                    if (rememberCheck?.isChecked == true) {
                        storeCredentials(view, host, realm, user, pass)
                    }
                    handler.proceed(user, pass)
                }
                .setNegativeButton(context.getString(com.helix.browser.R.string.cancel)) { _, _ ->
                    handler.cancel()
                }
                // Tapping outside / back must still resolve the challenge.
                .setOnCancelListener { handler.cancel() }
                .setCancelable(true)
                .show()
        } catch (e: Exception) {
            // Activity went away between the liveness check and show(): never
            // leave the request hanging.
            Log.w(TAG, "HTTP auth dialog could not be shown", e)
            handler.cancel()
        }
    }

    // Reads any stored credentials for (host, realm). Uses the in-flight handler
    // hint first (set by the platform on a failed-auth retry) then the persisted
    // WebViewDatabase store (API 26+). All WebView/DB access is wrapped because a
    // destroyed WebView or unavailable provider must fail soft, not crash.
    private fun lookupSavedCredentials(
        view: WebView,
        host: String?,
        realm: String?
    ): Pair<String?, String?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null to null
        return try {
            val db = WebViewDatabase.getInstance(view.context)
            val creds = db.getHttpAuthUsernamePassword(host, realm)
            if (creds != null && creds.size >= 2) creds[0] to creds[1] else null to null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read saved HTTP auth credentials", e)
            null to null
        }
    }

    // Persists credentials in the WebView auth store (API 26+). Wrapped so a
    // storage failure never blocks the actual proceed() that follows it.
    private fun storeCredentials(
        view: WebView,
        host: String?,
        realm: String?,
        user: String,
        pass: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (host.isNullOrEmpty()) return
        try {
            WebViewDatabase.getInstance(view.context)
                .setHttpAuthUsernamePassword(host, realm ?: "", user, pass)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist HTTP auth credentials", e)
        }
    }

    // Trims and length-caps a host/realm before it is shown in a plain TextView.
    // These are server-controlled strings: a hostile realm could be megabytes of
    // junk, contain control chars, or be RTL-override trickery, so we collapse
    // whitespace and hard-cap the length. The TextView renders them as literal
    // text (no HTML), so no markup escaping is required.
    private fun sanitizeForDisplay(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val collapsed = value.trim().replace(Regex("\\s+"), " ")
        return if (collapsed.length > MAX_DISPLAY_LEN) {
            collapsed.substring(0, MAX_DISPLAY_LEN) + "…"
        } else collapsed
    }

    private fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics
    ).toInt()

    // Unwraps a (possibly themed) Context to its backing Activity, returning null
    // when there is no Activity or it is finishing/destroyed. Matches the guard
    // used for the JS/SSL dialogs so a stale window token never crashes a prompt.
    private fun Context.findAliveActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return if (ctx.isFinishing || ctx.isDestroyed) null else ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()

        // Block ad redirect URLs
        if (isAdBlockEnabled() && (AdBlockEngine.isAd(url) || AdBlockEngine.isPopupAd(url))) {
            return true
        }

        // HTTPS-Only Mode (strict): refuse cleartext entirely; show interstitial.
        if (isHttpsOnlyModeEnabled() && url.startsWith("http://")) {
            val upgraded = PrivacyManager.upgradeToHttps(url)
            if (upgraded != null) {
                view.loadUrl(upgraded)
            } else {
                val html = buildHttpsOnlyInterstitial(view.context, url)
                view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
            return true
        }

        // HTTPS upgrade (best-effort): attempt https rewrite, fall back to http on failure.
        if (isHttpsUpgradeEnabled()) {
            val upgraded = PrivacyManager.upgradeToHttps(url)
            if (upgraded != null) {
                view.loadUrl(upgraded)
                return true
            }
        }

        // Handle special schemes
        return when (request.url.scheme?.lowercase()) {
            "http", "https", "about", "data", "blob", "javascript", null -> {
                shouldOverrideUrl?.invoke(url) ?: false
            }
            "intent" -> handleIntentScheme(view, request, url)
            else -> handleExternalScheme(view, request)
        }
    }

    /**
     * Resolves an `intent:` URL into a real Android Intent and dispatches it to
     * the OS. Untrusted, attacker-supplied intents are sanitised before launch:
     * the explicit component/selector are cleared (so the page cannot target an
     * arbitrary private component) and URI grant flags are stripped. Honours the
     * `browser_fallback_url` extra when no app can handle the intent.
     */
    private fun handleIntentScheme(view: WebView, request: WebResourceRequest, url: String): Boolean {
        val intent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse intent URL", e)
            return true
        }

        // Sanitise the attacker-controlled intent before dispatch.
        intent.component = null
        intent.selector = null
        intent.flags = intent.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or
             Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
             Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
             Intent.FLAG_GRANT_PREFIX_URI_PERMISSION).inv()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val fallbackUrl = intent.getStringExtra("browser_fallback_url")

        // Only auto-launch external apps for a real user gesture; this prevents
        // drive-by intent launches triggered by page scripts.
        if (!request.hasGesture()) {
            if (fallbackUrl != null && isWebUrl(fallbackUrl)) {
                view.loadUrl(fallbackUrl)
            }
            return true
        }

        if (launchExternal(view, intent)) return true

        if (fallbackUrl != null && isWebUrl(fallbackUrl)) {
            view.loadUrl(fallbackUrl)
        } else {
            showNoAppToast(view)
        }
        return true
    }

    /**
     * Dispatches non-web schemes (tel, mailto, sms, geo, market, …) to the OS
     * via an ACTION_VIEW intent, with graceful handling when no app resolves.
     */
    private fun handleExternalScheme(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.hasGesture()) {
            // Ignore non-user-initiated external navigations rather than letting
            // the WebView attempt (and fail) to load a non-web scheme.
            return true
        }
        val intent = Intent(Intent.ACTION_VIEW, request.url).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (!launchExternal(view, intent)) {
            showNoAppToast(view)
        }
        return true
    }

    private fun launchExternal(view: WebView, intent: Intent): Boolean {
        val pm = view.context.packageManager
        if (intent.resolveActivity(pm) == null) return false
        return try {
            view.context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity to handle external intent", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Not permitted to launch external intent", e)
            false
        }
    }

    private fun showNoAppToast(view: WebView) {
        Toast.makeText(
            view.context,
            view.context.getString(com.helix.browser.R.string.no_app_found_for_link),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun isWebUrl(url: String): Boolean {
        val scheme = try { Uri.parse(url).scheme?.lowercase() } catch (e: Exception) { null }
        return scheme == "http" || scheme == "https"
    }

    /**
     * Escapes text for safe interpolation into HTML element/attribute content.
     * These chrome pages reflect attacker-controlled values (the navigated URL
     * and the error description), so every dynamic value MUST pass through here
     * to prevent DOM XSS.
     */
    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    // Escapes a value for safe embedding inside a single-quoted JavaScript string
    // literal. This is the INNER layer only; the composed onclick attribute is
    // then HTML-escaped (see makeRetryOnclick) so the value is safe in BOTH the
    // JS-string and the HTML double-quoted-attribute contexts. Backslash is
    // escaped first so we don't double-escape the escapes we add afterwards.
    // U+2028/U+2029 are line terminators in JS and must be escaped even though
    // they are not newlines in most other contexts.
    private fun escapeJsString(s: String): String = s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")

    // Only http/https targets are safe to reload from an interstitial. The
    // failing URL is attacker-influenced, so we never wire a "Try again" that
    // could navigate to a javascript:, intent:, data: or other dangerous scheme.
    private fun isReloadableUrl(url: String): Boolean {
        val scheme = try { Uri.parse(url).scheme?.lowercase() } catch (e: Exception) { null }
        return scheme == "http" || scheme == "https"
    }

    // Shared brand-styled <style> block for all interstitials. Material dark:
    // app background (#0F0F0F), high-contrast text and the brand purple accent
    // (#7B68EE / pressed #6355D8) instead of the previous iOS blue/red.
    // accentColor lets the SSL page tint its title/url red-tinged purple while
    // keeping buttons on-brand.
    private fun interstitialStyle(accentColor: String): String = """
        :root { color-scheme: dark; }
        * { box-sizing: border-box; }
        body { font-family: -apple-system, "Roboto", "Segoe UI", system-ui, sans-serif;
               background: #0F0F0F; color: #F0F0F0;
               display: flex; align-items: center; justify-content: center;
               min-height: 100vh; margin: 0; flex-direction: column; text-align: center; padding: 24px;
               -webkit-text-size-adjust: 100%; }
        .card { max-width: 360px; width: 100%; }
        .icon { width: 72px; height: 72px; margin: 0 auto 20px; border-radius: 50%;
                display: flex; align-items: center; justify-content: center;
                background: #1A1A1A; font-size: 36px; line-height: 1; }
        h1 { font-size: 22px; font-weight: 600; color: #F0F0F0; margin: 0 0 10px; }
        p { color: #8E8E93; font-size: 14px; line-height: 1.5; margin: 6px 0; }
        .host { color: #F0F0F0; font-weight: 600; margin-top: 6px; font-size: 15px; word-break: break-all; }
        .url { color: $accentColor; word-break: break-all; margin-top: 14px; font-size: 13px; }
        .actions { display: flex; flex-direction: column; gap: 10px; margin-top: 28px; }
        button { padding: 13px 28px; border-radius: 24px; border: none; width: 100%;
                 font-size: 15px; font-weight: 600; cursor: pointer; font-family: inherit;
                 -webkit-tap-highlight-color: transparent; transition: background .12s ease; }
        button:active { transform: translateY(1px); }
        .btn-primary { background: #7B68EE; color: #FFFFFF; }
        .btn-primary:active { background: #6355D8; }
        .btn-secondary { background: transparent; color: #9B8FFF; box-shadow: inset 0 0 0 1px #2A2A2A; }
        .btn-secondary:active { background: #1A1A1A; }
    """.trimIndent()

    private fun buildErrorPage(context: android.content.Context, url: String, description: String): String {
        val safeUrl = escapeHtml(url)
        val safeDescription = escapeHtml(description)
        // "Try again" re-navigates to the original failing URL (Chrome parity),
        // but only when it is an http/https target. Two escaping layers protect
        // the attacker-influenced URL: escapeJsString makes it a safe single-
        // quoted JS-string literal, then escapeHtml makes the composed handler
        // safe inside the double-quoted HTML onclick attribute. The inline
        // handler is permitted by the page CSP's script-src 'unsafe-inline'.
        val retryButton = if (isReloadableUrl(url)) {
            val onclick = escapeHtml("location.href='${escapeJsString(url)}'")
            val retryLabel = escapeHtml(context.getString(com.helix.browser.R.string.error_page_try_again))
            "<button class=\"btn-primary\" onclick=\"$onclick\">$retryLabel</button>"
        } else ""
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'">
                <style>${interstitialStyle("#9B8FFF")}</style>
            </head>
            <body>
                <div class="card">
                    <div class="icon">⚠️</div>
                    <h1>${escapeHtml(context.getString(com.helix.browser.R.string.error_page_title))}</h1>
                    <p>$safeDescription</p>
                    <p class="url">$safeUrl</p>
                    <div class="actions">
                        $retryButton
                        <button class="btn-secondary" onclick="history.back()">${escapeHtml(context.getString(com.helix.browser.R.string.error_page_go_back))}</button>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildHttpsOnlyInterstitial(context: android.content.Context, url: String): String {
        val safeUrl = escapeHtml(url)
        return """
            <!DOCTYPE html>
            <html>
            <head><meta name="viewport" content="width=device-width, initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'">
            <style>${interstitialStyle("#9B8FFF")}</style></head>
            <body>
                <div class="card">
                    <div class="icon">🔒</div>
                    <h1>${escapeHtml(context.getString(com.helix.browser.R.string.https_only_blocked_title))}</h1>
                    <p>${escapeHtml(context.getString(com.helix.browser.R.string.https_only_blocked_message))}</p>
                    <p class="url">$safeUrl</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildSslErrorPage(context: android.content.Context, url: String, errorCode: Int): String {
        val safeUrl = escapeHtml(url)
        val host = try { Uri.parse(url).host } catch (e: Exception) { null }
        val safeHost = escapeHtml(host ?: url)
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'">
                <style>${interstitialStyle("#FF6B8A")}</style>
            </head>
            <body>
                <div class="card">
                    <div class="icon">🔒</div>
                    <h1>${escapeHtml(context.getString(com.helix.browser.R.string.ssl_error_page_title))}</h1>
                    <p class="host">$safeHost</p>
                    <p>${escapeHtml(context.getString(com.helix.browser.R.string.ssl_error_page_message, errorCode))}</p>
                    <p class="url">$safeUrl</p>
                    <div class="actions">
                        <button class="btn-primary" onclick="history.back()">${escapeHtml(context.getString(com.helix.browser.R.string.ssl_error_page_go_back))}</button>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}

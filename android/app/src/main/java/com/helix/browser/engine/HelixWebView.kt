package com.helix.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.core.view.NestedScrollingChild2
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import com.helix.browser.utils.Prefs

@SuppressLint("SetJavaScriptEnabled")
class HelixWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr), NestedScrollingChild2 {

    private val childHelper: NestedScrollingChildHelper = NestedScrollingChildHelper(this)
    private var lastY: Int = 0
    private val scrollOffset = IntArray(2)
    private val scrollConsumed = IntArray(2)
    private var nestedOffsetY: Int = 0

    private var incognito: Boolean = false

    // Hosts visited by this WebView while in incognito mode. clearIncognitoData()
    // uses this set to wipe ONLY the cookies/DOM-storage that belong to this
    // private session, instead of nuking every user's data process-wide.
    private val incognitoHosts: MutableSet<String> = mutableSetOf()

    init {
        isFocusable = true
        isFocusableInTouchMode = true

        setupSettings()
        // Default: accept third-party cookies on non-incognito tabs only.
        // Incognito state is applied later via setIncognitoMode().
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        isNestedScrollingEnabled = true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupSettings() {
        settings.apply {
            // Preference-driven at creation. The "JavaScript" Settings toggle
            // (default on) must actually disable scripts when turned off; do
            // not hardcode true. Re-applied later via applySettingsFromPrefs().
            javaScriptEnabled = Prefs.isJavaScriptEnabled(context)
            javaScriptCanOpenWindowsAutomatically = true
            // NOTE: setSupportMultipleWindows is deliberately left at its
            // default (false) in this wave. Enabling it makes onCreateWindow
            // fire, but routing window.open()/target=_blank into a real new
            // tab is NOT implemented yet (MainActivity does not wire
            // onCreateWindow). With multiple-windows enabled AND no host
            // routing, the platform would silently DROP every target=_blank
            // popup instead of loading it in-place — a worse regression than
            // the current behavior. Until tab routing is wired, links open
            // in-place. The orphan-WebView leak in HelixWebChromeClient is
            // fixed defensively so this is safe to flip on later (and on OEM
            // WebViews that invoke onCreateWindow regardless of this flag).
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            // Block mixed content by default — production browsers reject http on https pages.
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            loadsImagesAutomatically = true
            blockNetworkImage = false
            // Disallow file:// and content:// from web content to prevent XSS->local file read.
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            // Safe browsing (network requests verified against Google's blocklist where supported)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            useWideViewPort = true
            loadWithOverviewMode = true
            defaultTextEncodingName = "UTF-8"
            
            val isTablet = context.resources.getBoolean(com.helix.browser.R.bool.is_tablet)
            userAgentString = if (isTablet) DESKTOP_USER_AGENT else getDefaultUserAgent()
            if (isTablet) {
                useWideViewPort = true
                loadWithOverviewMode = true
            }

            // Set Accept-Language header based on current locale
            val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale
            }
            val languageTag = locale.toLanguageTag()
            // Some websites prefer comma separated list (e.g. en-US,en;q=0.9)
            // But just the current one is usually enough for redirection
        }
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isScrollbarFadingEnabled = true
        scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY

        // Apply web-content dark mode at creation: honor the user's explicit
        // preference, falling back to the current system night-mode state.
        applyNightModeFromPrefs()

        // Apply accessibility prefs (force-enable-zoom flags + minimum font
        // size) at creation. The viewport override is JS and is (re-)injected by
        // applySettingsFromPrefs() on settings changes / once content loads.
        applyAccessibilityFromPrefs()
    }

    // Re-applies web-content dark mode from the current preference / system
    // night-mode state. Safe to call again after the dark-mode setting changes.
    fun applyNightModeFromPrefs() {
        val systemNight = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        setNightMode(Prefs.isDarkMode(context) || systemNight)
    }

    // Re-reads the user-controllable engine preferences (JavaScript enabled,
    // Do-Not-Track, anti-fingerprinting, web-content dark mode) and applies
    // them to THIS WebView. Call this on each pooled WebView when settings
    // change so toggles take effect on already-open tabs without recreating
    // them.
    //
    // - JavaScript + dark mode are real WebSettings and apply immediately.
    // - DNT / anti-fingerprinting are realized as JS shims that the
    //   WebViewClient injects at page start/finish, so they normally only
    //   take effect on the NEXT navigation. To make a toggle visible on the
    //   page that is currently displayed we re-inject the privacy scripts
    //   here (only when scripts are allowed to run). Injection is idempotent:
    //   each shim redefines its property/prototype hook, so re-running it on
    //   an already-instrumented page is harmless.
    fun applySettingsFromPrefs() {
        val jsEnabled = Prefs.isJavaScriptEnabled(context)
        settings.javaScriptEnabled = jsEnabled
        applyNightModeFromPrefs()

        // Accessibility WebSettings (minimum font size + force-zoom flags) are
        // real settings and apply immediately, independent of JavaScript. Apply
        // them before the JS-gated early-return so a min-font change re-applies
        // to open tabs even when scripts are disabled.
        applyAccessibilityFromPrefs()

        // Privacy shims (DNT + anti-fingerprinting + the rest of the bundle)
        // are JavaScript; if scripts are disabled there is nothing to inject
        // and evaluateJavascript would be a no-op anyway.
        if (!jsEnabled) return

        // Re-assert user-scalable on the visible page when force-enable-zoom is
        // on. The WebSettings flags above re-enable the gesture; this defeats a
        // page-supplied user-scalable=no so pinch-zoom actually works.
        if (androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_FORCE_ZOOM, false)
        ) {
            applyForceZoomViewport()
        }
        val scripts = try {
            PrivacyManager.getPrivacyScripts(context)
        } catch (_: Exception) {
            // Never let a settings re-apply crash the engine; fail soft.
            ""
        }
        if (scripts.isNotBlank()) {
            try {
                evaluateJavascript(scripts, null)
            } catch (_: Exception) {
                // WebView may be mid-teardown; ignore.
            }
        }
    }

    fun setDesktopMode(enabled: Boolean) {
        settings.userAgentString = if (enabled) DESKTOP_USER_AGENT else getDefaultUserAgent()
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        reload()
    }

    fun setNightMode(enabled: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            settings.isAlgorithmicDarkeningAllowed = enabled
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            settings.forceDark = if (enabled) android.webkit.WebSettings.FORCE_DARK_ON else android.webkit.WebSettings.FORCE_DARK_OFF
        }
    }

    fun setIncognitoMode(enabled: Boolean) {
        incognito = enabled
        val cm = CookieManager.getInstance()
        if (enabled) {
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.databaseEnabled = false
            settings.domStorageEnabled = false
            settings.savePassword = false
            settings.saveFormData = false
            // Block third-party cookies on this WebView only; do not flip the
            // global accept flag (that would affect all tabs).
            cm.setAcceptThirdPartyCookies(this, false)
        } else {
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.databaseEnabled = true
            settings.domStorageEnabled = true
            cm.setAcceptThirdPartyCookies(this, true)
        }
        clearHistory()
    }

    // --- Accessibility: force-enable-zoom + minimum font size -----------------
    //
    // These two web-accessibility settings mirror Chrome's "Accessibility"
    // page. They are applied to THIS WebView's WebSettings at creation
    // (setupSettings) and re-applied on every settings change via
    // applySettingsFromPrefs(), so toggling them takes effect on already-open
    // tabs without recreating the WebView.
    //
    // - Force-enable-zoom: makes pinch-zoom work even on pages that ship
    //   user-scalable=no / maximum-scale=1. The WebSettings zoom flags only
    //   re-enable the gesture; they do NOT override a page's hostile viewport on
    //   every Android/WebView build, so when the toggle is on we ALSO inject a
    //   meta-viewport override (see applyForceZoomViewport) that rewrites the
    //   page's <meta name=viewport> to allow user scaling. The injection is
    //   gated on JavaScript being enabled (it is a no-op otherwise) and is
    //   idempotent. The WebSettings flags below are the durable part and apply
    //   regardless of JS.
    // - Minimum font size: floors the rendered font size so small-print pages
    //   stay legible. Realized via WebSettings.setMinimumFontSize /
    //   setMinimumLogicalFontSize (size in CSS px). This is independent of the
    //   per-tab "text zoom" (settings.textZoom) the overflow menu controls, so
    //   the two features compose instead of fighting.
    private fun applyAccessibilityFromPrefs() {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)

        // Minimum font size: clamp the stored value defensively in case a
        // future UI or a hand-edited pref writes something out of range.
        val minFont = sp.getInt(PREF_MIN_FONT_SIZE, MIN_FONT_OFF)
            .coerceIn(MIN_FONT_OFF, MIN_FONT_MAX)
        settings.minimumFontSize = minFont
        settings.minimumLogicalFontSize = minFont

        // Force-enable-zoom WebSettings flags. These re-enable the pinch gesture
        // and the built-in zoom machinery. Display zoom buttons stay hidden
        // (they overlap page chrome and look dated).
        val forceZoom = sp.getBoolean(PREF_FORCE_ZOOM, false)
        if (forceZoom) {
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        }
        // We never disable zoom here when the toggle is off: the engine's
        // baseline (setupSettings) already enables built-in zoom controls for
        // all pages, and removing that would be a regression. The toggle's job
        // is the extra step of defeating user-scalable=no, which is the viewport
        // override below.
    }

    // Injects a meta-viewport override that re-enables user scaling on the
    // CURRENTLY displayed page. Only meaningful when force-enable-zoom is on and
    // JavaScript is allowed to run; both are checked by the caller. Called again
    // on each navigation by the WebViewClient via the host is NOT required —
    // this is a best-effort re-apply for the visible page. Fails soft.
    private fun applyForceZoomViewport() {
        val js = """
            (function() {
              try {
                var v = document.querySelector('meta[name="viewport"]');
                if (!v) {
                  v = document.createElement('meta');
                  v.name = 'viewport';
                  (document.head || document.documentElement).appendChild(v);
                }
                v.setAttribute('content',
                  'width=device-width, initial-scale=1.0, ' +
                  'minimum-scale=1.0, maximum-scale=10.0, user-scalable=yes');
              } catch (e) {}
            })();
        """.trimIndent()
        try {
            evaluateJavascript(js, null)
        } catch (_: Exception) {
            // WebView may be mid-teardown; ignore.
        }
    }

    override fun loadUrl(url: String) {
        recordIncognitoHost(url)
        super.loadUrl(url)
    }

    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        recordIncognitoHost(url)
        super.loadUrl(url, additionalHttpHeaders)
    }

    // True when this WebView is a private/incognito session. Exposed so the
    // host can cheaply gate incognito-only bookkeeping.
    val isIncognito: Boolean
        get() = incognito

    // Records a visited host so clearIncognitoData() can scope its cookie /
    // DOM-storage wipe to this private session only.
    //
    // PUBLIC HOOK: the loadUrl() override below only sees URLs the host loads
    // explicitly — it does NOT see in-page link clicks, redirects or form
    // submits, which the platform navigates internally. Those navigations are
    // visible to HelixWebViewClient.onPageStarted(view, url). That file is
    // owned by another agent, so the host (MainActivity.createWebViewForTab)
    // must call webView.recordIncognitoHost(url) from the onPageStarted
    // callback to capture every committed host. Without that wiring, clicked
    // links in an incognito tab leak their cookies/localStorage into the
    // shared jar and are never cleared on tab close.
    fun recordIncognitoHost(url: String) {
        if (!incognito) return
        try {
            val host = android.net.Uri.parse(url).host
            if (!host.isNullOrBlank()) incognitoHosts.add(host)
        } catch (_: Exception) {
            // Ignore unparseable URLs; nothing to scope a cleanup to.
        }
    }

    /**
     * Clears data accumulated during this incognito session WITHOUT touching the
     * global (non-incognito) cookie/storage jar. We deliberately do NOT call the
     * process-global CookieManager.removeAllCookies(null) / WebStorage.deleteAllData(),
     * which would wipe every other user's data. Instead we expire only the cookies
     * and delete only the DOM/web-storage origins for hosts this session visited.
     */
    fun clearIncognitoData() {
        if (!incognito) return
        // Per-WebView state — safe, never global.
        clearCache(true)
        clearHistory()
        clearFormData()
        clearSslPreferences()

        val cookieManager = CookieManager.getInstance()
        val webStorage = WebStorage.getInstance()
        for (host in incognitoHosts) {
            for (scheme in arrayOf("https", "http")) {
                val origin = "$scheme://$host"
                // Expire each cookie set for this host by name.
                val cookieHeader = try { cookieManager.getCookie("$origin/") } catch (_: Exception) { null }
                cookieHeader?.split(";")?.forEach { pair ->
                    val name = pair.substringBefore('=').trim()
                    if (name.isNotEmpty()) {
                        cookieManager.setCookie(
                            "$origin/",
                            "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
                        )
                    }
                }
                // Delete DOM storage (localStorage / IndexedDB / WebSQL) for this origin.
                try { webStorage.deleteOrigin(origin) } catch (_: Exception) {}
            }
        }
        cookieManager.flush()
        incognitoHosts.clear()
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

    // --- NestedScrollingChild2 Implementation ---
    override fun setNestedScrollingEnabled(enabled: Boolean) { childHelper.isNestedScrollingEnabled = enabled }
    override fun isNestedScrollingEnabled(): Boolean = childHelper.isNestedScrollingEnabled
    override fun startNestedScroll(axes: Int, type: Int): Boolean = childHelper.startNestedScroll(axes, type)
    override fun stopNestedScroll(type: Int) { childHelper.stopNestedScroll(type) }
    override fun hasNestedScrollingParent(type: Int): Boolean = childHelper.hasNestedScrollingParent(type)
    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, offsetInWindow: IntArray?, type: Int): Boolean =
        childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type)
    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?, type: Int): Boolean =
        childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)

    override fun startNestedScroll(axes: Int): Boolean = startNestedScroll(axes, ViewCompat.TYPE_TOUCH)
    override fun stopNestedScroll() = stopNestedScroll(ViewCompat.TYPE_TOUCH)
    override fun hasNestedScrollingParent(): Boolean = hasNestedScrollingParent(ViewCompat.TYPE_TOUCH)
    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, offsetInWindow: IntArray?): Boolean =
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, ViewCompat.TYPE_TOUCH)
    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?): Boolean =
        dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, ViewCompat.TYPE_TOUCH)
    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean = childHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean = childHelper.dispatchNestedPreFling(velocityX, velocityY)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val event = MotionEvent.obtain(ev)
        val action = event.actionMasked

        if (action == MotionEvent.ACTION_DOWN) {
            nestedOffsetY = 0
        }
        val y = event.y.toInt()
        event.offsetLocation(0f, nestedOffsetY.toFloat())

        var returnValue = false
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                lastY = y
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
                returnValue = super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                var dy = lastY - y
                if (dispatchNestedPreScroll(0, dy, scrollConsumed, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                    dy -= scrollConsumed[1]
                    event.offsetLocation(0f, (-scrollOffset[1]).toFloat())
                    nestedOffsetY += scrollOffset[1]
                }
                lastY = y - scrollOffset[1]
                
                val oldOffset = computeVerticalScrollOffset()
                returnValue = super.onTouchEvent(event)
                val newOffset = computeVerticalScrollOffset()
                
                val consumed = newOffset - oldOffset
                val unconsumed = dy - consumed
                if (dispatchNestedScroll(0, consumed, 0, unconsumed, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                    nestedOffsetY += scrollOffset[1]
                    lastY -= scrollOffset[1]
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                returnValue = super.onTouchEvent(event)
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
            }
            else -> returnValue = super.onTouchEvent(event)
        }
        event.recycle()
        return returnValue
    }

    private fun getDefaultUserAgent(): String {
        return "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 HelixBrowser/1.0"
    }

    companion object {
        private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 HelixBrowser/1.0"

        // Accessibility pref keys. Shared with SettingsActivity (which writes
        // them) via these public constants so the read/write sides cannot drift.
        // Stored in the default SharedPreferences alongside the other engine
        // toggles. Prefs lives in another unit, so accessibility prefs are read
        // directly through PreferenceManager to stay self-contained.
        const val PREF_FORCE_ZOOM = "a11y_force_zoom"
        const val PREF_MIN_FONT_SIZE = "a11y_min_font_size"

        // Minimum-font-size scale, in CSS px, exposed as Off/Small/Medium/Large/
        // Huge in Settings. 0 (Off) lets WebView use its own default (~8px).
        // These are the persisted values for PREF_MIN_FONT_SIZE.
        const val MIN_FONT_OFF = 0
        const val MIN_FONT_SMALL = 12
        const val MIN_FONT_MEDIUM = 16
        const val MIN_FONT_LARGE = 20
        const val MIN_FONT_HUGE = 24
        // Upper clamp used when reading the pref defensively.
        private const val MIN_FONT_MAX = 72
    }
}

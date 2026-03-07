package com.helix.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.core.view.NestedScrollingChild2
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat

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

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        
        setupSettings()
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        
        isNestedScrollingEnabled = true
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


    // --- NestedScrollingChild2 Implementation ---

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        childHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return childHelper.isNestedScrollingEnabled
    }

    override fun startNestedScroll(axes: Int, type: Int): Boolean {
        return childHelper.startNestedScroll(axes, type)
    }

    override fun stopNestedScroll(type: Int) {
        childHelper.stopNestedScroll(type)
    }

    override fun hasNestedScrollingParent(type: Int): Boolean {
        return childHelper.hasNestedScrollingParent(type)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int,
        dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?, type: Int
    ): Boolean {
        return childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type)
    }

    override fun dispatchNestedPreScroll(
        dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?, type: Int
    ): Boolean {
        return childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
    }

    // Deprecated NestedScrollingChild methods (required for compatibility)
    override fun startNestedScroll(axes: Int): Boolean = startNestedScroll(axes, ViewCompat.TYPE_TOUCH)
    override fun stopNestedScroll() = stopNestedScroll(ViewCompat.TYPE_TOUCH)
    override fun hasNestedScrollingParent(): Boolean = hasNestedScrollingParent(ViewCompat.TYPE_TOUCH)
    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, offsetInWindow: IntArray?): Boolean =
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, ViewCompat.TYPE_TOUCH)
    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?): Boolean =
        dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, ViewCompat.TYPE_TOUCH)
    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean =
        childHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean =
        childHelper.dispatchNestedPreFling(velocityX, velocityY)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        var returnValue = false
        val event = MotionEvent.obtain(ev)
        val action = event.actionMasked

        if (action == MotionEvent.ACTION_DOWN) {
            nestedOffsetY = 0
        }
        val eventY = event.y.toInt()
        event.offsetLocation(0f, nestedOffsetY.toFloat())

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                returnValue = super.onTouchEvent(event)
                lastY = eventY
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
            }
            MotionEvent.ACTION_MOVE -> {
                var dy = lastY - eventY
                if (dispatchNestedPreScroll(0, dy, scrollConsumed, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                    dy -= scrollConsumed[1]
                    event.offsetLocation(0f, (-scrollOffset[1]).toFloat())
                    nestedOffsetY += scrollOffset[1]
                }
                returnValue = super.onTouchEvent(event)

                if (dispatchNestedScroll(0, scrollOffset[1], 0, dy, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                    event.offsetLocation(0f, scrollOffset[1].toFloat())
                    nestedOffsetY += scrollOffset[1]
                    lastY -= scrollOffset[1]
                }
                lastY = eventY - scrollOffset[1]
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                returnValue = super.onTouchEvent(event)
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
            }
            else -> {
                returnValue = super.onTouchEvent(event)
            }
        }
        event.recycle()
        return returnValue
    }

    private fun getDefaultUserAgent(): String {
        // Follows the same pattern as Brave, Edge, Samsung Internet:
        // keep Chrome/WebKit tokens so sites work normally,
        // then append our own brand at the end.
        return "Mozilla/5.0 (Linux; Android 14; Mobile) " +
               "AppleWebKit/537.36 (KHTML, like Gecko) " +
               "Chrome/124.0.0.0 Mobile Safari/537.36 " +
               "HelixBrowser/$APP_VERSION"
    }

    companion object {
        private const val APP_VERSION = "1.0"

        // Desktop mode: standard desktop Chrome UA + Helix brand
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36 " +
            "HelixBrowser/$APP_VERSION"
    }
}

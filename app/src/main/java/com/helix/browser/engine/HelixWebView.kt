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
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            loadsImagesAutomatically = true
            blockNetworkImage = false
            allowFileAccess = true
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            defaultTextEncodingName = "UTF-8"
            userAgentString = getDefaultUserAgent()
        }
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isScrollbarFadingEnabled = true
        scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
    }

    fun setDesktopMode(enabled: Boolean) {
        settings.userAgentString = if (enabled) DESKTOP_USER_AGENT else getDefaultUserAgent()
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
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
    }
}

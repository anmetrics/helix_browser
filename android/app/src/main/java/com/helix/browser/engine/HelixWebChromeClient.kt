package com.helix.browser.engine

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

class HelixWebChromeClient(
    private val onProgressChanged: (progress: Int) -> Unit,
    private val onTitleReceived: (title: String) -> Unit,
    private val onFaviconReceived: (favicon: Bitmap) -> Unit,
    private val onShowFileChooser: ((filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams) -> Boolean)? = null,
    private val onEnterFullscreen: ((customView: View, callback: CustomViewCallback) -> Unit)? = null,
    private val onExitFullscreen: (() -> Unit)? = null,
    private val onGeolocationPermission: ((origin: String, callback: GeolocationPermissions.Callback) -> Unit)? = null,
    private val onWebPermissionRequest: ((PermissionRequest) -> Unit)? = null,
    private val onCreateWindow: ((view: WebView) -> Boolean)? = null,
    private val isAdBlockEnabled: () -> Boolean = { false },
    private val isBlockPopupsEnabled: () -> Boolean = { false }
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        title?.let { onTitleReceived(it) }
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
        icon?.let { onFaviconReceived(it) }
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        return onShowFileChooser?.invoke(filePathCallback, fileChooserParams) ?: false
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        onEnterFullscreen?.invoke(view, callback)
    }

    override fun onHideCustomView() {
        onExitFullscreen?.invoke()
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        onGeolocationPermission?.invoke(origin, callback)
            ?: callback.invoke(origin, false, false)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        // Delegate to host (MainActivity) so the user is prompted and the
        // decision is persisted per-origin. Fallback denies if no host wired.
        val handler = onWebPermissionRequest
        if (handler != null) handler.invoke(request) else request.deny()
    }

    override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
        result.confirm()
        return true
    }

    override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
        result.cancel()
        return true
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        return true
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message
    ): Boolean {
        // Pop-up blocker: reject any window creation that wasn't a direct
        // result of a user gesture (click/tap). This matches Chrome's
        // default behavior — `window.open()` from a setTimeout or page
        // script is silently dropped, while a click-driven new tab is allowed.
        if (isBlockPopupsEnabled() && !isUserGesture) {
            return false
        }
        // Block ad-triggered popup tabs
        if (isAdBlockEnabled()) {
            // Non-user-gesture popups are almost always ads
            if (!isUserGesture) {
                return false
            }

            // Intercept the URL and check if it's an ad before allowing the new window
            val tempWebView = WebView(view.context).apply {
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: android.webkit.WebResourceRequest
                    ): Boolean {
                        val url = request.url.toString()
                        if (AdBlockEngine.isPopupAd(url) || AdBlockEngine.isAd(url)) {
                            // Block the ad URL — don't open new tab
                            view.destroy()
                            return true
                        }
                        return false
                    }
                }
            }
            val transport = resultMsg.obj as? WebView.WebViewTransport
            transport?.webView = tempWebView
            resultMsg.sendToTarget()
            return onCreateWindow?.invoke(view) ?: false
        }

        val newWebView = view.context?.let {
            WebView(it).apply {
                webViewClient = android.webkit.WebViewClient()
            }
        }
        val transport = resultMsg.obj as? WebView.WebViewTransport
        transport?.webView = newWebView
        resultMsg.sendToTarget()
        return onCreateWindow?.invoke(view) ?: false
    }
}

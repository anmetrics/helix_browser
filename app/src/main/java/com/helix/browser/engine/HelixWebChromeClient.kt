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
    private val onCreateWindow: ((view: WebView) -> Boolean)? = null
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
        // Auto-deny sensitive permissions unless explicitly allowed
        request.deny()
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

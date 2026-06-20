package com.helix.browser.engine

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.text.InputType
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText

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
        // This callback runs on the UI thread and the platform requires the
        // grant/deny decision on the UI thread too, so everything below stays
        // synchronous on this thread.
        val resources = request.resources ?: emptyArray()
        val wantsProtectedMedia =
            resources.contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
        // Camera/mic capture is owned by the host: it runs the OS runtime-
        // permission flow and the per-origin remembered prompt. A single
        // PermissionRequest resolves exactly once, so when a request MIXES
        // capture with protected media we MUST hand the whole thing to the
        // host. On "Allow" the host calls request.grant(request.resources),
        // which already contains the protected-media id, so DRM rides along.
        // Servicing it here would bypass the camera/mic prompt and weaken that
        // flow — explicitly disallowed.
        val wantsCapture =
            resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) ||
                resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)

        if (wantsProtectedMedia && !wantsCapture) {
            grantProtectedMediaOnly(request)
            return
        }

        // Everything else (camera/mic, with or without protected media, or any
        // other resource) goes to the host, which prompts and persists the
        // decision per-origin. Fallback denies if no host is wired.
        val handler = onWebPermissionRequest
        if (handler != null) handler.invoke(request) else request.deny()
    }

    // Services a protected-media-ONLY PermissionRequest (premium/streaming DRM,
    // e.g. Widevine). The host's permission handler denies any resource that is
    // not camera/mic, so DRM-only requests must be resolved here or premium
    // video silently breaks.
    //
    // Protected media is NOT an Android runtime permission and exposes no
    // capture/PII surface, so there is nothing to request from the OS and no
    // privacy capability to gate — Chrome on Android grants it for sites by
    // default. We deliberately do NOT show a per-origin prompt here: this
    // client receives no Context/Activity in onPermissionRequest (the callback
    // carries none and the constructor is owned by the host, which we must not
    // change), so a prompt could neither be shown nor persisted reliably.
    // Granting directly is the production-correct behavior and keeps everything
    // on the required UI thread.
    private fun grantProtectedMediaOnly(request: PermissionRequest) {
        // Grant only the protected-media resource we recognise rather than the
        // raw resources array, so nothing unexpected that slipped into the
        // request is ever granted. Guard the platform call so a malformed
        // request can never crash the renderer callback; on failure the request
        // is left for GC-time auto-deny by the framework after we attempt deny.
        val granted = runCatching {
            request.grant(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
        }.isSuccess
        if (!granted) {
            runCatching { request.deny() }
        }
    }

    override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
        // Resolve a live Activity for a window token; if the WebView is not
        // attached to an alive Activity, confirm immediately so the page's JS
        // is never left hanging and we avoid a WindowManager BadTokenException.
        val activity = view.context.findAliveActivity()
        if (activity == null) {
            result.confirm()
            return true
        }
        try {
            AlertDialog.Builder(activity)
                .setTitle(jsDialogTitle(activity, url))
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                // Dismissing without a button press must still resolve the result.
                .setOnCancelListener { result.confirm() }
                .setCancelable(true)
                .show()
        } catch (e: Exception) {
            result.confirm()
        }
        return true
    }

    override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
        val activity = view.context.findAliveActivity()
        if (activity == null) {
            result.cancel()
            return true
        }
        try {
            AlertDialog.Builder(activity)
                .setTitle(jsDialogTitle(activity, url))
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }
                .setCancelable(true)
                .show()
        } catch (e: Exception) {
            result.cancel()
        }
        return true
    }

    override fun onJsPrompt(
        view: WebView,
        url: String,
        message: String,
        defaultValue: String?,
        result: JsPromptResult
    ): Boolean {
        val activity = view.context.findAliveActivity()
        if (activity == null) {
            result.cancel()
            return true
        }
        val input = EditText(activity).apply {
            setText(defaultValue ?: "")
            setSelection(text.length)
            setPadding(60, 40, 60, 40)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        try {
            AlertDialog.Builder(activity)
                .setTitle(jsDialogTitle(activity, url))
                .setMessage(message)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm(input.text.toString()) }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }
                .setCancelable(true)
                .show()
        } catch (e: Exception) {
            result.cancel()
        }
        return true
    }

    /**
     * Title for JS dialogs. Shows the origin (host) of the page making the
     * request, like Chrome's "example.com says", falling back to the app name
     * when the host can't be derived.
     */
    private fun jsDialogTitle(context: Context, url: String): String {
        val host = runCatching { Uri.parse(url).host }.getOrNull()
        return if (!host.isNullOrBlank()) host
        else context.getString(com.helix.browser.R.string.app_name)
    }

    /**
     * Unwraps a (possibly themed) Context to its backing Activity, returning
     * null when there is no Activity or it is finishing/destroyed. Used to
     * guard dialog creation against a stale window token.
     */
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
        // We have NOT created or transported a WebView yet, so returning false
        // here leaks nothing: the platform simply discards the request.
        if (isBlockPopupsEnabled() && !isUserGesture) {
            return false
        }
        // Ad-triggered popups: non-user-gesture popups are almost always ads.
        // Again, nothing has been created/transported yet — safe to reject.
        if (isAdBlockEnabled() && !isUserGesture) {
            return false
        }

        // From here a real window was requested. This wave does NOT route
        // window.open()/target=_blank into a brand-new tab (that is deferred);
        // we hand the request to the host if it wired onCreateWindow, and
        // otherwise we MUST NOT strand a WebView.
        //
        // The platform contract: a WebView is only "consumed" by the popup
        // once we set it on the transport AND call resultMsg.sendToTarget().
        // If we are not going to keep that WebView alive in a real tab, we
        // must never sendToTarget() it — instead destroy() it and return
        // false so the page's window.open() resolves to null cleanly.
        val handler = onCreateWindow
        if (handler == null) {
            // No host routing in this wave: reject the popup without ever
            // building/transporting a WebView. Returning false (with no
            // sendToTarget) is the leak-free way to decline.
            return false
        }

        // Host is willing to take ownership. Build the popup WebView, hand it
        // over via the transport, and only sendToTarget() if the host accepts
        // ownership. If the host declines, tear the orphan down immediately so
        // we never leak a renderer/browser process.
        val ctx = view.context ?: return false
        val popupWebView = WebView(ctx).apply {
            webViewClient = android.webkit.WebViewClient()
        }
        val accepted = try {
            handler.invoke(popupWebView)
        } catch (e: Exception) {
            false
        }
        if (!accepted) {
            // Host did not adopt the WebView into a tab. Destroy it before it
            // can become an orphan (un-parented, unregistered, never freed).
            popupWebView.destroy()
            return false
        }
        val transport = resultMsg.obj as? WebView.WebViewTransport
        if (transport == null) {
            // Defensive: without a valid transport we cannot deliver the
            // popup. Avoid leaking the WebView we just built.
            popupWebView.destroy()
            return false
        }
        transport.webView = popupWebView
        resultMsg.sendToTarget()
        return true
    }
}

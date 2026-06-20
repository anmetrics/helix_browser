package com.helix.browser.ui

import android.annotation.SuppressLint
import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.helix.browser.HelixApp
import com.helix.browser.R
import com.helix.browser.databinding.ActivityMainBinding
import com.helix.browser.engine.HelixWebChromeClient
import com.helix.browser.engine.HelixWebView
import com.helix.browser.engine.HelixWebViewClient
import com.helix.browser.engine.PrivacyManager
import com.helix.browser.tabs.BrowserTab
import com.helix.browser.utils.Prefs
import com.helix.browser.utils.UrlUtils
import com.helix.browser.viewmodel.BrowserViewModel
import com.helix.browser.ui.adapter.DesktopTabAdapter
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.print.PrintManager
import android.text.Editable
import android.text.TextWatcher
import android.webkit.WebView.HitTestResult
import com.helix.browser.ui.adapter.SuggestionsAdapter
import java.io.File

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: BrowserViewModel by viewModels()
    private lateinit var tabManager: com.helix.browser.tabs.TabManager

    private var desktopTabAdapter: DesktopTabAdapter? = null
    private var suggestionsAdapter: SuggestionsAdapter? = null
    private var isTablet = false

    // Inline-omnibox-autocomplete state.
    // - lastUserTypedText: the user's own typed prefix WITHOUT any autocompletion
    //   we appended. Compared against the next afterTextChanged to decide whether
    //   the edit was forward typing (extends the prefix) vs a deletion/edit, so we
    //   never fight backspace.
    // - applyingInlineCompletion: re-entrancy guard. setText/setSelection from the
    //   completion path re-enters the address-bar TextWatcher; this flag makes that
    //   re-entry a no-op so we don't recurse or re-fetch suggestions.
    // - pendingInlineCompletion: the suffix last emitted by the ViewModel, applied
    //   on the next forward keystroke once the field text matches the prefix it was
    //   computed for (the suffix arrives slightly after the keystroke, debounced).
    private var lastUserTypedText: String = ""
    private var applyingInlineCompletion = false
    private var pendingInlineCompletion: String? = null

    // accessOrder=true makes iteration order least-recently-accessed first, so
    // the eldest entries are the best LRU eviction candidates. Capped to
    // MAX_LIVE_WEBVIEWS live WebViews (plus the foreground tab) to bound memory.
    private val webViewPool = LinkedHashMap<String, HelixWebView>(16, 0.75f, true)
    private var currentWebView: HelixWebView? = null

    // Id of the tab currently attached to the foreground. Written only on the main
    // thread (attachWebViewForTab); read from the off-UI-thread FindListener so it
    // can decide ownership WITHOUT dereferencing TabManager's main-thread-confined
    // mutable list (avoids a data race / IndexOutOfBounds on concurrent tab close).
    @Volatile
    private var foregroundTabId: String? = null

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: android.webkit.WebChromeClient.CustomViewCallback? = null

    private var headerHideRunnable: Runnable? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        fileChooserCallback?.onReceiveValue(uris.toTypedArray())
        fileChooserCallback = null
    }

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            matches?.firstOrNull()?.let { query ->
                val url = UrlUtils.formatUrl(query)
                loadUrl(url)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handle */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Opt into edge-to-edge so the system bars no longer eat layout space
        // automatically. Required for the OnApplyWindowInsetsListener below
        // to receive real status/nav bar insets and pad statusBarPadding
        // accordingly — otherwise the address bar slides under the notch.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            binding.statusBarSpace.layoutParams.height = systemBars.top
            binding.statusBarSpace.requestLayout()

            binding.statusBarPadding.layoutParams.height = systemBars.top
            binding.statusBarPadding.requestLayout()
            
            binding.toolbar.layoutParams.height = systemBars.top
            binding.toolbar.requestLayout()

            binding.navBarSpace.layoutParams.height = systemBars.bottom
            binding.navBarSpace.requestLayout()

            val isKeyboardOpen = ime.bottom > systemBars.bottom
            binding.bottomNavContainer.isVisible = !isKeyboardOpen

            if (isKeyboardOpen) {
                view.setPadding(0, 0, 0, ime.bottom)
            } else {
                view.setPadding(0, 0, 0, 0)
            }
            insets
        }

        tabManager = (application as HelixApp).tabManager
        isTablet = resources.getBoolean(R.bool.is_tablet)

        setupAddressBar()
        setupBottomNavigation()
        setupDesktopTabBar()
        setupObservers()
        setupSwipeRefresh()
        setupGestures()
        setupSuggestions()
        
        // Desktop mode is now per-tab (BrowserTab.isDesktopMode); the shared
        // LiveData only mirrors the current tab's flag for the menu checkmark and is
        // seeded when the first tab attaches. New tablet tabs default to desktop via
        // createNewTab().

        handleIntent(intent)

        // Restore tabs if enabled, otherwise create a new one
        if (tabManager.tabCount == 0) {
            val restored = if (PrivacyManager.isRestoreTabsEnabled(this)) {
                tabManager.restoreTabs(this)
            } else false
            if (restored && tabManager.tabCount > 0) {
                tabManager.currentTab?.let { switchToTab(it) }
            } else {
                createNewTab()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.findInPageBar.isVisible) {
                    hideFindInPage()
                } else if (currentWebView?.canGoBack() == true) {
                    currentWebView?.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val raw = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_WEB_SEARCH -> runCatching {
                intent.getStringExtra(android.app.SearchManager.QUERY)
            }.getOrNull()?.let { UrlUtils.buildSearchQuery(it, Prefs.getSearchEngine(this)) }
            else -> null
        } ?: return

        val url = sanitizeIncomingUrl(raw) ?: return
        if (tabManager.tabCount == 0) createNewTab(url) else loadUrl(url)
    }

    private fun sanitizeIncomingUrl(input: String): String? {
        // Cap length to defend against OOM via malicious deep links.
        if (input.length > MAX_INCOMING_URL_LENGTH) return null
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        // Only honor schemes the browser actually loads from EXTERNAL callers.
        // MainActivity is exported, so an attacker app can fire ACTION_VIEW: we must
        // reject data:, javascript:, file://, content://, intent:// etc. data: pages
        // are produced internally via loadDataWithBaseURL for chrome/interstitials
        // only and must NEVER be accepted from an intent (arbitrary HTML/JS in the
        // top frame -> phishing/JS execution in an opaque origin).
        val scheme = runCatching { Uri.parse(trimmed).scheme?.lowercase() }.getOrNull()
        return when (scheme) {
            "http", "https", "about" -> trimmed
            null -> UrlUtils.formatUrl(trimmed) // treat bare input as search/url
            else -> null
        }
    }

    private fun setupAddressBar() {
        binding.addressBar.apply {
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    // Entering edit mode: seed the pre-fill and reset inline-
                    // autocomplete state so the existing (selected-all) URL is never
                    // mistaken for a typed prefix to complete against.
                    pendingInlineCompletion = null
                    setText(viewModel.currentUrl.value)
                    lastUserTypedText = text?.toString() ?: ""
                    selectAll()
                    binding.btnCancelSearch.isVisible = true
                    binding.btnBookmark.isVisible = false
                    binding.btnRefresh.isVisible = false
                    // Mic (empty field) <-> clear-X (non-empty field), matching Chrome.
                    updateAddressBarEndIcons()
                    updateSiteIdentityIcon()
                    showKeyboard(this)
                } else {
                    // Leaving edit mode: discard inline-autocomplete state so it
                    // can't leak into the next editing session.
                    pendingInlineCompletion = null
                    lastUserTypedText = ""
                    updateAddressBarDisplay()
                    binding.btnCancelSearch.isVisible = false
                    binding.btnVoiceSearch.isVisible = false
                    binding.btnClearAddress.isVisible = false
                    binding.btnBookmark.isVisible = true
                    binding.btnRefresh.isVisible = true
                    binding.suggestionsRecyclerView.isVisible = false
                }
            }
            setOnClickListener {
                if (isFocused) showKeyboard(this)
            }
            setOnEditorActionListener { _, actionId, event ->
                // A hardware/Bluetooth Enter delivers the editor-action callback for
                // BOTH ACTION_DOWN and ACTION_UP; without filtering, loadUrl() would
                // run twice for one keypress. Soft-keyboard IME_ACTION_GO has a null
                // event, so we act on: IME action, or only the DOWN of a physical key.
                val isEnterKey = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER
                val isImeGo = actionId == EditorInfo.IME_ACTION_GO
                if (isImeGo || (isEnterKey && event!!.action == KeyEvent.ACTION_DOWN)) {
                    // Submit the full visible text INCLUDING any inline-autocomplete
                    // suffix (Chrome accepts the completion on Enter). Clear the
                    // inline-completion state so a stale suffix can't be reapplied.
                    val input = text.toString().trim()
                    pendingInlineCompletion = null
                    lastUserTypedText = ""
                    if (input.isNotEmpty()) {
                        val url = UrlUtils.formatUrl(input)
                        loadUrl(url)
                        clearFocus()
                        hideKeyboard()
                    }
                    true
                } else {
                    // Consume the matching key-up so the system does not also treat it
                    // as a default action, but never re-load on it.
                    isEnterKey
                }
            }
        }
        binding.btnCancelSearch.setOnClickListener {
            binding.addressBar.clearFocus()
            hideKeyboard()
        }
        binding.siteIdentityContainer.setOnClickListener {
            if (binding.addressBar.isFocused) {
                // Already in search mode; just keep it focused
                showKeyboard(binding.addressBar)
            } else {
                showPageInfoSheet()
            }
        }
        binding.notSecureChip.setOnClickListener { showPageInfoSheet() }
        binding.btnRefresh.setOnClickListener {
            if (viewModel.isLoading.value == true) {
                currentWebView?.stopLoading()
            } else {
                currentWebView?.reload()
            }
        }
        // Voice search
        binding.btnVoiceSearch.setOnClickListener {
            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, getString(R.string.search_hint))
            }
            try {
                voiceSearchLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.voice_search_unavailable), Toast.LENGTH_SHORT).show()
            }
        }
        // One-tap clear (Chrome-style). Keeps focus and reveals the mic + the
        // "Paste and go" chip (if the clipboard holds a URL) for a now-empty field.
        binding.btnClearAddress.setOnClickListener {
            binding.addressBar.setText("")
            binding.addressBar.requestFocus()
            showKeyboard(binding.addressBar)
        }
        // Paste-and-go: when the field is focused & empty and the clipboard holds
        // a usable URL/query, tapping the chip loads it directly.
        binding.btnPasteAndGo.setOnClickListener {
            val clip = clipboardText()?.trim()
            if (!clip.isNullOrEmpty()) {
                loadUrl(UrlUtils.formatUrl(clip))
                binding.addressBar.clearFocus()
                hideKeyboard()
            }
        }
    }

    // Show the clear (X) button when the focused address bar has text, otherwise
    // the mic (when speech is available). Also surfaces a "Paste and go" chip when
    // the field is empty and the clipboard holds non-blank text.
    private fun updateAddressBarEndIcons() {
        val focused = binding.addressBar.isFocused
        val empty = binding.addressBar.text.isNullOrEmpty()
        binding.btnClearAddress.isVisible = focused && !empty
        binding.btnVoiceSearch.isVisible = focused && empty &&
            android.speech.SpeechRecognizer.isRecognitionAvailable(this)
        val hasClip = focused && empty && !clipboardText().isNullOrBlank()
        binding.btnPasteAndGo.isVisible = hasClip
    }

    // Best-effort read of plain text from the primary clipboard. Returns null on
    // any failure (no clip, security exception on locked clipboards, etc.).
    private fun clipboardText(): String? = runCatching {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        cm?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(this)?.toString()
    }.getOrNull()

    private fun setupBottomNavigation() {
        binding.btnBack.setOnClickListener { animateClick(it); currentWebView?.goBack() }
        binding.btnForward.setOnClickListener { animateClick(it); currentWebView?.goForward() }
        binding.btnHome.setOnClickListener {
            animateClick(it); loadUrl(Prefs.getHomepage(this))
        }

        if (isTablet) {
            binding.btnTabs.isVisible = false
            // Hide the borderless container if possible, or just the whole button
            // To properly hide the frame layout since btnTabs is the content
            binding.btnTabs.layoutParams = android.widget.LinearLayout.LayoutParams(0, 0, 0f)
            binding.btnTabs.requestLayout()
        } else {
            binding.btnTabs.setOnClickListener {
                currentWebView?.let { webView -> captureTabThumbnail(webView) }
                val intent = Intent(this, TabSwitcherActivity::class.java)
                tabSwitcherLauncher.launch(intent)
                overridePendingTransitionCompat(R.anim.slide_up, R.anim.fade_out)
            }
        }
        binding.btnMenu.setOnClickListener { showMoreOptionsMenu() }
    }

    /**
     * Capture a downscaled snapshot of the current WebView for the tab switcher.
     *
     * We allocate the destination bitmap already downscaled (1/4) and scale the
     * Canvas, so we never hold a full-resolution ARGB_8888 buffer. The WebView
     * is hardware-accelerated, so a plain software Canvas.draw() can render
     * blank/black; we temporarily force LAYER_TYPE_SOFTWARE for the duration of
     * the draw so the snapshot is correct. Skips capture entirely when memory is
     * low or the view is not laid out.
     */
    private fun captureTabThumbnail(webView: HelixWebView) {
        val tab = tabManager.currentTab ?: return
        if (webView.width <= 0 || webView.height <= 0) return
        // Skip under memory pressure so capturing a thumbnail never tips us over.
        val mi = android.app.ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)
            ?.getMemoryInfo(mi)
        if (mi.lowMemory) return

        val tabId = tab.id
        val w = webView.width
        val h = webView.height

        // API 26+: copy the WebView's surface off the UI thread with PixelCopy so we
        // never force a software layer + synchronous full-tree draw on the main thread
        // (which hitches when the tabs button is tapped on a complex page). We capture
        // at full size into a hardware-sourced bitmap, then downscale on the callback.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val loc = IntArray(2)
            webView.getLocationInWindow(loc)
            val src = android.graphics.Rect(loc[0], loc[1], loc[0] + w, loc[1] + h)
            val full = try {
                android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            } catch (t: Throwable) {
                android.util.Log.w("MainActivity", "captureTabThumbnail alloc failed", t); return
            }
            try {
                android.view.PixelCopy.request(window, src, full, { result ->
                    if (result == android.view.PixelCopy.SUCCESS) {
                        val scaled = try {
                            android.graphics.Bitmap.createScaledBitmap(
                                full, (w * 0.25f).toInt().coerceAtLeast(1),
                                (h * 0.25f).toInt().coerceAtLeast(1), true
                            )
                        } catch (t: Throwable) { null }
                        full.recycle()
                        if (scaled != null) {
                            // The tab may have closed during the async copy; re-resolve.
                            val t = tabManager.findTab(tabId)
                            if (t != null && !isDestroyed) {
                                t.thumbnail?.takeIf { !it.isRecycled && it !== scaled }?.recycle()
                                t.thumbnail = scaled
                            } else {
                                scaled.recycle()
                            }
                        }
                    } else {
                        full.recycle()
                    }
                }, binding.root.handler ?: android.os.Handler(mainLooper))
            } catch (t: Throwable) {
                full.recycle()
                android.util.Log.w("MainActivity", "captureTabThumbnail PixelCopy failed", t)
            }
            return
        }

        // Pre-O fallback: software-layer draw (synchronous, but only on old devices).
        val scale = 0.25f
        val sw = (w * scale).toInt().coerceAtLeast(1)
        val sh = (h * scale).toInt().coerceAtLeast(1)
        val originalLayerType = webView.layerType
        try {
            val bitmap = android.graphics.Bitmap.createBitmap(sw, sh, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.scale(scale, scale)
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            webView.draw(canvas)
            tab.thumbnail?.takeIf { !it.isRecycled && it !== bitmap }?.recycle()
            tab.thumbnail = bitmap
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "captureTabThumbnail failed", e)
        } finally {
            webView.setLayerType(originalLayerType, null)
        }
    }

    private fun setupDesktopTabBar() {
        val desktopTabBar = findViewById<View>(R.id.desktopTabBar)
        if (!isTablet) {
            desktopTabBar?.isVisible = false
            return
        }

        desktopTabBar?.isVisible = true
        val rvDesktopTabs = findViewById<RecyclerView>(R.id.rvDesktopTabs)
        val btnNewDesktopTab = findViewById<ImageButton>(R.id.btnNewDesktopTab)
        
        btnNewDesktopTab?.setOnClickListener { createNewTab() }

        desktopTabAdapter = DesktopTabAdapter(
            onTabSelected = { tab -> switchToTab(tab) },
            onTabClosed = { tab ->
                val isClosingCurrent = tab.id == tabManager.currentTab?.id
                tabManager.closeTab(tab.id)
                // If closing the active tab, TabManager auto-switches to previous
                // We just need to attach the new current tab's webview
                if (isClosingCurrent) {
                    tabManager.currentTab?.let { switchToTab(it) } ?: run {
                        // All tabs closed
                        webViewPool.remove(tab.id)?.let { safelyDestroyWebView(it) }
                        binding.webViewContainer.removeAllViews()
                        currentWebView = null
                        createNewTab()
                    }
                } else {
                    // Just clean up the webview
                    webViewPool.remove(tab.id)?.let { safelyDestroyWebView(it) }
                }
            }
        )
        
        rvDesktopTabs?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = desktopTabAdapter
            itemAnimator = null // Prevent flashing on updates
        }

        btnNewDesktopTab?.setOnClickListener { createNewTab() }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.isVisible = loading
            binding.btnRefresh.setImageResource(if (loading) R.drawable.ic_close else R.drawable.ic_refresh)
            if (!loading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
        viewModel.loadingProgress.observe(this) { progress ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                binding.progressBar.setProgress(progress, true)
            } else {
                binding.progressBar.progress = progress
            }
        }
        viewModel.currentTitle.observe(this) { title ->
            tabManager.updateCurrentTab(title = title)
            updateTabCountBadge()
        }
        viewModel.currentUrl.observe(this) { url ->
            tabManager.updateCurrentTab(url = url)
            updateAddressBarDisplay()
        }
        viewModel.isBookmarked.observe(this) { bookmarked ->
            binding.btnBookmark.setImageResource(if (bookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
        }
        viewModel.canGoBack.observe(this) { can ->
            binding.btnBack.alpha = if (can) 1f else DISABLED_NAV_ALPHA
        }
        viewModel.canGoForward.observe(this) { can ->
            binding.btnForward.alpha = if (can) 1f else DISABLED_NAV_ALPHA
        }
        viewModel.showFindInPage.observe(this) { show ->
            binding.findInPageBar.isVisible = show
            if (show) {
                binding.findInPageInput.requestFocus()
                // Raise the keyboard so the user can type immediately (Chrome behavior),
                // rather than tapping into an empty bar with no IME.
                showKeyboard(binding.findInPageInput)
            }
        }

        if (isTablet) {
            tabManager.tabsLiveData.observe(this) { tabs ->
                desktopTabAdapter?.submitList(tabs) {
                    // Scroll to end when new tab is added
                    if (tabs.isNotEmpty()) {
                        val rv = findViewById<RecyclerView>(R.id.rvDesktopTabs)
                        rv?.smoothScrollToPosition(tabManager.currentIndex)
                    }
                }
            }
            tabManager.currentTabLiveData.observe(this) { tab ->
                desktopTabAdapter?.currentTabId = tab?.id
            }
        }

        binding.btnFindNext.setOnClickListener { currentWebView?.findNext(true) }
        binding.btnFindPrev.setOnClickListener { currentWebView?.findNext(false) }
        binding.btnCloseFindInPage.setOnClickListener { hideFindInPage() }
        binding.findInPageInput.setOnEditorActionListener { _, actionId, event ->
            // Only advance on the search/IME action or a physical Enter DOWN. Live
            // find (afterTextChanged) already issues the initial query, so Enter
            // should move to the NEXT match like a flagship browser instead of
            // re-running findAllAsync. Don't blanket-consume other actions.
            val isEnterDown = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnterDown) {
                currentWebView?.findNext(true)
                true
            } else {
                false
            }
        }
        // Live find: re-query as the user types so the counter updates in real time.
        binding.findInPageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                if (q.isEmpty()) {
                    currentWebView?.clearMatches()
                    binding.tvFindCount.text = ""
                    binding.tvFindCount.contentDescription = null
                } else {
                    currentWebView?.findAllAsync(q)
                }
            }
        })
    }

    fun createNewTab(url: String = "", isIncognito: Boolean = false) {
        val tab = tabManager.addTab(isIncognito, url)
        // Seed the new tab's desktop preference from the global default (the
        // Settings "Desktop site" pref, OR'd with the tablet default) before the
        // WebView is created, so the first load uses the right user-agent. After
        // this, desktop mode is per-tab and the menu toggle only affects one tab.
        if (defaultDesktopMode()) tab.isDesktopMode = true
        switchToTab(tab)
    }

    private fun switchToTab(tab: BrowserTab) {
        // Snapshot the OUTGOING tab before we detach its WebView, so both phone and
        // tablet flows keep fresh background-tab thumbnails (the phone tabs button
        // also captures, but tablet switches go only through here).
        currentWebView?.let { outgoing ->
            if (tabManager.currentTab?.id != tab.id) captureTabThumbnail(outgoing)
        }
        tabManager.switchToTab(tab.id)
        attachWebViewForTab(tab)
        updateTabCountBadge()

        viewModel.isIncognito.value = tab.isIncognito
        binding.incognitoIndicator.isVisible = tab.isIncognito
        binding.root.setBackgroundColor(getColor(if (tab.isIncognito) R.color.incognito_background else R.color.background))
    }

    // Tear down any active HTML5 fullscreen (<video>) view. Must run before we
    // detach/destroy the WebView that owns it, otherwise the chrome client keeps
    // believing it is fullscreen, the custom view + callback leak, and the system
    // UI stays hidden with no matching show. Safe to call when not in fullscreen.
    private fun exitFullscreenIfActive() {
        val view = fullscreenView ?: return
        binding.webViewContainer.removeView(view)
        runCatching { fullscreenCallback?.onCustomViewHidden() }
        fullscreenView = null
        fullscreenCallback = null
        showSystemUI()
    }

    private fun attachWebViewForTab(tab: BrowserTab) {
        // A page in fullscreen video that is being swapped out must exit fullscreen
        // first so its custom view/callback are released and system UI restored.
        exitFullscreenIfActive()
        binding.webViewContainer.removeAllViews()
        // Switching back to a tab clears its suspended flag; recreate from URL.
        tab.isSuspended = false
        val webView = webViewPool.getOrPut(tab.id) { createWebViewForTab(tab) }
        currentWebView = webView
        // Publish foreground tab id for the off-UI-thread FindListener (read-only).
        foregroundTabId = tab.id
        // Bound the pool: destroy least-recently-used non-current WebViews so a
        // large number of tabs cannot OOM. Recreation happens lazily from
        // tab.url the next time the tab is attached.
        enforceWebViewPoolCap(tab.id)
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        binding.webViewContainer.addView(webView, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        webView.requestFocus()
        viewModel.updateNavState(webView.canGoBack(), webView.canGoForward())
        viewModel.currentUrl.value = webView.url ?: tab.url
        viewModel.currentTitle.value = webView.title ?: tab.title
        // Seed loading UI from the attached WebView. An already-loaded tab fires no
        // onPageStarted/Finished on attach, so without this the previous tab's stale
        // isLoading/progress (and the swipe-refresh spinner) would persist, leaving a
        // stuck progress bar + stop icon on an idle page.
        val p = webView.progress
        val loading = p in 1..99
        viewModel.loadingProgress.value = p
        viewModel.isLoading.value = loading
        binding.swipeRefreshLayout.isRefreshing = false
        updateAddressBarDisplay()

        // Per-tab desktop mode. The attached WebView already carries the correct
        // user-agent for this tab (applied at creation in createWebViewForTab, or
        // by the menu toggle while it was foreground), so we do NOT re-apply here
        // (that would force a needless reload). We only mirror the tab's flag into
        // the shared LiveData so the overflow menu's "Desktop site" checkmark
        // reflects the tab the user is now looking at.
        viewModel.isDesktopMode.value = tab.isDesktopMode

        headerHideRunnable?.let { binding.root.removeCallbacks(it) }
        setToolbarScrollable(false)
        if (webView.progress >= 100 && webView.url != null && webView.url != "about:blank") {
            headerHideRunnable = Runnable { setToolbarScrollable(true) }.also {
                binding.root.postDelayed(it, 5000)
            }
        }
    }

    private fun createWebViewForTab(tab: BrowserTab): HelixWebView {
        val webView = HelixWebView(this)
        // Capture only the tab id, never the BrowserTab reference. The tab
        // may be removed from TabManager mid-load; we re-resolve via id and
        // bail if it is gone, so callbacks cannot mutate a closed tab.
        val tabId = tab.id
        fun resolveTab(): BrowserTab? = tabManager.findTab(tabId)
        fun isCurrent(): Boolean = tabManager.currentTab?.id == tabId
        webView.webViewClient = HelixWebViewClient(
            onPageStarted = { url, _ ->
                if (isCurrent()) runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    viewModel.onPageStarted(url)
                    updateNavButtons()
                    headerHideRunnable?.let { binding.root.removeCallbacks(it) }
                    setToolbarScrollable(false)
                }
            },
            onPageFinished = { url ->
                val t = resolveTab() ?: return@HelixWebViewClient  // tab closed; skip
                t.url = url
                t.title = webView.title ?: url
                if (isCurrent()) runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    viewModel.onPageFinished(url, webView.title ?: "")
                    updateNavButtons()
                    // Apply remembered per-site zoom (or the global default) now that
                    // the page is committed and currentUrl reflects the final URL.
                    applyZoomForFinishedPage(webView)
                    headerHideRunnable?.let { binding.root.removeCallbacks(it) }
                    if (url != "about:blank" && url.isNotEmpty()) {
                        headerHideRunnable = Runnable { setToolbarScrollable(true) }.also {
                            binding.root.postDelayed(it, 5000)
                        }
                    } else if (url == "about:blank" && webView.url == "about:blank") {
                        // The internal start page just rendered; populate the
                        // most-visited tiles from local history (off the main
                        // thread). Falls back silently to the hardcoded shortcuts
                        // already in the HTML when history is empty / disabled.
                        loadTopSiteTiles(webView)
                    }
                }
            },
            onPageError = { _, _, _ ->
                if (isCurrent()) runOnUiThread {
                    if (!isFinishing && !isDestroyed) viewModel.isLoading.value = false
                }
            },
            isAdBlockEnabled = { Prefs.isAdBlockEnabled(this) },
            isTrackerBlockEnabled = { PrivacyManager.isBlockTrackersEnabled(this) },
            isHttpsUpgradeEnabled = { PrivacyManager.isHttpsUpgradeEnabled(this) },
            isHttpsOnlyModeEnabled = { PrivacyManager.isHttpsOnlyModeEnabled(this) },
            getPrivacyScripts = { PrivacyManager.getPrivacyScripts(this) },
            onTrackerBlocked = { PrivacyManager.incrementTrackersBlocked(this) }
        )
        // Apply third-party cookie policy
        PrivacyManager.applyThirdPartyCookiePolicy(this, webView)
        webView.webChromeClient = HelixWebChromeClient(
            onProgressChanged = { progress ->
                if (isCurrent()) runOnUiThread {
                    if (!isFinishing && !isDestroyed) viewModel.onProgressChanged(progress)
                }
            },
            onTitleReceived = { title ->
                val t = resolveTab() ?: return@HelixWebChromeClient
                t.title = title
                if (isCurrent()) runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    viewModel.currentTitle.value = title
                    desktopTabAdapter?.notifyItemChanged(tabManager.currentIndex)
                }
            },
            onFaviconReceived = { favicon ->
                val t = resolveTab() ?: return@HelixWebChromeClient
                // Recycle the previous favicon before replacing to avoid bitmap leak.
                t.favicon?.takeIf { it !== favicon && !it.isRecycled }?.recycle()
                t.favicon = favicon
                if (isCurrent()) runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    desktopTabAdapter?.notifyItemChanged(tabManager.currentIndex)
                    updateSiteIdentityIcon()
                }
            },
            onShowFileChooser = { callback, params ->
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                fileChooserLauncher.launch(resolveFileChooserMimeFilter(params))
                true
            },
            onEnterFullscreen = { view, callback ->
                fullscreenView = view
                fullscreenCallback = callback
                binding.webViewContainer.addView(view)
                hideSystemUI()
            },
            onExitFullscreen = {
                exitFullscreenIfActive()
            },
            onGeolocationPermission = { origin, callback -> requestGeolocationPermission(origin, callback) },
            onWebPermissionRequest = { request -> handleWebPermissionRequest(request) },
            isAdBlockEnabled = { Prefs.isAdBlockEnabled(this) },
            isBlockPopupsEnabled = { PrivacyManager.isBlockPopupsEnabled(this) }
        )
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength -> downloadFile(url, userAgent, contentDisposition, mimeType, contentLength) }
        webView.setFindListener { active, total, doneCounting ->
            // FindListener fires off the UI thread (on findAllAsync AND findNext);
            // only update if this WebView is the visible one. We compare against the
            // @Volatile foregroundTabId instead of tabManager.currentTab so we never
            // touch the main-thread-confined tab list from this background callback
            // (avoids a data race). Wait for the final (doneCounting) callback so the
            // total is stable instead of flickering through intermediate counts.
            if (!doneCounting) return@setFindListener
            if (foregroundTabId == tabId) runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                updateFindCount(active, total)
            }
        }
        setupWebViewContextMenu(webView)
        // Bridge so the start-page search box can focus the real omnibox.
        // The bridge only acts when the WebView is showing the internal
        // new-tab page (about:blank), so it cannot be abused by real sites.
        webView.addJavascriptInterface(HelixHomeBridge(webView), "HelixHome")
        if (tab.isIncognito) webView.setIncognitoMode(true)
        // Apply this tab's OWN desktop-site preference before the first load so the
        // page is fetched with the right user-agent in one pass. setDesktopMode()
        // ends with reload(), which is a no-op on a brand-new WebView with nothing
        // loaded yet, so this does not double-load.
        if (tab.isDesktopMode) webView.setDesktopMode(true)
        if (tab.url.isNotEmpty()) webView.loadUrl(tab.url)
        else {
            // A brand-new incognito tab gets a privacy-explainer start page instead
            // of the most-visited grid (which would surface nothing in incognito and
            // is the wrong affordance). Reuses the same start-page chrome/builder.
            val html = if (tab.isIncognito) buildIncognitoNewTabHtml() else buildNewTabHtml()
            webView.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)
        }
        return webView
    }

    private fun setToolbarScrollable(scrollable: Boolean) {
        val params = binding.collapsingLayout.layoutParams as com.google.android.material.appbar.AppBarLayout.LayoutParams
        if (scrollable) {
            params.scrollFlags = com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED or
                    com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
        } else {
            params.scrollFlags = 0
            binding.appBarLayout.setExpanded(true, true)
        }
        binding.collapsingLayout.layoutParams = params
    }

    fun loadUrl(url: String) {
        val tab = tabManager.currentTab ?: run { createNewTab(url); return }
        val webView = webViewPool[tab.id] ?: run { createNewTab(url); return }
        webView.loadUrl(url)
        hideKeyboard()
    }

    private fun buildNewTabHtml(): String = """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><title>${getString(R.string.new_tab)}</title>
<style>
*{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent;}
html,body{height:100%;}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
  background:
    radial-gradient(115% 75% at 50% -8%, rgba(139,139,255,.20), transparent 58%),
    radial-gradient(95% 55% at 50% 2%, rgba(255,126,179,.10), transparent 55%),
    #0B0B14;
  min-height:100%;display:flex;flex-direction:column;align-items:center;
  padding:14vh 22px 40px;color:#EDEDF5;-webkit-user-select:none;overflow-x:hidden;}
.logo{font-size:48px;font-weight:800;letter-spacing:-2px;line-height:1;}
.logo span{background:linear-gradient(135deg,#8B8BFF,#FF7EB3);-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent;}
.tagline{font-size:12px;color:#8B8B9E;margin-top:12px;margin-bottom:36px;font-weight:600;letter-spacing:2px;text-transform:uppercase;}
.search-box{width:100%;max-width:440px;background:rgba(255,255,255,.045);border:1px solid rgba(255,255,255,.07);border-radius:18px;padding:16px 20px;display:flex;align-items:center;gap:14px;margin-bottom:42px;box-shadow:0 10px 34px rgba(0,0,0,.38);transition:transform .15s,border-color .2s,box-shadow .2s;}
.search-box:active{transform:scale(.985);border-color:rgba(139,139,255,.55);box-shadow:0 0 0 3px rgba(139,139,255,.18),0 10px 34px rgba(0,0,0,.42);}
.search-box svg{width:20px;height:20px;fill:#8B8B9E;flex-shrink:0;}
.search-box span{color:#9A9AB0;font-size:15px;font-weight:400;}
.shortcuts{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;width:100%;max-width:440px;}
.shortcut{display:flex;flex-direction:column;align-items:center;gap:9px;padding:14px 2px;border-radius:18px;text-decoration:none;color:#C9C9D6;font-size:11.5px;font-weight:500;transition:background .15s,transform .12s;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:100%;}
.shortcut:active{background:rgba(255,255,255,.05);transform:scale(.93);}
.shortcut-icon{width:56px;height:56px;border-radius:19px;background:linear-gradient(160deg,#22222F,#181821);border:1px solid rgba(255,255,255,.07);box-shadow:0 5px 16px rgba(0,0,0,.32);display:flex;align-items:center;justify-content:center;font-size:23px;font-weight:700;}
.s-google{color:#4285F4;}.s-yt{color:#FF3B30;}.s-gh{color:#F0F0F0;}.s-fb{color:#4596FF;}
.s-x{color:#F0F0F0;}.s-reddit{color:#FF5414;}.s-wiki{color:#F0F0F0;}.s-netflix{color:#FF2A38;}
</style></head>
<body>
<div class="logo"><span>H</span>elix</div>
<div class="tagline">${getString(R.string.fast_secure_private)}</div>
<div class="search-box" onclick="if(window.HelixHome)window.HelixHome.focusAddressBar();">
<svg viewBox="0 0 24 24"><path d="M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>
<span>${getString(R.string.search_or_type_url)}</span>
</div>
<div class="shortcuts" id="helixTiles">
<a class="shortcut" href="https://google.com"><div class="shortcut-icon s-google">G</div>Google</a>
<a class="shortcut" href="https://youtube.com"><div class="shortcut-icon s-yt">&#9654;</div>YouTube</a>
<a class="shortcut" href="https://github.com"><div class="shortcut-icon s-gh">&#10023;</div>GitHub</a>
<a class="shortcut" href="https://facebook.com"><div class="shortcut-icon s-fb">f</div>Facebook</a>
<a class="shortcut" href="https://twitter.com"><div class="shortcut-icon s-x">&#120143;</div>X</a>
<a class="shortcut" href="https://reddit.com"><div class="shortcut-icon s-reddit">r/</div>Reddit</a>
<a class="shortcut" href="https://wikipedia.org"><div class="shortcut-icon s-wiki">W</div>Wikipedia</a>
<a class="shortcut" href="https://netflix.com"><div class="shortcut-icon s-netflix">N</div>Netflix</a>
</div>
<script>
// Replace the hardcoded shortcut grid with the user's most-visited tiles.
// Receives a parsed array of {u:url, l:label, a:avatarLetter, c:hexColor}.
// We build the DOM with textContent / setAttribute only (never innerHTML for
// the dynamic strings), so a malicious page title in history can't inject markup.
function helixRenderTiles(items){
  try{
    if(!items || !items.length) return; // keep the fallback grid
    var grid = document.getElementById('helixTiles');
    if(!grid) return;
    grid.textContent = '';
    for(var i=0;i<items.length;i++){
      var it = items[i];
      if(!it || !it.u) continue;
      var a = document.createElement('a');
      a.className = 'shortcut';
      a.setAttribute('href','#');
      (function(url){
        a.addEventListener('click', function(e){
          e.preventDefault();
          if(window.HelixHome && window.HelixHome.openUrl) window.HelixHome.openUrl(url);
        });
      })(it.u);
      var icon = document.createElement('div');
      icon.className = 'shortcut-icon';
      icon.textContent = it.a || '?';
      if(it.c) icon.style.color = it.c;
      a.appendChild(icon);
      a.appendChild(document.createTextNode(it.l || it.u));
      grid.appendChild(a);
    }
  }catch(e){}
}
</script>
</body></html>""".trimIndent()

    // Incognito start page: a privacy explainer ("You've gone incognito") with what
    // is and isn't saved, in place of the most-visited grid. No remote requests; all
    // copy comes from string resources. Mirrors buildNewTabHtml's self-contained,
    // inline-styled, no-network shape and reuses the same start-page search box +
    // HelixHome bridge so tapping it focuses the real omnibox. The base URL stays
    // about:blank so the existing internal-start-page detection (and the bridge's
    // start-page guard) treats it identically to the normal new-tab page.
    private fun buildIncognitoNewTabHtml(): String {
        // Escape every user-visible string before embedding it in HTML so a
        // translated string containing < & " cannot break the markup (defense in
        // depth; these are our own resources, never page/remote content).
        fun esc(s: String): String = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        val title = esc(getString(R.string.incognito_ntp_title))
        val subtitle = esc(getString(R.string.incognito_ntp_subtitle))
        val savedHeading = esc(getString(R.string.incognito_ntp_not_saved_heading))
        val notSaved1 = esc(getString(R.string.incognito_ntp_not_saved_history))
        val notSaved2 = esc(getString(R.string.incognito_ntp_not_saved_cookies))
        val notSaved3 = esc(getString(R.string.incognito_ntp_not_saved_form))
        val visibleHeading = esc(getString(R.string.incognito_ntp_visible_heading))
        val visible1 = esc(getString(R.string.incognito_ntp_visible_sites))
        val visible2 = esc(getString(R.string.incognito_ntp_visible_employer))
        val searchHint = esc(getString(R.string.search_or_type_url))
        return """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><title>$title</title>
<style>
*{margin:0;padding:0;box-sizing:border-box;}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#202124;min-height:100vh;display:flex;flex-direction:column;align-items:center;padding:14vh 20px 40px;color:#E8EAED;-webkit-user-select:none;-webkit-tap-highlight-color:transparent;}
.glyph{width:64px;height:64px;border-radius:50%;background:#3C4043;display:flex;align-items:center;justify-content:center;margin-bottom:20px;}
.glyph svg{width:34px;height:34px;fill:#BDC1C6;}
.title{font-size:22px;font-weight:600;margin-bottom:8px;text-align:center;color:#F1F3F4;}
.subtitle{font-size:14px;color:#9AA0A6;margin-bottom:28px;text-align:center;max-width:420px;line-height:1.5;}
.search-box{width:100%;max-width:420px;background:#2A2A2D;border-radius:24px;padding:14px 20px;display:flex;align-items:center;gap:12px;margin-bottom:28px;border:1px solid #3C4043;}
.search-box svg{width:18px;height:18px;fill:#9AA0A6;flex-shrink:0;}
.search-box span{color:#9AA0A6;font-size:14px;}
.card{width:100%;max-width:420px;background:#292A2D;border-radius:14px;padding:18px 20px;margin-bottom:14px;border:1px solid #3C4043;}
.card h2{font-size:13px;font-weight:600;color:#E8EAED;margin-bottom:10px;letter-spacing:0.3px;}
.card ul{list-style:none;}
.card li{font-size:13px;color:#9AA0A6;line-height:1.45;padding-left:18px;position:relative;margin-bottom:6px;}
.card li:last-child{margin-bottom:0;}
.card li:before{content:'';position:absolute;left:0;top:7px;width:6px;height:6px;border-radius:50%;background:#5F6368;}
</style></head>
<body>
<div class="glyph"><svg viewBox="0 0 24 24"><path d="M12 2C9.24 2 7 4.24 7 7v2H6c-1.1 0-2 .9-2 2v9c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-9c0-1.1-.9-2-2-2h-1V7c0-2.76-2.24-5-5-5zm0 2c1.66 0 3 1.34 3 3v2H9V7c0-1.66 1.34-3 3-3zm0 11c1.1 0 2 .9 2 2s-.9 2-2 2-2-.9-2-2 .9-2 2-2z"/></svg></div>
<div class="title">$title</div>
<div class="subtitle">$subtitle</div>
<div class="search-box" onclick="if(window.HelixHome)window.HelixHome.focusAddressBar();">
<svg viewBox="0 0 24 24"><path d="M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>
<span>$searchHint</span>
</div>
<div class="card">
<h2>$savedHeading</h2>
<ul><li>$notSaved1</li><li>$notSaved2</li><li>$notSaved3</li></ul>
</div>
<div class="card">
<h2>$visibleHeading</h2>
<ul><li>$visible1</li><li>$visible2</li></ul>
</div>
</body></html>""".trimIndent()
    }

    // Fetch the user's most-visited sites off the main thread and inject them into
    // the already-rendered internal start page, replacing the hardcoded shortcut
    // grid. Fails soft to the hardcoded grid when history is empty/disabled or the
    // DB read throws. No remote network: tiles use letter avatars derived locally.
    private fun loadTopSiteTiles(webView: HelixWebView) {
        // Respect the privacy toggle: if history saving is off, keep the curated
        // shortcuts and never touch the history DB.
        if (!Prefs.isSaveHistoryEnabled(this)) return
        // Incognito tabs must not surface browsing history on their start page.
        if (tabManager.currentTab?.isIncognito == true) return
        val repo = (application as HelixApp).historyRepository
        lifecycleScope.launch {
            val json = try {
                withContext(Dispatchers.IO) { buildTopSitesJson(repo.getTopSites(TOP_SITES_COUNT)) }
            } catch (t: Throwable) {
                android.util.Log.w("MainActivity", "getTopSites failed", t)
                null
            } ?: return@launch
            if (isFinishing || isDestroyed) return@launch
            // Only inject if this WebView is still the foreground one showing the
            // start page (the user may have navigated/switched during the IO read).
            if (webView !== currentWebView) return@launch
            if (webView.url != null && webView.url != "about:blank") return@launch
            try {
                webView.evaluateJavascript("helixRenderTiles($json);", null)
            } catch (t: Throwable) {
                android.util.Log.w("MainActivity", "tile injection failed", t)
            }
        }
    }

    // Map history rows to a compact JSON array the start page understands. Each
    // entry carries the loadable URL, a host label, a single avatar letter, and a
    // deterministic accent color. org.json handles all string escaping so no page
    // title or URL can break out of the JSON / JS string context.
    private fun buildTopSitesJson(rows: List<com.helix.browser.data.HistoryItem>): String {
        val arr = org.json.JSONArray()
        val seenHosts = HashSet<String>()
        for (row in rows) {
            val url = row.url
            // Only http/https tiles are openable and safe to surface; skip internal
            // / data / about rows defensively.
            val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
            if (scheme != "http" && scheme != "https") continue
            val host = UrlUtils.getDisplayUrl(url).substringBefore('/').removePrefix("www.")
            if (host.isEmpty() || !seenHosts.add(host)) continue
            val label = row.title.takeIf { it.isNotBlank() } ?: host
            val letter = host.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
            arr.put(org.json.JSONObject().apply {
                put("u", url)
                put("l", label.take(MAX_TILE_LABEL_LEN))
                put("a", letter)
                put("c", tileColorForHost(host))
            })
        }
        return arr.toString()
    }

    // Deterministic accent color from the host so a site keeps the same tile color
    // across sessions. Palette mirrors the curated-shortcut accents already in CSS.
    private fun tileColorForHost(host: String): String {
        val palette = arrayOf(
            "#4285F4", "#FF4500", "#1877F2", "#E50914",
            "#7B68EE", "#49CCF9", "#34A853", "#FBBC05"
        )
        val idx = (host.hashCode() and Int.MAX_VALUE) % palette.size
        return palette[idx]
    }

    private fun showMoreOptionsMenu() {
        val dialog = BottomSheetDialog(this, R.style.Theme_HelixBrowser_BottomSheet)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_menu, null)
        dialog.setContentView(view)
        view.findViewById<View>(R.id.menu_new_tab).setOnClickListener { createNewTab(); dialog.dismiss() }
        view.findViewById<View>(R.id.menu_incognito_tab).setOnClickListener { createNewTab(isIncognito = true); dialog.dismiss() }
        view.findViewById<View>(R.id.menu_bookmark).setOnClickListener {
            val url = viewModel.currentUrl.value ?: return@setOnClickListener
            viewModel.toggleBookmark(viewModel.currentTitle.value ?: url, url)
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.menu_bookmarks).setOnClickListener { startActivity(Intent(this, BookmarksActivity::class.java)); dialog.dismiss() }
        view.findViewById<View>(R.id.menu_history).setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)); dialog.dismiss() }
        view.findViewById<View>(R.id.menu_reopen_tab).setOnClickListener {
            dialog.dismiss()
            showRecentlyClosedTabs()
        }
        view.findViewById<View>(R.id.menu_downloads).setOnClickListener { startActivity(Intent(this, DownloadsActivity::class.java)); dialog.dismiss() }

        val historySwitch = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switch_save_history)
        historySwitch.isChecked = Prefs.isSaveHistoryEnabled(this)
        historySwitch.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setSaveHistoryEnabled(this, isChecked)
        }

        view.findViewById<View>(R.id.menu_find_in_page).setOnClickListener { viewModel.showFindInPage.value = true; dialog.dismiss() }
        // Per-tab desktop site: checkmark reflects the CURRENT tab's flag, and the
        // toggle applies only to the current tab (its WebView reloads with the new
        // user-agent). Other tabs keep their own desktop/mobile preference.
        val currentTab = tabManager.currentTab
        view.findViewById<View>(R.id.ic_check_desktop).isVisible = currentTab?.isDesktopMode == true
        view.findViewById<View>(R.id.menu_desktop_site).setOnClickListener {
            val tab = tabManager.currentTab
            if (tab != null) {
                val isDesktop = !tab.isDesktopMode
                tabManager.setDesktopMode(tab.id, isDesktop)
                viewModel.isDesktopMode.value = isDesktop
                currentWebView?.setDesktopMode(isDesktop)
            }
            dialog.dismiss()
        }
        // Text zoom. Changes go through the engine's clamp (applyZoomPercent) and are
        // persisted per-host so the site keeps this zoom on the next visit. Incognito
        // tabs apply the change for the session but never persist it (no trace).
        val tvZoomLevel = view.findViewById<android.widget.TextView>(R.id.tvZoomLevel)
        val currentZoom = currentWebView?.settings?.textZoom ?: defaultZoomPercent()
        tvZoomLevel.text = getString(R.string.zoom_percent, currentZoom)
        fun changeZoom(delta: Int) {
            val wv = currentWebView ?: return
            // Keep the menu control's familiar 50..200 band; the engine clamp is a
            // wider superset and will not narrow this.
            val newZoom = ((wv.settings.textZoom) + delta).coerceIn(50, 200)
            wv.applyZoomPercent(newZoom)
            tvZoomLevel.text = getString(R.string.zoom_percent, newZoom)
            if (!wv.isIncognito) zoomHostForCurrentPage()?.let { persistZoomForHost(it, newZoom) }
        }
        view.findViewById<View>(R.id.btnZoomIn).setOnClickListener { changeZoom(10) }
        view.findViewById<View>(R.id.btnZoomOut).setOnClickListener { changeZoom(-10) }
        view.findViewById<View>(R.id.menu_share).setOnClickListener { shareCurrentPage(); dialog.dismiss() }
        view.findViewById<View>(R.id.menu_save_page).setOnClickListener { savePageAsArchive(); dialog.dismiss() }
        view.findViewById<View>(R.id.menu_print).setOnClickListener { printCurrentPage(); dialog.dismiss() }
        view.findViewById<View>(R.id.menu_screenshot).setOnClickListener { captureScreenshot(); dialog.dismiss() }
        view.findViewById<View>(R.id.menu_add_to_home).setOnClickListener { addToHomeScreen(); dialog.dismiss() }
        // Reader mode toggle (label swaps to "Exit reader" when active).
        val tvReaderMode = view.findViewById<android.widget.TextView>(R.id.tvReaderMode)
        currentWebView?.let { wv ->
            com.helix.browser.engine.ReaderMode.isActive(wv) { active ->
                tvReaderMode.setText(if (active) R.string.reader_mode_exit else R.string.reader_mode)
            }
        }
        view.findViewById<View>(R.id.menu_reader_mode).setOnClickListener {
            toggleReaderMode(); dialog.dismiss()
        }
        view.findViewById<View>(R.id.menu_pip).setOnClickListener {
            enterPictureInPicture(); dialog.dismiss()
        }
        view.findViewById<View>(R.id.menu_settings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)); dialog.dismiss() }
        view.findViewById<View>(R.id.menu_reopen_tab).isVisible = tabManager.recentlyClosed.isNotEmpty()
        dialog.show()
    }

    // Print / "Save as PDF" the current page. The Android print framework always
    // offers a "Save as PDF" target, so this is the Save-as-PDF entry point and is
    // also reused by the overflow Save-as-PDF action. Guards the internal start /
    // error pages (nothing meaningful to print) and wraps the framework call so a
    // device without a print spooler fails soft instead of crashing.
    private fun printCurrentPage() {
        val webView = currentWebView ?: return
        val url = viewModel.currentUrl.value
        if (url.isNullOrBlank() || url == "about:blank") {
            Toast.makeText(this, R.string.print_no_page, Toast.LENGTH_SHORT).show()
            return
        }
        val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(this, R.string.print_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        // Sanitize the page title for use as both the document name and job name;
        // an empty/blank title falls back to a generic name.
        val docName = (viewModel.currentTitle.value?.trim()?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.app_name))
            .replace(Regex("[\\x00-\\x1F/\\\\]"), "_")
            .take(100)
        try {
            val printAdapter = webView.createPrintDocumentAdapter(docName)
            printManager.print(docName, printAdapter, null)
        } catch (t: Throwable) {
            android.util.Log.w("MainActivity", "print failed", t)
            Toast.makeText(this, R.string.print_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    // Capture the current page as an image and share it via the system share sheet.
    //
    // Strategy (mirrors the tab-thumbnail capture path for correctness):
    //  - Full page first: when the WebView reports a content height taller than the
    //    viewport and the total pixel budget stays under a cap, we draw the whole
    //    document into one bitmap via a temporary software layer on the UI thread
    //    (WebView.draw() is main-thread-only; hardware layers render blank to a
    //    software Canvas, hence the forced LAYER_TYPE_SOFTWARE for the draw).
    //  - Visible area fallback: if full-page is too large / unavailable / fails, we
    //    grab the on-screen region with PixelCopy (API 26+, off the UI thread) or a
    //    software-layer draw of the visible rect on older devices.
    //  - The expensive PNG encode + file write always run on Dispatchers.IO; the
    //    bitmap is recycled afterwards so we never retain a full-res buffer.
    // Internal start / error pages and zero-sized / low-memory states are skipped.
    @Suppress("DEPRECATION") // WebView.getScale() is the only API to map contentHeight to device px.
    private fun captureScreenshot() {
        val webView = currentWebView ?: return
        val url = viewModel.currentUrl.value
        if (url.isNullOrBlank() || url == "about:blank") {
            Toast.makeText(this, R.string.screenshot_no_page, Toast.LENGTH_SHORT).show()
            return
        }
        val w = webView.width
        val h = webView.height
        if (w <= 0 || h <= 0) {
            Toast.makeText(this, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
            return
        }
        // Skip under memory pressure: a full-page bitmap is large and must never be
        // the thing that tips the process over.
        val mi = android.app.ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.getMemoryInfo(mi)
        if (mi.lowMemory) {
            Toast.makeText(this, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, R.string.screenshot_capturing, Toast.LENGTH_SHORT).show()

        // Full document height in device pixels (contentHeight is in CSS px at the
        // current scale). Clamp to at least the viewport so a bad/zero report still
        // yields the visible area.
        val fullHeight = (webView.contentHeight * webView.scale).toInt().coerceAtLeast(h)
        // Cap total pixels so a very long page (e.g. an infinite feed) cannot OOM.
        // ARGB_8888 == 4 bytes/px; SCREENSHOT_MAX_PIXELS keeps the buffer bounded.
        val cappedHeight = fullHeight.coerceAtMost((SCREENSHOT_MAX_PIXELS / w).coerceAtLeast(h))

        val fullPageBitmap: android.graphics.Bitmap? =
            if (cappedHeight > h) drawWebViewToBitmap(webView, w, cappedHeight) else null

        if (fullPageBitmap != null) {
            finishScreenshot(fullPageBitmap)
            return
        }

        // Visible-area fallback. Prefer PixelCopy (off the UI thread) on API 26+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val loc = IntArray(2)
            webView.getLocationInWindow(loc)
            val src = android.graphics.Rect(loc[0], loc[1], loc[0] + w, loc[1] + h)
            val bmp = try {
                android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            } catch (t: Throwable) {
                android.util.Log.w("MainActivity", "screenshot alloc failed", t)
                Toast.makeText(this, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                return
            }
            try {
                android.view.PixelCopy.request(window, src, bmp, { result ->
                    if (isFinishing || isDestroyed) { bmp.recycle(); return@request }
                    if (result == android.view.PixelCopy.SUCCESS) {
                        finishScreenshot(bmp)
                    } else {
                        bmp.recycle()
                        // Last-ditch: software draw of the visible rect.
                        val sw = drawWebViewToBitmap(webView, w, h)
                        if (sw != null) finishScreenshot(sw)
                        else Toast.makeText(this, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                    }
                }, binding.root.handler ?: android.os.Handler(mainLooper))
            } catch (t: Throwable) {
                bmp.recycle()
                android.util.Log.w("MainActivity", "screenshot PixelCopy failed", t)
                val sw = drawWebViewToBitmap(webView, w, h)
                if (sw != null) finishScreenshot(sw)
                else Toast.makeText(this, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Pre-O: software-layer draw of the visible area.
        val bmp = drawWebViewToBitmap(webView, w, h)
        if (bmp != null) finishScreenshot(bmp)
        else Toast.makeText(this, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
    }

    // Draw [webView] at [width] x [height] device pixels into a fresh ARGB_8888
    // bitmap via a temporary software layer (WebView is hardware-accelerated, so a
    // plain Canvas draw renders blank). Used for full-page capture and the pre-O /
    // PixelCopy-failure visible-area fallback. Returns null on allocation/draw
    // failure (e.g. OOM on a very tall page). MUST run on the UI thread.
    private fun drawWebViewToBitmap(
        webView: HelixWebView,
        width: Int,
        height: Int
    ): android.graphics.Bitmap? {
        if (width <= 0 || height <= 0) return null
        val originalLayerType = webView.layerType
        return try {
            val bitmap = android.graphics.Bitmap.createBitmap(
                width, height, android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            // Paint the theme background first so transparent page regions are not
            // left as black/transparent in the exported PNG.
            canvas.drawColor(getColor(R.color.background))
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            webView.draw(canvas)
            bitmap
        } catch (t: Throwable) {
            android.util.Log.w("MainActivity", "drawWebViewToBitmap failed", t)
            null
        } finally {
            webView.setLayerType(originalLayerType, null)
        }
    }

    // Encode [bitmap] to PNG in the cache dir (off the main thread) and hand the
    // resulting FileProvider content URI to the system share sheet. The bitmap is
    // recycled once encoded. Old screenshots are pruned so the cache stays bounded.
    private fun finishScreenshot(bitmap: android.graphics.Bitmap) {
        lifecycleScope.launch {
            val uri = try {
                withContext(Dispatchers.IO) {
                    val dir = File(cacheDir, "screenshots").apply { mkdirs() }
                    pruneOldFiles(dir, SCREENSHOT_KEEP_COUNT)
                    val safeTitle = (viewModel.currentTitle.value ?: "screenshot")
                        .replace(Regex("[^A-Za-z0-9_\\- ]"), "_").trim()
                        .ifEmpty { "screenshot" }.take(40)
                    val file = File(dir, "$safeTitle-${System.currentTimeMillis()}.png")
                    java.io.FileOutputStream(file).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                    androidx.core.content.FileProvider.getUriForFile(
                        this@MainActivity, "$packageName.fileprovider", file
                    )
                }
            } catch (t: Throwable) {
                android.util.Log.w("MainActivity", "screenshot save failed", t)
                null
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            if (isFinishing || isDestroyed) return@launch
            if (uri == null) {
                Toast.makeText(this@MainActivity, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, viewModel.currentTitle.value ?: getString(R.string.screenshot))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(share, getString(R.string.share_via)))
                Toast.makeText(this@MainActivity, R.string.screenshot_saved, Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                android.util.Log.w("MainActivity", "screenshot share failed", t)
                Toast.makeText(this@MainActivity, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Keep only the [keep] most-recent files in [dir], deleting the rest. Bounds the
    // cache so repeated screenshots/PDF downloads don't grow unbounded. Best-effort.
    private fun pruneOldFiles(dir: File, keep: Int) {
        runCatching {
            val files = dir.listFiles()?.filter { it.isFile } ?: return
            if (files.size <= keep) return
            files.sortedByDescending { it.lastModified() }
                .drop(keep)
                .forEach { runCatching { it.delete() } }
        }
    }

    private fun showRecentlyClosedTabs() {
        val closed = tabManager.recentlyClosed
        if (closed.isEmpty()) {
            Toast.makeText(this, R.string.no_recently_closed, Toast.LENGTH_SHORT).show()
            return
        }
        val items = closed.map { tab ->
            val title = tab.title.ifBlank { tab.url }
            // Two-line item: title on top, host on bottom (truncated).
            "$title\n${UrlUtils.getDisplayUrl(tab.url)}"
        }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reopen_closed_tab)
            .setItems(items) { d, which ->
                val tab = closed.getOrNull(which) ?: return@setItems
                createNewTab(tab.url, tab.isIncognito)
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun savePageAsArchive() {
        val wv = currentWebView ?: return
        val url = viewModel.currentUrl.value
        if (url.isNullOrBlank() || url == "about:blank") {
            Toast.makeText(this, R.string.save_page_no_url, Toast.LENGTH_SHORT).show()
            return
        }
        // WebView.saveWebArchive writes a single self-contained .mht file to
        // app-owned scoped storage. Falls back to filesDir if external storage
        // is unavailable (no SD card mounted on legacy devices) so we never
        // silently skip the save.
        val baseDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        val dir = File(baseDir, "archives").apply { mkdirs() }
        val safeName = (viewModel.currentTitle.value ?: "page")
            .take(60).replace(Regex("[^A-Za-z0-9_\\- ]"), "_")
        val file = File(dir, "$safeName-${System.currentTimeMillis()}.mht")
        wv.saveWebArchive(file.absolutePath, /* autoname = */ false) { saved ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (saved != null) {
                    Toast.makeText(this, getString(R.string.save_page_done, file.name), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, R.string.save_page_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleReaderMode() {
        val wv = currentWebView ?: return
        com.helix.browser.engine.ReaderMode.isActive(wv) { active ->
            runOnUiThread {
                if (active) {
                    com.helix.browser.engine.ReaderMode.exit(wv)
                } else {
                    // The app is dark-locked, so reader mode always renders dark.
                    com.helix.browser.engine.ReaderMode.enter(wv, dark = true)
                }
            }
        }
    }

    private fun pipSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    // Manual PiP entry (overflow menu). Only enters when a <video> is actually
    // present/playing, matching Chrome — otherwise PiP would show a blank page.
    // The fullscreen-video case is known synchronously; the inline-video case is
    // probed via JS and entered from the async callback.
    private fun enterPictureInPicture() {
        if (!pipSupported()) {
            Toast.makeText(this, R.string.pip_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        if (fullscreenView != null) {
            enterPipNow()
            return
        }
        detectVideoPlaying { playing ->
            if (isFinishing || isDestroyed) return@detectVideoPlaying
            if (playing) enterPipNow()
            else Toast.makeText(this, R.string.pip_no_video, Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-PiP: when the user leaves the activity (Home / Recents) while a video is
    // actively playing, slide into a PiP window like Chrome. We only enter when a
    // video is genuinely playing so backgrounding a normal page is unaffected.
    // The fullscreen-video state is authoritative and synchronous; otherwise we do a
    // quick JS probe and enter from the callback (still within the leave window).
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!pipSupported() || isFinishing || isDestroyed) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) return
        if (fullscreenView != null) {
            enterPipNow()
            return
        }
        detectVideoPlaying { playing ->
            if (playing && !isFinishing && !isDestroyed) enterPipNow()
        }
    }

    @SuppressLint("NewApi") // gated by pipSupported() (API 26+) at every call site.
    private fun enterPipNow() {
        try {
            enterPictureInPictureMode(buildPipParams())
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "enterPictureInPictureMode failed", e)
        }
    }

    // Probe the live page for a <video> that is currently playing (not paused, not
    // ended, has a media source). Falls back to "true" when the fullscreen-video
    // custom view is present. The callback runs on the UI thread. Fails soft to
    // false when JS is disabled / the eval throws.
    private fun detectVideoPlaying(callback: (Boolean) -> Unit) {
        if (fullscreenView != null) { callback(true); return }
        val wv = currentWebView ?: run { callback(false); return }
        val js = """
            (function(){
              try {
                var vs = document.querySelectorAll('video');
                for (var i=0;i<vs.length;i++){
                  var v = vs[i];
                  if (!v.paused && !v.ended && v.readyState > 2 && v.currentTime > 0) return true;
                }
              } catch(e){}
              return false;
            })();
        """.trimIndent()
        try {
            wv.evaluateJavascript(js) { raw ->
                if (isFinishing || isDestroyed) { callback(false); return@evaluateJavascript }
                callback(raw == "true")
            }
        } catch (e: Exception) {
            callback(false)
        }
    }

    // Toggle play/pause on the first playing/paused <video> via JS. Driven by the
    // PiP RemoteAction broadcast. Best-effort; safe when no video / JS disabled.
    private fun togglePipPlayback() {
        val wv = currentWebView ?: return
        val js = """
            (function(){
              try {
                var vs = document.querySelectorAll('video');
                for (var i=0;i<vs.length;i++){
                  var v = vs[i];
                  if (v.paused) { v.play(); } else { v.pause(); }
                  return v.paused ? 'paused' : 'playing';
                }
              } catch(e){}
              return 'none';
            })();
        """.trimIndent()
        try {
            wv.evaluateJavascript(js) { state ->
                if (isFinishing || isDestroyed) return@evaluateJavascript
                // Re-publish the params so the action icon reflects the new state.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    isInPictureInPictureMode) {
                    val playing = state == "\"playing\""
                    runCatching { setPictureInPictureParams(buildPipParams(playing)) }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "togglePipPlayback failed", e)
        }
    }

    // Build PiP params with a single play/pause RemoteAction (when supported). The
    // [playing] flag picks the pause vs play icon/label. The PendingIntent targets
    // our own non-exported receiver with an explicit action, so no other app can
    // trigger it. Falls back to params without actions on pre-O / action-build
    // failure.
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(playing: Boolean = true): android.app.PictureInPictureParams {
        val builder = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
        runCatching {
            val action = buildPipPlayPauseAction(playing)
            if (action != null) builder.setActions(listOf(action))
        }
        return builder.build()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipPlayPauseAction(playing: Boolean): android.app.RemoteAction? {
        return try {
            // FLAG_IMMUTABLE is required on API 31+ and harmless earlier; the intent
            // is explicit and carries no mutable extras a caller could fill in.
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    android.app.PendingIntent.FLAG_IMMUTABLE else 0)
            val intent = Intent(ACTION_PIP_TOGGLE).setPackage(packageName)
            val pi = android.app.PendingIntent.getBroadcast(this, 0, intent, flags)
            val icon = android.graphics.drawable.Icon.createWithResource(this, pipPlayPauseIcon(playing))
            val label = getString(if (playing) R.string.pip_pause else R.string.pip_play)
            android.app.RemoteAction(icon, label, label, pi)
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "buildPipPlayPauseAction failed", e)
            null
        }
    }

    // Resolve the play/pause icon, preferring dedicated media drawables when the
    // resource exists in this build and falling back to always-present icons so the
    // action never fails to render (and lint never sees a missing resource ref).
    private fun pipPlayPauseIcon(playing: Boolean): Int {
        val name = if (playing) "ic_pause" else "ic_play_arrow"
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id != 0) return id
        return if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
    }

    // Receiver for the PiP play/pause RemoteAction. Registered only while in PiP and
    // explicitly (non-exported), so it is reachable only by our own PendingIntent.
    private var pipActionReceiver: android.content.BroadcastReceiver? = null

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun registerPipReceiver() {
        if (pipActionReceiver != null) return
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_PIP_TOGGLE) togglePipPlayback()
            }
        }
        pipActionReceiver = receiver
        val filter = android.content.IntentFilter(ACTION_PIP_TOGGLE)
        // RECEIVER_NOT_EXPORTED (API 33+) keeps the receiver private; our intent is
        // explicit/package-scoped anyway, so this is defense in depth.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    private fun unregisterPipReceiver() {
        pipActionReceiver?.let { runCatching { unregisterReceiver(it) } }
        pipActionReceiver = null
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            // Collapse browser chrome while the PiP window is showing so the video
            // surface is unobstructed; keep the foreground WebView resumed/playing.
            binding.bottomNavContainer.isVisible = false
            currentWebView?.onResume()
            // Start listening for the play/pause RemoteAction and seed the controls
            // with the current playback state so the icon is correct on entry.
            registerPipReceiver()
            detectVideoPlaying { playing ->
                if (isInPictureInPictureMode && !isFinishing && !isDestroyed) {
                    runCatching { setPictureInPictureParams(buildPipParams(playing)) }
                }
            }
        } else {
            // Restored to full-window (or the PiP window was dismissed): bring chrome
            // back and stop listening for the RemoteAction.
            binding.bottomNavContainer.isVisible = true
            unregisterPipReceiver()
        }
    }

    private fun addToHomeScreen() {
        val url = viewModel.currentUrl.value ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
        if (!shortcutManager.isRequestPinShortcutSupported) return

        // Probe the page for a Web App Manifest (PWA). Falls back to current
        // title + Helix icon if no manifest, an invalid manifest, or no
        // suitable icon is found. We never block the install flow on this.
        val wv = currentWebView
        val fallbackTitle = viewModel.currentTitle.value ?: url
        if (wv == null) {
            pinShortcut(url, fallbackTitle, null); return
        }
        val probe = """
            (function(){
              try {
                var link = document.querySelector('link[rel="manifest"]');
                if (!link) return JSON.stringify({});
                var href = new URL(link.getAttribute('href'), document.baseURI).href;
                return fetch(href, {credentials: 'omit'})
                  .then(function(r){return r.json();})
                  .then(function(m){
                    var icons = (m.icons||[]).slice().sort(function(a,b){
                      var as = parseInt((a.sizes||'0').split('x')[0])||0;
                      var bs = parseInt((b.sizes||'0').split('x')[0])||0;
                      return bs - as;
                    });
                    var icon = icons[0] ? new URL(icons[0].src, href).href : null;
                    return JSON.stringify({name: m.name || m.short_name, icon: icon, start_url: m.start_url});
                  })
                  .catch(function(){ return JSON.stringify({}); });
              } catch(e){ return JSON.stringify({}); }
            })();
        """.trimIndent()
        try {
            wv.evaluateJavascript(probe) { raw ->
                // evaluateJavascript returns a JSON-encoded string; promises
                // are stringified as "[object Promise]" — in that case we
                // can't await synchronously and fall back to the default icon.
                val s = raw?.trim('"', ' ')?.replace("\\\"", "\"") ?: ""
                val (name, iconUrl) = parseManifestProbe(s)
                if (iconUrl != null) {
                    loadIconAsync(iconUrl) { bmp ->
                        pinShortcut(url, name ?: fallbackTitle, bmp)
                    }
                } else {
                    pinShortcut(url, name ?: fallbackTitle, null)
                }
            }
        } catch (e: Exception) {
            pinShortcut(url, fallbackTitle, null)
        }
    }

    private fun parseManifestProbe(json: String): Pair<String?, String?> {
        if (json.isEmpty() || json == "null" || json.startsWith("[object")) return null to null
        return try {
            val obj = org.json.JSONObject(json)
            (obj.optString("name").takeIf { it.isNotBlank() }) to
            (obj.optString("icon").takeIf { it.isNotBlank() })
        } catch (e: Exception) { null to null }
    }

    private fun loadIconAsync(iconUrl: String, onReady: (android.graphics.Bitmap?) -> Unit) {
        Thread {
            val bmp = try {
                val conn = java.net.URL(iconUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
            } catch (e: Exception) { null }
            runOnUiThread { onReady(bmp) }
        }.start()
    }

    private fun pinShortcut(url: String, title: String, customIcon: android.graphics.Bitmap?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (isFinishing || isDestroyed) return
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(url)
        }
        val icon = if (customIcon != null)
            Icon.createWithBitmap(customIcon)
        else
            Icon.createWithResource(this, R.drawable.ic_helix_logo)
        val shortcut = ShortcutInfo.Builder(this, url)
            .setShortLabel(title.take(10))
            .setLongLabel(title.take(25))
            .setIcon(icon)
            .setIntent(intent)
            .build()
        shortcutManager.requestPinShortcut(shortcut, null)
    }

    private var pendingGeolocationCallback: android.webkit.GeolocationPermissions.Callback? = null
    private var pendingGeolocationOrigin: String? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val origin = pendingGeolocationOrigin
        pendingGeolocationCallback?.invoke(origin, granted, false)
        pendingGeolocationCallback = null
        pendingGeolocationOrigin = null
    }

    private fun requestGeolocationPermission(
        origin: String,
        callback: android.webkit.GeolocationPermissions.Callback
    ) {
        // Never auto-allow. Check remembered per-origin decision first.
        when (Prefs.getSitePermission(this, "geolocation", origin)) {
            "allow" -> { callback.invoke(origin, true, true); return }
            "deny"  -> { callback.invoke(origin, false, true); return }
        }
        val hasOsPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_location_title))
            .setMessage(getString(R.string.permission_location_message, origin))
            .setPositiveButton(R.string.allow) { _, _ ->
                Prefs.setSitePermission(this, "geolocation", origin, "allow")
                if (hasOsPerm) {
                    callback.invoke(origin, true, true)
                } else {
                    pendingGeolocationCallback = callback
                    pendingGeolocationOrigin = origin
                    locationPermissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
            }
            .setNegativeButton(R.string.deny) { _, _ ->
                Prefs.setSitePermission(this, "geolocation", origin, "deny")
                callback.invoke(origin, false, true)
            }
            .setOnCancelListener { callback.invoke(origin, false, false) }
            .show()
    }

    private var pendingWebPermissionRequest: android.webkit.PermissionRequest? = null
    private var pendingWebPermissionKey: String? = null
    private var pendingWebPermissionOrigin: String? = null
    private val osPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val req = pendingWebPermissionRequest
        val key = pendingWebPermissionKey
        val origin = pendingWebPermissionOrigin
        pendingWebPermissionRequest = null
        pendingWebPermissionKey = null
        pendingWebPermissionOrigin = null
        if (req == null) return@registerForActivityResult
        val allOsGranted = grants.values.all { it } && grants.isNotEmpty()
        if (allOsGranted) {
            req.grant(req.resources)
        } else {
            // User denied the OS permission. Clear the remembered "allow" for
            // this site so we re-prompt next time rather than silently retrying
            // a permission they revoked at the OS layer.
            if (key != null && origin != null) {
                Prefs.clearSitePermission(this, key, origin)
            }
            req.deny()
        }
    }

    private fun handleWebPermissionRequest(request: android.webkit.PermissionRequest) {
        val origin = request.origin.toString()
        val webResources = request.resources
        val permKey: String
        val titleRes: Int
        val messageRes: Int
        val osPerms: Array<String>
        when {
            webResources.contains(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE) -> {
                permKey = "camera"
                titleRes = R.string.permission_camera_title
                messageRes = R.string.permission_camera_message
                osPerms = arrayOf(Manifest.permission.CAMERA)
            }
            webResources.contains(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE) -> {
                permKey = "microphone"
                titleRes = R.string.permission_mic_title
                messageRes = R.string.permission_mic_message
                osPerms = arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            else -> { request.deny(); return }
        }

        when (Prefs.getSitePermission(this, permKey, origin)) {
            "allow" -> { grantWebPermissionIfOsAllows(request, osPerms, permKey, origin); return }
            "deny"  -> { request.deny(); return }
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(titleRes))
            .setMessage(getString(messageRes, origin))
            .setPositiveButton(R.string.allow) { _, _ ->
                Prefs.setSitePermission(this, permKey, origin, "allow")
                grantWebPermissionIfOsAllows(request, osPerms, permKey, origin)
            }
            .setNegativeButton(R.string.deny) { _, _ ->
                Prefs.setSitePermission(this, permKey, origin, "deny")
                request.deny()
            }
            .setOnCancelListener { request.deny() }
            .show()
    }

    private fun grantWebPermissionIfOsAllows(
        request: android.webkit.PermissionRequest,
        osPerms: Array<String>,
        permKey: String,
        origin: String
    ) {
        val missing = osPerms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            request.grant(request.resources)
        } else {
            // Remember which site we are prompting for so the OS-permission
            // callback can clean up stale "allow" if the user denies.
            pendingWebPermissionRequest = request
            pendingWebPermissionKey = permKey
            pendingWebPermissionOrigin = origin
            osPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun setupWebViewContextMenu(webView: HelixWebView) {
        webView.setOnLongClickListener {
            val result = webView.hitTestResult
            when (result.type) {
                HitTestResult.SRC_ANCHOR_TYPE, HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    showLinkContextMenu(result.extra ?: return@setOnLongClickListener false)
                    true
                }
                HitTestResult.IMAGE_TYPE -> {
                    showImageContextMenu(result.extra ?: return@setOnLongClickListener false)
                    true
                }
                else -> false
            }
        }
    }

    private fun showLinkContextMenu(url: String) {
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(url)
            .setItems(arrayOf(
                getString(R.string.open_in_new_tab),
                getString(R.string.open_in_incognito),
                getString(R.string.copy_link),
                getString(R.string.share_link)
            )) { _, which ->
                when (which) {
                    0 -> createNewTab(url)
                    1 -> createNewTab(url, isIncognito = true)
                    2 -> {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("url", url))
                        Toast.makeText(this, getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
                    }
                }
            }
            .create()
        dialog.show()
    }

    private fun showImageContextMenu(imageUrl: String) {
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.save_image))
            .setItems(arrayOf(
                getString(R.string.save_image),
                getString(R.string.open_in_new_tab),
                getString(R.string.copy_link),
                getString(R.string.share_link)
            )) { _, which ->
                when (which) {
                    0 -> downloadFile(imageUrl, "", "", "image/*")
                    1 -> createNewTab(imageUrl)
                    2 -> {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("url", imageUrl))
                        Toast.makeText(this, getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, imageUrl)
                        }
                        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
                    }
                }
            }
            .create()
        dialog.show()
    }

    private fun showPageInfoSheet() {
        val url = viewModel.currentUrl.value ?: return
        val isHttps = url.startsWith("https://")
        val isHttp = url.startsWith("http://")
        val isInternal = !isHttps && !isHttp
        val domain = try { java.net.URI(url).host ?: url } catch (_: Exception) { url }

        val dialog = BottomSheetDialog(this, R.style.Theme_HelixBrowser_BottomSheet)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_page_info, null)
        dialog.setContentView(view)

        val pageInfoIcon = view.findViewById<android.widget.ImageView>(R.id.pageInfoIcon)
        val pageInfoIconBg = view.findViewById<View>(R.id.pageInfoIconBackground)
        val pageInfoTitle = view.findViewById<android.widget.TextView>(R.id.pageInfoTitle)
        val pageInfoDomain = view.findViewById<android.widget.TextView>(R.id.pageInfoDomain)
        val pageInfoSubtitle = view.findViewById<android.widget.TextView>(R.id.pageInfoSubtitle)
        val pageInfoConnectionDetail = view.findViewById<android.widget.TextView>(R.id.pageInfoConnectionDetail)
        val pageInfoCookiesDetail = view.findViewById<android.widget.TextView>(R.id.pageInfoCookiesDetail)

        when {
            isHttps -> {
                pageInfoIcon.setImageResource(R.drawable.ic_lock)
                pageInfoIcon.setColorFilter(getColor(R.color.green_secure))
                pageInfoIconBg.setBackgroundResource(R.drawable.bg_page_info_icon_secure)
                pageInfoTitle.text = getString(R.string.connection_secure)
                pageInfoTitle.setTextColor(getColor(R.color.text_primary))
                pageInfoSubtitle.text = getString(R.string.page_info_secure_subtitle)
                pageInfoConnectionDetail.text = getString(R.string.page_info_certificate)
            }
            isHttp -> {
                pageInfoIcon.setImageResource(R.drawable.ic_lock_open)
                pageInfoIcon.setColorFilter(getColor(R.color.warning_orange))
                pageInfoIconBg.setBackgroundResource(R.drawable.bg_page_info_icon_warning)
                pageInfoTitle.text = getString(R.string.connection_not_secure)
                pageInfoTitle.setTextColor(getColor(R.color.warning_orange))
                pageInfoSubtitle.text = getString(R.string.page_info_not_secure_subtitle)
                pageInfoConnectionDetail.text = getString(R.string.not_secure)
            }
            else -> {
                pageInfoIcon.setImageResource(R.drawable.ic_helix_logo)
                pageInfoIcon.setColorFilter(getColor(R.color.accent_purple))
                pageInfoIconBg.setBackgroundResource(R.drawable.bg_page_info_icon_neutral)
                pageInfoTitle.text = getString(R.string.connection_internal)
                pageInfoTitle.setTextColor(getColor(R.color.text_primary))
                pageInfoSubtitle.text = getString(R.string.page_info_internal_subtitle)
                pageInfoConnectionDetail.text = getString(R.string.connection_internal)
            }
        }

        pageInfoDomain.text = domain

        // Build the scheme://host origin (cookies are origin-scoped, not path-scoped).
        val origin = try {
            val u = java.net.URI(url)
            if (u.scheme != null && u.host != null) "${u.scheme}://${u.host}" else url
        } catch (_: Exception) { url }

        // Cookies count: getCookie returns "a=1; b=2" (or "" / null). Trim and drop
        // blank entries so a cleared origin reports 0 rather than 1.
        fun countCookies(): Int = try {
            android.webkit.CookieManager.getInstance().getCookie(origin)
                ?.split(";")?.map { it.trim() }?.count { it.isNotEmpty() } ?: 0
        } catch (_: Exception) { 0 }
        val cookieCount = countCookies()
        pageInfoCookiesDetail.text = if (cookieCount > 0)
            getString(R.string.cookies_in_use, cookieCount) else getString(R.string.no_cookies)

        view.findViewById<View>(R.id.pageInfoConnectionRow).setOnClickListener {
            // Connection details — show toast for now or dedicated screen
            Toast.makeText(this, pageInfoConnectionDetail.text, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<View>(R.id.pageInfoPermissionsRow).setOnClickListener {
            // Open app settings for site permissions
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            } catch (_: Exception) {}
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.pageInfoCookiesRow).setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_cookies)
                .setMessage("Clear cookies for $domain?")
                .setPositiveButton(R.string.clear_history_confirm) { _, _ ->
                    clearCookiesForHost(domain)
                    // Refresh the count label so the sheet reflects reality if reopened.
                    pageInfoCookiesDetail.text = getString(R.string.no_cookies)
                    Toast.makeText(this, getString(R.string.cookies_cleared), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btnPageInfoCopy).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("url", url))
            Toast.makeText(this, getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btnPageInfoShare).setOnClickListener {
            shareCurrentPage()
            dialog.dismiss()
        }

        dialog.show()
    }

    // Actually delete every cookie for a host on both http and https origins by
    // enumerating them and issuing an explicit expiry per name (setCookie(url, "")
    // is a no-op). Mirrors HelixWebView.clearIncognitoData's deletion pattern.
    private fun clearCookiesForHost(host: String) {
        if (host.isBlank()) return
        val cm = android.webkit.CookieManager.getInstance()
        for (scheme in arrayOf("https", "http")) {
            val origin = "$scheme://$host"
            val header = runCatching { cm.getCookie("$origin/") }.getOrNull() ?: continue
            header.split(";").forEach { pair ->
                val name = pair.substringBefore('=').trim()
                if (name.isNotEmpty()) {
                    runCatching {
                        cm.setCookie(
                            "$origin/",
                            "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
                        )
                    }
                }
            }
        }
        cm.flush()
    }

    private fun shareCurrentPage() {
        val url = viewModel.currentUrl.value ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, viewModel.currentTitle.value ?: url)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
    }

    private fun downloadFile(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long = 0L
    ) {
        // DownloadManager only understands http/https. Reject any other scheme
        // (data:, blob:, file:, javascript:, content:, intent: …) rather than
        // forwarding attacker-controlled URLs to the system downloader.
        val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
        if (scheme != "http" && scheme != "https") {
            Toast.makeText(this, getString(R.string.download_unsupported_scheme), Toast.LENGTH_SHORT).show()
            return
        }
        val guessed = URLUtil.guessFileName(url, contentDisposition, mimeType)

        // In-app PDF routing: a PDF download opens directly in the bundled PDF
        // viewer instead of going through DownloadManager. We detect either the
        // application/pdf MIME or a .pdf extension on the (disposition-derived)
        // filename or the URL path. The viewer reads from app cache via a
        // FileProvider content URI, so no storage permission is needed here.
        val isPdf = mimeType.substringBefore(';').trim().equals("application/pdf", ignoreCase = true) ||
            guessed.endsWith(".pdf", ignoreCase = true) ||
            runCatching { Uri.parse(url).path }.getOrNull()?.endsWith(".pdf", ignoreCase = true) == true
        if (isPdf) {
            openPdfInViewer(url, userAgent, guessed)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            return
        }
        showDownloadConfirmation(url, userAgent, guessed, contentLength)
    }

    // Fetch a PDF into app cache (off the main thread, bounded size) and launch the
    // in-app PdfViewerActivity with a FileProvider content URI. Keeps the http/https
    // scheme guarantee from downloadFile(). Falls back to the normal download-confirm
    // sheet on any fetch failure so the user can still save the file.
    private fun openPdfInViewer(url: String, userAgent: String, guessedName: String) {
        Toast.makeText(this, R.string.pdf_opening, Toast.LENGTH_SHORT).show()
        val title = guessedName.substringBeforeLast('.', guessedName)
            .ifBlank { getString(R.string.pdf_viewer_title) }
        val safeName = sanitizeDownloadFileName(guessedName, "document.pdf")
        lifecycleScope.launch {
            val uri = try {
                withContext(Dispatchers.IO) {
                    val dir = File(cacheDir, "pdf").apply { mkdirs() }
                    pruneOldFiles(dir, PDF_CACHE_KEEP_COUNT)
                    val file = File(dir, "${System.currentTimeMillis()}-$safeName")
                    val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 20000
                        instanceFollowRedirects = true
                        if (userAgent.isNotEmpty()) setRequestProperty("User-Agent", userAgent)
                    }
                    try {
                        // Defend against a hostile/huge response: stop writing past the
                        // cap and treat it as a failure so we don't fill the cache.
                        conn.inputStream.use { input ->
                            file.outputStream().use { out ->
                                val buf = ByteArray(64 * 1024)
                                var total = 0L
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    total += n
                                    if (total > MAX_PDF_BYTES) throw java.io.IOException("pdf too large")
                                    out.write(buf, 0, n)
                                }
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                    if (file.length() <= 0L) throw java.io.IOException("empty pdf")
                    androidx.core.content.FileProvider.getUriForFile(
                        this@MainActivity, "$packageName.fileprovider", file
                    )
                }
            } catch (t: Throwable) {
                android.util.Log.w("MainActivity", "openPdfInViewer fetch failed", t)
                null
            }
            if (isFinishing || isDestroyed) return@launch
            if (uri == null) {
                // Fetch failed: fall back to the standard download path so the user
                // is not left with nothing.
                showDownloadConfirmation(url, userAgent, guessedName, 0L)
                return@launch
            }
            try {
                val intent = Intent(this@MainActivity, PdfViewerActivity::class.java).apply {
                    putExtra(PdfViewerActivity.EXTRA_PDF_URI, uri.toString())
                    putExtra(PdfViewerActivity.EXTRA_PDF_TITLE, title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            } catch (t: Throwable) {
                android.util.Log.w("MainActivity", "launch PdfViewerActivity failed", t)
                Toast.makeText(this@MainActivity, R.string.download_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Chrome-style pre-download bottom sheet: previews the guessed filename
    // (editable), the host, and the size (when known), with Download / Cancel.
    // Nothing is enqueued until the user confirms. Built programmatically so it
    // needs no owned layout file. Re-validates the (possibly edited) filename
    // and the scheme at confirm time before enqueueing.
    private fun showDownloadConfirmation(
        url: String,
        userAgent: String,
        guessedName: String,
        contentLength: Long
    ) {
        if (isFinishing || isDestroyed) return
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val host = runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.")
            ?: UrlUtils.getDisplayUrl(url).substringBefore('/')

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
        }

        val titleView = android.widget.TextView(this).apply {
            text = getString(R.string.download_confirm_title)
            setTextColor(getColor(R.color.text_primary))
            textSize = 18f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        container.addView(titleView)

        val nameInput = android.widget.EditText(this).apply {
            setText(guessedName)
            setSelection(0, guessedName.substringBeforeLast('.', guessedName).length.coerceAtMost(guessedName.length))
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.hint_color))
            hint = getString(R.string.download_filename_hint)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            }
        }
        container.addView(nameInput, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        val sizeText = if (contentLength > 0) formatBytes(contentLength)
            else getString(R.string.download_size_unknown)
        val metaView = android.widget.TextView(this).apply {
            text = getString(R.string.download_meta, host, sizeText)
            setTextColor(getColor(R.color.text_secondary))
            textSize = 13f
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setSingleLine(true)
        }
        container.addView(metaView, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        val buttonRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
        }
        val dialog = BottomSheetDialog(this, R.style.Theme_HelixBrowser_BottomSheet)

        val cancelBtn = com.google.android.material.button.MaterialButton(
            this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = getString(R.string.cancel)
            setOnClickListener { dialog.dismiss() }
        }
        val downloadBtn = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.download)
            setOnClickListener {
                val chosen = sanitizeDownloadFileName(nameInput.text?.toString(), guessedName)
                dialog.dismiss()
                enqueueDownload(url, userAgent, chosen)
            }
        }
        buttonRow.addView(cancelBtn)
        buttonRow.addView(downloadBtn, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(8) })
        container.addView(buttonRow, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })

        dialog.setContentView(container)
        dialog.show()
    }

    // Strip path separators and control/reserved characters from a user-edited
    // download filename so it can never escape the Downloads directory (path
    // traversal) or produce an invalid file. Falls back to the guessed name, then
    // to a generic name, if the user clears the field entirely.
    private fun sanitizeDownloadFileName(input: String?, fallback: String): String {
        val raw = input?.trim().orEmpty().ifEmpty { fallback }
        // Keep only the last path segment, then drop anything not safe in a name.
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.replace(Regex("[\\x00-\\x1F/\\\\:*?\"<>|]"), "_").trim().trim('.')
        return cleaned.ifEmpty { fallback.ifEmpty { "download" } }.take(200)
    }

    private fun enqueueDownload(url: String, userAgent: String, fileName: String) {
        // Re-validate scheme at enqueue time (defense in depth; the URL is the same
        // one validated in downloadFile but this method is the single enqueue point).
        val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
        if (scheme != "http" && scheme != "https") {
            Toast.makeText(this, getString(R.string.download_unsupported_scheme), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription(getString(R.string.downloading_via_helix))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                if (userAgent.isNotEmpty()) addRequestHeader("User-Agent", userAgent)
                allowScanningByMediaScanner()
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, getString(R.string.download_started, fileName), Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            android.util.Log.w("MainActivity", "enqueueDownload failed", t)
            Toast.makeText(this, getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
        }
    }

    // Human-readable byte size (e.g. "4.2 MB"). Used only for the download preview.
    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024.0
        var i = 0
        while (value >= 1024.0 && i < units.size - 1) { value /= 1024.0; i++ }
        return String.format(java.util.Locale.US, "%.1f %s", value, units[i])
    }

    // Derive a single MIME filter for the system file picker from the
    // <input accept="..."> hint. GetMultipleContents takes one MIME string,
    // so when the page accepts several distinct top-level types (or extension
    // hints we cannot map) we fall back to "*/*". File extensions and the
    // "image/*" style wildcards are honored; anything unrecognized widens to
    // all files rather than silently blocking the user's real file.
    private fun resolveFileChooserMimeFilter(
        params: android.webkit.WebChromeClient.FileChooserParams
    ): String {
        val accept = params.acceptTypes
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        if (accept.isEmpty()) return "*/*"

        val mimeMap = android.webkit.MimeTypeMap.getSingleton()
        val mimeTypes = LinkedHashSet<String>()
        for (entry in accept) {
            val mime = when {
                entry.startsWith(".") -> {
                    val ext = entry.substring(1).lowercase()
                    mimeMap.getMimeTypeFromExtension(ext)
                }
                entry.contains("/") -> entry // already a MIME type or MIME wildcard
                else -> null
            }
            if (mime == null) return "*/*" // unknown hint: don't restrict the picker
            mimeTypes.add(mime)
        }
        return when {
            mimeTypes.size == 1 -> mimeTypes.first()
            // Multiple concrete types that share one top-level type can use the
            // wildcard for that family (e.g. image/png + image/jpeg -> image/*).
            mimeTypes.map { it.substringBefore("/") }.distinct().size == 1 ->
                "${mimeTypes.first().substringBefore("/")}/*"
            else -> "*/*"
        }
    }

    private fun updateAddressBarDisplay() {
        val url = viewModel.currentUrl.value ?: ""
        if (!binding.addressBar.isFocused) {
            binding.addressBar.setText(if (url.isEmpty() || url == "about:blank") "" else UrlUtils.getDisplayUrl(url))
        }
        updateSiteIdentityIcon()
    }

    /**
     * Smart left-side icon for the address bar (Chrome-style).
     * - Focused / empty / new tab → search icon
     * - HTTPS with favicon → favicon + small green lock badge
     * - HTTPS no favicon → green lock icon
     * - HTTP → orange unlock icon + "Not secure" chip
     * - Internal pages → search icon
     */
    private fun updateSiteIdentityIcon() {
        val url = viewModel.currentUrl.value ?: ""
        val isFocused = binding.addressBar.isFocused
        val isEmpty = url.isEmpty() || url == "about:blank"
        val isHttps = url.startsWith("https://")
        val isHttp = url.startsWith("http://")
        val favicon = tabManager.currentTab?.favicon

        // Reset all
        binding.iconSearch.isVisible = false
        binding.iconSecure.isVisible = false
        binding.iconFavicon.isVisible = false
        binding.faviconLockBadge.isVisible = false
        binding.notSecureChip.isVisible = false

        when {
            isFocused || isEmpty -> {
                binding.iconSearch.isVisible = true
                binding.iconSearch.setColorFilter(getColor(R.color.text_secondary))
            }
            isHttp -> {
                // Chrome-style: "Not secure" warning prominent
                binding.iconSecure.isVisible = true
                binding.iconSecure.setImageResource(R.drawable.ic_lock_open)
                binding.iconSecure.setColorFilter(getColor(R.color.warning_orange))
                binding.notSecureChip.isVisible = true
            }
            favicon != null && isHttps -> {
                binding.iconFavicon.isVisible = true
                binding.iconFavicon.setImageBitmap(favicon)
                binding.faviconLockBadge.isVisible = true
            }
            isHttps -> {
                binding.iconSecure.isVisible = true
                binding.iconSecure.setImageResource(R.drawable.ic_lock)
                binding.iconSecure.setColorFilter(getColor(R.color.green_secure))
            }
            else -> {
                binding.iconSearch.isVisible = true
                binding.iconSearch.setColorFilter(getColor(R.color.text_secondary))
            }
        }
    }

    private fun updateNavButtons() {
        viewModel.updateNavState(currentWebView?.canGoBack() ?: false, currentWebView?.canGoForward() ?: false)
    }

    private fun updateTabCountBadge() {
        val count = tabManager.tabCount
        val newText = if (count > 99) "99+" else count.toString()
        if (binding.tabCountBadge.text.toString() != newText) {
            binding.tabCountBadge.text = newText
            // Animate the badge
            binding.tabCountBadge.animate()
                .scaleX(1.3f).scaleY(1.3f)
                .setDuration(100)
                .withEndAction {
                    binding.tabCountBadge.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2f))
                        .start()
                }.start()
        }
    }

    private fun hideFindInPage() {
        viewModel.showFindInPage.value = false
        currentWebView?.clearMatches()
        binding.tvFindCount.text = ""
        binding.tvFindCount.contentDescription = null
        hideKeyboard()
    }

    // Render the find-in-page match counter (Chrome-style). WebView's FindListener
    // reports a ZERO-based activeMatchOrdinal and numberOfMatches; we show 1-based
    // "n/total" on a hit, a localized "No results" when the query matches nothing,
    // and clear it for an empty query. Also publishes a content description so the
    // running count is announced to TalkBack. Defensive against an active ordinal of
    // -1 (WebView reports this transiently before the first match is positioned).
    private fun updateFindCount(activeOrdinal: Int, total: Int) {
        if (total <= 0) {
            binding.tvFindCount.text = getString(R.string.find_no_results)
            binding.tvFindCount.contentDescription = getString(R.string.find_no_results)
            return
        }
        val current = (activeOrdinal + 1).coerceIn(1, total)
        // Use a localized n/total format so RTL locales lay the counter out correctly.
        val label = getString(R.string.find_match_count, current, total)
        binding.tvFindCount.text = label
        binding.tvFindCount.contentDescription = label
    }

    private fun showKeyboard(view: View) {
        view.post { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(binding.root.windowToken, 0)
        currentFocus?.clearFocus()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.insetsController?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
        else window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    @Suppress("DEPRECATION")
    private fun showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.insetsController?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
        else window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private val tabSwitcherLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data
        val tabId = data?.getStringExtra("tab_id")
        val liveTabs = tabManager.tabs.map { it.id }.toSet()
        // Evict any WebView whose tab was closed while the switcher was open.
        webViewPool.keys.toList().forEach {
            if (it !in liveTabs) webViewPool.remove(it)?.let(::safelyDestroyWebView)
        }
        if (data?.getBooleanExtra("closed_all", false) == true ||
            data?.getBooleanExtra("new_tab", false) == true) {
            createNewTab(isIncognito = data.getBooleanExtra("incognito", false))
        } else if (tabId != null) {
            tabManager.switchToTab(tabId)
            tabManager.currentTab?.let { switchToTab(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        currentWebView?.onResume()
        // Coordination contract: returning from Settings (or any other screen) must
        // re-apply web settings (JS, night, privacy) to EVERY pooled WebView so
        // toggles take effect without forcing a manual reload.
        webViewPool.values.forEach { runCatching { it.applySettingsFromPrefs() } }
        // Desktop mode is per-tab, but the Settings screen still exposes a global
        // "Desktop site" switch. Detect a change to that pref while we were away and
        // apply it to the CURRENT tab only (so the toggle visibly takes effect),
        // without disturbing the per-tab preferences of background tabs.
        syncGlobalDesktopPrefToCurrentTab()
    }

    // --- Per-site text zoom ---------------------------------------------------
    //
    // We persist a host -> zoom-percent map so a site keeps the user's chosen zoom
    // across visits/sessions (Chrome behavior). The engine owns the clamp+apply
    // (HelixWebView.applyZoomPercent); MainActivity owns the map. Values are stored
    // as discrete keys ("$PREFIX$host") in the default SharedPreferences so they
    // live alongside the rest of the app's prefs and a future Settings screen can
    // enumerate/clear them. The global default-zoom pref ($KEY_DEFAULT_ZOOM) is
    // owned by the Settings unit; we read it with a 100% fallback so this works
    // whether or not Settings has written it yet.
    //
    // Incognito: per-site zoom is a display preference (no browsing content), but to
    // honor incognito's "leave no trace" contract we neither read nor write the
    // persisted map for incognito tabs — they always use the global default and any
    // in-session zoom change is not stored.

    private fun defaultPrefs(): android.content.SharedPreferences =
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)

    // Default zoom percent (Settings-owned key). Clamped to the engine's band so a
    // hand-edited / out-of-range value can never push a page into an unusable state.
    private fun defaultZoomPercent(): Int =
        runCatching { defaultPrefs().getInt(KEY_DEFAULT_ZOOM, 100) }.getOrDefault(100)
            .coerceIn(HelixWebView.ZOOM_MIN_PERCENT, HelixWebView.ZOOM_MAX_PERCENT)

    // Stable host key for the current page, or null for internal / non-host URLs
    // (about:blank, the start pages) where per-site zoom is meaningless.
    private fun zoomHostForCurrentPage(): String? {
        val url = viewModel.currentUrl.value ?: return null
        if (url.isBlank() || url == "about:blank") return null
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.")
        return host?.takeIf { it.isNotBlank() }
    }

    // Remembered zoom for [host], or null when none is stored. Never consulted for
    // incognito tabs (caller-gated) so private sessions leave no zoom trace.
    private fun zoomForHost(host: String): Int? {
        val v = runCatching { defaultPrefs().getInt(KEY_SITE_ZOOM_PREFIX + host, -1) }.getOrDefault(-1)
        return if (v < 0) null else v.coerceIn(HelixWebView.ZOOM_MIN_PERCENT, HelixWebView.ZOOM_MAX_PERCENT)
    }

    private fun persistZoomForHost(host: String, percent: Int) {
        val clamped = percent.coerceIn(HelixWebView.ZOOM_MIN_PERCENT, HelixWebView.ZOOM_MAX_PERCENT)
        runCatching {
            // A value equal to the global default is stored as a removal so the map
            // stays small and a later default change keeps tracking such sites.
            if (clamped == defaultZoomPercent()) {
                defaultPrefs().edit().remove(KEY_SITE_ZOOM_PREFIX + host).apply()
            } else {
                defaultPrefs().edit().putInt(KEY_SITE_ZOOM_PREFIX + host, clamped).apply()
            }
        }
    }

    // Apply the right zoom to [webView] for the page it just finished loading: the
    // remembered per-host value if any, otherwise the global default. Skipped for
    // incognito tabs and internal pages.
    private fun applyZoomForFinishedPage(webView: HelixWebView) {
        if (webView.isIncognito) {
            webView.applyZoomPercent(defaultZoomPercent())
            return
        }
        val host = zoomHostForCurrentPage()
        val percent = (host?.let { zoomForHost(it) }) ?: defaultZoomPercent()
        webView.applyZoomPercent(percent)
    }

    // The effective default desktop preference for a newly created tab: the global
    // Settings pref, OR'd with the tablet default (tablets browse desktop unless the
    // user has explicitly turned the global switch off via Settings).
    private fun defaultDesktopMode(): Boolean =
        runCatching { Prefs.isDesktopMode(this) }.getOrDefault(false) || isTablet

    // Field tracks the last global desktop pref we observed so we only react to an
    // actual change made in Settings, never re-clobbering a per-tab menu toggle.
    private var lastGlobalDesktopPref: Boolean? = null

    private fun syncGlobalDesktopPrefToCurrentTab() {
        val pref = runCatching { Prefs.isDesktopMode(this) }.getOrNull() ?: return
        val previous = lastGlobalDesktopPref
        lastGlobalDesktopPref = pref
        // First observation just records the baseline; don't override the tab.
        if (previous == null || previous == pref) return
        val tab = tabManager.currentTab ?: return
        if (tab.isDesktopMode == pref) return
        tabManager.setDesktopMode(tab.id, pref)
        viewModel.isDesktopMode.value = pref
        currentWebView?.setDesktopMode(pref)
    }
    override fun onPause() {
        super.onPause()
        // In Picture-in-Picture the foreground WebView's <video> must keep playing
        // inside the PiP window, so we skip pausing it; only background WebViews are
        // paused. WebView.onPause() would otherwise freeze the PiP video.
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        webViewPool.values.forEach { wv ->
            if (inPip && wv === currentWebView) return@forEach
            wv.onPause()
        }
        // Save tabs if restore is enabled
        if (PrivacyManager.isRestoreTabsEnabled(this)) {
            tabManager.saveTabs(this)
        }
        // Suspend inactive tabs if enabled, then reclaim their WebViews so the
        // suspension actually frees memory (TabManager only flips the flag).
        if (PrivacyManager.isSuspendInactiveTabsEnabled(this)) {
            tabManager.suspendInactiveTabs()
            evictSuspendedWebViews()
        }
    }
    override fun onDestroy() {
        // Release any active fullscreen video (custom view + callback) and restore
        // system UI before tearing the pool down, otherwise it leaks.
        exitFullscreenIfActive()
        // Deny any outstanding web/geolocation permission prompts so their callbacks
        // never fire against a destroyed WebView and the page is not left hanging.
        pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, false, false)
        pendingGeolocationCallback = null
        pendingGeolocationOrigin = null
        runCatching { pendingWebPermissionRequest?.deny() }
        pendingWebPermissionRequest = null
        pendingWebPermissionKey = null
        pendingWebPermissionOrigin = null
        // Stop listening for the PiP RemoteAction if we are torn down while in PiP.
        unregisterPipReceiver()
        binding.webViewContainer.removeAllViews()
        currentWebView = null
        // Clear any pending toolbar callbacks holding references to root view.
        headerHideRunnable?.let { binding.root.removeCallbacks(it) }
        headerHideRunnable = null
        webViewPool.values.forEach { safelyDestroyWebView(it) }
        webViewPool.clear()
        tabManager.closeAllIncognito()
        super.onDestroy()
    }

    // --- Edge-swipe gesture navigation (Chrome/Opera-style) ---
    //
    // Tracks ACTION_DOWN inside the left/right edge gutter and, if the user
    // drags far enough horizontally within the gesture window, fires back or
    // forward navigation. We intentionally only consider gestures that start
    // very close to the edge (within EDGE_PX) and require a strong horizontal
    // bias, otherwise normal scroll/pan gestures inside WebView would steal
    // navigation accidentally.
    private var edgeSwipeStartX: Float = 0f
    private var edgeSwipeStartY: Float = 0f
    private var edgeSwipeFromLeft: Boolean = false
    private var edgeSwipeTracking: Boolean = false
    private val edgeGutterPx by lazy { (resources.displayMetrics.density * 18).toInt() }
    private val edgeSwipeMinDistPx by lazy { (resources.displayMetrics.density * 80).toInt() }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                val w = window?.decorView?.width ?: 0
                edgeSwipeStartX = ev.rawX
                edgeSwipeStartY = ev.rawY
                edgeSwipeFromLeft = ev.rawX <= edgeGutterPx
                val fromRight = w > 0 && ev.rawX >= w - edgeGutterPx
                edgeSwipeTracking = edgeSwipeFromLeft || fromRight
            }
            android.view.MotionEvent.ACTION_UP -> {
                if (edgeSwipeTracking) {
                    val dx = ev.rawX - edgeSwipeStartX
                    val dy = kotlin.math.abs(ev.rawY - edgeSwipeStartY)
                    val webView = currentWebView
                    if (webView != null && dy < kotlin.math.abs(dx) / 2 &&
                        kotlin.math.abs(dx) >= edgeSwipeMinDistPx) {
                        if (edgeSwipeFromLeft && dx > 0 && webView.canGoBack()) {
                            webView.goBack(); edgeSwipeTracking = false; return true
                        }
                        if (!edgeSwipeFromLeft && dx < 0 && webView.canGoForward()) {
                            webView.goForward(); edgeSwipeTracking = false; return true
                        }
                    }
                }
                edgeSwipeTracking = false
            }
            android.view.MotionEvent.ACTION_CANCEL -> edgeSwipeTracking = false
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Cap the number of live WebViews. Iterating an access-ordered LinkedHashMap
     * yields least-recently-used first, so we destroy from the front until the
     * pool is within MAX_LIVE_WEBVIEWS, never evicting the foreground tab.
     */
    private fun enforceWebViewPoolCap(currentTabId: String) {
        if (webViewPool.size <= MAX_LIVE_WEBVIEWS) return
        val evictable = webViewPool.keys.filter { it != currentTabId }
        var toEvict = webViewPool.size - MAX_LIVE_WEBVIEWS
        for (id in evictable) {
            if (toEvict <= 0) break
            webViewPool.remove(id)?.let(::safelyDestroyWebView)
            toEvict--
        }
    }

    /**
     * Destroy WebViews for tabs the TabManager has marked suspended (called after
     * suspendInactiveTabs). Never touches the current tab. Recreation happens
     * lazily from tab.url on next attach.
     */
    private fun evictSuspendedWebViews() {
        val currentId = tabManager.currentTab?.id
        webViewPool.keys.toList().forEach { id ->
            if (id == currentId) return@forEach
            if (tabManager.findTab(id)?.isSuspended == true) {
                webViewPool.remove(id)?.let(::safelyDestroyWebView)
            }
        }
    }

    /**
     * Free all background WebViews, keeping only the foreground tab. Called on
     * memory pressure so we shed native WebView resources before the system
     * kills the process.
     */
    private fun evictAllBackgroundWebViews() {
        val currentId = tabManager.currentTab?.id
        webViewPool.keys.toList().forEach { id ->
            if (id == currentId) return@forEach
            webViewPool.remove(id)?.let(::safelyDestroyWebView)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            evictAllBackgroundWebViews()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onLowMemory() {
        super.onLowMemory()
        evictAllBackgroundWebViews()
    }

    /**
     * Tear down a WebView so it cannot retain callbacks, the activity, or
     * native resources after removal. Safe to call from any path (tab close,
     * activity destroy, low-memory eviction).
     */
    private fun safelyDestroyWebView(webView: HelixWebView) {
        try {
            // Wipe per-WebView incognito data (cookies/DOM storage for visited
            // hosts) before teardown. No-ops for non-incognito WebViews. This is
            // the single chokepoint every close path routes through, so closing
            // an incognito tab now actually clears its private data.
            webView.clearIncognitoData()
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.pauseTimers()
            webView.clearHistory()
            webView.removeAllViews()
            // Detach from any parent so destroy() is legal.
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            // Null out delegates so any in-flight callback no longer reaches us.
            webView.webViewClient = WebViewClient()
            webView.webChromeClient = null
            webView.setDownloadListener(null)
            webView.setFindListener(null)
            webView.setOnLongClickListener(null)
            webView.setOnTouchListener(null)
            webView.destroy()
        } catch (t: Throwable) {
            android.util.Log.w("MainActivity", "safelyDestroyWebView failed", t)
        }
    }

    private fun setupSuggestions() {
        suggestionsAdapter = SuggestionsAdapter(
            onSuggestionClick = { url ->
                loadUrl(url)
                binding.addressBar.clearFocus()
                hideKeyboard()
                binding.suggestionsRecyclerView.isVisible = false
            },
            onInsertClick = { url ->
                binding.addressBar.setText(url)
                binding.addressBar.setSelection(url.length)
            }
        )
        binding.suggestionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = suggestionsAdapter
        }

        binding.addressBar.addTextChangedListener(object : TextWatcher {
            // Whether THIS text change extended the previous user-typed text by
            // appending at the end (forward typing) — the only case where inline
            // autocomplete may be applied. A deletion, mid-string edit, paste, or
            // selection-replace must never trigger autocompletion (don't fight
            // backspace). Captured in onTextChanged where the deltas are available.
            private var forwardTyping = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (applyingInlineCompletion) return
                val newText = s?.toString() ?: ""
                // Forward typing == purely additive at the very end: the previous
                // user text is a strict prefix of the new text and the change is an
                // insertion (count > before) at/after that prefix. This rejects
                // backspace (before > count) and mid-string edits.
                forwardTyping = newText.length > lastUserTypedText.length &&
                    newText.startsWith(lastUserTypedText) &&
                    start >= lastUserTypedText.length &&
                    before == 0
            }

            override fun afterTextChanged(s: Editable?) {
                if (applyingInlineCompletion) return
                if (binding.addressBar.isFocused) {
                    val raw = s?.toString() ?: ""
                    // The visible text is now the user's own typed text (we have not
                    // appended anything yet for this keystroke). Record it as the
                    // canonical prefix and try to apply any completion the ViewModel
                    // previously emitted for exactly this prefix.
                    lastUserTypedText = raw
                    if (forwardTyping) {
                        applyPendingInlineCompletion(raw)
                    } else {
                        // Deletion / edit: drop any stale completion so the next
                        // emission must re-qualify before we ever append again.
                        pendingInlineCompletion = null
                    }
                    val query = raw.trim()
                    viewModel.fetchSuggestions(query)
                    // Swap mic <-> clear-X and toggle the paste-and-go chip as the
                    // field gains/loses content (Chrome behavior).
                    updateAddressBarEndIcons()
                }
            }
        })

        viewModel.suggestions.observe(this) { suggestions ->
            if (binding.addressBar.isFocused && suggestions.isNotEmpty()) {
                suggestionsAdapter?.submitList(suggestions)
                binding.suggestionsRecyclerView.isVisible = true
            } else {
                binding.suggestionsRecyclerView.isVisible = false
            }
        }

        // Inline autocomplete: the ViewModel posts the trailing completion suffix
        // (e.g. typed "git" -> "hub.com") on the main thread in lockstep with the
        // suggestion list. We stash it and apply it on the NEXT forward keystroke,
        // and also opportunistically now if the field currently holds exactly the
        // bare user prefix with no selection, so the grey-out appears promptly.
        viewModel.inlineCompletion.observe(this) { suffix ->
            pendingInlineCompletion = suffix?.takeIf { it.isNotEmpty() }
            if (!binding.addressBar.isFocused) return@observe
            val current = binding.addressBar.text?.toString() ?: ""
            // Only auto-apply when the field is exactly the user's typed prefix
            // (nothing already appended/selected). Avoids re-appending on top of an
            // existing completion or clobbering a manual selection.
            if (current == lastUserTypedText) {
                applyPendingInlineCompletion(current)
            }
        }
    }

    // Append [pendingInlineCompletion] to [typed] and select the appended range so
    // the next keystroke overwrites it (Chrome inline autocomplete). No-ops unless
    // the completion is still valid for this exact prefix. The re-entrancy guard
    // stops the resulting setText/setSelection from re-triggering the TextWatcher.
    private fun applyPendingInlineCompletion(typed: String) {
        val suffix = pendingInlineCompletion ?: return
        if (typed.isEmpty() || suffix.isEmpty()) return
        // The suffix must not already be present (e.g. user typed the whole host).
        if (typed.endsWith(suffix, ignoreCase = true)) return
        val completed = typed + suffix
        applyingInlineCompletion = true
        try {
            binding.addressBar.setText(completed)
            // Highlight only the appended portion; the cursor sits at the prefix
            // end so a real keystroke replaces the whole selection.
            binding.addressBar.setSelection(typed.length, completed.length)
        } catch (t: Throwable) {
            android.util.Log.w("MainActivity", "inline autocomplete apply failed", t)
        } finally {
            applyingInlineCompletion = false
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.apply {
            setColorSchemeColors(getColor(R.color.accent_purple))
            setProgressBackgroundColorSchemeColor(getColor(R.color.surface_container))
            setOnRefreshListener {
                currentWebView?.reload()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        // Swipe on address bar to switch tabs
        var startX = 0f
        binding.addressBar.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    false // Allow normal touch handling
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - startX
                    if (kotlin.math.abs(deltaX) > 120 && !v.isFocused) {
                        if (deltaX > 0 && tabManager.currentIndex > 0) {
                            // Swipe right -> previous tab
                            val prevTab = tabManager.tabs[tabManager.currentIndex - 1]
                            switchToTab(prevTab)
                            performHapticFeedback()
                        } else if (deltaX < 0 && tabManager.currentIndex < tabManager.tabCount - 1) {
                            // Swipe left -> next tab
                            val nextTab = tabManager.tabs[tabManager.currentIndex + 1]
                            switchToTab(nextTab)
                            performHapticFeedback()
                        }
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    private fun performHapticFeedback() {
        binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun animateClick(view: View) {
        view.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }.start()
    }

    /**
     * JavaScript bridge for the internal start page. Exposes a single method
     * that focuses the real address bar so the start-page search box behaves
     * like every shortcut-grid browser's omnibox. Guarded so it only acts on
     * the internal new-tab page (about:blank) and never affects real sites.
     */
    private inner class HelixHomeBridge(private val webView: HelixWebView) {
        @android.webkit.JavascriptInterface
        fun focusAddressBar() {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                // Only honor the call when the visible page is the start page
                // and this WebView is the foreground one.
                val onStartPage = webView.url == null || webView.url == "about:blank"
                if (webView === currentWebView && onStartPage) {
                    binding.addressBar.requestFocus()
                    showKeyboard(binding.addressBar)
                }
            }
        }

        // Open a most-visited tile. The url originates from the LOCAL history DB
        // (never from page content), but we still scheme-allow-list it and only act
        // from the foreground start page, so a real site that somehow reached this
        // bridge cannot use it to navigate to arbitrary schemes (file:, intent:, …).
        @android.webkit.JavascriptInterface
        fun openUrl(url: String?) {
            val target = url?.trim().orEmpty()
            if (target.isEmpty() || target.length > MAX_INCOMING_URL_LENGTH) return
            val scheme = runCatching { Uri.parse(target).scheme?.lowercase() }.getOrNull()
            if (scheme != "http" && scheme != "https") return
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val onStartPage = webView.url == null || webView.url == "about:blank"
                if (webView === currentWebView && onStartPage) {
                    loadUrl(target)
                }
            }
        }
    }

    companion object {
        const val REQUEST_TAB_SWITCHER = 1001
        private const val MAX_INCOMING_URL_LENGTH = 4096
        // PiP play/pause RemoteAction broadcast action. Package-scoped + delivered to
        // a non-exported, dynamically-registered receiver, so it is private to us.
        private const val ACTION_PIP_TOGGLE = "com.helix.browser.action.PIP_TOGGLE"
        // Default-zoom pref key (OWNED by the Settings unit as
        // SettingsActivity.PREF_DEFAULT_ZOOM = "default_page_zoom"; read here with a
        // 100% fallback). Kept in sync by value so the two units cannot drift without
        // a deliberate change. Per-site overrides are stored under
        // KEY_SITE_ZOOM_PREFIX + host.
        private const val KEY_DEFAULT_ZOOM = "default_page_zoom"
        private const val KEY_SITE_ZOOM_PREFIX = "site_zoom_"
        private const val MAX_LIVE_WEBVIEWS = 6
        // Most-visited tiles: 8 fills the 4-column start-page grid exactly.
        private const val TOP_SITES_COUNT = 8
        private const val MAX_TILE_LABEL_LEN = 24
        // Disabled back/forward alpha — raised from 0.4 so the disabled state stays
        // perceptible on text_primary over the dark background (contrast guidance).
        private const val DISABLED_NAV_ALPHA = 0.5f
        // Screenshot full-page capture pixel cap (~24 MP -> ~96 MB ARGB_8888). Long
        // pages are clipped to the visible width * this/width so the buffer is bounded.
        private const val SCREENSHOT_MAX_PIXELS = 24_000_000
        // Keep only the N most-recent screenshots / cached PDFs in their cache subdirs.
        private const val SCREENSHOT_KEEP_COUNT = 10
        private const val PDF_CACHE_KEEP_COUNT = 5
        // Hard cap on a cached PDF download (50 MB) to bound cache + memory use.
        private const val MAX_PDF_BYTES = 50L * 1024L * 1024L
    }
}

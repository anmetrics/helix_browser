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

    private val webViewPool = LinkedHashMap<String, HelixWebView>()
    private var currentWebView: HelixWebView? = null

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
        
        // Initialize desktop mode state based on device type if not already set
        if (viewModel.isDesktopMode.value == null) {
            viewModel.isDesktopMode.value = isTablet
        }
        
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
        // Only honor schemes the browser actually loads. Reject file://, intent://,
        // javascript:, content:// and other privileged/dangerous schemes from
        // untrusted callers.
        val scheme = runCatching { Uri.parse(trimmed).scheme?.lowercase() }.getOrNull()
        return when (scheme) {
            "http", "https", "about", "data" -> trimmed
            null -> UrlUtils.formatUrl(trimmed) // treat bare input as search/url
            else -> null
        }
    }

    private fun setupAddressBar() {
        binding.addressBar.apply {
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    setText(viewModel.currentUrl.value)
                    selectAll()
                    binding.btnCancelSearch.isVisible = true
                    // Show mic when address bar is focused (Chrome behavior).
                    // It hides as the user types in the TextWatcher below.
                    binding.btnVoiceSearch.isVisible = text.isNullOrEmpty() &&
                        android.speech.SpeechRecognizer.isRecognitionAvailable(this@MainActivity)
                    binding.btnBookmark.isVisible = false
                    binding.btnRefresh.isVisible = false
                    updateSiteIdentityIcon()
                    showKeyboard(this)
                } else {
                    updateAddressBarDisplay()
                    binding.btnCancelSearch.isVisible = false
                    binding.btnVoiceSearch.isVisible = false
                    binding.btnBookmark.isVisible = true
                    binding.btnRefresh.isVisible = true
                    binding.suggestionsRecyclerView.isVisible = false
                }
            }
            setOnClickListener {
                if (isFocused) showKeyboard(this)
            }
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    val input = text.toString().trim()
                    if (input.isNotEmpty()) {
                        val url = UrlUtils.formatUrl(input)
                        loadUrl(url)
                        clearFocus()
                        hideKeyboard()
                    }
                    true
                } else false
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
                Toast.makeText(this, "Voice search not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                currentWebView?.let { webView ->
                    val tab = tabManager.currentTab
                    if (tab != null && webView.width > 0 && webView.height > 0) {
                        try {
                            val bitmap = android.graphics.Bitmap.createBitmap(webView.width, webView.height, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            webView.draw(canvas)
                            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, webView.width / 4, webView.height / 4, true)
                            tab.thumbnail = scaledBitmap
                            if (bitmap != scaledBitmap) bitmap.recycle()
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
                val intent = Intent(this, TabSwitcherActivity::class.java)
                tabSwitcherLauncher.launch(intent)
                overridePendingTransitionCompat(R.anim.slide_up, R.anim.fade_out)
            }
        }
        binding.btnMenu.setOnClickListener { showMoreOptionsMenu() }
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
            binding.btnBack.alpha = if (can) 1f else 0.4f
        }
        viewModel.canGoForward.observe(this) { can ->
            binding.btnForward.alpha = if (can) 1f else 0.4f
        }
        viewModel.showFindInPage.observe(this) { show ->
            binding.findInPageBar.isVisible = show
            if (show) binding.findInPageInput.requestFocus()
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
        binding.findInPageInput.setOnEditorActionListener { v, _, _ ->
            currentWebView?.findAllAsync(v.text.toString())
            true
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
                } else {
                    currentWebView?.findAllAsync(q)
                }
            }
        })
    }

    fun createNewTab(url: String = "", isIncognito: Boolean = false) {
        val tab = tabManager.addTab(isIncognito, url)
        switchToTab(tab)
    }

    private fun switchToTab(tab: BrowserTab) {
        tabManager.switchToTab(tab.id)
        attachWebViewForTab(tab)
        updateTabCountBadge()

        viewModel.isIncognito.value = tab.isIncognito
        binding.incognitoIndicator.isVisible = tab.isIncognito
        binding.root.setBackgroundColor(getColor(if (tab.isIncognito) R.color.incognito_background else R.color.background))
    }

    private fun attachWebViewForTab(tab: BrowserTab) {
        binding.webViewContainer.removeAllViews()
        val webView = webViewPool.getOrPut(tab.id) { createWebViewForTab(tab) }
        currentWebView = webView
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        binding.webViewContainer.addView(webView, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        webView.requestFocus()
        viewModel.updateNavState(webView.canGoBack(), webView.canGoForward())
        viewModel.currentUrl.value = webView.url ?: tab.url
        viewModel.currentTitle.value = webView.title ?: tab.title
        updateAddressBarDisplay()

        // Sync desktop mode state when switching tabs
        if (viewModel.isDesktopMode.value == true) {
            webView.setDesktopMode(true)
        }

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
                    headerHideRunnable?.let { binding.root.removeCallbacks(it) }
                    if (url != "about:blank" && url.isNotEmpty()) {
                        headerHideRunnable = Runnable { setToolbarScrollable(true) }.also {
                            binding.root.postDelayed(it, 5000)
                        }
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
            onShowFileChooser = { callback, _ ->
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                fileChooserLauncher.launch("*/*")
                true
            },
            onEnterFullscreen = { view, callback ->
                fullscreenView = view
                fullscreenCallback = callback
                binding.webViewContainer.addView(view)
                hideSystemUI()
            },
            onExitFullscreen = {
                fullscreenView?.let { binding.webViewContainer.removeView(it) }
                fullscreenCallback?.onCustomViewHidden()
                fullscreenView = null
                fullscreenCallback = null
                showSystemUI()
            },
            onGeolocationPermission = { origin, callback -> requestGeolocationPermission(origin, callback) },
            onWebPermissionRequest = { request -> handleWebPermissionRequest(request) },
            isAdBlockEnabled = { Prefs.isAdBlockEnabled(this) },
            isBlockPopupsEnabled = { PrivacyManager.isBlockPopupsEnabled(this) }
        )
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ -> downloadFile(url, userAgent, contentDisposition, mimeType) }
        webView.setFindListener { active, total, doneCounting ->
            // FindListener fires off the UI thread; only update if this WebView
            // is the visible one (otherwise we'd overwrite the count for the
            // foreground tab).
            if (!doneCounting) return@setFindListener
            if (tabManager.currentTab?.id == tabId) runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.tvFindCount.text = if (total > 0) "${active + 1}/$total" else "0/0"
            }
        }
        setupWebViewContextMenu(webView)
        if (tab.isIncognito) webView.setIncognitoMode(true)
        if (tab.url.isNotEmpty()) webView.loadUrl(tab.url)
        else webView.loadDataWithBaseURL("about:blank", buildNewTabHtml(), "text/html", "UTF-8", null)
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
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');
*{margin:0;padding:0;box-sizing:border-box;}
body{font-family:'Inter',-apple-system,BlinkMacSystemFont,sans-serif;background:#0F0F0F;min-height:100vh;display:flex;flex-direction:column;align-items:center;padding-top:15vh;color:#F0F0F0;-webkit-user-select:none;-webkit-tap-highlight-color:transparent;}
.logo{font-size:44px;font-weight:700;margin-bottom:4px;color:#F0F0F0;letter-spacing:-1.5px;}
.logo span{background:linear-gradient(135deg,#7B68EE,#49CCF9);-webkit-background-clip:text;-webkit-text-fill-color:transparent;}
.tagline{font-size:13px;color:#636366;margin-bottom:40px;font-weight:400;letter-spacing:0.5px;}
.search-box{width:88%;max-width:380px;background:#2A2A2A;border-radius:24px;padding:14px 20px;display:flex;align-items:center;gap:12px;margin-bottom:40px;transition:background 0.2s,box-shadow 0.2s;}
.search-box:active{background:#333;box-shadow:0 0 0 2px #7B68EE40;}
.search-box svg{width:18px;height:18px;fill:#636366;flex-shrink:0;}
.search-box span{color:#636366;font-size:14px;font-weight:400;}
.shortcuts{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;width:88%;max-width:380px;}
.shortcut{display:flex;flex-direction:column;align-items:center;gap:8px;padding:14px 6px;border-radius:16px;background:#1A1A1A;text-decoration:none;color:#F0F0F0;font-size:11px;font-weight:500;transition:all 0.15s ease;border:1px solid transparent;}
.shortcut:active{background:#2A2A2A;transform:scale(0.96);border-color:#333;}
.shortcut-icon{width:44px;height:44px;border-radius:14px;background:#1E1E1E;border:1px solid #2A2A2A;display:flex;align-items:center;justify-content:center;font-size:20px;font-weight:600;transition:all 0.15s;}
.shortcut:active .shortcut-icon{border-color:#7B68EE40;}
.s-google{color:#4285F4;}.s-yt{color:#FF0000;}.s-gh{color:#F0F0F0;}.s-fb{color:#1877F2;}
.s-x{color:#F0F0F0;}.s-reddit{color:#FF4500;}.s-wiki{color:#F0F0F0;}.s-netflix{color:#E50914;}
</style></head>
<body>
<div class="logo"><span>H</span>elix</div>
<div class="tagline">${getString(R.string.fast_secure_private)}</div>
<div class="search-box" onclick="window.location.href='about:blank'">
<svg viewBox="0 0 24 24"><path d="M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>
<span>${getString(R.string.search_or_type_url)}</span>
</div>
<div class="shortcuts">
<a class="shortcut" href="https://google.com"><div class="shortcut-icon s-google">G</div>Google</a>
<a class="shortcut" href="https://youtube.com"><div class="shortcut-icon s-yt">&#9654;</div>YouTube</a>
<a class="shortcut" href="https://github.com"><div class="shortcut-icon s-gh">&#10023;</div>GitHub</a>
<a class="shortcut" href="https://facebook.com"><div class="shortcut-icon s-fb">f</div>Facebook</a>
<a class="shortcut" href="https://twitter.com"><div class="shortcut-icon s-x">&#120143;</div>X</a>
<a class="shortcut" href="https://reddit.com"><div class="shortcut-icon s-reddit">r/</div>Reddit</a>
<a class="shortcut" href="https://wikipedia.org"><div class="shortcut-icon s-wiki">W</div>Wikipedia</a>
<a class="shortcut" href="https://netflix.com"><div class="shortcut-icon s-netflix">N</div>Netflix</a>
</div>
</body></html>""".trimIndent()

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
        view.findViewById<View>(R.id.ic_check_desktop).isVisible = viewModel.isDesktopMode.value == true
        view.findViewById<View>(R.id.menu_desktop_site).setOnClickListener {
            val isDesktop = viewModel.isDesktopMode.value?.not() ?: false
            viewModel.isDesktopMode.value = isDesktop
            
            // Apply to all active WebViews
            webViewPool.values.forEach { it.setDesktopMode(isDesktop) }
            
            dialog.dismiss()
        }
        // Text zoom
        val tvZoomLevel = view.findViewById<android.widget.TextView>(R.id.tvZoomLevel)
        val currentZoom = currentWebView?.settings?.textZoom ?: 100
        tvZoomLevel.text = "${currentZoom}%"
        view.findViewById<View>(R.id.btnZoomIn).setOnClickListener {
            val newZoom = ((currentWebView?.settings?.textZoom ?: 100) + 10).coerceAtMost(200)
            currentWebView?.settings?.textZoom = newZoom
            tvZoomLevel.text = "${newZoom}%"
        }
        view.findViewById<View>(R.id.btnZoomOut).setOnClickListener {
            val newZoom = ((currentWebView?.settings?.textZoom ?: 100) - 10).coerceAtLeast(50)
            currentWebView?.settings?.textZoom = newZoom
            tvZoomLevel.text = "${newZoom}%"
        }
        view.findViewById<View>(R.id.menu_share).setOnClickListener { shareCurrentPage(); dialog.dismiss() }
        view.findViewById<View>(R.id.menu_save_page).setOnClickListener { savePageAsArchive(); dialog.dismiss() }
        view.findViewById<View>(R.id.menu_print).setOnClickListener { printCurrentPage(); dialog.dismiss() }
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

    private fun printCurrentPage() {
        val webView = currentWebView ?: return
        val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val printAdapter = webView.createPrintDocumentAdapter(viewModel.currentTitle.value ?: "Page")
        printManager.print(viewModel.currentTitle.value ?: "Helix Browser", printAdapter, null)
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
                    val dark = Prefs.isDarkMode(this) ||
                        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                            android.content.res.Configuration.UI_MODE_NIGHT_YES
                    com.helix.browser.engine.ReaderMode.enter(wv, dark)
                }
            }
        }
    }

    private fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            !packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this, R.string.pip_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        // Best-effort: pause any visible playback before suspending the
        // activity into a PiP window. WebView keeps playing its <video>
        // element inside the PiP container.
        val params = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
            .build()
        try {
            enterPictureInPictureMode(params)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.pip_unsupported, Toast.LENGTH_SHORT).show()
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

        // Cookies count (best-effort)
        val cookieCount = try {
            android.webkit.CookieManager.getInstance().getCookie(url)?.split(";")?.size ?: 0
        } catch (_: Exception) { 0 }
        pageInfoCookiesDetail.text = if (cookieCount > 0) "$cookieCount cookies in use" else "No cookies"

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
                    android.webkit.CookieManager.getInstance().setCookie(url, "")
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

    private fun shareCurrentPage() {
        val url = viewModel.currentUrl.value ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, viewModel.currentTitle.value ?: url)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
    }

    private fun downloadFile(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            return
        }
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription(getString(R.string.downloading_via_helix))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            addRequestHeader("User-Agent", userAgent)
            allowScanningByMediaScanner()
        }
        (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        Toast.makeText(this, getString(R.string.download_started, fileName), Toast.LENGTH_SHORT).show()
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
        hideKeyboard()
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

    override fun onResume() { super.onResume() ; currentWebView?.onResume() }
    override fun onPause() {
        super.onPause()
        webViewPool.values.forEach { it.onPause() }
        // Save tabs if restore is enabled
        if (PrivacyManager.isRestoreTabsEnabled(this)) {
            tabManager.saveTabs(this)
        }
        // Suspend inactive tabs if enabled
        if (PrivacyManager.isSuspendInactiveTabsEnabled(this)) {
            tabManager.suspendInactiveTabs()
        }
    }
    override fun onDestroy() {
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
     * Tear down a WebView so it cannot retain callbacks, the activity, or
     * native resources after removal. Safe to call from any path (tab close,
     * activity destroy, low-memory eviction).
     */
    private fun safelyDestroyWebView(webView: HelixWebView) {
        try {
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
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (binding.addressBar.isFocused) {
                    val query = s?.toString()?.trim() ?: ""
                    viewModel.fetchSuggestions(query)
                    // Mic visible only while the field is empty — matches
                    // Chrome's behavior of swapping mic for the clear-X.
                    binding.btnVoiceSearch.isVisible = query.isEmpty() &&
                        android.speech.SpeechRecognizer.isRecognitionAvailable(this@MainActivity)
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

    companion object {
        const val REQUEST_TAB_SWITCHER = 1001
        private const val MAX_INCOMING_URL_LENGTH = 4096
    }
}

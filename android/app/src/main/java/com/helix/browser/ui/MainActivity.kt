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
        val url = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(android.app.SearchManager.QUERY)
                ?.let { UrlUtils.buildSearchQuery(it, Prefs.getSearchEngine(this)) }
            else -> null
        }
        if (url != null) {
            if (tabManager.tabCount == 0) {
                createNewTab(url)
            } else {
                loadUrl(url)
            }
        }
    }

    private fun setupAddressBar() {
        binding.addressBar.apply {
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    setText(viewModel.currentUrl.value)
                    selectAll()
                    binding.btnCancelSearch.isVisible = true
                    binding.btnVoiceSearch.isVisible = false
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
                startActivityForResult(intent, REQUEST_TAB_SWITCHER)
                overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
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
                        webViewPool.remove(tab.id)?.destroy()
                        binding.webViewContainer.removeAllViews()
                        currentWebView = null
                        createNewTab()
                    }
                } else {
                    // Just clean up the webview
                    webViewPool.remove(tab.id)?.destroy()
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
        webView.webViewClient = HelixWebViewClient(
            onPageStarted = { url, _ ->
                if (tabManager.currentTab?.id == tab.id) runOnUiThread {
                    viewModel.onPageStarted(url)
                    updateNavButtons()
                    headerHideRunnable?.let { binding.root.removeCallbacks(it) }
                    setToolbarScrollable(false)
                }
            },
            onPageFinished = { url ->
                tab.url = url
                tab.title = webView.title ?: url
                if (tabManager.currentTab?.id == tab.id) runOnUiThread {
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
                if (tabManager.currentTab?.id == tab.id) runOnUiThread { viewModel.isLoading.value = false }
            },
            isAdBlockEnabled = { Prefs.isAdBlockEnabled(this) },
            isTrackerBlockEnabled = { PrivacyManager.isBlockTrackersEnabled(this) },
            isHttpsUpgradeEnabled = { PrivacyManager.isHttpsUpgradeEnabled(this) },
            getPrivacyScripts = { PrivacyManager.getPrivacyScripts(this) },
            onTrackerBlocked = { PrivacyManager.incrementTrackersBlocked(this) }
        )
        // Apply third-party cookie policy
        PrivacyManager.applyThirdPartyCookiePolicy(this, webView)
        webView.webChromeClient = HelixWebChromeClient(
            onProgressChanged = { progress -> if (tabManager.currentTab?.id == tab.id) runOnUiThread { viewModel.onProgressChanged(progress) } },
            onTitleReceived = { title ->
                tab.title = title
                if (tabManager.currentTab?.id == tab.id) runOnUiThread { 
                    viewModel.currentTitle.value = title 
                    // Force refresh tab adapter for title update
                    desktopTabAdapter?.notifyItemChanged(tabManager.currentIndex)
                }
            },
            onFaviconReceived = { favicon ->
                tab.favicon = favicon
                if (tabManager.currentTab?.id == tab.id) runOnUiThread {
                    // Force refresh tab adapter for favicon update
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
            onGeolocationPermission = { origin, callback -> callback.invoke(origin, true, false) },
            isAdBlockEnabled = { Prefs.isAdBlockEnabled(this) }
        )
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ -> downloadFile(url, userAgent, contentDisposition, mimeType) }
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
            val recent = tabManager.recentlyClosed.firstOrNull()
            if (recent != null) {
                createNewTab(recent.url, recent.isIncognito)
            } else {
                Toast.makeText(this, "No recently closed tabs", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
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
        view.findViewById<View>(R.id.menu_print).setOnClickListener { printCurrentPage(); dialog.dismiss() }
        view.findViewById<View>(R.id.menu_add_to_home).setOnClickListener { addToHomeScreen(); dialog.dismiss() }
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

    private fun addToHomeScreen() {
        val url = viewModel.currentUrl.value ?: return
        val title = viewModel.currentTitle.value ?: url
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
            if (shortcutManager.isRequestPinShortcutSupported) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse(url)
                }
                val shortcut = ShortcutInfo.Builder(this, url)
                    .setShortLabel(title)
                    .setLongLabel(title)
                    .setIcon(Icon.createWithResource(this, R.drawable.ic_helix_logo))
                    .setIntent(intent)
                    .build()
                shortcutManager.requestPinShortcut(shortcut, null)
            }
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TAB_SWITCHER && resultCode == RESULT_OK) {
            val tabId = data?.getStringExtra("tab_id")
            val liveTabs = tabManager.tabs.map { it.id }.toSet()
            webViewPool.keys.toList().forEach { if (it !in liveTabs) webViewPool.remove(it)?.destroy() }
            if (data?.getBooleanExtra("closed_all", false) == true || data?.getBooleanExtra("new_tab", false) == true) createNewTab(isIncognito = data.getBooleanExtra("incognito", false))
            else if (tabId != null) {
                tabManager.switchToTab(tabId)
                tabManager.currentTab?.let { switchToTab(it) }
            }
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
        // Remove all WebViews from their parent before destroying to prevent memory leaks
        binding.webViewContainer.removeAllViews()
        currentWebView = null
        webViewPool.values.forEach { webView ->
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.webChromeClient = null
            webView.removeAllViews()
            webView.destroy()
        }
        webViewPool.clear()
        tabManager.closeAllIncognito()
        super.onDestroy()
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

    companion object { const val REQUEST_TAB_SWITCHER = 1001 }
}

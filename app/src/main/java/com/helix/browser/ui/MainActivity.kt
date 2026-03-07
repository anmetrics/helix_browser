package com.helix.browser.ui

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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.helix.browser.HelixApp
import com.helix.browser.R
import com.helix.browser.databinding.ActivityMainBinding
import com.helix.browser.engine.HelixWebChromeClient
import com.helix.browser.engine.HelixWebView
import com.helix.browser.engine.HelixWebViewClient
import com.helix.browser.tabs.BrowserTab
import com.helix.browser.utils.Prefs
import com.helix.browser.utils.UrlUtils
import com.helix.browser.viewmodel.BrowserViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: BrowserViewModel by viewModels()
    private lateinit var tabManager: com.helix.browser.tabs.TabManager

    private var currentWebView: HelixWebView? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private var fullscreenView: View? = null
    private var fullscreenCallback: android.webkit.WebChromeClient.CustomViewCallback? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        fileChooserCallback?.onReceiveValue(uris.toTypedArray())
        fileChooserCallback = null
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handle */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tabManager = (application as HelixApp).tabManager

        setupAddressBar()
        setupBottomNavigation()
        setupObservers()
        handleIntent(intent)

        // If no tabs yet, create first tab
        if (tabManager.tabCount == 0) {
            createNewTab()
        }

        // Handle back press
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
                } else {
                    updateAddressBarDisplay()
                    binding.btnCancelSearch.isVisible = false
                }
            }

            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO ||
                    event?.keyCode == KeyEvent.KEYCODE_ENTER
                ) {
                    val input = text.toString().trim()
                    if (input.isNotEmpty()) {
                        val engine = Prefs.getSearchEngine(this@MainActivity)
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

        binding.btnRefresh.setOnClickListener {
            if (viewModel.isLoading.value == true) {
                currentWebView?.stopLoading()
            } else {
                currentWebView?.reload()
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.btnBack.setOnClickListener { currentWebView?.goBack() }
        binding.btnForward.setOnClickListener { currentWebView?.goForward() }
        binding.btnHome.setOnClickListener {
            loadUrl(Prefs.getHomepage(this))
        }
        binding.btnTabs.setOnClickListener {
            val intent = Intent(this, TabSwitcherActivity::class.java)
            startActivityForResult(intent, REQUEST_TAB_SWITCHER)
        }
        binding.btnMenu.setOnClickListener { showMoreOptionsMenu() }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.isVisible = loading
            binding.btnRefresh.setImageResource(
                if (loading) R.drawable.ic_close else R.drawable.ic_refresh
            )
        }

        viewModel.loadingProgress.observe(this) { progress ->
            binding.progressBar.progress = progress
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
            binding.btnBookmark.setImageResource(
                if (bookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
            )
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

        // Find in page actions
        binding.btnFindNext.setOnClickListener {
            currentWebView?.findNext(true)
        }
        binding.btnFindPrev.setOnClickListener {
            currentWebView?.findNext(false)
        }
        binding.btnCloseFindInPage.setOnClickListener {
            hideFindInPage()
        }
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
        loadWebViewForCurrentTab()
        updateTabCountBadge()

        // Update incognito state
        viewModel.isIncognito.value = tab.isIncognito
        binding.incognitoIndicator.isVisible = tab.isIncognito
        if (tab.isIncognito) {
            binding.root.setBackgroundResource(R.color.incognito_background)
        } else {
            binding.root.setBackgroundResource(android.R.color.transparent)
        }
    }

    private fun loadWebViewForCurrentTab() {
        val tab = tabManager.currentTab ?: return

        // Remove old WebView
        binding.webViewContainer.removeAllViews()

        // Create new WebView for this tab if needed
        val webView = HelixWebView(this).also {
            currentWebView = it
        }

        webView.webViewClient = HelixWebViewClient(
            onPageStarted = { url, _ ->
                runOnUiThread {
                    viewModel.onPageStarted(url)
                    updateNavButtons()
                }
            },
            onPageFinished = { url ->
                runOnUiThread {
                    viewModel.onPageFinished(url, webView.title ?: "")
                    updateNavButtons()
                }
            },
            onPageError = { url, code, description ->
                runOnUiThread {
                    viewModel.isLoading.value = false
                }
            }
        )

        webView.webChromeClient = HelixWebChromeClient(
            onProgressChanged = { progress ->
                runOnUiThread { viewModel.onProgressChanged(progress) }
            },
            onTitleReceived = { title ->
                runOnUiThread { viewModel.currentTitle.value = title }
            },
            onFaviconReceived = { favicon ->
                runOnUiThread { tab.favicon = favicon }
            },
            onShowFileChooser = { callback, params ->
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
            onGeolocationPermission = { origin, callback ->
                callback.invoke(origin, true, false)
            }
        )

        // Download listener
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            downloadFile(url, userAgent, contentDisposition, mimeType)
        }

        if (tab.isIncognito) webView.setIncognitoMode(true)

        binding.webViewContainer.addView(
            webView,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Load URL if there is one, otherwise show new tab page
        if (tab.url.isNotEmpty()) {
            webView.loadUrl(tab.url)
        } else {
            showNewTabPage()
        }
    }

    fun loadUrl(url: String) {
        val tab = tabManager.currentTab
        if (tab == null) {
            createNewTab(url)
            return
        }
        currentWebView?.loadUrl(url)
        hideKeyboard()
    }

    private fun showNewTabPage() {
        // Load a beautiful new tab page
        val newTabHtml = buildNewTabHtml()
        currentWebView?.loadDataWithBaseURL("about:blank", newTabHtml, "text/html", "UTF-8", null)
        viewModel.currentTitle.value = "New Tab"
        viewModel.currentUrl.value = ""
    }

    private fun buildNewTabHtml(): String {
        return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>New Tab</title>
<style>
  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;700&display=swap');
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: 'Inter', sans-serif;
    background: linear-gradient(135deg, #0f0c29, #302b63, #24243e);
    min-height: 100vh; display: flex; flex-direction: column;
    align-items: center; justify-content: center; color: white;
    -webkit-user-select: none;
  }
  .logo { font-size: 48px; font-weight: 700; margin-bottom: 8px;
    background: linear-gradient(135deg, #7c7cff, #ff7cc8);
    -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
  .tagline { font-size: 14px; color: rgba(255,255,255,0.5); margin-bottom: 32px; }
  .search-box {
    width: 90%; max-width: 360px;
    background: rgba(255,255,255,0.08);
    border: 1px solid rgba(255,255,255,0.15);
    border-radius: 28px; padding: 14px 20px;
    display: flex; align-items: center; gap: 12px;
    backdrop-filter: blur(10px); margin-bottom: 32px;
  }
  .search-box span { color: rgba(255,255,255,0.4); font-size: 15px; }
  .shortcuts { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px;
    width: 90%; max-width: 360px; }
  .shortcut {
    display: flex; flex-direction: column; align-items: center; gap: 6px;
    padding: 12px 8px; border-radius: 16px;
    background: rgba(255,255,255,0.06);
    text-decoration: none; color: white; font-size: 11px;
    border: 1px solid rgba(255,255,255,0.08);
    transition: background 0.2s;
  }
  .shortcut:active { background: rgba(124,124,255,0.2); }
  .shortcut-icon { font-size: 24px; }
</style>
</head>
<body>
<div class="logo">⬡ Helix</div>
<div class="tagline">Fast • Secure • Private</div>
<div class="search-box">
  <span>🔍</span>
  <span>Search or enter URL...</span>
</div>
<div class="shortcuts">
  <a class="shortcut" href="https://google.com">
    <span class="shortcut-icon">🔍</span>Google
  </a>
  <a class="shortcut" href="https://youtube.com">
    <span class="shortcut-icon">▶️</span>YouTube
  </a>
  <a class="shortcut" href="https://github.com">
    <span class="shortcut-icon">🐙</span>GitHub
  </a>
  <a class="shortcut" href="https://facebook.com">
    <span class="shortcut-icon">📘</span>Facebook
  </a>
  <a class="shortcut" href="https://twitter.com">
    <span class="shortcut-icon">🐦</span>Twitter
  </a>
  <a class="shortcut" href="https://reddit.com">
    <span class="shortcut-icon">🤖</span>Reddit
  </a>
  <a class="shortcut" href="https://wikipedia.org">
    <span class="shortcut-icon">📖</span>Wikipedia
  </a>
  <a class="shortcut" href="https://netflix.com">
    <span class="shortcut-icon">🎬</span>Netflix
  </a>
</div>
</body>
</html>
        """.trimIndent()
    }

    private fun showMoreOptionsMenu() {
        val dialog = BottomSheetDialog(this, R.style.Theme_HelixBrowser_BottomSheet)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_menu, null)
        dialog.setContentView(view)

        view.findViewById<View>(R.id.menu_new_tab).setOnClickListener {
            createNewTab()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.menu_incognito_tab).setOnClickListener {
            createNewTab(isIncognito = true)
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.menu_bookmark).setOnClickListener {
            val url = viewModel.currentUrl.value ?: return@setOnClickListener
            val title = viewModel.currentTitle.value ?: url
            viewModel.toggleBookmark(title, url)
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.menu_bookmarks).setOnClickListener {
            startActivity(Intent(this, BookmarksActivity::class.java))
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.menu_history).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.menu_downloads).setOnClickListener {
            startActivity(Intent(this, DownloadsActivity::class.java))
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.menu_find_in_page).setOnClickListener {
            viewModel.showFindInPage.value = true
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.menu_desktop_site).setOnClickListener {
            val isDesktop = viewModel.isDesktopMode.value?.not() ?: false
            viewModel.isDesktopMode.value = isDesktop
            currentWebView?.setDesktopMode(isDesktop)
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.menu_share).setOnClickListener {
            shareCurrentPage()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.menu_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun shareCurrentPage() {
        val url = viewModel.currentUrl.value ?: return
        val title = viewModel.currentTitle.value ?: url
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun downloadFile(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                return
            }
        }
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("Downloading via Helix Browser")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            addRequestHeader("User-Agent", userAgent)
            allowScanningByMediaScanner()
        }
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(this, "Đang tải: $fileName", Toast.LENGTH_SHORT).show()
    }

    private fun updateAddressBarDisplay() {
        val url = viewModel.currentUrl.value ?: ""
        if (!binding.addressBar.isFocused) {
            binding.addressBar.setText(
                if (url.isEmpty() || url == "about:blank") "" else UrlUtils.getDisplayUrl(url)
            )
        }
        // Security icon
        binding.iconSecure.isVisible = url.startsWith("https://")
    }

    private fun updateNavButtons() {
        viewModel.updateNavState(
            currentWebView?.canGoBack() ?: false,
            currentWebView?.canGoForward() ?: false
        )
    }

    private fun updateTabCountBadge() {
        val count = tabManager.tabCount
        binding.tabCountBadge.text = if (count > 99) "99+" else count.toString()
    }

    private fun hideFindInPage() {
        viewModel.showFindInPage.value = false
        currentWebView?.clearMatches()
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }

    private fun showSystemUI() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TAB_SWITCHER && resultCode == RESULT_OK) {
            val tabId = data?.getStringExtra("tab_id")
            val newTab = data?.getBooleanExtra("new_tab", false) ?: false
            val incognito = data?.getBooleanExtra("incognito", false) ?: false
            when {
                newTab -> createNewTab(isIncognito = incognito)
                tabId != null -> {
                    tabManager.switchToTab(tabId)
                    val tab = tabManager.currentTab ?: return
                    switchToTab(tab)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        currentWebView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        currentWebView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        tabManager.closeAllIncognito()
    }

    companion object {
        const val REQUEST_TAB_SWITCHER = 1001
    }
}

import UIKit
import WebKit

class BrowserViewController: UIViewController {

    // MARK: - UI Components
    private let addressBar = UITextField()
    private let progressBar = UIProgressView()
    private let sslIcon = UIImageView()
    private let backButton = UIButton(type: .system)
    private let forwardButton = UIButton(type: .system)
    private let reloadButton = UIButton(type: .system)
    private let shareButton = UIButton(type: .system)
    private let tabsButton = UIButton(type: .system)
    private let menuButton = UIButton(type: .system)
    private let bookmarkButton = UIButton(type: .system)
    private let homeButton = UIButton(type: .system)
    private let webViewContainer = UIView()
    private let topBar = UIView()
    private let bottomBar = UIView()
    private let tabCountLabel = UILabel()

    private var currentWebView: WKWebView?
    private var progressObserver: NSKeyValueObservation?
    private let tabManager = TabManager.shared
    private let processPool = WKProcessPool()

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = BrandColors.background
        setupUI()
        setupActions()

        tabManager.onActiveTabChanged = { [weak self] tab in
            self?.loadActiveTab()
        }
        tabManager.onTabsChanged = { [weak self] in
            self?.updateTabCount()
        }

        if Prefs.shared.isAdBlockEnabled {
            AdBlockEngine.shared.compileRules { _ in }
        }

        loadActiveTab()
        updateTabCount()
    }

    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }

    // MARK: - Setup UI

    private func setupUI() {
        // Top bar
        topBar.backgroundColor = BrandColors.toolbar
        topBar.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(topBar)

        // Navigation row
        let navStack = UIStackView(arrangedSubviews: [backButton, forwardButton, reloadButton])
        navStack.spacing = 4
        navStack.translatesAutoresizingMaskIntoConstraints = false

        [backButton, forwardButton, reloadButton].forEach {
            $0.tintColor = .white
            $0.widthAnchor.constraint(equalToConstant: 36).isActive = true
            $0.heightAnchor.constraint(equalToConstant: 36).isActive = true
        }
        backButton.setImage(UIImage(systemName: "chevron.left"), for: .normal)
        forwardButton.setImage(UIImage(systemName: "chevron.right"), for: .normal)
        reloadButton.setImage(UIImage(systemName: "arrow.clockwise"), for: .normal)

        // SSL icon
        sslIcon.image = UIImage(systemName: "lock.fill")
        sslIcon.tintColor = BrandColors.secureGreen
        sslIcon.contentMode = .scaleAspectFit
        sslIcon.translatesAutoresizingMaskIntoConstraints = false
        sslIcon.widthAnchor.constraint(equalToConstant: 16).isActive = true

        // Address bar
        addressBar.backgroundColor = BrandColors.addressBar
        addressBar.textColor = .white
        addressBar.font = .systemFont(ofSize: 14)
        addressBar.layer.cornerRadius = 8
        addressBar.clipsToBounds = true
        addressBar.attributedPlaceholder = NSAttributedString(string: "Tìm kiếm hoặc nhập địa chỉ", attributes: [.foregroundColor: BrandColors.textSecondary])
        addressBar.returnKeyType = .go
        addressBar.autocapitalizationType = .none
        addressBar.autocorrectionType = .no
        addressBar.clearButtonMode = .whileEditing
        addressBar.delegate = self
        addressBar.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 8, height: 0))
        addressBar.leftViewMode = .always
        addressBar.translatesAutoresizingMaskIntoConstraints = false

        // Address row
        let addressRow = UIStackView(arrangedSubviews: [sslIcon, addressBar])
        addressRow.spacing = 6
        addressRow.alignment = .center
        addressRow.translatesAutoresizingMaskIntoConstraints = false

        let topStack = UIStackView(arrangedSubviews: [navStack, addressRow])
        topStack.spacing = 8
        topStack.alignment = .center
        topStack.translatesAutoresizingMaskIntoConstraints = false
        topBar.addSubview(topStack)

        // Progress bar
        progressBar.progressTintColor = BrandColors.accentPurpleUI
        progressBar.trackTintColor = .clear
        progressBar.translatesAutoresizingMaskIntoConstraints = false
        topBar.addSubview(progressBar)

        // WebView container
        webViewContainer.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(webViewContainer)

        // Bottom bar
        bottomBar.backgroundColor = BrandColors.toolbar
        bottomBar.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(bottomBar)

        [homeButton, bookmarkButton, shareButton, tabsButton, menuButton].forEach {
            $0.tintColor = .white
            $0.widthAnchor.constraint(equalToConstant: 44).isActive = true
            $0.heightAnchor.constraint(equalToConstant: 44).isActive = true
        }
        homeButton.setImage(UIImage(systemName: "house"), for: .normal)
        bookmarkButton.setImage(UIImage(systemName: "star"), for: .normal)
        shareButton.setImage(UIImage(systemName: "square.and.arrow.up"), for: .normal)
        tabsButton.setImage(UIImage(systemName: "square.on.square"), for: .normal)
        menuButton.setImage(UIImage(systemName: "ellipsis.circle"), for: .normal)

        // Tab count badge
        tabCountLabel.font = .systemFont(ofSize: 10, weight: .bold)
        tabCountLabel.textColor = .white
        tabCountLabel.textAlignment = .center
        tabCountLabel.translatesAutoresizingMaskIntoConstraints = false
        tabsButton.addSubview(tabCountLabel)

        let bottomStack = UIStackView(arrangedSubviews: [homeButton, bookmarkButton, shareButton, tabsButton, menuButton])
        bottomStack.distribution = .equalSpacing
        bottomStack.translatesAutoresizingMaskIntoConstraints = false
        bottomBar.addSubview(bottomStack)

        // Constraints
        NSLayoutConstraint.activate([
            topBar.topAnchor.constraint(equalTo: view.topAnchor),
            topBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            topBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),

            topStack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 4),
            topStack.leadingAnchor.constraint(equalTo: topBar.leadingAnchor, constant: 8),
            topStack.trailingAnchor.constraint(equalTo: topBar.trailingAnchor, constant: -8),
            topStack.bottomAnchor.constraint(equalTo: topBar.bottomAnchor, constant: -4),
            addressBar.heightAnchor.constraint(equalToConstant: 36),

            progressBar.leadingAnchor.constraint(equalTo: topBar.leadingAnchor),
            progressBar.trailingAnchor.constraint(equalTo: topBar.trailingAnchor),
            progressBar.bottomAnchor.constraint(equalTo: topBar.bottomAnchor),
            progressBar.heightAnchor.constraint(equalToConstant: 2),

            webViewContainer.topAnchor.constraint(equalTo: topBar.bottomAnchor),
            webViewContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webViewContainer.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            webViewContainer.bottomAnchor.constraint(equalTo: bottomBar.topAnchor),

            bottomBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            bottomBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            bottomBar.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            bottomStack.topAnchor.constraint(equalTo: bottomBar.topAnchor, constant: 4),
            bottomStack.leadingAnchor.constraint(equalTo: bottomBar.leadingAnchor, constant: 24),
            bottomStack.trailingAnchor.constraint(equalTo: bottomBar.trailingAnchor, constant: -24),
            bottomStack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -4),

            tabCountLabel.centerXAnchor.constraint(equalTo: tabsButton.centerXAnchor, constant: 8),
            tabCountLabel.centerYAnchor.constraint(equalTo: tabsButton.centerYAnchor, constant: -8),
        ])
    }

    // MARK: - Actions

    private func setupActions() {
        backButton.addTarget(self, action: #selector(goBack), for: .touchUpInside)
        forwardButton.addTarget(self, action: #selector(goForward), for: .touchUpInside)
        reloadButton.addTarget(self, action: #selector(reload), for: .touchUpInside)
        homeButton.addTarget(self, action: #selector(goHome), for: .touchUpInside)
        bookmarkButton.addTarget(self, action: #selector(toggleBookmark), for: .touchUpInside)
        shareButton.addTarget(self, action: #selector(sharePage), for: .touchUpInside)
        tabsButton.addTarget(self, action: #selector(showTabSwitcher), for: .touchUpInside)
        menuButton.addTarget(self, action: #selector(showMenu), for: .touchUpInside)
    }

    @objc private func goBack() { currentWebView?.goBack() }
    @objc private func goForward() { currentWebView?.goForward() }
    @objc private func reload() { currentWebView?.reload() }

    @objc private func goHome() {
        loadUrl(Prefs.shared.homepage)
    }

    @objc private func toggleBookmark() {
        guard let tab = tabManager.activeTab else { return }
        let url = tab.url
        var bookmarks = getBookmarks()
        if let index = bookmarks.firstIndex(where: { $0["url"] == url }) {
            bookmarks.remove(at: index)
            bookmarkButton.setImage(UIImage(systemName: "star"), for: .normal)
            bookmarkButton.tintColor = .white
        } else {
            bookmarks.append(["title": tab.title, "url": url, "timestamp": String(Date().timeIntervalSince1970)])
            bookmarkButton.setImage(UIImage(systemName: "star.fill"), for: .normal)
            bookmarkButton.tintColor = .systemYellow
        }
        UserDefaults.standard.set(bookmarks, forKey: "helix_bookmarks")
    }

    @objc private func sharePage() {
        guard let url = currentWebView?.url else { return }
        let ac = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        ac.popoverPresentationController?.sourceView = shareButton
        present(ac, animated: true)
    }

    @objc private func showTabSwitcher() {
        let vc = TabSwitcherViewController()
        vc.modalPresentationStyle = .fullScreen
        vc.onDismiss = { [weak self] in self?.loadActiveTab() }
        present(vc, animated: true)
    }

    @objc private func showMenu() {
        let alert = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        alert.addAction(UIAlertAction(title: "Tab mới", style: .default) { [weak self] _ in
            self?.tabManager.createTab()
            self?.loadActiveTab()
        })
        alert.addAction(UIAlertAction(title: "Tab ẩn danh mới", style: .default) { [weak self] _ in
            self?.tabManager.createTab(isIncognito: true)
            self?.loadActiveTab()
        })
        alert.addAction(UIAlertAction(title: "Lịch sử", style: .default) { [weak self] _ in
            let vc = HistoryViewController()
            vc.onSelectUrl = { [weak self] url in self?.loadUrl(url) }
            self?.present(UINavigationController(rootViewController: vc), animated: true)
        })
        alert.addAction(UIAlertAction(title: "Dấu trang", style: .default) { [weak self] _ in
            let vc = BookmarksViewController()
            vc.onSelectUrl = { [weak self] url in self?.loadUrl(url) }
            self?.present(UINavigationController(rootViewController: vc), animated: true)
        })
        alert.addAction(UIAlertAction(title: "Cài đặt", style: .default) { [weak self] _ in
            let vc = SettingsViewController()
            self?.present(UINavigationController(rootViewController: vc), animated: true)
        })
        alert.addAction(UIAlertAction(title: "Hủy", style: .cancel))
        alert.popoverPresentationController?.sourceView = menuButton
        present(alert, animated: true)
    }

    // MARK: - WebView Management

    func loadUrl(_ input: String) {
        let formatted = UrlUtils.formatUrl(input)
        guard let tab = tabManager.activeTab else { return }
        tab.url = formatted
        addressBar.text = formatted.hasPrefix("helix://") ? "" : formatted

        if formatted.hasPrefix("helix://") {
            showStartPage()
            return
        }

        guard let url = URL(string: formatted) else { return }
        if let webView = currentWebView {
            webView.load(URLRequest(url: url))
        }
    }

    private func loadActiveTab() {
        guard let tab = tabManager.activeTab else { return }

        // Remove old WebView
        currentWebView?.removeFromSuperview()
        progressObserver?.invalidate()

        if let existingWebView = tab.webView {
            currentWebView = existingWebView
        } else {
            let webView = createWebView(isIncognito: tab.isIncognito)
            tab.webView = webView
            currentWebView = webView

            if !tab.url.hasPrefix("helix://"), let url = URL(string: tab.url) {
                webView.load(URLRequest(url: url))
            }
        }

        if tab.url.hasPrefix("helix://") {
            showStartPage()
        } else if let webView = currentWebView {
            webViewContainer.subviews.forEach { $0.removeFromSuperview() }
            webView.frame = webViewContainer.bounds
            webView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            webViewContainer.addSubview(webView)
        }

        addressBar.text = tab.url.hasPrefix("helix://") ? "" : tab.url
        updateNavigationState()
        updateBookmarkButton()
        updateTabCount()
    }

    private func createWebView(isIncognito: Bool) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.processPool = processPool
        config.allowsAirPlayForMediaPlayback = true
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []

        if isIncognito {
            config.websiteDataStore = WKWebsiteDataStore.nonPersistent()
        }

        if Prefs.shared.isAdBlockEnabled {
            AdBlockEngine.shared.apply(to: config)
        }
        PrivacyManager.shared.applyPrivacySettings(to: config)

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.isOpaque = false
        webView.backgroundColor = BrandColors.background

        applyUserAgent(to: webView)

        progressObserver = webView.observe(\.estimatedProgress, options: .new) { [weak self] webView, _ in
            let progress = Float(webView.estimatedProgress)
            self?.progressBar.setProgress(progress, animated: true)
            self?.progressBar.isHidden = progress >= 1.0
        }

        return webView
    }

    private func applyUserAgent(to webView: WKWebView) {
        if Prefs.shared.isDesktopMode {
            webView.customUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"
        } else {
            webView.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
        }
    }

    private func showStartPage() {
        webViewContainer.subviews.forEach { $0.removeFromSuperview() }
        let startView = StartPageView(frame: webViewContainer.bounds)
        startView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        startView.onUrlSelected = { [weak self] url in self?.loadUrl(url) }
        webViewContainer.addSubview(startView)
    }

    // MARK: - State Updates

    private func updateNavigationState() {
        backButton.isEnabled = currentWebView?.canGoBack ?? false
        forwardButton.isEnabled = currentWebView?.canGoForward ?? false
        backButton.alpha = backButton.isEnabled ? 1.0 : 0.3
        forwardButton.alpha = forwardButton.isEnabled ? 1.0 : 0.3
    }

    private func updateBookmarkButton() {
        let isBookmarked = getBookmarks().contains(where: { $0["url"] == tabManager.activeTab?.url })
        bookmarkButton.setImage(UIImage(systemName: isBookmarked ? "star.fill" : "star"), for: .normal)
        bookmarkButton.tintColor = isBookmarked ? .systemYellow : .white
    }

    private func updateTabCount() {
        tabCountLabel.text = "\(tabManager.tabs.count)"
    }

    private func getBookmarks() -> [[String: String]] {
        return UserDefaults.standard.array(forKey: "helix_bookmarks") as? [[String: String]] ?? []
    }

    private func updateSSLIcon(url: URL?) {
        let isSecure = url?.scheme == "https"
        sslIcon.image = UIImage(systemName: isSecure ? "lock.fill" : "info.circle")
        sslIcon.tintColor = isSecure ? BrandColors.secureGreen : BrandColors.textSecondary
    }
}

// MARK: - UITextFieldDelegate

extension BrowserViewController: UITextFieldDelegate {
    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        textField.resignFirstResponder()
        if let text = textField.text, !text.isEmpty {
            loadUrl(text)
        }
        return true
    }
}

// MARK: - WKNavigationDelegate

extension BrowserViewController: WKNavigationDelegate {
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        if let url = navigationAction.request.url, Prefs.shared.isHttpsUpgradeEnabled && url.scheme == "http" {
            var comps = URLComponents(url: url, resolvingAgainstBaseURL: false)
            comps?.scheme = "https"
            if let httpsUrl = comps?.url {
                decisionHandler(.cancel)
                webView.load(URLRequest(url: httpsUrl))
                return
            }
        }
        decisionHandler(.allow)
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        progressBar.isHidden = false
        progressBar.setProgress(0.1, animated: true)
        if let url = webView.url {
            addressBar.text = url.absoluteString
            updateSSLIcon(url: url)
        }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        progressBar.setProgress(1.0, animated: true)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { self.progressBar.isHidden = true }

        let url = webView.url?.absoluteString ?? ""
        let title = webView.title ?? ""

        if let tab = tabManager.activeTab {
            tab.url = url
            tab.title = title.isEmpty ? UrlUtils.getDisplayUrl(url) : title
            tab.faviconUrl = UrlUtils.getFaviconUrl(url)
        }

        addressBar.text = url
        updateNavigationState()
        updateBookmarkButton()
        updateSSLIcon(url: webView.url)

        // Save history
        if Prefs.shared.isSaveHistoryEnabled && !(tabManager.activeTab?.isIncognito ?? false) {
            saveHistory(title: title, url: url)
        }
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        let nsError = error as NSError
        if nsError.code == NSURLErrorCancelled { return }
        progressBar.isHidden = true
        showErrorPage(in: webView, error: error)
    }

    private func showErrorPage(in webView: WKWebView, error: Error) {
        let html = """
        <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>
        body{font-family:-apple-system,sans-serif;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;background:#0A091E;color:#fff}
        .c{text-align:center;max-width:400px;padding:20px}
        h1{font-size:48px;margin:0}h2{color:#A0A0D0;font-weight:400;font-size:18px}
        p{color:#8888bb;line-height:1.6;font-size:14px}
        button{background:#8B8BFF;color:white;border:none;padding:12px 32px;border-radius:8px;font-size:16px;cursor:pointer;margin-top:16px}
        </style></head><body><div class="c">
        <h1>⚠️</h1><h2>Không thể truy cập trang này</h2>
        <p>\(error.localizedDescription)</p>
        <button onclick="location.reload()">Thử lại</button>
        </div></body></html>
        """
        webView.loadHTMLString(html, baseURL: nil)
    }

    private func saveHistory(title: String, url: String) {
        var history = UserDefaults.standard.array(forKey: "helix_history") as? [[String: String]] ?? []
        if history.first?["url"] == url { return }
        let entry: [String: String] = ["title": title.isEmpty ? url : title, "url": url, "timestamp": String(Date().timeIntervalSince1970)]
        history.insert(entry, at: 0)
        if history.count > 5000 { history = Array(history.prefix(5000)) }
        UserDefaults.standard.set(history, forKey: "helix_history")
    }
}

// MARK: - WKUIDelegate

extension BrowserViewController: WKUIDelegate {
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if let url = navigationAction.request.url {
            tabManager.createTab(url: url.absoluteString)
        }
        return nil
    }

    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        let alert = UIAlertController(title: "Helix Browser", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler() })
        present(alert, animated: true)
    }

    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
        let alert = UIAlertController(title: "Helix Browser", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler(true) })
        alert.addAction(UIAlertAction(title: "Hủy", style: .cancel) { _ in completionHandler(false) })
        present(alert, animated: true)
    }
}

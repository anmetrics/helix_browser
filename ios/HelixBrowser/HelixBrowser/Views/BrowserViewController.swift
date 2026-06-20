import UIKit
import WebKit
import Security

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
    private let incognitoProcessPool = WKProcessPool()

    /// Hosts that have already been auto-upgraded from http to https this session,
    /// so a failed upgrade can fall back to the original http URL exactly once.
    private var upgradedHosts: Set<String> = []
    /// Hosts where the https upgrade failed and we fell back to http; do not re-upgrade.
    private var httpsUpgradeFailedHosts: Set<String> = []

    /// Maps an in-flight WKDownload to the source URL string used as the DataManager key.
    private var activeDownloads: [WKDownload: String] = [:]

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
            AdBlockEngine.shared.compileRules { [weak self] ruleList in
                // The first WebView may have been created before the rules finished
                // compiling, so apply the compiled list to any already-live WebViews.
                guard let self = self, let ruleList = ruleList else { return }
                for tab in self.tabManager.tabs {
                    tab.webView?.configuration.userContentController.add(ruleList)
                }
            }
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
        backButton.accessibilityLabel = "Quay lại"
        forwardButton.accessibilityLabel = "Tiến tới"
        reloadButton.accessibilityLabel = "Tải lại"

        // SSL icon
        sslIcon.image = UIImage(systemName: "lock.fill")
        sslIcon.tintColor = BrandColors.secureGreen
        sslIcon.contentMode = .scaleAspectFit
        sslIcon.translatesAutoresizingMaskIntoConstraints = false
        sslIcon.widthAnchor.constraint(equalToConstant: 16).isActive = true
        sslIcon.isAccessibilityElement = true
        sslIcon.accessibilityLabel = "Kết nối an toàn"

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
        addressBar.accessibilityLabel = "Thanh địa chỉ"

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
        homeButton.accessibilityLabel = "Trang chủ"
        bookmarkButton.accessibilityLabel = "Dấu trang"
        shareButton.accessibilityLabel = "Chia sẻ"
        tabsButton.accessibilityLabel = "Thẻ"
        menuButton.accessibilityLabel = "Menu"

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
            bookmarkButton.accessibilityLabel = "Dấu trang"
        } else {
            bookmarks.append(["title": tab.title, "url": url, "timestamp": String(Date().timeIntervalSince1970)])
            bookmarkButton.setImage(UIImage(systemName: "star.fill"), for: .normal)
            bookmarkButton.tintColor = .systemYellow
            bookmarkButton.accessibilityLabel = "Xóa dấu trang"
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
        alert.addAction(UIAlertAction(title: "Tải xuống", style: .default) { [weak self] _ in
            let vc = DownloadsViewController()
            self?.present(UINavigationController(rootViewController: vc), animated: true)
        })
        alert.addAction(UIAlertAction(title: "Tìm trên trang", style: .default) { [weak self] _ in
            self?.presentFindInPage()
        })
        alert.addAction(UIAlertAction(title: "In trang", style: .default) { [weak self] _ in
            self?.printCurrentPage()
        })
        alert.addAction(UIAlertAction(title: "Cài đặt", style: .default) { [weak self] _ in
            let vc = SettingsViewController()
            self?.present(UINavigationController(rootViewController: vc), animated: true)
        })
        alert.addAction(UIAlertAction(title: "Hủy", style: .cancel))
        alert.popoverPresentationController?.sourceView = menuButton
        present(alert, animated: true)
    }

    private func presentFindInPage() {
        guard #available(iOS 16.0, *), let webView = currentWebView else { return }
        webView.isFindInteractionEnabled = true
        webView.findInteraction?.presentFindNavigator(showingReplace: false)
    }

    private func printCurrentPage() {
        guard let webView = currentWebView else { return }
        let printController = UIPrintInteractionController.shared
        let printInfo = UIPrintInfo(dictionary: nil)
        printInfo.outputType = .general
        printInfo.jobName = webView.title ?? "Helix"
        printController.printInfo = printInfo
        printController.printFormatter = webView.viewPrintFormatter()
        printController.present(animated: true, completionHandler: nil)
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

            // Restore the persisted back/forward history + scroll position after a
            // process-death relaunch (iOS 15+). TabManager only stores state for
            // non-incognito tabs, so incognito falls through to a plain URL load.
            if #available(iOS 15.0, *), let state = tabManager.interactionState(forTabId: tab.id) {
                webView.interactionState = state
                // If the restored blob had no committed page, load the saved URL.
                if webView.url == nil, !tab.url.hasPrefix("helix://"), let url = URL(string: tab.url) {
                    webView.load(URLRequest(url: url))
                }
            } else if !tab.url.hasPrefix("helix://"), let url = URL(string: tab.url) {
                webView.load(URLRequest(url: url))
            }
        }

        // Re-attach the progress observer to whichever WebView is now current, so the
        // progress bar keeps working when switching back to a previously-created tab.
        if let webView = currentWebView {
            attachProgressObserver(to: webView)
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
        // Use a dedicated process pool for incognito tabs so private and normal
        // content never share a web content process.
        config.processPool = isIncognito ? incognitoProcessPool : processPool
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
        if #available(iOS 16.0, *) {
            webView.isFindInteractionEnabled = true
        }

        applyUserAgent(to: webView)
        attachProgressObserver(to: webView)

        return webView
    }

    /// Observes the estimated progress of the given WebView and invalidates any
    /// previous observation. Must be called whenever `currentWebView` changes so the
    /// progress bar tracks the visible tab (including reused tabs).
    private func attachProgressObserver(to webView: WKWebView) {
        progressObserver?.invalidate()
        progressObserver = webView.observe(\.estimatedProgress, options: .new) { [weak self] webView, _ in
            let progress = Float(webView.estimatedProgress)
            self?.progressBar.setProgress(progress, animated: true)
            self?.progressBar.isHidden = progress >= 1.0
        }
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
        bookmarkButton.accessibilityLabel = isBookmarked ? "Xóa dấu trang" : "Dấu trang"
    }

    private func updateTabCount() {
        tabCountLabel.text = "\(tabManager.tabs.count)"
        tabsButton.accessibilityValue = "\(tabManager.tabs.count)"
    }

    private func getBookmarks() -> [[String: String]] {
        return UserDefaults.standard.array(forKey: "helix_bookmarks") as? [[String: String]] ?? []
    }

    private enum SSLIndicatorState {
        case secure       // https with only secure content
        case mixed        // https page loading insecure subresources
        case insecure     // http (or non-https)
        case neutral      // loading / unknown
    }

    /// Drives the lock indicator from the WebView's real connection state rather than
    /// just the URL scheme.
    private func updateSSLIndicator(for webView: WKWebView) {
        guard webView.url?.scheme == "https" else {
            setSSLIndicator(state: .insecure)
            return
        }
        setSSLIndicator(state: webView.hasOnlySecureContent ? .secure : .mixed)
    }

    private func setSSLIndicator(state: SSLIndicatorState) {
        switch state {
        case .secure:
            sslIcon.image = UIImage(systemName: "lock.fill")
            sslIcon.tintColor = BrandColors.secureGreen
            sslIcon.accessibilityLabel = "Kết nối an toàn"
        case .mixed:
            sslIcon.image = UIImage(systemName: "lock.open.fill")
            sslIcon.tintColor = BrandColors.accentPinkUI
            sslIcon.accessibilityLabel = "Không an toàn"
        case .insecure:
            sslIcon.image = UIImage(systemName: "info.circle")
            sslIcon.tintColor = BrandColors.textSecondary
            sslIcon.accessibilityLabel = "Không an toàn"
        case .neutral:
            sslIcon.image = UIImage(systemName: "info.circle")
            sslIcon.tintColor = BrandColors.textSecondary
            sslIcon.accessibilityLabel = "Đang tải"
        }
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
        if let url = navigationAction.request.url, Prefs.shared.isHttpsUpgradeEnabled, url.scheme == "http",
           let host = url.host,
           // Only upgrade safe, top-level GET navigations so we never drop a POST
           // body, and never hijack subresources / iframes.
           navigationAction.targetFrame?.isMainFrame == true,
           (navigationAction.request.httpMethod ?? "GET").uppercased() == "GET",
           // Do not re-upgrade a host that already failed over to http this session.
           !httpsUpgradeFailedHosts.contains(host) {
            var comps = URLComponents(url: url, resolvingAgainstBaseURL: false)
            comps?.scheme = "https"
            if let httpsUrl = comps?.url {
                upgradedHosts.insert(host)
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
        }
        // Show a neutral indicator while the page is still loading/validating; the
        // real secure state is only known once the navigation finishes.
        setSSLIndicator(state: .neutral)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        progressBar.setProgress(1.0, animated: true)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { self.progressBar.isHidden = true }

        let url = webView.url?.absoluteString ?? ""
        let title = webView.title ?? ""

        if let tab = tabManager.activeTab {
            tab.url = url
            tab.title = title.isEmpty ? UrlUtils.getDisplayUrl(url) : title
            // Do not derive (and persist) a Google favicon URL for incognito tabs to
            // avoid leaking private browsing into a persistent third-party request.
            if !tab.isIncognito {
                tab.faviconUrl = UrlUtils.getFaviconUrl(url)
            }
        }

        addressBar.text = url
        updateNavigationState()
        updateBookmarkButton()
        updateSSLIndicator(for: webView)

        // Save history
        if Prefs.shared.isSaveHistoryEnabled && !(tabManager.activeTab?.isIncognito ?? false) {
            saveHistory(title: title, url: url)
        }
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        let nsError = error as NSError
        if nsError.code == NSURLErrorCancelled { return }
        progressBar.isHidden = true

        // If an http->https auto-upgrade failed for a *connection* reason (host
        // unreachable / timeout), fall back once to the original http URL instead
        // of stranding genuinely http-only sites. SECURITY: never fall back on a
        // TLS/certificate failure — doing so would let an attacker who presents a
        // bad cert force a silent downgrade to cleartext http (MITM). Those flow
        // to the certificate-error page below instead.
        if let failingURL = nsError.userInfo[NSURLErrorFailingURLErrorKey] as? URL ?? webView.url,
           failingURL.scheme == "https",
           let host = failingURL.host,
           upgradedHosts.contains(host),
           isConnectionError(nsError.code) {
            upgradedHosts.remove(host)
            httpsUpgradeFailedHosts.insert(host)
            var comps = URLComponents(url: failingURL, resolvingAgainstBaseURL: false)
            comps?.scheme = "http"
            if let httpURL = comps?.url {
                webView.load(URLRequest(url: httpURL))
                return
            }
        }

        if isTLSError(nsError.code) {
            showCertificateErrorPage(in: webView, error: error)
        } else {
            showErrorPage(in: webView, error: error)
        }
    }

    private func isTLSError(_ code: Int) -> Bool {
        switch code {
        case NSURLErrorServerCertificateUntrusted,
             NSURLErrorServerCertificateHasBadDate,
             NSURLErrorServerCertificateHasUnknownRoot,
             NSURLErrorServerCertificateNotYetValid,
             NSURLErrorClientCertificateRejected,
             NSURLErrorClientCertificateRequired,
             NSURLErrorSecureConnectionFailed:
            return true
        default:
            return false
        }
    }

    /// Genuine connection failures only — deliberately EXCLUDES TLS/certificate
    /// errors so the https->http upgrade fallback can never downgrade past a bad cert.
    private func isConnectionError(_ code: Int) -> Bool {
        switch code {
        case NSURLErrorCannotConnectToHost,
             NSURLErrorCannotFindHost,
             NSURLErrorTimedOut,
             NSURLErrorNetworkConnectionLost,
             NSURLErrorDNSLookupFailed:
            return true
        default:
            return false
        }
    }

    func webView(_ webView: WKWebView, didReceive challenge: URLAuthenticationChallenge, completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let serverTrust = challenge.protectionSpace.serverTrust else {
            completionHandler(.performDefaultHandling, nil)
            return
        }
        // Validate the certificate chain ourselves and only proceed when it is valid.
        // Never unconditionally accept the trust object (that would enable MITM).
        var secError: CFError?
        if SecTrustEvaluateWithError(serverTrust, &secError) {
            completionHandler(.useCredential, URLCredential(trust: serverTrust))
        } else {
            completionHandler(.cancelAuthenticationChallenge, nil)
        }
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

    private func showCertificateErrorPage(in webView: WKWebView, error: Error) {
        let html = """
        <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>
        body{font-family:-apple-system,sans-serif;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;background:#0A091E;color:#fff}
        .c{text-align:center;max-width:400px;padding:20px}
        h1{font-size:48px;margin:0}h2{color:#FF7EB3;font-weight:400;font-size:18px}
        p{color:#8888bb;line-height:1.6;font-size:14px}
        </style></head><body><div class="c">
        <h1>🔒</h1><h2>Kết nối của bạn không an toàn</h2>
        <p>Không thể xác minh chứng chỉ bảo mật của trang web này. Để bảo vệ bạn, Helix Browser đã chặn trang này.</p>
        <p>\(error.localizedDescription)</p>
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

    // MARK: - Downloads

    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse, decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        // Treat explicit attachments and non-renderable MIME types as downloads.
        var shouldDownload = !navigationResponse.canShowMIMEType
        if let http = navigationResponse.response as? HTTPURLResponse,
           let disposition = http.value(forHTTPHeaderField: "Content-Disposition"),
           disposition.lowercased().contains("attachment") {
            shouldDownload = true
        }
        if shouldDownload {
            decisionHandler(.download)
        } else {
            decisionHandler(.allow)
        }
    }

    func webView(_ webView: WKWebView, navigationResponse: WKNavigationResponse, didBecome download: WKDownload) {
        download.delegate = self
    }

    func webView(_ webView: WKWebView, navigationAction: WKNavigationAction, didBecome download: WKDownload) {
        download.delegate = self
    }
}

// MARK: - WKUIDelegate

extension BrowserViewController: WKUIDelegate {
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        // Only open new tabs for user-initiated link activations (or when popup
        // blocking is disabled) so page scripts cannot spawn drive-by tabs.
        guard navigationAction.navigationType == .linkActivated || !Prefs.shared.isBlockPopupsEnabled else {
            return nil
        }
        if let url = navigationAction.request.url {
            tabManager.createTab(url: url.absoluteString)
        }
        return nil
    }

    @available(iOS 15.0, *)
    func webView(_ webView: WKWebView, requestMediaCapturePermissionFor origin: WKSecurityOrigin, initiatedByFrame frame: WKFrameInfo, type: WKMediaCaptureType, decisionHandler: @escaping (WKPermissionDecision) -> Void) {
        // Grant camera/microphone access for WebRTC; the system still presents its own
        // OS-level permission prompt the first time, backed by the Info.plist strings.
        decisionHandler(.grant)
    }

    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        let alert = UIAlertController(title: "Helix Browser", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler() })
        presentDialog(alert, fallback: { completionHandler() })
    }

    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
        let alert = UIAlertController(title: "Helix Browser", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler(true) })
        alert.addAction(UIAlertAction(title: "Hủy", style: .cancel) { _ in completionHandler(false) })
        presentDialog(alert, fallback: { completionHandler(false) })
    }

    func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String, defaultText: String?, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (String?) -> Void) {
        let alert = UIAlertController(title: "Helix Browser", message: prompt, preferredStyle: .alert)
        alert.addTextField { $0.text = defaultText }
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in
            completionHandler(alert.textFields?.first?.text)
        })
        alert.addAction(UIAlertAction(title: "Hủy", style: .cancel) { _ in completionHandler(nil) })
        presentDialog(alert, fallback: { completionHandler(nil) })
    }

    /// Presents a JS dialog from the top-most presented controller, and always invokes
    /// the fallback (resolving the JS callback) if presentation is not possible, so the
    /// page is never left hanging.
    private func presentDialog(_ alert: UIAlertController, fallback: @escaping () -> Void) {
        var presenter: UIViewController = self
        while let presented = presenter.presentedViewController, !(presented is UIAlertController) {
            presenter = presented
        }
        guard presenter.presentedViewController == nil else {
            fallback()
            return
        }
        presenter.present(alert, animated: true)
    }
}

// MARK: - WKDownloadDelegate

extension BrowserViewController: WKDownloadDelegate {
    func download(_ download: WKDownload, decideDestinationUsing response: URLResponse, suggestedFilename: String, completionHandler: @escaping (URL?) -> Void) {
        let sanitized = sanitizeFilename(suggestedFilename)
        let dir = downloadsDirectory()
        var destination = dir.appendingPathComponent(sanitized)

        // Avoid clobbering an existing file by appending a numeric suffix.
        let fm = FileManager.default
        if fm.fileExists(atPath: destination.path) {
            let base = (sanitized as NSString).deletingPathExtension
            let ext = (sanitized as NSString).pathExtension
            var index = 1
            repeat {
                let candidate = ext.isEmpty ? "\(base)-\(index)" : "\(base)-\(index).\(ext)"
                destination = dir.appendingPathComponent(candidate)
                index += 1
            } while fm.fileExists(atPath: destination.path)
        }

        let sourceUrl = download.originalRequest?.url?.absoluteString ?? destination.lastPathComponent
        activeDownloads[download] = sourceUrl
        let size = (response as? HTTPURLResponse)?.expectedContentLength ?? response.expectedContentLength
        DataManager.shared.addDownload(url: sourceUrl, filename: destination.lastPathComponent, filesize: max(0, size))

        completionHandler(destination)
    }

    func downloadDidFinish(_ download: WKDownload) {
        if let url = activeDownloads[download] {
            DataManager.shared.updateDownloadStatus(url: url, status: "completed")
        }
        activeDownloads[download] = nil
    }

    func download(_ download: WKDownload, didFailWithError error: Error, resumeData: Data?) {
        if let url = activeDownloads[download] {
            DataManager.shared.updateDownloadStatus(url: url, status: "failed")
        }
        activeDownloads[download] = nil
    }

    private func downloadsDirectory() -> URL {
        let fm = FileManager.default
        let docs = fm.urls(for: .documentDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        let dir = docs.appendingPathComponent("Downloads", isDirectory: true)
        if !fm.fileExists(atPath: dir.path) {
            try? fm.createDirectory(at: dir, withIntermediateDirectories: true)
        }
        return dir
    }

    /// Strips path separators and parent-directory traversal from a suggested filename.
    private func sanitizeFilename(_ name: String) -> String {
        var cleaned = (name as NSString).lastPathComponent
        cleaned = cleaned.replacingOccurrences(of: "/", with: "_")
        cleaned = cleaned.replacingOccurrences(of: "\\", with: "_")
        cleaned = cleaned.replacingOccurrences(of: "..", with: "_")
        cleaned = cleaned.trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? "download" : cleaned
    }
}

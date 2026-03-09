import SwiftUI
import WebKit

struct WebView: NSViewRepresentable {
    let tabId: UUID
    @ObservedObject var viewModel: WebViewModel
    
    // Cache WebViews to maintain state when switching tabs
    private static var webViewCache: [UUID: WKWebView] = [:]
    // Track which URLs were explicitly requested by the user/VM
    private static var pendingURLLoad: [UUID: String] = [:]
    
    static func clearCache(for tabId: UUID) {
        webViewCache.removeValue(forKey: tabId)
        pendingURLLoad.removeValue(forKey: tabId)
    }
    
    static func cachedWebView(for tabId: UUID) -> WKWebView? {
        return webViewCache[tabId]
    }
    
    /// Mark a URL as explicitly requested (from VM loadUrl)
    static func requestLoad(tabId: UUID, url: String) {
        pendingURLLoad[tabId] = url
    }

    func makeNSView(context: Context) -> WKWebView {
        if let cached = Self.webViewCache[tabId] {
            cached.navigationDelegate = context.coordinator
            cached.uiDelegate = context.coordinator
            context.coordinator.lastWebView = cached
            return cached
        }
        
        let config = WKWebViewConfiguration()
        config.processPool = viewModel.processPool
        config.allowsAirPlayForMediaPlayback = true
        config.preferences.minimumFontSize = 9
        
        // ===== MEDIA STREAMING (Critical for video seeking) =====
        config.mediaTypesRequiringUserActionForPlayback = []
        config.preferences.setValue(true, forKey: "allowsPictureInPictureMediaPlayback")
        config.websiteDataStore = WKWebsiteDataStore.default()
        
        // Allow inline media and fullscreen
        config.preferences.setValue(true, forKey: "fullScreenEnabled")
        
        // Enable developer extras
        config.preferences.setValue(true, forKey: "developerExtrasEnabled")
        
        // Apply ad blocker if enabled
        if viewModel.prefs.isAdBlockEnabled {
            AdBlockEngine.shared.apply(to: config)
        }

        // Apply privacy protections
        PrivacyManager.shared.applyPrivacySettings(to: config)

        // Use incognito data store for private tabs
        let tab = viewModel.tabs.first(where: { $0.id == tabId })
        if tab?.isIncognito == true {
            config.websiteDataStore = WKWebsiteDataStore.nonPersistent()
        }

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = true
        webView.setValue(false, forKey: "drawsBackground") // Transparent bg
        
        applyUserAgent(to: webView)

        // Initial load
        if let urlStr = tab?.url, let url = URL(string: urlStr) {
            webView.load(URLRequest(url: url))
        }
        
        context.coordinator.lastWebView = webView
        Self.webViewCache[tabId] = webView
        return webView
    }

    func updateNSView(_ nsView: WKWebView, context: Context) {
        applyUserAgent(to: nsView)
        
        // CRITICAL: Only reload if there's a PENDING explicit URL load
        // This prevents reloading during video seek, hash changes, etc.
        guard let pendingUrl = Self.pendingURLLoad[tabId] else { return }
        
        // Consume the pending load
        Self.pendingURLLoad.removeValue(forKey: tabId)
        
        guard !pendingUrl.hasPrefix("helix://"),
              let url = URL(string: pendingUrl) else { return }
        
        // Don't reload if we're already on this exact URL
        let currentUrl = nsView.url?.absoluteString ?? ""
        if currentUrl == pendingUrl { return }
        
        // Don't reload if only the fragment changed (e.g. #t=30m on YouTube)
        if samePageWithDifferentFragment(current: currentUrl, new: pendingUrl) { return }
        
        nsView.load(URLRequest(url: url))
    }
    
    /// Check if two URLs differ only by their fragment (#...)
    private func samePageWithDifferentFragment(current: String, new: String) -> Bool {
        guard var currentComps = URLComponents(string: current),
              var newComps = URLComponents(string: new) else { return false }
        currentComps.fragment = nil
        newComps.fragment = nil
        return currentComps.url == newComps.url
    }
    
    private func applyUserAgent(to webView: WKWebView) {
        let desktopUA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"
        let mobileUA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
        let targetUA = viewModel.prefs.isDesktopMode ? desktopUA : mobileUA
        
        if webView.customUserAgent != targetUA {
            webView.customUserAgent = targetUA
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(viewModel: viewModel, tabId: tabId)
    }

    class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        var viewModel: WebViewModel
        var tabId: UUID
        var lastWebView: WKWebView?
        private var backObserver: NSObjectProtocol?
        private var forwardObserver: NSObjectProtocol?
        private var reloadObserver: NSObjectProtocol?
        private var findObserver: NSObjectProtocol?
        private var findNextObserver: NSObjectProtocol?
        private var findPrevObserver: NSObjectProtocol?
        private var findDismissObserver: NSObjectProtocol?
        private var zoomObserver: NSObjectProtocol?

        init(viewModel: WebViewModel, tabId: UUID) {
            self.viewModel = viewModel
            self.tabId = tabId
            super.init()
            setupObservers()
        }
        
        deinit {
            removeObservers()
        }
        
        private func setupObservers() {
            backObserver = NotificationCenter.default.addObserver(
                forName: NSNotification.Name("WebViewGoBack"), object: nil, queue: .main
            ) { [weak self] note in
                guard let self = self, let id = note.object as? UUID, id == self.tabId else { return }
                self.lastWebView?.goBack()
            }
            
            forwardObserver = NotificationCenter.default.addObserver(
                forName: NSNotification.Name("WebViewGoForward"), object: nil, queue: .main
            ) { [weak self] note in
                guard let self = self, let id = note.object as? UUID, id == self.tabId else { return }
                self.lastWebView?.goForward()
            }
            
            reloadObserver = NotificationCenter.default.addObserver(
                forName: NSNotification.Name("WebViewReload"), object: nil, queue: .main
            ) { [weak self] note in
                guard let self = self, let id = note.object as? UUID, id == self.tabId else { return }
                self.lastWebView?.reload()
            }
            
            findObserver = NotificationCenter.default.addObserver(
                forName: NSNotification.Name("WebViewFind"), object: nil, queue: .main
            ) { [weak self] note in
                guard let self = self, let info = note.object as? [String: Any],
                      let id = info["tabId"] as? UUID, id == self.tabId,
                      let text = info["text"] as? String, !text.isEmpty else { return }
                if #available(macOS 13.0, *) {
                    self.lastWebView?.find(text, configuration: .init(), completionHandler: { _ in })
                } else {
                    self.lastWebView?.evaluateJavaScript("window.find('\(text.replacingOccurrences(of: "'", with: "\\'"))')")
                }
            }
            
            findNextObserver = NotificationCenter.default.addObserver(
                forName: NSNotification.Name("WebViewFindNext"), object: nil, queue: .main
            ) { [weak self] note in
                guard let self = self, let id = note.object as? UUID, id == self.tabId else { return }
                self.lastWebView?.evaluateJavaScript("window.find()")
            }
            
            findPrevObserver = NotificationCenter.default.addObserver(
                forName: NSNotification.Name("WebViewFindPrev"), object: nil, queue: .main
            ) { [weak self] note in
                guard let self = self, let id = note.object as? UUID, id == self.tabId else { return }
                self.lastWebView?.evaluateJavaScript("window.find('', false, true)")
            }
            
            findDismissObserver = NotificationCenter.default.addObserver(
                forName: NSNotification.Name("WebViewFindDismiss"), object: nil, queue: .main
            ) { [weak self] note in
                guard let self = self, let id = note.object as? UUID, id == self.tabId else { return }
                self.lastWebView?.evaluateJavaScript("window.getSelection().removeAllRanges()")
            }
            
            zoomObserver = NotificationCenter.default.addObserver(
                forName: NSNotification.Name("WebViewZoom"), object: nil, queue: .main
            ) { [weak self] note in
                guard let self = self, let info = note.object as? [String: Any],
                      let id = info["tabId"] as? UUID, id == self.tabId,
                      let zoom = info["zoom"] as? Int else { return }
                let scale = Double(zoom) / 100.0
                self.lastWebView?.evaluateJavaScript("document.body.style.zoom = '\(scale)'")
            }
        }
        
        private func removeObservers() {
            [backObserver, forwardObserver, reloadObserver, findObserver, findNextObserver, findPrevObserver, findDismissObserver, zoomObserver].compactMap { $0 }.forEach {
                NotificationCenter.default.removeObserver($0)
            }
        }
        
        // MARK: - WKNavigationDelegate
        
        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = navigationAction.request.url else {
                decisionHandler(.allow)
                return
            }

            // HTTPS upgrade
            if Prefs.shared.isHttpsUpgradeEnabled && url.scheme == "http" {
                var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
                components?.scheme = "https"
                if let httpsUrl = components?.url {
                    decisionHandler(.cancel)
                    webView.load(URLRequest(url: httpsUrl))
                    return
                }
            }

            // Block third-party popups if enabled
            if Prefs.shared.isBlockPopupsEnabled && navigationAction.navigationType == .other && navigationAction.targetFrame == nil {
                // Open in new tab instead of popup
                DispatchQueue.main.async {
                    self.viewModel.createNewTab(url: url.absoluteString)
                }
                decisionHandler(.cancel)
                return
            }

            decisionHandler(.allow)
        }
        
        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            self.lastWebView = webView
            guard viewModel.activeTabId == tabId else { return }
            DispatchQueue.main.async {
                self.viewModel.isLoading = true
                if let url = webView.url?.absoluteString {
                    self.viewModel.currentUrlString = url
                }
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            DispatchQueue.main.async {
                self.viewModel.isLoading = false
                self.viewModel.progress = 1.0
                if self.viewModel.activeTabId == self.tabId {
                    self.viewModel.onPageFinished(
                        url: webView.url?.absoluteString ?? "",
                        title: webView.title ?? ""
                    )
                    self.viewModel.canGoBack = webView.canGoBack
                    self.viewModel.canGoForward = webView.canGoForward
                    
                    // Re-apply zoom level on page load
                    self.viewModel.applyZoom(to: self.tabId)
                }
            }
        }
        
        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            // didFail = error AFTER page started loading (e.g. network drop mid-load)
            // Only show error for the active tab's main frame
            handleNavigationError(error, webView: webView)
        }
        
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            // didFailProvisionalNavigation = error BEFORE page committed
            handleNavigationError(error, webView: webView)
        }
        
        private func handleNavigationError(_ error: Error, webView: WKWebView) {
            let nsError = error as NSError
            
            // ===== SMART ERROR FILTERING =====
            // Ignore these — they are NOT real page failures:
            let ignoredCodes: Set<Int> = [
                NSURLErrorCancelled,            // -999: User navigated away or JS cancelled
                102,                            // Frame load interrupted (iframe/subframe)
                NSURLErrorNetworkConnectionLost, // -1005: Transient network blip
            ]
            if ignoredCodes.contains(nsError.code) { return }
            
            // Ignore WebKit internal errors (plugin, frame errors)
            if nsError.domain == "WebKitErrorDomain" { return }
            
            // Ignore errors for non-active tabs
            guard viewModel.activeTabId == tabId else { return }
            
            // Only show error page if this is the MAIN frame that failed
            // (sub-resource / iframe failures should NOT replace the page)
            DispatchQueue.main.async {
                self.viewModel.isLoading = false
                self.viewModel.progress = 0
                
                let errorHTML = """
                <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"><style>
                    body { font-family: -apple-system, sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background: #0A091E; color: #fff; }
                    .c { text-align: center; max-width: 500px; }
                    h1 { font-size: 48px; margin: 0; }
                    h2 { color: #A0A0D0; font-weight: 400; font-size: 18px; }
                    p { color: #8888bb; line-height: 1.6; font-size: 14px; }
                    .code { background: rgba(255,255,255,0.05); padding: 8px 16px; border-radius: 8px; font-family: monospace; color: #8B8BFF; margin: 16px 0; display: inline-block; font-size: 12px; }
                    button { background: #8B8BFF; color: white; border: none; padding: 12px 32px; border-radius: 8px; font-size: 16px; cursor: pointer; margin-top: 16px; }
                    button:hover { background: #7070ff; }
                </style></head><body><div class="c">
                    <h1>⚠️</h1>
                    <h2>Không thể truy cập trang này</h2>
                    <div class="code">Error \(nsError.code)</div>
                    <p>\(error.localizedDescription)</p>
                    <button onclick="location.reload()">Thử lại</button>
                </div></body></html>
                """
                webView.loadHTMLString(errorHTML, baseURL: nil)
            }
        }
        
        // MARK: - SSL Challenge
        func webView(_ webView: WKWebView, didReceive challenge: URLAuthenticationChallenge, completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
            if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
               let trust = challenge.protectionSpace.serverTrust {
                completionHandler(.useCredential, URLCredential(trust: trust))
            } else {
                completionHandler(.performDefaultHandling, nil)
            }
        }
        
        // MARK: - WKUIDelegate
        
        // Ad domains to block when they try to open new tabs/popups
        private static let popupAdDomains: Set<String> = [
            "popads.net", "popcash.net", "propellerads.com", "juicyads.com",
            "exoclick.com", "trafficjunky.net", "revcontent.com", "mgid.com",
            "adsterra.com", "hilltopads.net", "clickadu.com", "ad-maven.com",
            "richpush.co", "trafficstars.com", "doubleclick.net", "googlesyndication.com",
            "adnxs.com", "taboola.com", "outbrain.com", "criteo.com",
            "amazon-adsystem.com", "ads.yahoo.com", "moatads.com", "imasdk.googleapis.com"
        ]

        private func isAdPopup(url: URL) -> Bool {
            let host = url.host?.lowercased() ?? ""
            return Self.popupAdDomains.contains(where: { host.contains($0) })
        }

        func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            if let url = navigationAction.request.url {
                // Block ad popups
                if isAdPopup(url) {
                    return nil
                }
                // Block popups without user interaction
                if Prefs.shared.isBlockPopupsEnabled && navigationAction.navigationType == .other {
                    return nil
                }
                DispatchQueue.main.async {
                    self.viewModel.createNewTab(url: url.absoluteString)
                }
            }
            return nil
        }
        
        func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
            let alert = NSAlert()
            alert.messageText = "Helix Browser"
            alert.informativeText = message
            alert.addButton(withTitle: "OK")
            alert.runModal()
            completionHandler()
        }
        
        func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
            let alert = NSAlert()
            alert.messageText = "Helix Browser"
            alert.informativeText = message
            alert.addButton(withTitle: "OK")
            alert.addButton(withTitle: "Hủy")
            completionHandler(alert.runModal() == .alertFirstButtonReturn)
        }
        
        func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String, defaultText: String?, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (String?) -> Void) {
            let alert = NSAlert()
            alert.messageText = "Helix Browser"
            alert.informativeText = prompt
            alert.addButton(withTitle: "OK")
            alert.addButton(withTitle: "Hủy")
            let input = NSTextField(frame: NSRect(x: 0, y: 0, width: 250, height: 24))
            input.stringValue = defaultText ?? ""
            alert.accessoryView = input
            completionHandler(alert.runModal() == .alertFirstButtonReturn ? input.stringValue : nil)
        }
        
        // Handle downloads
        func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse, decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
            if let response = navigationResponse.response as? HTTPURLResponse,
               let contentDisposition = response.value(forHTTPHeaderField: "Content-Disposition"),
               contentDisposition.lowercased().contains("attachment") {
                if #available(macOS 12.0, *) {
                    decisionHandler(.download)
                    return
                }
            }
            
            let downloadMIMEs = ["application/zip", "application/x-gzip", "application/x-tar",
                                "application/octet-stream", "application/x-apple-diskimage", "application/x-bzip2"]
            if let mime = navigationResponse.response.mimeType, downloadMIMEs.contains(mime) && !navigationResponse.canShowMIMEType {
                if #available(macOS 12.0, *) {
                    decisionHandler(.download)
                    return
                }
            }
            
            decisionHandler(.allow)
        }
        
        @available(macOS 12.0, *)
        func webView(_ webView: WKWebView, navigationResponse: WKNavigationResponse, didBecome download: WKDownload) {
            let filename = navigationResponse.response.suggestedFilename ?? "download"
            let url = navigationResponse.response.url?.absoluteString ?? ""
            DownloadManager.shared.handleDownload(download, filename: filename, url: url)
        }
    }
}

import SwiftUI

struct ContentView: View {
    @StateObject var viewModel = WebViewModel()
    @State private var urlInput: String = ""
    @State private var isSidebarVisible = false
    @ObservedObject private var downloadManager = DownloadManager.shared
    
    var body: some View {
        HStack(spacing: 0) {
            // Sidebar
            if isSidebarVisible {
                SidebarView(viewModel: viewModel)
                    .frame(width: 250)
                    .background(VisualEffectView(material: .sidebar, blendingMode: .withinWindow))
                    .transition(AnyTransition.move(edge: .leading))
                
                Divider().background(Color.white.opacity(0.1))
            }
            
            VStack(spacing: 0) {
                // Header
                VStack(spacing: 0) {
                    // 1. Tab Bar Row
                    HStack(spacing: 0) {
                        Color.clear.frame(width: 78, height: 34)
                        
                        HorizontalTabBar(viewModel: viewModel)
                            .frame(height: 34)
                        
                        Spacer()
                    }
                    .background(VisualEffectView(material: .titlebar, blendingMode: .withinWindow))
                    
                    // 2. Navigation & Address Bar Row
                    HStack(spacing: 8) {
                        Spacer()
                        
                        // Navigation Buttons
                        HStack(spacing: 2) {
                            NavButton(icon: "chevron.left", action: { viewModel.goBack() }, active: viewModel.canGoBack)
                            NavButton(icon: "chevron.right", action: { viewModel.goForward() }, active: viewModel.canGoForward)
                            NavButton(icon: "arrow.clockwise", action: { viewModel.reload() }, active: true)
                        }
                        
                        // Address Bar
                        HStack(spacing: 8) {
                            // SSL indicator
                            Image(systemName: viewModel.currentUrlString.hasPrefix("https") ? "lock.fill" : "info.circle")
                                .foregroundColor(viewModel.currentUrlString.hasPrefix("https") ? BrandColors.secureGreen : BrandColors.textSecondary)
                                .font(.system(size: 10))
                            
                            TextField("Tìm kiếm hoặc nhập địa chỉ", text: $urlInput)
                                .textFieldStyle(.plain)
                                .foregroundColor(BrandColors.textPrimary)
                                .font(.system(size: 12))
                                .onSubmit { viewModel.loadUrl(urlInput) }
                            
                            if viewModel.isLoading {
                                ProgressView()
                                    .controlSize(.small)
                                    .scaleEffect(0.5)
                            }
                        }
                        .frame(maxWidth: 600) // Center and limit address bar width
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(BrandColors.addressBar)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                        .overlay(
                            RoundedRectangle(cornerRadius: 6)
                                .stroke(Color.white.opacity(0.08), lineWidth: 1)
                        )
                        
                        // Bookmark toggle
                        ToolbarButton(
                            icon: viewModel.isCurrentPageBookmarked() ? "star.fill" : "star",
                            isActive: viewModel.isCurrentPageBookmarked(),
                            activeColor: .yellow,
                            help: "Bookmark (⌘D)"
                        ) {
                            viewModel.toggleBookmark()
                        }
                        
                        // Downloads indicator
                        if downloadManager.activeDownloadCount > 0 {
                            ToolbarButton(icon: "arrow.down.circle.fill", isActive: true, activeColor: BrandColors.accentPurple, help: "Downloads") {}
                        }
                        
                        // Zoom controls
                        HStack(spacing: 2) {
                            Button(action: { viewModel.zoomOut() }) {
                                Image(systemName: "minus.magnifyingglass")
                                    .font(.system(size: 12))
                                    .foregroundColor(BrandColors.textPrimary)
                                    .frame(width: 24, height: 24)
                            }
                            .buttonStyle(.plain)
                            .help("Thu nhỏ (⌘-)")
                            
                            let zoom = viewModel.zoomLevels[viewModel.activeTabId] ?? 100
                            Text("\(zoom)%")
                                .font(.system(size: 10, weight: .medium, design: .monospaced))
                                .foregroundColor(zoom != 100 ? BrandColors.accentPurple : BrandColors.textSecondary)
                                .frame(width: 36)
                                .onTapGesture { viewModel.resetZoom() }
                                .help("Đặt lại zoom (⌘0)")
                            
                            Button(action: { viewModel.zoomIn() }) {
                                Image(systemName: "plus.magnifyingglass")
                                    .font(.system(size: 12))
                                    .foregroundColor(BrandColors.textPrimary)
                                    .frame(width: 24, height: 24)
                            }
                            .buttonStyle(.plain)
                            .help("Phóng to (⌘+)")
                        }
                        
                        // Menu (cleaned up)
                        Menu {
                            Button("Tab mới   ⌘T") { withAnimation { viewModel.createNewTab() } }
                            Button("Tab ẩn danh mới") { withAnimation { viewModel.createNewTab(isIncognito: true) } }
                            Button("Tìm trên trang   ⌘F") { viewModel.isFindBarVisible = true }
                            Divider()
                            Button("Lịch sử   ⌘Y") { viewModel.loadUrl("helix://history") }
                            Button("Bookmarks   ⌘⌥B") { viewModel.loadUrl("helix://bookmarks") }
                            Button("Trang chủ") { viewModel.goHome() }
                            Divider()
                            // Privacy stats
                            HStack {
                                Text("Trình theo dõi đã chặn: \(viewModel.trackersBlocked)")
                            }
                            Divider()
                            Button("Cài đặt") { viewModel.loadUrl("helix://settings") }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                                .font(.system(size: 18))
                                .foregroundColor(BrandColors.textPrimary)
                                .frame(width: 34, height: 34)
                        }
                        .menuStyle(.borderlessButton)
                        .menuIndicator(.hidden) // Remove the default down-arrow
                        
                        Spacer()
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(VisualEffectView(material: .titlebar, blendingMode: .withinWindow))
                    
                    // Progress bar
                    if viewModel.isLoading {
                        LinearProgressView(progress: viewModel.progress)
                    } else {
                        Rectangle().fill(Color.white.opacity(0.05)).frame(height: 1)
                    }
                }
                
                // Find Bar
                if viewModel.isFindBarVisible {
                    FindBarView(
                        searchText: $viewModel.findText,
                        isVisible: $viewModel.isFindBarVisible,
                        onSearch: { text in viewModel.findInPage(text) },
                        onNext: { viewModel.findNext() },
                        onPrevious: { viewModel.findPrevious() },
                        onDismiss: { viewModel.dismissFind() }
                    )
                }
                
                // WebView Area
                ZStack {
                    ForEach(viewModel.tabs) { tab in
                        if tab.id == viewModel.activeTabId {
                            Group {
                                if tab.url == "helix://start" || tab.url.isEmpty {
                                    StartPageView(viewModel: viewModel)
                                } else if tab.url == "helix://history" {
                                    HistoryView(viewModel: viewModel)
                                } else if tab.url == "helix://bookmarks" {
                                    BookmarksView(viewModel: viewModel)
                                } else if tab.url == "helix://settings" {
                                    SettingsView(viewModel: viewModel)
                                } else {
                                    WebView(tabId: tab.id, viewModel: viewModel)
                                }
                            }
                            .transition(.opacity)
                        }
                    }
                    
                    // Sidebar dismissal overlay (only active when sidebar is visible)
                    if isSidebarVisible {
                        Color.black.opacity(0.001) // Transparent but clickable
                            .onTapGesture {
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                                    isSidebarVisible = false
                                }
                            }
                    }
                }
            }
            .background(BrandColors.background)
            .ignoresSafeArea(.container, edges: .top)
        }
        .preferredColorScheme(.dark)
        .onChange(of: viewModel.currentUrlString) { newValue in
            if !newValue.hasPrefix("helix://") {
                urlInput = newValue
            } else {
                urlInput = ""
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppNewTab"))) { _ in
            withAnimation { viewModel.createNewTab() }
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppNewIncognitoTab"))) { _ in
            withAnimation { viewModel.createNewTab(isIncognito: true) }
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppCloseTab"))) { _ in
            withAnimation { viewModel.closeTab(id: viewModel.activeTabId) }
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppFind"))) { _ in
            viewModel.isFindBarVisible.toggle()
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppReload"))) { _ in
            viewModel.reload()
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppToggleSidebar"))) { _ in
            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) { isSidebarVisible.toggle() }
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppFocusURL"))) { _ in
            // Focus is handled through NSApp first responder
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppBookmark"))) { _ in
            viewModel.toggleBookmark()
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppShowBookmarks"))) { _ in
            viewModel.loadUrl("helix://bookmarks")
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppGoBack"))) { _ in
            viewModel.goBack()
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppGoForward"))) { _ in
            viewModel.goForward()
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppShowHistory"))) { _ in
            viewModel.loadUrl("helix://history")
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppZoomIn"))) { _ in
            viewModel.zoomIn()
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppZoomOut"))) { _ in
            viewModel.zoomOut()
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppZoomReset"))) { _ in
            viewModel.resetZoom()
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppSettings"))) { _ in
            viewModel.loadUrl("helix://settings")
        }
    }
}

// MARK: - Tab Bar (Chrome-style Smart Tabs)

struct HorizontalTabBar: View {
    @ObservedObject var viewModel: WebViewModel
    
    private let plusButtonWidth: CGFloat = 30
    private let maxTabWidth: CGFloat = 220
    private let activeTabWidth: CGFloat = 180   // Full-width for active tab
    private let iconOnlyWidth: CGFloat = 36     // Icon-only collapsed
    private let manyTabsThreshold = 20          // Switch to icon-only mode
    
    var body: some View {
        GeometryReader { geometry in
            let availableWidth = geometry.size.width - plusButtonWidth - 2
            let tabCount = viewModel.tabs.count
            let hasManyTabs = tabCount > manyTabsThreshold
            
            ScrollViewReader { proxy in
                HStack(spacing: 0) {
                    if hasManyTabs {
                        // >20 tabs: active = full, rest = icon-only, scroll if needed
                        let totalWidth = activeTabWidth + CGFloat(tabCount - 1) * (iconOnlyWidth + 1) + 1
                        let needsScroll = totalWidth > availableWidth
                        
                        if needsScroll {
                            ScrollView(.horizontal, showsIndicators: false) {
                                mixedTabsRow()
                            }
                        } else {
                            mixedTabsRow()
                        }
                    } else {
                        // <=20 tabs: smart uniform shrink
                        let rawWidth = availableWidth / max(CGFloat(tabCount), 1)
                        let tabWidth = min(maxTabWidth, max(iconOnlyWidth, rawWidth))
                        let totalNeeded = tabWidth * CGFloat(tabCount) + CGFloat(tabCount)
                        let needsScroll = totalNeeded > availableWidth
                        
                        if needsScroll {
                            ScrollView(.horizontal, showsIndicators: false) {
                                uniformTabsRow(tabWidth: iconOnlyWidth)
                            }
                        } else {
                            uniformTabsRow(tabWidth: tabWidth)
                        }
                    }
                    
                    // "+" button always next to last tab
                    Button(action: { withAnimation(.easeInOut(duration: 0.2)) { viewModel.createNewTab() } }) {
                        Image(systemName: "plus")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(BrandColors.textSecondary)
                            .frame(width: plusButtonWidth, height: 30)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    
                    Spacer(minLength: 0)
                }
                .onChange(of: viewModel.activeTabId) { newId in
                    withAnimation {
                        proxy.scrollTo(newId, anchor: .center)
                    }
                }
            }
        }
    }
    
    /// >20 tabs: active tab = full width, others = icon-only
    @ViewBuilder
    func mixedTabsRow() -> some View {
        HStack(spacing: 1) {
            ForEach(viewModel.tabs) { tab in
                tabItem(tab: tab, isActive: viewModel.activeTabId == tab.id, width: (viewModel.activeTabId == tab.id) ? activeTabWidth : iconOnlyWidth)
            }
        }
    }
    
    /// <=20 tabs: all same width
    @ViewBuilder
    func uniformTabsRow(tabWidth: CGFloat) -> some View {
        HStack(spacing: 1) {
            ForEach(viewModel.tabs) { tab in
                tabItem(tab: tab, isActive: viewModel.activeTabId == tab.id, width: tabWidth)
            }
        }
    }
    
    @ViewBuilder
    func tabItem(tab: WebTab, isActive: Bool, width: CGFloat) -> some View {
        DesktopTabItem(
            tab: tab,
            isActive: isActive,
            tabWidth: width
        ) {
            withAnimation(.easeInOut(duration: 0.15)) {
                viewModel.activeTabId = tab.id
            }
        } onClose: {
            withAnimation(.easeInOut(duration: 0.2)) { viewModel.closeTab(id: tab.id) }
        }
        .contextMenu {
            Button("Tab mới") { viewModel.createNewTab() }
            Button("Nhân đôi tab") { viewModel.duplicateTab(id: tab.id) }
            Divider()
            Button(tab.isPinned ? "Bỏ ghim tab" : "Ghim tab") { viewModel.pinTab(id: tab.id) }
            Button(tab.isMuted ? "Bật âm thanh" : "Tắt âm thanh") { viewModel.muteTab(id: tab.id) }
            Divider()
            Menu("Thêm vào nhóm") {
                ForEach(viewModel.tabGroups) { group in
                    Button(group.name) { viewModel.addTabToGroup(tabId: tab.id, groupId: group.id) }
                }
                Divider()
                Button("Nhóm mới...") {
                    viewModel.createTabGroup(name: "Nhóm mới", tabIds: [tab.id])
                }
                if tab.groupId != nil {
                    Button("Xóa khỏi nhóm") { viewModel.removeTabFromGroup(tabId: tab.id) }
                }
            }
            Divider()
            Button("Đóng tab") { viewModel.closeTab(id: tab.id) }
            if viewModel.tabs.count > 1 {
                Button("Đóng tab khác") { viewModel.closeOtherTabs(except: tab.id) }
                Button("Đóng tab bên phải") { viewModel.closeTabsToRight(of: tab.id) }
            }
        }
    }
}

// MARK: - Tab Item with Real Favicon

struct DesktopTabItem: View {
    let tab: WebTab
    let isActive: Bool
    let tabWidth: CGFloat
    let onSelect: () -> Void
    let onClose: () -> Void
    @State private var isHovering = false
    
    /// 3 display tiers
    private var isIconOnly: Bool { tabWidth < 50 }
    private var isCompact: Bool { tabWidth < 120 && !isIconOnly }
    
    private var groupColor: Color {
        if let hex = tab.groupName != nil ? (tab.groupId != nil ? "#8B8BFF" : nil) : nil {
            return Color(hex: hex)
        }
        return .clear
    }

    var body: some View {
        HStack(spacing: isIconOnly ? 0 : (isCompact ? 4 : 6)) {
            // Pin indicator
            if tab.isPinned && isIconOnly {
                Image(systemName: "pin.fill")
                    .font(.system(size: 10))
                    .foregroundColor(BrandColors.accentPurple)
            } else if tab.isIncognito && isIconOnly {
                Image(systemName: "eye.slash.fill")
                    .font(.system(size: 12))
                    .foregroundColor(BrandColors.accentPink)
            } else {
                // Real favicon or fallback
                TabFavicon(faviconUrl: tab.faviconUrl, url: tab.url, size: isIconOnly ? 16 : 14)
            }

            // Title (hidden in icon-only mode)
            if !isIconOnly {
                HStack(spacing: 3) {
                    if tab.isPinned {
                        Image(systemName: "pin.fill")
                            .font(.system(size: 8))
                            .foregroundColor(BrandColors.accentPurple)
                    }
                    if tab.isIncognito {
                        Image(systemName: "eye.slash.fill")
                            .font(.system(size: 9))
                            .foregroundColor(BrandColors.accentPink)
                    }
                    if tab.isMuted {
                        Image(systemName: "speaker.slash.fill")
                            .font(.system(size: 9))
                            .foregroundColor(BrandColors.textSecondary)
                    }
                    Text(tab.title)
                        .font(.system(size: 11, weight: isActive ? .semibold : .regular))
                        .lineLimit(1)
                        .truncationMode(.tail)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }

            // Close button (hidden for pinned & icon-only, visible on active/hover)
            if !isIconOnly && !tab.isPinned && (isActive || isHovering) {
                Button(action: onClose) {
                    Image(systemName: "xmark")
                        .font(.system(size: 8, weight: .bold))
                        .foregroundColor(BrandColors.textSecondary)
                        .frame(width: 16, height: 16)
                        .background(Color.white.opacity(isHovering ? 0.1 : 0))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, isIconOnly ? 0 : (isCompact ? 6 : 10))
        .frame(width: tabWidth, height: 34)
        .background(
            Group {
                if isActive {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(tab.isIncognito ? BrandColors.accentPink.opacity(0.12) : Color.white.opacity(0.08))
                } else if isHovering {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.white.opacity(0.04))
                }
            }
        )
        .foregroundColor(isActive ? BrandColors.textPrimary : BrandColors.textSecondary)
        .onTapGesture(perform: onSelect)
        .onHover { hovering in isHovering = hovering }
        .overlay(alignment: .bottom) {
            if isActive {
                Rectangle()
                    .fill(tab.isIncognito ? BrandColors.accentPink : BrandColors.accentPurple)
                    .frame(height: 1.5)
            }
        }
        .overlay(alignment: .top) {
            // Group color indicator
            if tab.groupId != nil {
                Rectangle()
                    .fill(BrandColors.accentPurple)
                    .frame(height: 2)
                    .clipShape(RoundedRectangle(cornerRadius: 1))
            }
        }
        .help(tab.title)
    }
}

/// Loads a real favicon from Google's API, with smart fallback
struct TabFavicon: View {
    let faviconUrl: String?
    let url: String
    let size: CGFloat
    
    private var resolvedFaviconUrl: String {
        // Use stored favicon URL, or generate from current URL
        if let fav = faviconUrl, !fav.isEmpty { return fav }
        return UrlUtils.getFaviconUrl(url)
    }
    
    private var isInternalPage: Bool {
        url.hasPrefix("helix://") || url.isEmpty
    }
    
    var body: some View {
        if isInternalPage {
            // Internal pages get a branded icon
            internalIcon
        } else if let imgUrl = URL(string: resolvedFaviconUrl) {
            AsyncImage(url: imgUrl) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .interpolation(.high)
                        .aspectRatio(contentMode: .fit)
                        .frame(width: size, height: size)
                        .clipShape(RoundedRectangle(cornerRadius: 3))
                case .failure(_):
                    fallbackIcon
                case .empty:
                    fallbackIcon
                @unknown default:
                    fallbackIcon
                }
            }
        } else {
            fallbackIcon
        }
    }
    
    private var internalIcon: some View {
        Group {
            if url.contains("history") {
                Image(systemName: "clock.fill")
                    .font(.system(size: size * 0.75))
                    .foregroundColor(BrandColors.accentPurple)
            } else if url.contains("bookmarks") {
                Image(systemName: "bookmark.fill")
                    .font(.system(size: size * 0.75))
                    .foregroundColor(BrandColors.accentPurple)
            } else {
                // Start page / new tab
                Image(systemName: "globe.americas.fill")
                    .font(.system(size: size * 0.75))
                    .foregroundStyle(
                        LinearGradient(colors: [BrandColors.accentPurple, BrandColors.accentPink],
                                       startPoint: .topLeading, endPoint: .bottomTrailing)
                    )
            }
        }
        .frame(width: size, height: size)
    }
    
    private var fallbackIcon: some View {
        // First letter of domain as fallback
        let letter = extractDomainLetter()
        return ZStack {
            RoundedRectangle(cornerRadius: 3)
                .fill(BrandColors.accentPurple.opacity(0.2))
                .frame(width: size, height: size)
            Text(letter)
                .font(.system(size: size * 0.55, weight: .bold, design: .rounded))
                .foregroundColor(BrandColors.accentPurple)
        }
    }
    
    private func extractDomainLetter() -> String {
        guard let host = URL(string: url)?.host else { return "?" }
        let cleaned = host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
        return String(cleaned.prefix(1)).uppercased()
    }
}

// MARK: - Sidebar

struct SidebarView: View {
    @ObservedObject var viewModel: WebViewModel
    @State private var selectedSection = 0
    
    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $selectedSection) {
                Text("Lịch sử").tag(0)
                Text("Bookmarks").tag(1)
            }
            .pickerStyle(.segmented)
            .padding()
            
            List {
                if selectedSection == 0 {
                    ForEach(viewModel.getHistory(), id: \.self) { entry in
                        SidebarRow(title: entry["title"] ?? "", url: entry["url"] ?? "") {
                            viewModel.loadUrl(entry["url"] ?? "")
                        }
                    }
                } else {
                    ForEach(viewModel.getBookmarks(), id: \.self) { entry in
                        SidebarRow(title: entry["title"] ?? "", url: entry["url"] ?? "") {
                            viewModel.loadUrl(entry["url"] ?? "")
                        }
                    }
                }
            }
            .listStyle(.sidebar)
        }
        .background(BrandColors.toolbar.opacity(0.9))
    }
}

struct SidebarRow: View {
    let title: String
    let url: String
    let action: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(BrandColors.textPrimary)
            Text(url)
                .font(.system(size: 10))
                .foregroundColor(BrandColors.textSecondary)
                .lineLimit(1)
        }
        .padding(.vertical, 4)
        .onTapGesture(perform: action)
    }
}

// MARK: - Reusable Components

struct ToolbarButton: View {
    let icon: String
    var isActive: Bool = false
    var activeColor: Color = BrandColors.accentPurple
    var help: String = ""
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(isActive ? activeColor : BrandColors.textPrimary)
                .frame(width: 34, height: 34)
        }
        .buttonStyle(.plain)
        .help(help)
    }
}

struct NavButton: View {
    let icon: String
    let action: () -> Void
    let active: Bool
    
    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(active ? BrandColors.textPrimary : BrandColors.textSecondary.opacity(0.3))
                .frame(width: 26, height: 26)
                .background(Color.white.opacity(active ? 0.05 : 0))
                .clipShape(RoundedRectangle(cornerRadius: 6))
        }
        .buttonStyle(.plain)
        .disabled(!active)
    }
}

struct LinearProgressView: View {
    let progress: Double
    var body: some View {
        GeometryReader { geo in
            Rectangle()
                .fill(
                    LinearGradient(colors: [BrandColors.accentPurple, BrandColors.accentPink],
                                   startPoint: .leading, endPoint: .trailing)
                )
                .frame(width: geo.size.width * max(CGFloat(progress), 0.1), height: 2)
                .animation(.easeInOut(duration: 0.3), value: progress)
        }
        .frame(height: 2)
    }
}


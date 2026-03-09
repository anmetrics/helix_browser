import SwiftUI

struct StartPageView: View {
    @ObservedObject var viewModel: WebViewModel
    
    let columns = [
        GridItem(.adaptive(minimum: 100, maximum: 120), spacing: 20)
    ]
    
    var body: some View {
        ZStack {
            BrandColors.background.ignoresSafeArea()
            
            VisualEffectView(material: .underWindowBackground, blendingMode: .withinWindow)
                .ignoresSafeArea()
            
            ScrollView {
                VStack(spacing: 36) {
                    // Hero
                    VStack(spacing: 16) {
                        Image(systemName: "globe.americas.fill")
                            .font(.system(size: 72))
                            .foregroundStyle(
                                LinearGradient(colors: [BrandColors.accentPurple, BrandColors.accentPink],
                                               startPoint: .topLeading, endPoint: .bottomTrailing)
                            )
                            .shadow(color: BrandColors.accentPurple.opacity(0.4), radius: 20)

                        Text("Helix Browser")
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(BrandColors.textPrimary)

                        Text("Nhanh. An toàn. Riêng tư.")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(BrandColors.textSecondary)
                    }
                    .padding(.top, 60)

                    // Privacy Shield Stats
                    if viewModel.trackersBlocked > 0 {
                        HStack(spacing: 16) {
                            Image(systemName: "shield.checkered")
                                .font(.system(size: 24))
                                .foregroundColor(BrandColors.secureGreen)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(viewModel.trackersBlocked)")
                                    .font(.system(size: 20, weight: .bold, design: .rounded))
                                    .foregroundColor(BrandColors.secureGreen)
                                Text("Trình theo dõi đã bị chặn")
                                    .font(.system(size: 12))
                                    .foregroundColor(BrandColors.textSecondary)
                            }
                            Spacer()
                        }
                        .padding(16)
                        .background(BrandColors.secureGreen.opacity(0.08))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(BrandColors.secureGreen.opacity(0.2), lineWidth: 1)
                        )
                        .padding(.horizontal, 40)
                    }

                    // Favorites
                    VStack(alignment: .leading, spacing: 14) {
                        Text("Trang yêu thích")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(BrandColors.textSecondary)
                            .padding(.horizontal)

                        LazyVGrid(columns: columns, spacing: 20) {
                            FavoriteItem(title: "Google", icon: "magnifyingglass") { viewModel.loadUrl("https://www.google.com") }
                            FavoriteItem(title: "Facebook", icon: "person.2.fill") { viewModel.loadUrl("https://www.facebook.com") }
                            FavoriteItem(title: "YouTube", icon: "play.rectangle.fill") { viewModel.loadUrl("https://www.youtube.com") }
                            FavoriteItem(title: "GitHub", icon: "chevron.left.forwardslash.chevron.right") { viewModel.loadUrl("https://github.com") }
                            FavoriteItem(title: "Twitter", icon: "bubble.left.fill") { viewModel.loadUrl("https://twitter.com") }
                            FavoriteItem(title: "Reddit", icon: "r.circle.fill") { viewModel.loadUrl("https://www.reddit.com") }
                            FavoriteItem(title: "Wikipedia", icon: "book.fill") { viewModel.loadUrl("https://www.wikipedia.org") }
                            FavoriteItem(title: "Netflix", icon: "play.tv.fill") { viewModel.loadUrl("https://www.netflix.com") }
                        }
                        .padding(.horizontal)
                    }
                    .padding()
                    .background(Color.white.opacity(0.04))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.white.opacity(0.06), lineWidth: 1)
                    )
                    .padding(.horizontal, 40)

                    // Bookmarks section
                    let bookmarks = Array(viewModel.getBookmarks().prefix(8))
                    if !bookmarks.isEmpty {
                        VStack(alignment: .leading, spacing: 14) {
                            HStack {
                                Text("Dấu trang")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(BrandColors.textSecondary)
                                Spacer()
                                Button("Xem tất cả") { viewModel.loadUrl("helix://bookmarks") }
                                    .font(.system(size: 11))
                                    .foregroundColor(BrandColors.accentPurple)
                                    .buttonStyle(.plain)
                            }
                            .padding(.horizontal)

                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 200, maximum: 300), spacing: 12)], spacing: 12) {
                                ForEach(bookmarks, id: \.self) { item in
                                    RecentItem(title: item["title"] ?? "", url: item["url"] ?? "") {
                                        viewModel.loadUrl(item["url"] ?? "")
                                    }
                                }
                            }
                            .padding(.horizontal)
                        }
                        .padding()
                        .background(Color.white.opacity(0.04))
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color.white.opacity(0.06), lineWidth: 1)
                        )
                        .padding(.horizontal, 40)
                    }

                    // Recently Visited
                    let recentHistory = Array(viewModel.getHistory().prefix(6))
                    if !recentHistory.isEmpty {
                        VStack(alignment: .leading, spacing: 14) {
                            HStack {
                                Text("Đã truy cập gần đây")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(BrandColors.textSecondary)
                                Spacer()
                                Button("Xem tất cả") { viewModel.loadUrl("helix://history") }
                                    .font(.system(size: 11))
                                    .foregroundColor(BrandColors.accentPurple)
                                    .buttonStyle(.plain)
                            }
                            .padding(.horizontal)

                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 200, maximum: 300), spacing: 12)], spacing: 12) {
                                ForEach(recentHistory, id: \.self) { item in
                                    RecentItem(title: item["title"] ?? "", url: item["url"] ?? "") {
                                        viewModel.loadUrl(item["url"] ?? "")
                                    }
                                }
                            }
                            .padding(.horizontal)
                        }
                        .padding()
                        .background(Color.white.opacity(0.04))
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color.white.opacity(0.06), lineWidth: 1)
                        )
                        .padding(.horizontal, 40)
                    }

                    Spacer(minLength: 40)
                }
            }
        }
    }
}

struct FavoriteItem: View {
    let title: String
    let icon: String
    let action: () -> Void
    @State private var isHovering = false
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14)
                        .fill(.ultraThinMaterial)
                        .frame(width: 60, height: 60)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(Color.white.opacity(isHovering ? 0.15 : 0.05), lineWidth: 1)
                        )
                        .scaleEffect(isHovering ? 1.08 : 1.0)
                    
                    Image(systemName: icon)
                        .font(.system(size: 22))
                        .foregroundColor(BrandColors.textPrimary)
                }
                
                Text(title)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(BrandColors.textSecondary)
                    .lineLimit(1)
            }
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            withAnimation(.easeInOut(duration: 0.2)) { isHovering = hovering }
        }
    }
}

struct RecentItem: View {
    let title: String
    let url: String
    let action: () -> Void
    @State private var isHovering = false
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: "clock.arrow.circlepath")
                    .font(.system(size: 14))
                    .foregroundColor(BrandColors.accentPurple)
                    .frame(width: 28)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(title.isEmpty ? url : title)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(BrandColors.textPrimary)
                        .lineLimit(1)
                    
                    Text(UrlUtils.getDisplayUrl(url))
                        .font(.system(size: 10))
                        .foregroundColor(BrandColors.textSecondary)
                        .lineLimit(1)
                }
                
                Spacer()
            }
            .padding(10)
            .background(Color.white.opacity(isHovering ? 0.06 : 0.02))
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            withAnimation(.easeInOut(duration: 0.15)) { isHovering = hovering }
        }
    }
}

struct VisualEffectView: NSViewRepresentable {
    let material: NSVisualEffectView.Material
    let blendingMode: NSVisualEffectView.BlendingMode
    
    func makeNSView(context: Context) -> NSVisualEffectView {
        let view = NSVisualEffectView()
        view.material = material
        view.blendingMode = blendingMode
        view.state = .active
        return view
    }
    
    func updateNSView(_ nsView: NSVisualEffectView, context: Context) {
        nsView.material = material
        nsView.blendingMode = blendingMode
    }
}

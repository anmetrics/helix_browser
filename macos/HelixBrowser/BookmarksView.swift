import SwiftUI

struct BookmarksView: View {
    @ObservedObject var viewModel: WebViewModel
    @State private var searchText = ""

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Image(systemName: "bookmark.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(
                        LinearGradient(colors: [BrandColors.accentPurple, BrandColors.accentPink],
                                       startPoint: .topLeading, endPoint: .bottomTrailing)
                    )
                Text("Dấu trang")
                    .font(.title2.bold())
                    .foregroundColor(BrandColors.textPrimary)
                Spacer()

                TextField("Tìm kiếm dấu trang...", text: $searchText)
                    .textFieldStyle(.roundedBorder)
                    .frame(width: 250)
            }
            .padding()
            .background(BrandColors.toolbar)

            Divider().background(Color.white.opacity(0.1))

            // Bookmarks List
            if filteredBookmarks.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "bookmark.slash")
                        .font(.system(size: 48))
                        .foregroundColor(BrandColors.textSecondary.opacity(0.4))
                    Text(searchText.isEmpty ? "Chưa có dấu trang" : "Không tìm thấy")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(BrandColors.textSecondary)
                    if searchText.isEmpty {
                        Text("Nhấn ⌘D để thêm trang hiện tại vào dấu trang")
                            .font(.system(size: 12))
                            .foregroundColor(BrandColors.textSecondary.opacity(0.6))
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(filteredBookmarks, id: \.self) { bookmark in
                        BookmarkRow(bookmark: bookmark) {
                            viewModel.loadUrl(bookmark["url"] ?? "")
                        } onDelete: {
                            viewModel.deleteBookmark(url: bookmark["url"] ?? "")
                        }
                    }
                }
                .listStyle(.inset)
            }
        }
        .background(BrandColors.background)
    }

    var filteredBookmarks: [[String: String]] {
        let bookmarks = viewModel.getBookmarks()
        if searchText.isEmpty { return bookmarks }
        return bookmarks.filter {
            ($0["title"] ?? "").localizedCaseInsensitiveContains(searchText) ||
            ($0["url"] ?? "").localizedCaseInsensitiveContains(searchText)
        }
    }
}

struct BookmarkRow: View {
    let bookmark: [String: String]
    let action: () -> Void
    let onDelete: () -> Void
    @State private var isHovering = false

    var body: some View {
        HStack(spacing: 12) {
            Button(action: action) {
                HStack(spacing: 12) {
                    TabFavicon(faviconUrl: bookmark["favicon"], url: bookmark["url"] ?? "", size: 16)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(bookmark["title"] ?? "Untitled")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(BrandColors.textPrimary)
                            .lineLimit(1)

                        Text(UrlUtils.getDisplayUrl(bookmark["url"] ?? ""))
                            .font(.system(size: 11))
                            .foregroundColor(BrandColors.textSecondary)
                            .lineLimit(1)
                    }

                    Spacer()
                }
            }
            .buttonStyle(.plain)

            if isHovering {
                Button(action: onDelete) {
                    Image(systemName: "trash")
                        .font(.system(size: 12))
                        .foregroundColor(BrandColors.accentPink)
                }
                .buttonStyle(.plain)
                .help("Xóa dấu trang")
            }
        }
        .padding(.vertical, 4)
        .onHover { hovering in isHovering = hovering }
    }
}

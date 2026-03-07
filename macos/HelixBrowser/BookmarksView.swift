import SwiftUI

struct BookmarksView: View {
    @ObservedObject var viewModel: WebViewModel
    
    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("Dấu trang (Bookmarks)")
                    .font(.title2.bold())
                Spacer()
                
                Button(action: { /* Add folder logic later */ }) {
                    Label("Thư mục mới", systemImage: "folder.badge.plus")
                }
                .buttonStyle(.bordered)
            }
            .padding()
            .background(BrandColors.toolbar)
            
            Divider().background(Color.white.opacity(0.1))
            
            // Bookmarks List
            List {
                ForEach(viewModel.getBookmarks(), id: \.self) { bookmark in
                    BookmarkRow(bookmark: bookmark) {
                        viewModel.loadUrl(bookmark["url"] ?? "")
                    }
                }
            }
            .listStyle(.inset)
        }
        .background(BrandColors.background)
    }
}

struct BookmarkRow: View {
    let bookmark: [String: String]
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: "bookmark.fill")
                    .foregroundColor(BrandColors.accentPurple)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(bookmark["title"] ?? "Untitled")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(BrandColors.textPrimary)
                    
                    Text(bookmark["url"] ?? "")
                        .font(.system(size: 11))
                        .foregroundColor(BrandColors.textSecondary)
                }
                
                Spacer()
            }
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
    }
}

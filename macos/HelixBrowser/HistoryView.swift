import SwiftUI

struct HistoryView: View {
    @ObservedObject var viewModel: WebViewModel
    @State private var searchText = ""
    
    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("Lịch sử")
                    .font(.title2.bold())
                Spacer()
                
                TextField("Tìm kiếm lịch sử...", text: $searchText)
                    .textFieldStyle(.roundedBorder)
                    .frame(width: 250)
                
                Button("Xóa tất cả") {
                    UserDefaults.standard.removeObject(forKey: "helix_history")
                    viewModel.objectWillChange.send()
                }
                .buttonStyle(.bordered)
            }
            .padding()
            .background(BrandColors.toolbar)
            
            Divider().background(Color.white.opacity(0.1))
            
            // History List
            List {
                ForEach(filteredHistory, id: \.self) { item in
                    HistoryRow(item: item) {
                        viewModel.loadUrl(item["url"] ?? "")
                    }
                }
            }
            .listStyle(.inset)
        }
        .background(BrandColors.background)
    }
    
    var filteredHistory: [[String: String]] {
        let history = viewModel.getHistory()
        if searchText.isEmpty { return history }
        return history.filter { 
            ($0["title"] ?? "").localizedCaseInsensitiveContains(searchText) ||
            ($0["url"] ?? "").localizedCaseInsensitiveContains(searchText)
        }
    }
}

struct HistoryRow: View {
    let item: [String: String]
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: "clock.arrow.circlepath")
                    .foregroundColor(BrandColors.textSecondary)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(item["title"] ?? "Untitled")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(BrandColors.textPrimary)
                    
                    Text(item["url"] ?? "")
                        .font(.system(size: 11))
                        .foregroundColor(BrandColors.textSecondary)
                }
                
                Spacer()
                
                if let timestamp = Double(item["timestamp"] ?? "0") {
                    Text(formatDate(Date(timeIntervalSince1970: timestamp)))
                        .font(.system(size: 10))
                        .foregroundColor(BrandColors.textSecondary)
                }
            }
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
    }
    
    func formatDate(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}

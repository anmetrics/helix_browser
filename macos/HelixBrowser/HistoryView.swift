import SwiftUI

struct HistoryView: View {
    @ObservedObject var viewModel: WebViewModel
    @State private var searchText = ""
    @State private var showClearConfirm = false

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Image(systemName: "clock.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(
                        LinearGradient(colors: [BrandColors.accentPurple, BrandColors.accentPink],
                                       startPoint: .topLeading, endPoint: .bottomTrailing)
                    )
                Text("Lịch sử")
                    .font(.title2.bold())
                    .foregroundColor(BrandColors.textPrimary)
                Spacer()

                TextField("Tìm kiếm lịch sử...", text: $searchText)
                    .textFieldStyle(.roundedBorder)
                    .frame(width: 250)

                Button(action: { showClearConfirm = true }) {
                    HStack(spacing: 4) {
                        Image(systemName: "trash")
                        Text("Xóa tất cả")
                    }
                }
                .buttonStyle(.bordered)
                .tint(BrandColors.accentPink)
            }
            .padding()
            .background(BrandColors.toolbar)

            Divider().background(Color.white.opacity(0.1))

            // History List grouped by date
            if groupedHistory.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "clock.badge.xmark")
                        .font(.system(size: 48))
                        .foregroundColor(BrandColors.textSecondary.opacity(0.4))
                    Text("Không có lịch sử")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(BrandColors.textSecondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(groupedHistory, id: \.0) { group in
                        Section(header:
                            Text(group.0)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(BrandColors.accentPurple)
                                .textCase(.uppercase)
                        ) {
                            ForEach(group.1, id: \.self) { item in
                                HistoryRow(item: item) {
                                    viewModel.loadUrl(item["url"] ?? "")
                                } onDelete: {
                                    viewModel.deleteHistoryItem(url: item["url"] ?? "")
                                }
                            }
                        }
                    }
                }
                .listStyle(.inset)
            }
        }
        .background(BrandColors.background)
        .alert("Xóa tất cả lịch sử?", isPresented: $showClearConfirm) {
            Button("Xóa", role: .destructive) {
                viewModel.clearHistory()
            }
            Button("Hủy", role: .cancel) {}
        } message: {
            Text("Hành động này không thể hoàn tác.")
        }
    }

    var groupedHistory: [(String, [[String: String]])] {
        let history = filteredHistory
        var groups: [String: [[String: String]]] = [:]
        let calendar = Calendar.current
        let now = Date()

        for item in history {
            guard let ts = Double(item["timestamp"] ?? "0") else { continue }
            let date = Date(timeIntervalSince1970: ts)
            let label: String
            if calendar.isDateInToday(date) {
                label = "Hôm nay"
            } else if calendar.isDateInYesterday(date) {
                label = "Hôm qua"
            } else if calendar.isDate(date, equalTo: now, toGranularity: .weekOfYear) {
                label = "Tuần này"
            } else if calendar.isDate(date, equalTo: now, toGranularity: .month) {
                label = "Tháng này"
            } else {
                let formatter = DateFormatter()
                formatter.dateFormat = "MMMM yyyy"
                label = formatter.string(from: date)
            }
            groups[label, default: []].append(item)
        }

        let order = ["Hôm nay", "Hôm qua", "Tuần này", "Tháng này"]
        return groups.sorted { a, b in
            let ai = order.firstIndex(of: a.key) ?? Int.max
            let bi = order.firstIndex(of: b.key) ?? Int.max
            return ai < bi
        }
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
    let onDelete: () -> Void
    @State private var isHovering = false

    var body: some View {
        HStack(spacing: 12) {
            Button(action: action) {
                HStack(spacing: 12) {
                    // Favicon
                    TabFavicon(faviconUrl: nil, url: item["url"] ?? "", size: 16)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(item["title"] ?? "Untitled")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(BrandColors.textPrimary)
                            .lineLimit(1)

                        Text(item["url"] ?? "")
                            .font(.system(size: 11))
                            .foregroundColor(BrandColors.textSecondary)
                            .lineLimit(1)
                    }

                    Spacer()

                    if let timestamp = Double(item["timestamp"] ?? "0") {
                        Text(formatDate(Date(timeIntervalSince1970: timestamp)))
                            .font(.system(size: 10))
                            .foregroundColor(BrandColors.textSecondary)
                    }
                }
            }
            .buttonStyle(.plain)

            if isHovering {
                Button(action: onDelete) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 14))
                        .foregroundColor(BrandColors.textSecondary)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 4)
        .onHover { hovering in isHovering = hovering }
    }

    func formatDate(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}

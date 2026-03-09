import SwiftUI

struct SettingsView: View {
    @ObservedObject var viewModel: WebViewModel
    @ObservedObject var prefs = Prefs.shared
    
    var body: some View {
        ZStack {
            BrandColors.background.ignoresSafeArea()
            
            ScrollView {
                VStack(spacing: 24) {
                    // Header
                    VStack(spacing: 8) {
                        Image(systemName: "gearshape.fill")
                            .font(.system(size: 48))
                            .foregroundStyle(
                                LinearGradient(colors: [BrandColors.accentPurple, BrandColors.accentPink],
                                               startPoint: .topLeading, endPoint: .bottomTrailing)
                            )
                        Text("Cài đặt")
                            .font(.system(size: 24, weight: .bold, design: .rounded))
                            .foregroundColor(BrandColors.textPrimary)
                    }
                    .padding(.top, 40)
                    
                    // General
                    SettingsSection(title: "Chung", icon: "globe") {
                        SettingsToggle(title: "Phiên bản máy tính", subtitle: "Yêu cầu website gửi bản desktop", icon: "desktopcomputer", isOn: $prefs.isDesktopMode)
                        SettingsDivider()
                        SettingsToggle(title: "Lưu lịch sử duyệt web", subtitle: "Tự động lưu các trang đã truy cập", icon: "clock.arrow.circlepath", isOn: $prefs.isSaveHistoryEnabled)
                    }
                    
                    // Privacy & Security
                    SettingsSection(title: "Quyền riêng tư & Bảo mật", icon: "shield.fill") {
                        SettingsToggle(title: "Chặn quảng cáo", subtitle: "Chặn quảng cáo và trình theo dõi trên web", icon: "hand.raised.fill", isOn: $prefs.isAdBlockEnabled)
                        SettingsDivider()
                        SettingsToggle(title: "Chặn trình theo dõi", subtitle: "Ngăn các trang web theo dõi hoạt động của bạn", icon: "eye.slash.fill", isOn: $prefs.isBlockTrackersEnabled)
                        SettingsDivider()
                        SettingsToggle(title: "Chặn cookie bên thứ ba", subtitle: "Ngăn cookie theo dõi từ các trang khác", icon: "xmark.shield.fill", isOn: $prefs.isBlockThirdPartyCookies)
                        SettingsDivider()
                        SettingsToggle(title: "Do Not Track", subtitle: "Gửi yêu cầu không theo dõi đến website", icon: "hand.raised.slash.fill", isOn: $prefs.isDoNotTrackEnabled)
                        SettingsDivider()
                        SettingsToggle(title: "Nâng cấp HTTPS", subtitle: "Tự động chuyển sang kết nối an toàn", icon: "lock.shield.fill", isOn: $prefs.isHttpsUpgradeEnabled)
                        SettingsDivider()
                        SettingsToggle(title: "Chống lấy dấu vân tay", subtitle: "Ngăn kỹ thuật nhận diện trình duyệt", icon: "fingerprint", isOn: $prefs.isBlockFingerprintingEnabled)
                        SettingsDivider()
                        SettingsToggle(title: "Chặn popup", subtitle: "Ngăn các cửa sổ bật lên không mong muốn", icon: "rectangle.on.rectangle.slash", isOn: $prefs.isBlockPopupsEnabled)

                        SettingsDivider()

                        // Privacy stats
                        HStack {
                            Image(systemName: "shield.checkered")
                                .font(.system(size: 14))
                                .foregroundColor(BrandColors.secureGreen)
                                .frame(width: 28)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Trình theo dõi đã chặn")
                                    .font(.system(size: 13))
                                    .foregroundColor(BrandColors.textPrimary)
                                Text("\(viewModel.trackersBlocked) trình theo dõi")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(BrandColors.secureGreen)
                            }
                            Spacer()
                        }
                        .padding(.vertical, 4)

                        SettingsDivider()

                        // Clear data buttons
                        HStack {
                            Image(systemName: "trash.fill")
                                .font(.system(size: 14))
                                .foregroundColor(BrandColors.accentPink)
                                .frame(width: 28)
                            Text("Xóa dữ liệu duyệt web")
                                .font(.system(size: 13))
                                .foregroundColor(BrandColors.textPrimary)
                            Spacer()
                            Button("Xóa tất cả") {
                                viewModel.clearAllBrowsingData()
                            }
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 5)
                            .background(BrandColors.accentPink.opacity(0.8))
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                        }
                        .padding(.vertical, 4)
                    }
                    
                    // Search Engine
                    SettingsSection(title: "Công cụ tìm kiếm", icon: "magnifyingglass") {
                        HStack {
                            Image(systemName: "magnifyingglass")
                                .font(.system(size: 14))
                                .foregroundColor(BrandColors.accentPurple)
                                .frame(width: 28)
                            
                            Text("Công cụ tìm kiếm mặc định")
                                .font(.system(size: 13))
                                .foregroundColor(BrandColors.textPrimary)
                            
                            Spacer()
                            
                            Picker("", selection: $prefs.searchEngine) {
                                Text("Google").tag("google")
                                Text("Bing").tag("bing")
                                Text("DuckDuckGo").tag("duckduckgo")
                                Text("Yahoo").tag("yahoo")
                                Text("Brave").tag("brave")
                            }
                            .pickerStyle(.menu)
                            .frame(width: 140)
                        }
                        .padding(.vertical, 4)
                    }
                    
                    // Homepage
                    SettingsSection(title: "Trang chủ", icon: "house.fill") {
                        HStack {
                            Image(systemName: "house.fill")
                                .font(.system(size: 14))
                                .foregroundColor(BrandColors.accentPurple)
                                .frame(width: 28)
                            
                            Text("URL trang chủ")
                                .font(.system(size: 13))
                                .foregroundColor(BrandColors.textPrimary)
                            
                            Spacer()
                            
                            TextField("https://www.google.com", text: $prefs.homepage)
                                .textFieldStyle(.plain)
                                .font(.system(size: 12))
                                .foregroundColor(BrandColors.textPrimary)
                                .padding(6)
                                .background(BrandColors.addressBar)
                                .clipShape(RoundedRectangle(cornerRadius: 6))
                                .frame(width: 250)
                        }
                        .padding(.vertical, 4)
                    }
                    
                    // Zoom
                    SettingsSection(title: "Thu phóng trang", icon: "textformat.size") {
                        HStack {
                            Image(systemName: "textformat.size")
                                .font(.system(size: 14))
                                .foregroundColor(BrandColors.accentPurple)
                                .frame(width: 28)
                            
                            Text("Mức thu phóng mặc định")
                                .font(.system(size: 13))
                                .foregroundColor(BrandColors.textPrimary)
                            
                            Spacer()
                            
                            HStack(spacing: 12) {
                                Button(action: { if prefs.defaultZoom > 50 { prefs.defaultZoom -= 10 } }) {
                                    Image(systemName: "minus.circle.fill")
                                        .font(.system(size: 18))
                                        .foregroundColor(BrandColors.textSecondary)
                                }
                                .buttonStyle(.plain)
                                
                                Text("\(prefs.defaultZoom)%")
                                    .font(.system(size: 14, weight: .semibold, design: .monospaced))
                                    .foregroundColor(BrandColors.textPrimary)
                                    .frame(width: 50)
                                
                                Button(action: { if prefs.defaultZoom < 300 { prefs.defaultZoom += 10 } }) {
                                    Image(systemName: "plus.circle.fill")
                                        .font(.system(size: 18))
                                        .foregroundColor(BrandColors.textSecondary)
                                }
                                .buttonStyle(.plain)
                                
                                Button("Đặt lại") {
                                    prefs.defaultZoom = 100
                                }
                                .font(.system(size: 11))
                                .foregroundColor(BrandColors.accentPurple)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    
                    // Tab behavior
                    SettingsSection(title: "Quản lý tab", icon: "square.on.square") {
                        SettingsToggle(title: "Khôi phục tab khi khởi động", subtitle: "Mở lại các tab từ phiên trước", icon: "arrow.counterclockwise", isOn: $prefs.isRestoreTabsEnabled)
                        SettingsDivider()
                        SettingsToggle(title: "Tạm ngưng tab không hoạt động", subtitle: "Tiết kiệm bộ nhớ bằng cách tạm dừng tab cũ", icon: "moon.zzz.fill", isOn: $prefs.isSuspendInactiveEnabled)
                        SettingsDivider()
                        SettingsToggle(title: "Xác nhận khi đóng nhiều tab", subtitle: "Hỏi trước khi đóng nhiều tab cùng lúc", icon: "exclamationmark.triangle.fill", isOn: $prefs.isConfirmCloseMultiple)
                    }

                    // About
                    SettingsSection(title: "Thông tin", icon: "info.circle.fill") {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Helix Browser")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(BrandColors.textPrimary)
                                Text("Phiên bản 3.0 (Build 1)")
                                    .font(.system(size: 12))
                                    .foregroundColor(BrandColors.textSecondary)
                                Text("WebKit Engine • Universal Binary (arm64 + x86_64)")
                                    .font(.system(size: 11))
                                    .foregroundColor(BrandColors.textSecondary.opacity(0.7))
                                Text("Hỗ trợ: macOS, iOS, Android, Windows, Linux")
                                    .font(.system(size: 11))
                                    .foregroundColor(BrandColors.textSecondary.opacity(0.7))
                            }
                            Spacer()
                        }
                        .padding(.vertical, 4)
                    }
                    
                    Spacer(minLength: 40)
                }
                .frame(maxWidth: 600)
                .padding(.horizontal, 40)
            }
        }
    }
}

// MARK: - Settings Components

struct SettingsSection<Content: View>: View {
    let title: String
    let icon: String
    @ViewBuilder let content: Content
    
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(BrandColors.accentPurple)
                Text(title)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(BrandColors.textSecondary)
                    .textCase(.uppercase)
            }
            .padding(.bottom, 8)
            
            VStack(spacing: 0) {
                content
            }
            .padding(16)
            .background(Color.white.opacity(0.04))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.white.opacity(0.06), lineWidth: 1)
            )
        }
    }
}

struct SettingsToggle: View {
    let title: String
    let subtitle: String
    let icon: String
    @Binding var isOn: Bool
    
    var body: some View {
        HStack {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundColor(BrandColors.accentPurple)
                .frame(width: 28)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 13))
                    .foregroundColor(BrandColors.textPrimary)
                Text(subtitle)
                    .font(.system(size: 11))
                    .foregroundColor(BrandColors.textSecondary)
            }
            
            Spacer()
            
            Toggle("", isOn: $isOn)
                .toggleStyle(.switch)
                .controlSize(.small)
        }
        .padding(.vertical, 4)
    }
}

struct SettingsDivider: View {
    var body: some View {
        Divider()
            .background(Color.white.opacity(0.06))
            .padding(.vertical, 4)
    }
}

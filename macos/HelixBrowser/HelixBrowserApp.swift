import SwiftUI

@main
struct HelixBrowserApp: App {
    @StateObject private var viewModel = WebViewModel()
    
    init() {
        // Set process name for Activity Monitor
        ProcessInfo.processInfo.processName = "Helix Browser"
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .frame(minWidth: 800, minHeight: 500)
        }
        .windowStyle(.hiddenTitleBar)
        .windowToolbarStyle(.unified)
        .commands {
            // File Menu
            CommandGroup(replacing: .newItem) {
                Button("Tab mới") {
                    NotificationCenter.default.post(name: NSNotification.Name("AppNewTab"), object: nil)
                }
                .keyboardShortcut("t", modifiers: .command)

                Button("Tab ẩn danh mới") {
                    NotificationCenter.default.post(name: NSNotification.Name("AppNewIncognitoTab"), object: nil)
                }
                .keyboardShortcut("t", modifiers: [.command, .shift])

                Divider()

                Button("Đóng Tab") {
                    NotificationCenter.default.post(name: NSNotification.Name("AppCloseTab"), object: nil)
                }
                .keyboardShortcut("w", modifiers: .command)
            }
            
            // Edit → Find
            CommandGroup(after: .textEditing) {
                Button("Tìm trên trang...") {
                    NotificationCenter.default.post(name: NSNotification.Name("AppFind"), object: nil)
                }
                .keyboardShortcut("f", modifiers: .command)
            }
            
            // View Menu
            CommandGroup(after: .toolbar) {
                Button("Tải lại") {
                    NotificationCenter.default.post(name: NSNotification.Name("AppReload"), object: nil)
                }
                .keyboardShortcut("r", modifiers: .command)
                
                Divider()
                
                Button("Phóng to") {
                    NotificationCenter.default.post(name: NSNotification.Name("AppZoomIn"), object: nil)
                }
                .keyboardShortcut("+", modifiers: .command)
                
                Button("Thu nhỏ") {
                    NotificationCenter.default.post(name: NSNotification.Name("AppZoomOut"), object: nil)
                }
                .keyboardShortcut("-", modifiers: .command)
                
                Button("Kích thước gốc") {
                    NotificationCenter.default.post(name: NSNotification.Name("AppZoomReset"), object: nil)
                }
                .keyboardShortcut("0", modifiers: .command)
                
                Divider()
                
                Button("Thanh bên") {
                    NotificationCenter.default.post(name: NSNotification.Name("AppToggleSidebar"), object: nil)
                }
                .keyboardShortcut("s", modifiers: [.command, .shift])
                
                Button("Tập trung vào thanh địa chỉ") {
                    NotificationCenter.default.post(name: NSNotification.Name("AppFocusURL"), object: nil)
                }
                .keyboardShortcut("l", modifiers: .command)
                
                Divider()
                
                Button("Cài đặt") {
                    NotificationCenter.default.post(name: NSNotification.Name("AppSettings"), object: nil)
                }
                .keyboardShortcut(",", modifiers: .command)
            }
            
            // Bookmarks
            CommandMenu("Bookmarks") {
                Button("Bookmark trang này") {
                    NotificationCenter.default.post(name: NSNotification.Name("AppBookmark"), object: nil)
                }
                .keyboardShortcut("d", modifiers: .command)
                
                Button("Xem Bookmarks") {
                    NotificationCenter.default.post(name: NSNotification.Name("AppShowBookmarks"), object: nil)
                }
                .keyboardShortcut("b", modifiers: [.command, .option])
            }
            
            // History
            CommandMenu("History") {
                Button("Quay lại") {
                    NotificationCenter.default.post(name: NSNotification.Name("AppGoBack"), object: nil)
                }
                .keyboardShortcut("[", modifiers: .command)
                
                Button("Tiến tới") {
                    NotificationCenter.default.post(name: NSNotification.Name("AppGoForward"), object: nil)
                }
                .keyboardShortcut("]", modifiers: .command)
                
                Divider()
                
                Button("Xem lịch sử") {
                    NotificationCenter.default.post(name: NSNotification.Name("AppShowHistory"), object: nil)
                }
                .keyboardShortcut("y", modifiers: .command)
            }
        }
    }
}

import Foundation
import WebKit
import SwiftUI

struct DownloadItem: Identifiable {
    let id = UUID()
    let filename: String
    let url: String
    var progress: Double = 0
    var isComplete: Bool = false
    var error: String?
    var localPath: URL?
}

class DownloadManager: NSObject, ObservableObject {
    static let shared = DownloadManager()
    
    @Published var downloads: [DownloadItem] = []
    @Published var activeDownloadCount: Int = 0
    
    private var downloadMap: [WKDownload: UUID] = [:]
    
    private override init() { super.init() }
    
    var downloadsDirectory: URL {
        FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first!
    }
    
    func handleDownload(_ download: WKDownload, filename: String, url: String) {
        let item = DownloadItem(filename: filename, url: url)
        DispatchQueue.main.async {
            self.downloads.insert(item, at: 0)
            self.activeDownloadCount += 1
        }
        downloadMap[download] = item.id
        download.delegate = self
    }
    
    private func updateItem(id: UUID, update: @escaping (inout DownloadItem) -> Void) {
        DispatchQueue.main.async {
            if let index = self.downloads.firstIndex(where: { $0.id == id }) {
                update(&self.downloads[index])
            }
        }
    }
}

extension DownloadManager: WKDownloadDelegate {
    func download(_ download: WKDownload, decideDestinationUsing response: URLResponse, suggestedFilename: String, completionHandler: @escaping (URL?) -> Void) {
        let dest = downloadsDirectory.appendingPathComponent(suggestedFilename)
        
        // Remove existing file if needed
        try? FileManager.default.removeItem(at: dest)
        
        if let id = downloadMap[download] {
            updateItem(id: id) { item in
                item.localPath = dest
            }
        }
        completionHandler(dest)
    }
    
    func downloadDidFinish(_ download: WKDownload) {
        if let id = downloadMap[download] {
            updateItem(id: id) { item in
                item.isComplete = true
                item.progress = 1.0
            }
            DispatchQueue.main.async {
                self.activeDownloadCount = max(0, self.activeDownloadCount - 1)
            }
        }
        downloadMap.removeValue(forKey: download)
    }
    
    func download(_ download: WKDownload, didFailWithError error: Error, resumeData: Data?) {
        if let id = downloadMap[download] {
            updateItem(id: id) { item in
                item.error = error.localizedDescription
            }
            DispatchQueue.main.async {
                self.activeDownloadCount = max(0, self.activeDownloadCount - 1)
            }
        }
        downloadMap.removeValue(forKey: download)
    }
}

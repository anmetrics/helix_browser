import UIKit

class BookmarksViewController: UITableViewController, UISearchResultsUpdating {
    var onSelectUrl: ((String) -> Void)?
    private var bookmarks: [[String: String]] = []
    private var filteredBookmarks: [[String: String]] = []
    private let searchController = UISearchController(searchResultsController: nil)

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Dấu trang"
        navigationController?.navigationBar.prefersLargeTitles = true
        view.backgroundColor = BrandColors.background
        tableView.backgroundColor = BrandColors.background
        tableView.separatorColor = UIColor.white.withAlphaComponent(0.06)

        searchController.searchResultsUpdater = self
        searchController.obscuresBackgroundDuringPresentation = false
        searchController.searchBar.placeholder = "Tìm kiếm dấu trang..."
        navigationItem.searchController = searchController
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(dismissView))

        loadBookmarks()
    }

    private func loadBookmarks() {
        bookmarks = UserDefaults.standard.array(forKey: "helix_bookmarks") as? [[String: String]] ?? []
        filteredBookmarks = bookmarks
        tableView.reloadData()
    }

    @objc private func dismissView() { dismiss(animated: true) }

    func updateSearchResults(for searchController: UISearchController) {
        let query = searchController.searchBar.text ?? ""
        filteredBookmarks = query.isEmpty ? bookmarks : bookmarks.filter {
            ($0["title"] ?? "").localizedCaseInsensitiveContains(query) ||
            ($0["url"] ?? "").localizedCaseInsensitiveContains(query)
        }
        tableView.reloadData()
    }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return filteredBookmarks.count
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        cell.backgroundColor = BrandColors.toolbar
        let item = filteredBookmarks[indexPath.row]
        cell.textLabel?.text = item["title"] ?? "Untitled"
        cell.textLabel?.textColor = .white
        cell.textLabel?.font = .systemFont(ofSize: 14, weight: .medium)
        cell.detailTextLabel?.text = UrlUtils.getDisplayUrl(item["url"] ?? "")
        cell.detailTextLabel?.textColor = BrandColors.textSecondary
        cell.imageView?.image = UIImage(systemName: "bookmark.fill")
        cell.imageView?.tintColor = BrandColors.accentPurpleUI
        return cell
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let url = filteredBookmarks[indexPath.row]["url"] ?? ""
        dismiss(animated: true) { [weak self] in self?.onSelectUrl?(url) }
    }

    override func tableView(_ tableView: UITableView, commit editingStyle: UITableViewCell.EditingStyle, forRowAt indexPath: IndexPath) {
        if editingStyle == .delete {
            let url = filteredBookmarks[indexPath.row]["url"] ?? ""
            bookmarks.removeAll(where: { $0["url"] == url })
            filteredBookmarks.remove(at: indexPath.row)
            UserDefaults.standard.set(bookmarks, forKey: "helix_bookmarks")
            tableView.deleteRows(at: [indexPath], with: .fade)
        }
    }
}

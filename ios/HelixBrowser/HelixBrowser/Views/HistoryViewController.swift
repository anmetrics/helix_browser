import UIKit

class HistoryViewController: UITableViewController, UISearchResultsUpdating {
    var onSelectUrl: ((String) -> Void)?
    private var history: [[String: String]] = []
    private var filteredHistory: [[String: String]] = []
    private let searchController = UISearchController(searchResultsController: nil)

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Lịch sử"
        navigationController?.navigationBar.prefersLargeTitles = true
        view.backgroundColor = BrandColors.background
        tableView.backgroundColor = BrandColors.background
        tableView.separatorColor = UIColor.white.withAlphaComponent(0.06)

        searchController.searchResultsUpdater = self
        searchController.obscuresBackgroundDuringPresentation = false
        searchController.searchBar.placeholder = "Tìm kiếm lịch sử..."
        navigationItem.searchController = searchController

        navigationItem.rightBarButtonItems = [
            UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(dismissView)),
            UIBarButtonItem(title: "Xóa tất cả", style: .plain, target: self, action: #selector(clearAll))
        ]

        loadHistory()
    }

    private func loadHistory() {
        history = UserDefaults.standard.array(forKey: "helix_history") as? [[String: String]] ?? []
        filteredHistory = history
        tableView.reloadData()
    }

    @objc private func dismissView() { dismiss(animated: true) }

    @objc private func clearAll() {
        let alert = UIAlertController(title: "Xóa tất cả lịch sử?", message: "Không thể hoàn tác.", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Xóa", style: .destructive) { [weak self] _ in
            UserDefaults.standard.removeObject(forKey: "helix_history")
            self?.loadHistory()
        })
        alert.addAction(UIAlertAction(title: "Hủy", style: .cancel))
        present(alert, animated: true)
    }

    func updateSearchResults(for searchController: UISearchController) {
        let query = searchController.searchBar.text ?? ""
        if query.isEmpty {
            filteredHistory = history
        } else {
            filteredHistory = history.filter {
                ($0["title"] ?? "").localizedCaseInsensitiveContains(query) ||
                ($0["url"] ?? "").localizedCaseInsensitiveContains(query)
            }
        }
        tableView.reloadData()
    }

    // MARK: - Table

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return filteredHistory.count
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        cell.backgroundColor = BrandColors.toolbar
        let item = filteredHistory[indexPath.row]
        cell.textLabel?.text = item["title"] ?? "Untitled"
        cell.textLabel?.textColor = .white
        cell.textLabel?.font = .systemFont(ofSize: 14, weight: .medium)
        cell.detailTextLabel?.text = UrlUtils.getDisplayUrl(item["url"] ?? "")
        cell.detailTextLabel?.textColor = BrandColors.textSecondary
        cell.imageView?.image = UIImage(systemName: "clock.arrow.circlepath")
        cell.imageView?.tintColor = BrandColors.accentPurpleUI
        return cell
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let url = filteredHistory[indexPath.row]["url"] ?? ""
        dismiss(animated: true) { [weak self] in self?.onSelectUrl?(url) }
    }

    override func tableView(_ tableView: UITableView, commit editingStyle: UITableViewCell.EditingStyle, forRowAt indexPath: IndexPath) {
        if editingStyle == .delete {
            let url = filteredHistory[indexPath.row]["url"] ?? ""
            history.removeAll(where: { $0["url"] == url })
            filteredHistory.remove(at: indexPath.row)
            UserDefaults.standard.set(history, forKey: "helix_history")
            tableView.deleteRows(at: [indexPath], with: .fade)
        }
    }
}

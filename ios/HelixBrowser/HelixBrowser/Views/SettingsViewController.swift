import UIKit

class SettingsViewController: UITableViewController {

    private let prefs = Prefs.shared

    private enum Section: Int, CaseIterable {
        case general, privacy, searchEngine, tabBehavior, data, about
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Cài đặt"
        navigationController?.navigationBar.prefersLargeTitles = true
        view.backgroundColor = BrandColors.background
        tableView.backgroundColor = BrandColors.background
        tableView.separatorColor = UIColor.white.withAlphaComponent(0.06)
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(dismissView))
    }

    @objc private func dismissView() { dismiss(animated: true) }

    // MARK: - Table View

    override func numberOfSections(in tableView: UITableView) -> Int { Section.allCases.count }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        switch Section(rawValue: section)! {
        case .general: return 2
        case .privacy: return 7
        case .searchEngine: return 1
        case .tabBehavior: return 2
        case .data: return 1
        case .about: return 1
        }
    }

    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        switch Section(rawValue: section)! {
        case .general: return "Chung"
        case .privacy: return "Quyền riêng tư & Bảo mật"
        case .searchEngine: return "Công cụ tìm kiếm"
        case .tabBehavior: return "Quản lý tab"
        case .data: return "Dữ liệu"
        case .about: return "Thông tin"
        }
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        cell.backgroundColor = BrandColors.toolbar
        cell.textLabel?.textColor = .white
        cell.detailTextLabel?.textColor = BrandColors.textSecondary
        cell.selectionStyle = .none

        switch Section(rawValue: indexPath.section)! {
        case .general:
            if indexPath.row == 0 {
                cell.textLabel?.text = "Phiên bản máy tính"
                cell.detailTextLabel?.text = "Yêu cầu website gửi bản desktop"
                let toggle = UISwitch()
                toggle.isOn = prefs.isDesktopMode
                toggle.onTintColor = BrandColors.accentPurpleUI
                toggle.tag = 100
                toggle.addTarget(self, action: #selector(toggleChanged(_:)), for: .valueChanged)
                cell.accessoryView = toggle
            } else {
                cell.textLabel?.text = "Lưu lịch sử duyệt web"
                cell.detailTextLabel?.text = "Tự động lưu các trang đã truy cập"
                let toggle = UISwitch()
                toggle.isOn = prefs.isSaveHistoryEnabled
                toggle.onTintColor = BrandColors.accentPurpleUI
                toggle.tag = 101
                toggle.addTarget(self, action: #selector(toggleChanged(_:)), for: .valueChanged)
                cell.accessoryView = toggle
            }

        case .privacy:
            let items: [(String, String, Bool, Int)] = [
                ("Chặn quảng cáo", "Chặn quảng cáo và trình theo dõi", prefs.isAdBlockEnabled, 200),
                ("Chặn trình theo dõi", "Ngăn các trang web theo dõi", prefs.isBlockTrackersEnabled, 201),
                ("Chặn cookie bên thứ ba", "Ngăn cookie theo dõi", prefs.isBlockThirdPartyCookies, 202),
                ("Do Not Track", "Gửi yêu cầu không theo dõi", prefs.isDoNotTrackEnabled, 203),
                ("Nâng cấp HTTPS", "Tự động chuyển kết nối an toàn", prefs.isHttpsUpgradeEnabled, 204),
                ("Chống lấy dấu vân tay", "Ngăn nhận diện trình duyệt", prefs.isBlockFingerprintingEnabled, 205),
                ("Chặn popup", "Ngăn cửa sổ bật lên", prefs.isBlockPopupsEnabled, 206),
            ]
            let item = items[indexPath.row]
            cell.textLabel?.text = item.0
            cell.detailTextLabel?.text = item.1
            let toggle = UISwitch()
            toggle.isOn = item.2
            toggle.onTintColor = BrandColors.accentPurpleUI
            toggle.tag = item.3
            toggle.addTarget(self, action: #selector(toggleChanged(_:)), for: .valueChanged)
            cell.accessoryView = toggle

        case .searchEngine:
            cell.textLabel?.text = "Công cụ tìm kiếm mặc định"
            cell.detailTextLabel?.text = prefs.searchEngine.capitalized
            cell.accessoryType = .disclosureIndicator

        case .tabBehavior:
            if indexPath.row == 0 {
                cell.textLabel?.text = "Khôi phục tab khi khởi động"
                let toggle = UISwitch()
                toggle.isOn = prefs.isRestoreTabsEnabled
                toggle.onTintColor = BrandColors.accentPurpleUI
                toggle.tag = 300
                toggle.addTarget(self, action: #selector(toggleChanged(_:)), for: .valueChanged)
                cell.accessoryView = toggle
            } else {
                cell.textLabel?.text = "Trình theo dõi đã chặn"
                cell.detailTextLabel?.text = "\(TabManager.shared.trackersBlocked)"
                cell.detailTextLabel?.textColor = BrandColors.secureGreen
            }

        case .data:
            cell.textLabel?.text = "Xóa tất cả dữ liệu duyệt web"
            cell.textLabel?.textColor = BrandColors.accentPinkUI
            cell.selectionStyle = .default

        case .about:
            cell.textLabel?.text = "Helix Browser"
            cell.detailTextLabel?.text = "Phiên bản 3.0 • iOS • WebKit Engine"
        }

        return cell
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)

        switch Section(rawValue: indexPath.section)! {
        case .searchEngine:
            showSearchEnginePicker()
        case .data:
            showClearDataAlert()
        default: break
        }
    }

    // MARK: - Actions

    @objc private func toggleChanged(_ sender: UISwitch) {
        switch sender.tag {
        case 100: prefs.isDesktopMode = sender.isOn
        case 101: prefs.isSaveHistoryEnabled = sender.isOn
        case 200: prefs.isAdBlockEnabled = sender.isOn
        case 201: prefs.isBlockTrackersEnabled = sender.isOn
        case 202: prefs.isBlockThirdPartyCookies = sender.isOn
        case 203: prefs.isDoNotTrackEnabled = sender.isOn
        case 204: prefs.isHttpsUpgradeEnabled = sender.isOn
        case 205: prefs.isBlockFingerprintingEnabled = sender.isOn
        case 206: prefs.isBlockPopupsEnabled = sender.isOn
        case 300: prefs.isRestoreTabsEnabled = sender.isOn
        default: break
        }
    }

    private func showSearchEnginePicker() {
        let alert = UIAlertController(title: "Công cụ tìm kiếm", message: nil, preferredStyle: .actionSheet)
        for engine in ["Google", "Bing", "DuckDuckGo", "Yahoo", "Brave"] {
            let action = UIAlertAction(title: engine, style: .default) { [weak self] _ in
                self?.prefs.searchEngine = engine.lowercased()
                self?.tableView.reloadData()
            }
            if prefs.searchEngine == engine.lowercased() {
                action.setValue(true, forKey: "checked")
            }
            alert.addAction(action)
        }
        alert.addAction(UIAlertAction(title: "Hủy", style: .cancel))
        present(alert, animated: true)
    }

    private func showClearDataAlert() {
        let alert = UIAlertController(title: "Xóa tất cả dữ liệu?", message: "Lịch sử, cookie, cache sẽ bị xóa. Không thể hoàn tác.", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Xóa", style: .destructive) { _ in
            PrivacyManager.shared.clearAllData {
                UserDefaults.standard.removeObject(forKey: "helix_history")
            }
        })
        alert.addAction(UIAlertAction(title: "Hủy", style: .cancel))
        present(alert, animated: true)
    }
}

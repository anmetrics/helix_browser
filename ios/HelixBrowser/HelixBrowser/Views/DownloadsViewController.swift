import UIKit

class DownloadsViewController: UIViewController {

    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private var downloads: [[String: String]] = []
    private let emptyLabel = UILabel()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Tải xuống"
        view.backgroundColor = BrandColors.background

        setupNavBar()
        setupTableView()
        setupEmptyState()
        loadDownloads()
    }

    private func setupNavBar() {
        navigationController?.navigationBar.prefersLargeTitles = false
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            title: "Đóng", style: .done, target: self, action: #selector(dismissVC)
        )
        navigationItem.rightBarButtonItem = UIBarButtonItem(
            title: "Xóa tất cả", style: .plain, target: self, action: #selector(clearAll)
        )
        navigationItem.rightBarButtonItem?.tintColor = BrandColors.accentPink
    }

    private func setupTableView() {
        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.backgroundColor = .clear
        tableView.delegate = self
        tableView.dataSource = self
        tableView.register(DownloadCell.self, forCellReuseIdentifier: "DownloadCell")
        tableView.separatorColor = UIColor.white.withAlphaComponent(0.06)
        view.addSubview(tableView)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    private func setupEmptyState() {
        emptyLabel.text = "Không có tải xuống"
        emptyLabel.textColor = BrandColors.textSecondary
        emptyLabel.font = .systemFont(ofSize: 16, weight: .medium)
        emptyLabel.textAlignment = .center
        emptyLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(emptyLabel)

        NSLayoutConstraint.activate([
            emptyLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            emptyLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])
    }

    private func loadDownloads() {
        downloads = DataManager.shared.getDownloads()
        emptyLabel.isHidden = !downloads.isEmpty
        tableView.isHidden = downloads.isEmpty
        tableView.reloadData()
    }

    @objc private func dismissVC() {
        dismiss(animated: true)
    }

    @objc private func clearAll() {
        let alert = UIAlertController(
            title: "Xóa lịch sử tải xuống?",
            message: "Hành động này không thể hoàn tác.",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "Hủy", style: .cancel))
        alert.addAction(UIAlertAction(title: "Xóa", style: .destructive) { [weak self] _ in
            DataManager.shared.clearDownloads()
            self?.loadDownloads()
        })
        present(alert, animated: true)
    }
}

extension DownloadsViewController: UITableViewDelegate, UITableViewDataSource {

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return downloads.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "DownloadCell", for: indexPath) as! DownloadCell
        cell.configure(with: downloads[indexPath.row])
        return cell
    }

    func tableView(_ tableView: UITableView, trailingSwipeActionsConfigurationForRowAt indexPath: IndexPath) -> UISwipeActionsConfiguration? {
        let delete = UIContextualAction(style: .destructive, title: "Xóa") { [weak self] _, _, completion in
            guard let self = self else { completion(false); return }
            self.downloads.remove(at: indexPath.row)
            // Re-save by clearing and re-adding (simple approach)
            DataManager.shared.clearDownloads()
            for d in self.downloads.reversed() {
                DataManager.shared.addDownload(
                    url: d["url"] ?? "",
                    filename: d["filename"] ?? "",
                    filesize: Int64(d["filesize"] ?? "0") ?? 0
                )
            }
            tableView.deleteRows(at: [indexPath], with: .automatic)
            self.emptyLabel.isHidden = !self.downloads.isEmpty
            completion(true)
        }
        return UISwipeActionsConfiguration(actions: [delete])
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        return 72
    }
}

// MARK: - Download Cell

class DownloadCell: UITableViewCell {

    private let filenameLabel = UILabel()
    private let statusLabel = UILabel()
    private let sizeLabel = UILabel()
    private let iconView = UIImageView()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        setupViews()
    }

    required init?(coder: NSCoder) { fatalError() }

    private func setupViews() {
        backgroundColor = .clear
        selectionStyle = .none

        iconView.translatesAutoresizingMaskIntoConstraints = false
        iconView.tintColor = BrandColors.accentPurple
        iconView.contentMode = .scaleAspectFit
        contentView.addSubview(iconView)

        filenameLabel.translatesAutoresizingMaskIntoConstraints = false
        filenameLabel.font = .systemFont(ofSize: 14, weight: .medium)
        filenameLabel.textColor = BrandColors.textPrimary
        filenameLabel.lineBreakMode = .byTruncatingMiddle
        contentView.addSubview(filenameLabel)

        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        statusLabel.font = .systemFont(ofSize: 12)
        statusLabel.textColor = BrandColors.textSecondary
        contentView.addSubview(statusLabel)

        sizeLabel.translatesAutoresizingMaskIntoConstraints = false
        sizeLabel.font = .systemFont(ofSize: 11)
        sizeLabel.textColor = BrandColors.textSecondary
        sizeLabel.textAlignment = .right
        contentView.addSubview(sizeLabel)

        NSLayoutConstraint.activate([
            iconView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            iconView.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            iconView.widthAnchor.constraint(equalToConstant: 28),
            iconView.heightAnchor.constraint(equalToConstant: 28),

            filenameLabel.leadingAnchor.constraint(equalTo: iconView.trailingAnchor, constant: 12),
            filenameLabel.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 14),
            filenameLabel.trailingAnchor.constraint(lessThanOrEqualTo: sizeLabel.leadingAnchor, constant: -8),

            statusLabel.leadingAnchor.constraint(equalTo: filenameLabel.leadingAnchor),
            statusLabel.topAnchor.constraint(equalTo: filenameLabel.bottomAnchor, constant: 4),
            statusLabel.trailingAnchor.constraint(lessThanOrEqualTo: sizeLabel.leadingAnchor, constant: -8),

            sizeLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            sizeLabel.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            sizeLabel.widthAnchor.constraint(equalToConstant: 70),
        ])
    }

    func configure(with item: [String: String]) {
        filenameLabel.text = item["filename"] ?? "Unknown"

        let status = item["status"] ?? "pending"
        switch status {
        case "completed":
            statusLabel.text = "Hoàn tất"
            statusLabel.textColor = BrandColors.secureGreen
            iconView.image = UIImage(systemName: "checkmark.circle.fill")
            iconView.tintColor = BrandColors.secureGreen
        case "downloading":
            statusLabel.text = "Đang tải..."
            statusLabel.textColor = BrandColors.accentPurple
            iconView.image = UIImage(systemName: "arrow.down.circle.fill")
            iconView.tintColor = BrandColors.accentPurple
        case "failed":
            statusLabel.text = "Thất bại"
            statusLabel.textColor = BrandColors.accentPink
            iconView.image = UIImage(systemName: "xmark.circle.fill")
            iconView.tintColor = BrandColors.accentPink
        default:
            statusLabel.text = "Đang chờ"
            statusLabel.textColor = BrandColors.textSecondary
            iconView.image = UIImage(systemName: "clock.fill")
            iconView.tintColor = BrandColors.textSecondary
        }

        if let sizeStr = item["filesize"], let bytes = Int64(sizeStr), bytes > 0 {
            sizeLabel.text = formatSize(bytes)
        } else {
            sizeLabel.text = ""
        }
    }

    private func formatSize(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
}

import UIKit

class TabSwitcherViewController: UIViewController, UICollectionViewDelegate, UICollectionViewDataSource {

    var onDismiss: (() -> Void)?
    private var collectionView: UICollectionView!
    private let tabManager = TabManager.shared

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = BrandColors.background
        setupUI()
    }

    private func setupUI() {
        // Header
        let titleLabel = UILabel()
        titleLabel.text = "Các tab (\(tabManager.tabs.count))"
        titleLabel.font = .systemFont(ofSize: 20, weight: .bold)
        titleLabel.textColor = .white
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(titleLabel)

        let doneButton = UIButton(type: .system)
        doneButton.setTitle("Xong", for: .normal)
        doneButton.tintColor = BrandColors.accentPurpleUI
        doneButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        doneButton.addTarget(self, action: #selector(dismissView), for: .touchUpInside)
        doneButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(doneButton)

        let newTabButton = UIButton(type: .system)
        newTabButton.setImage(UIImage(systemName: "plus.circle.fill"), for: .normal)
        newTabButton.tintColor = BrandColors.accentPurpleUI
        newTabButton.addTarget(self, action: #selector(newTab), for: .touchUpInside)
        newTabButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(newTabButton)

        // Collection View
        let layout = UICollectionViewFlowLayout()
        layout.itemSize = CGSize(width: (UIScreen.main.bounds.width - 48) / 2, height: 200)
        layout.minimumInteritemSpacing = 12
        layout.minimumLineSpacing = 12
        layout.sectionInset = UIEdgeInsets(top: 12, left: 16, bottom: 16, right: 16)

        collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.backgroundColor = .clear
        collectionView.delegate = self
        collectionView.dataSource = self
        collectionView.register(TabCell.self, forCellWithReuseIdentifier: "TabCell")
        collectionView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(collectionView)

        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            titleLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            doneButton.centerYAnchor.constraint(equalTo: titleLabel.centerYAnchor),
            doneButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            newTabButton.centerYAnchor.constraint(equalTo: titleLabel.centerYAnchor),
            newTabButton.trailingAnchor.constraint(equalTo: doneButton.leadingAnchor, constant: -12),
            collectionView.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 16),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    @objc private func dismissView() {
        dismiss(animated: true) { self.onDismiss?() }
    }

    @objc private func newTab() {
        tabManager.createTab()
        collectionView.reloadData()
        dismissView()
    }

    // MARK: - CollectionView

    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return tabManager.tabs.count
    }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "TabCell", for: indexPath) as! TabCell
        let tab = tabManager.tabs[indexPath.item]
        let isActive = tab.id == tabManager.activeTabId
        cell.configure(tab: tab, isActive: isActive)
        cell.onClose = { [weak self] in
            self?.tabManager.closeTab(id: tab.id)
            self?.collectionView.reloadData()
        }
        return cell
    }

    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        let tab = tabManager.tabs[indexPath.item]
        tabManager.switchToTab(id: tab.id)
        dismissView()
    }
}

// MARK: - Tab Cell

class TabCell: UICollectionViewCell {
    private let titleLabel = UILabel()
    private let urlLabel = UILabel()
    private let closeButton = UIButton(type: .system)
    private let thumbnailView = UIView()
    private let faviconView = UIImageView()
    private let incognitoIcon = UIImageView()
    private let pinnedIcon = UIImageView()
    var onClose: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupCell()
    }

    required init?(coder: NSCoder) { fatalError() }

    private func setupCell() {
        contentView.backgroundColor = UIColor.white.withAlphaComponent(0.06)
        contentView.layer.cornerRadius = 12
        contentView.clipsToBounds = true

        thumbnailView.backgroundColor = BrandColors.background
        thumbnailView.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(thumbnailView)

        let infoView = UIView()
        infoView.backgroundColor = BrandColors.toolbar
        infoView.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(infoView)

        faviconView.contentMode = .scaleAspectFit
        faviconView.layer.cornerRadius = 4
        faviconView.clipsToBounds = true
        faviconView.translatesAutoresizingMaskIntoConstraints = false
        infoView.addSubview(faviconView)

        titleLabel.font = .systemFont(ofSize: 12, weight: .semibold)
        titleLabel.textColor = .white
        titleLabel.lineBreakMode = .byTruncatingTail
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        infoView.addSubview(titleLabel)

        urlLabel.font = .systemFont(ofSize: 10)
        urlLabel.textColor = BrandColors.textSecondary
        urlLabel.lineBreakMode = .byTruncatingTail
        urlLabel.translatesAutoresizingMaskIntoConstraints = false
        infoView.addSubview(urlLabel)

        closeButton.setImage(UIImage(systemName: "xmark.circle.fill"), for: .normal)
        closeButton.tintColor = BrandColors.textSecondary
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        closeButton.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        contentView.addSubview(closeButton)

        incognitoIcon.image = UIImage(systemName: "eye.slash.fill")
        incognitoIcon.tintColor = BrandColors.accentPinkUI
        incognitoIcon.translatesAutoresizingMaskIntoConstraints = false
        incognitoIcon.isHidden = true
        infoView.addSubview(incognitoIcon)

        pinnedIcon.image = UIImage(systemName: "pin.fill")
        pinnedIcon.tintColor = BrandColors.accentPurpleUI
        pinnedIcon.translatesAutoresizingMaskIntoConstraints = false
        pinnedIcon.isHidden = true
        infoView.addSubview(pinnedIcon)

        NSLayoutConstraint.activate([
            thumbnailView.topAnchor.constraint(equalTo: contentView.topAnchor),
            thumbnailView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            thumbnailView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            thumbnailView.heightAnchor.constraint(equalTo: contentView.heightAnchor, multiplier: 0.65),

            infoView.topAnchor.constraint(equalTo: thumbnailView.bottomAnchor),
            infoView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            infoView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            infoView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor),

            faviconView.leadingAnchor.constraint(equalTo: infoView.leadingAnchor, constant: 8),
            faviconView.centerYAnchor.constraint(equalTo: infoView.centerYAnchor),
            faviconView.widthAnchor.constraint(equalToConstant: 16),
            faviconView.heightAnchor.constraint(equalToConstant: 16),

            titleLabel.leadingAnchor.constraint(equalTo: faviconView.trailingAnchor, constant: 6),
            titleLabel.topAnchor.constraint(equalTo: infoView.topAnchor, constant: 8),
            titleLabel.trailingAnchor.constraint(equalTo: infoView.trailingAnchor, constant: -28),

            urlLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            urlLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 2),
            urlLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            closeButton.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 4),
            closeButton.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -4),
            closeButton.widthAnchor.constraint(equalToConstant: 24),
            closeButton.heightAnchor.constraint(equalToConstant: 24),

            incognitoIcon.widthAnchor.constraint(equalToConstant: 14),
            incognitoIcon.heightAnchor.constraint(equalToConstant: 14),
            incognitoIcon.trailingAnchor.constraint(equalTo: infoView.trailingAnchor, constant: -8),
            incognitoIcon.centerYAnchor.constraint(equalTo: infoView.centerYAnchor),

            pinnedIcon.widthAnchor.constraint(equalToConstant: 12),
            pinnedIcon.heightAnchor.constraint(equalToConstant: 12),
            pinnedIcon.trailingAnchor.constraint(equalTo: incognitoIcon.leadingAnchor, constant: -4),
            pinnedIcon.centerYAnchor.constraint(equalTo: infoView.centerYAnchor),
        ])
    }

    func configure(tab: BrowserTab, isActive: Bool) {
        titleLabel.text = tab.title
        urlLabel.text = UrlUtils.getDisplayUrl(tab.url)
        incognitoIcon.isHidden = !tab.isIncognito
        pinnedIcon.isHidden = !tab.isPinned

        if isActive {
            contentView.layer.borderWidth = 2
            contentView.layer.borderColor = BrandColors.accentPurpleCG
        } else {
            contentView.layer.borderWidth = 0
        }

        // Load favicon
        if let faviconUrlStr = tab.faviconUrl, let faviconUrl = URL(string: faviconUrlStr) {
            URLSession.shared.dataTask(with: faviconUrl) { [weak self] data, _, _ in
                if let data = data, let image = UIImage(data: data) {
                    DispatchQueue.main.async { self?.faviconView.image = image }
                }
            }.resume()
        } else {
            faviconView.image = UIImage(systemName: "globe")
            faviconView.tintColor = BrandColors.accentPurpleUI
        }
    }

    @objc private func closeTapped() { onClose?() }
}

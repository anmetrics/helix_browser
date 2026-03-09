import UIKit

class StartPageView: UIView {
    var onUrlSelected: ((String) -> Void)?

    private let favorites: [(String, String, String)] = [
        ("Google", "magnifyingglass", "https://www.google.com"),
        ("Facebook", "person.2.fill", "https://www.facebook.com"),
        ("YouTube", "play.rectangle.fill", "https://www.youtube.com"),
        ("GitHub", "chevron.left.forwardslash.chevron.right", "https://github.com"),
        ("Twitter", "bubble.left.fill", "https://twitter.com"),
        ("Reddit", "r.circle.fill", "https://www.reddit.com"),
        ("Wikipedia", "book.fill", "https://www.wikipedia.org"),
        ("Netflix", "play.tv.fill", "https://www.netflix.com"),
    ]

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = BrandColors.background
        setupUI()
    }

    required init?(coder: NSCoder) { fatalError() }

    private func setupUI() {
        let scroll = UIScrollView()
        scroll.translatesAutoresizingMaskIntoConstraints = false
        addSubview(scroll)

        let content = UIView()
        content.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(content)

        // Logo
        let logo = UIImageView(image: UIImage(systemName: "globe.americas.fill"))
        logo.tintColor = BrandColors.accentPurpleUI
        logo.contentMode = .scaleAspectFit
        logo.translatesAutoresizingMaskIntoConstraints = false
        content.addSubview(logo)

        let titleLabel = UILabel()
        titleLabel.text = "Helix Browser"
        titleLabel.font = .systemFont(ofSize: 28, weight: .bold)
        titleLabel.textColor = .white
        titleLabel.textAlignment = .center
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        content.addSubview(titleLabel)

        let subtitleLabel = UILabel()
        subtitleLabel.text = "Nhanh. An toàn. Riêng tư."
        subtitleLabel.font = .systemFont(ofSize: 14, weight: .medium)
        subtitleLabel.textColor = BrandColors.textSecondary
        subtitleLabel.textAlignment = .center
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false
        content.addSubview(subtitleLabel)

        // Privacy stats
        let trackerCount = TabManager.shared.trackersBlocked
        let statsView = UIView()
        statsView.backgroundColor = BrandColors.secureGreen.withAlphaComponent(0.1)
        statsView.layer.cornerRadius = 12
        statsView.layer.borderWidth = 1
        statsView.layer.borderColor = BrandColors.secureGreen.withAlphaComponent(0.3).cgColor
        statsView.translatesAutoresizingMaskIntoConstraints = false
        statsView.isHidden = trackerCount == 0
        content.addSubview(statsView)

        let shieldIcon = UIImageView(image: UIImage(systemName: "shield.checkered"))
        shieldIcon.tintColor = BrandColors.secureGreen
        shieldIcon.translatesAutoresizingMaskIntoConstraints = false
        statsView.addSubview(shieldIcon)

        let statsLabel = UILabel()
        statsLabel.text = "\(trackerCount) trình theo dõi đã bị chặn"
        statsLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        statsLabel.textColor = BrandColors.secureGreen
        statsLabel.translatesAutoresizingMaskIntoConstraints = false
        statsView.addSubview(statsLabel)

        // Favorites section
        let favLabel = UILabel()
        favLabel.text = "Trang yêu thích"
        favLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        favLabel.textColor = BrandColors.textSecondary
        favLabel.translatesAutoresizingMaskIntoConstraints = false
        content.addSubview(favLabel)

        let favGrid = UIStackView()
        favGrid.axis = .vertical
        favGrid.spacing = 12
        favGrid.translatesAutoresizingMaskIntoConstraints = false
        content.addSubview(favGrid)

        // Create 2-column grid
        for row in stride(from: 0, to: favorites.count, by: 4) {
            let rowStack = UIStackView()
            rowStack.distribution = .fillEqually
            rowStack.spacing = 12
            for col in 0..<4 where row + col < favorites.count {
                let (title, icon, url) = favorites[row + col]
                let btn = createFavoriteButton(title: title, icon: icon, url: url)
                rowStack.addArrangedSubview(btn)
            }
            favGrid.addArrangedSubview(rowStack)
        }

        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: topAnchor),
            scroll.leadingAnchor.constraint(equalTo: leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: trailingAnchor),
            scroll.bottomAnchor.constraint(equalTo: bottomAnchor),

            content.topAnchor.constraint(equalTo: scroll.topAnchor),
            content.leadingAnchor.constraint(equalTo: scroll.leadingAnchor),
            content.trailingAnchor.constraint(equalTo: scroll.trailingAnchor),
            content.bottomAnchor.constraint(equalTo: scroll.bottomAnchor),
            content.widthAnchor.constraint(equalTo: scroll.widthAnchor),

            logo.topAnchor.constraint(equalTo: content.topAnchor, constant: 60),
            logo.centerXAnchor.constraint(equalTo: content.centerXAnchor),
            logo.widthAnchor.constraint(equalToConstant: 72),
            logo.heightAnchor.constraint(equalToConstant: 72),

            titleLabel.topAnchor.constraint(equalTo: logo.bottomAnchor, constant: 16),
            titleLabel.centerXAnchor.constraint(equalTo: content.centerXAnchor),

            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 6),
            subtitleLabel.centerXAnchor.constraint(equalTo: content.centerXAnchor),

            statsView.topAnchor.constraint(equalTo: subtitleLabel.bottomAnchor, constant: 24),
            statsView.leadingAnchor.constraint(equalTo: content.leadingAnchor, constant: 24),
            statsView.trailingAnchor.constraint(equalTo: content.trailingAnchor, constant: -24),
            statsView.heightAnchor.constraint(equalToConstant: 52),

            shieldIcon.leadingAnchor.constraint(equalTo: statsView.leadingAnchor, constant: 16),
            shieldIcon.centerYAnchor.constraint(equalTo: statsView.centerYAnchor),
            shieldIcon.widthAnchor.constraint(equalToConstant: 24),
            shieldIcon.heightAnchor.constraint(equalToConstant: 24),

            statsLabel.leadingAnchor.constraint(equalTo: shieldIcon.trailingAnchor, constant: 10),
            statsLabel.centerYAnchor.constraint(equalTo: statsView.centerYAnchor),

            favLabel.topAnchor.constraint(equalTo: statsView.bottomAnchor, constant: 28),
            favLabel.leadingAnchor.constraint(equalTo: content.leadingAnchor, constant: 24),

            favGrid.topAnchor.constraint(equalTo: favLabel.bottomAnchor, constant: 14),
            favGrid.leadingAnchor.constraint(equalTo: content.leadingAnchor, constant: 24),
            favGrid.trailingAnchor.constraint(equalTo: content.trailingAnchor, constant: -24),
            favGrid.bottomAnchor.constraint(equalTo: content.bottomAnchor, constant: -40),
        ])
    }

    private func createFavoriteButton(title: String, icon: String, url: String) -> UIView {
        let container = UIView()
        container.backgroundColor = UIColor.white.withAlphaComponent(0.04)
        container.layer.cornerRadius = 12
        container.layer.borderWidth = 1
        container.layer.borderColor = UIColor.white.withAlphaComponent(0.06).cgColor

        let iconView = UIImageView(image: UIImage(systemName: icon))
        iconView.tintColor = .white
        iconView.contentMode = .scaleAspectFit
        iconView.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(iconView)

        let label = UILabel()
        label.text = title
        label.font = .systemFont(ofSize: 11, weight: .medium)
        label.textColor = BrandColors.textSecondary
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(label)

        let tap = UITapGestureRecognizer(target: self, action: #selector(favoriteTapped(_:)))
        container.addGestureRecognizer(tap)
        container.accessibilityValue = url

        NSLayoutConstraint.activate([
            container.heightAnchor.constraint(equalToConstant: 72),
            iconView.centerXAnchor.constraint(equalTo: container.centerXAnchor),
            iconView.topAnchor.constraint(equalTo: container.topAnchor, constant: 14),
            iconView.widthAnchor.constraint(equalToConstant: 24),
            iconView.heightAnchor.constraint(equalToConstant: 24),
            label.topAnchor.constraint(equalTo: iconView.bottomAnchor, constant: 6),
            label.centerXAnchor.constraint(equalTo: container.centerXAnchor),
        ])
        return container
    }

    @objc private func favoriteTapped(_ gesture: UITapGestureRecognizer) {
        if let url = gesture.view?.accessibilityValue {
            onUrlSelected?(url)
        }
    }
}

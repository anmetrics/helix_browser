import UIKit

class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = (scene as? UIWindowScene) else { return }
        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = BrowserViewController()
        window.makeKeyAndVisible()
        self.window = window

        // Dark status bar
        window.overrideUserInterfaceStyle = .dark
    }

    func sceneDidEnterBackground(_ scene: UIScene) {
        TabManager.shared.saveTabs()
    }
}

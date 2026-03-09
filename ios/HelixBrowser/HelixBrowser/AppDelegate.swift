import UIKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        // Set global appearance
        UINavigationBar.appearance().barTintColor = BrandColors.toolbar
        UINavigationBar.appearance().tintColor = BrandColors.accentPurpleUI
        UINavigationBar.appearance().titleTextAttributes = [.foregroundColor: UIColor.white]
        UITabBar.appearance().barTintColor = BrandColors.toolbar
        UITabBar.appearance().tintColor = BrandColors.accentPurpleUI
        return true
    }

    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        return UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
    }
}

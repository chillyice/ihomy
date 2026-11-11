import UIKit
import Capacitor

class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }

        window = UIWindow(windowScene: windowScene)
        // 用自建控制器注册本地插件;CAPBridgeViewController 直接当 root 时插件无处安放。
        window?.rootViewController = MainViewController()
        window?.makeKeyAndVisible()

        // 冷启动时用户点了快捷方式:先存住,等 WebView 的 appUrlOpen 监听就绪。
        if let shortcut = connectionOptions.shortcutItem {
            IhomyLaunch.capture(shortcut)
        }

        SceneDelegateProxy.shared.scene(scene, willConnectTo: session, options: connectionOptions)
    }

    /// App 已在运行/在后台时点快捷方式走这里。
    func windowScene(_ windowScene: UIWindowScene,
                     performActionFor shortcutItem: UIApplicationShortcutItem,
                     completionHandler: @escaping (Bool) -> Void) {
        IhomyLaunch.capture(shortcutItem)
        completionHandler(true)
    }

    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        SceneDelegateProxy.shared.scene(scene, openURLContexts: URLContexts)
    }

    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        SceneDelegateProxy.shared.scene(scene, continue: userActivity)
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        // 分享扩展/通知点进来时可能没有走 openURL:回到前台补投一次。
        IhomyLaunch.flushPending(after: 0.2)
    }
}

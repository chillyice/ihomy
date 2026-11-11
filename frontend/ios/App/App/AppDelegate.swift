import UIKit
import Capacitor

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        // 崩溃日志落盘要在任何业务代码之前装好:扩展不编译这段,主 App 才有诊断页。
        IhomyCrashStore.install()

        // 必须在启动结束前注册 BGTask,且标识与 Info.plist 的
        // BGTaskSchedulerPermittedIdentifiers 完全一致,否则注册即崩。
        IhomyBackground.register()

        // 冷启动的快捷方式先存住,等 WebView 装好监听再投递。
        // URL 型深链由 `SceneDelegateProxy` 在 `capacitorViewDidAppear` 后统一转发,这里不重复接。
        if let shortcut = launchOptions?[.shortcutItem] as? UIApplicationShortcutItem {
            IhomyLaunch.capture(shortcut)
        }
        return true
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        // 每次退后台都重排一次:系统执行过一轮后不会自动续排。
        IhomyBackground.schedule()
    }

    func applicationWillTerminate(_ application: UIApplication) {
        IhomyBackground.schedule()
    }

    /// iOS 12 及之前的快捷方式回调;Scene 工程里通常走 SceneDelegate,两处都留。
    func application(_ application: UIApplication,
                     performActionFor shortcutItem: UIApplicationShortcutItem,
                     completionHandler: @escaping (Bool) -> Void) {
        IhomyLaunch.capture(shortcutItem)
        completionHandler(true)
    }

    func application(_ application: UIApplication,
                     configurationForConnecting connectingSceneSession: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: "Default Configuration",
                                          sessionRole: connectingSceneSession.role)
        config.delegateClass = SceneDelegate.self
        return config
    }
}

// MARK: - 快捷方式 / 冷启动深链投递

/// 桌面上长按图标弹出的快捷方式走 `ihomy://` 深链,和卡片、通知、分享扩展同一条链路:
/// 这里只负责把 URL 变成 `appUrlOpen` 事件,路由与一次性参数由前端 `deeplink.js` 统一消费。
///
/// 投递方式必须走 Capacitor 自己的通知 `CapacitorOpenURLNotification`:
/// 前端 `App.addListener('appUrlOpen')` 由 App 插件的原生监听器转发,
/// 自己 `triggerJSEvent` 打 window 事件前端是收不到的。
enum IhomyLaunch {
    /// 等待投递的 URL(同一时刻只留最后一条,和用户点得最快的那次一致)。
    private static var pending: String?
    private static var attempts = 0
    /// WebView 桥就绪前先存着;由 `MainViewController.capacitorDidLoad()` 赋值。
    /// 桥存在 === App 插件已经 `load()` 并挂上 openURL 观察者。
    static weak var bridge: CAPBridgeProtocol?

    static func capture(_ item: UIApplicationShortcutItem) {
        guard let url = item.userInfo?["url"] as? String, !url.isEmpty else { return }
        capture(url)
    }

    static func capture(_ url: URL) {
        capture(url.absoluteString)
    }

    static func capture(_ raw: String) {
        guard !raw.isEmpty else { return }
        pending = raw
        attempts = 0
        flushPending(after: 0.6)
    }

    /// 桥或页面还没好时按 0.5 秒重试(最多 10 秒),避免冷启动时事件被吞。
    /// 投递用 `retainUntilConsumed`:JS 监听晚一步注册也会在一次 addListener 时补发。
    static func flushPending(after delay: TimeInterval) {
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
            guard let raw = pending else { return }
            guard bridge != nil else {
                attempts += 1
                if attempts <= 20 {
                    flushPending(after: 0.5)
                }
                return
            }
            pending = nil
            attempts = 0
            guard let url = URL(string: raw) else { return }
            NotificationCenter.default.post(name: .capacitorOpenURL,
                                            object: ["url": url, "options": [String: Any]()])
        }
    }
}

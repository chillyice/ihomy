//
//  MainViewController.swift
//  ihomy
//
//  Capacitor 的 WebView 控制器。本地插件全部在 `capacitorDidLoad()` 里按实例注册:
//  壳里没有 CocoaPods 式的插件清单,JS 侧 `registerPlugin('Ihomy...')` 能否命中,
//  完全取决于这里注册的 jsName。
//
//  注意:只在本文件(即 App target)里注册插件;WidgetKit / Share 扩展是独立进程,
//  它们只编 `IhomyShared.swift`,不加载 Capacitor。
//

import UIKit
import Capacitor

class MainViewController: CAPBridgeViewController {

    override func capacitorDidLoad() {
        super.capacitorDidLoad()

        guard let bridge = bridge else {
            NSLog("[ihomy] bridge unavailable, plugins not registered")
            return
        }

        // 与前端 `src/native/*.js` 的 registerPlugin 名一一对应,改名会直接让调用落空。
        bridge.registerPluginInstance(IhomyBiometricPlugin())
        bridge.registerPluginInstance(IhomyHapticsPlugin())
        bridge.registerPluginInstance(IhomyScreenPlugin())
        bridge.registerPluginInstance(IhomyCallPlugin())
        bridge.registerPluginInstance(IhomySpeechPlugin())
        bridge.registerPluginInstance(IhomyCameraPlugin())

        bridge.registerPluginInstance(IhomyCalendarPlugin())
        bridge.registerPluginInstance(IhomyPhotoBackupPlugin())
        bridge.registerPluginInstance(IhomySharePlugin())
        bridge.registerPluginInstance(IhomyDiagnosticsPlugin())

        bridge.registerPluginInstance(IhomyWidgetsPlugin())
        bridge.registerPluginInstance(IhomyUpdaterPlugin())
        bridge.registerPluginInstance(IhomyPipPlugin())

        // 后台刷新链路在第一帧之后才开始排,避免和启动请求抢网络。
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            IhomyBackground.schedule()
        }

        // 快捷方式冷启动:AppDelegate/SceneDelegate 已把待处理深链存下来。
        // 此时桥与 App 插件都已就绪(`registerPlugins()` 在 init 里跑过),把桥交出去后即可按
        // Capacitor 原生约定投递,插件会把事件保留到 JS 装上 appUrlOpen 监听为止。
        IhomyLaunch.bridge = bridge
        IhomyLaunch.flushPending(after: 1.0)
    }
}

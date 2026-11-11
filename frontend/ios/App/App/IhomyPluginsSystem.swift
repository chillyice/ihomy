//
//  IhomyPluginsSystem.swift
//  ihomy
//
//  桌面卡片桥、应用内更新与画中画。三者都是「WebView 发起、原生执行」的旁路能力:
//  卡片只读共享缓存,更新在 iOS 只能去 App Store,画中画在浏览器里只能由 <video> 自己呈现。
//  接口形状与 Android 同名插件保持一致,做不到的部分按能力降级并如实返回。
//

import Foundation
import UIKit
import AVKit
import AVFoundation

// MARK: - 桌面卡片桥

/// 插件名 `IhomyWidgets`。令牌的唯一来源永远是 WebView:
/// 登录/切家庭时下发,登出时清掉;卡片扩展只读 App Group 缓存。
@objc(IhomyWidgetsPlugin)
class IhomyWidgetsPlugin: CAPPlugin, CAPBridgedPlugin {
    let identifier = "IhomyWidgetsPlugin"
    let jsName = "IhomyWidgets"
    let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "setWidgetToken", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clearWidgetToken", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setReminderSyncAt", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setUnreadNotifyEnabled", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getUnreadNotifyEnabled", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startLive", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopLive", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isLiveRunning", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getDailyWeather", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setDailyWeatherEnabled", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setDailyWeatherMinute", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "previewDailyWeather", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "refreshWidgets", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openNotificationSettings", returnType: CAPPluginReturnPromise)
    ]

    /// 「后台消息提醒」在 iOS 没有常驻服务可开,这里只表示用户已打开开关、
    /// 后台刷新链路在排——如实告诉调用方,别假装有 30 秒轮询。
    private static var liveEnabled = false

    @objc func setWidgetToken(_ call: CAPPluginCall) {
        guard let token = call.getString("token"), !token.isEmpty else {
            call.reject("token required")
            return
        }
        IhomyPrefs.token = token
        IhomyBackground.schedule()
        Task {
            await IhomyWidgetStore.syncAll()
            IhomyWidgetStore.reloadWidgets()
            call.resolve()
        }
    }

    @objc func clearWidgetToken(_ call: CAPPluginCall) {
        IhomyPrefs.clearToken()
        // 清掉缓存,桌面卡片立刻回到「未登录」空态;通知也一并撤掉。
        IhomyPrefs.putCache("today", "")
        IhomyPrefs.putCache("reminder", "")
        IhomyPrefs.putCache("countdown", "")
        IhomyPrefs.putCache("book", "")
        IhomyPrefs.todayInfo = ""
        IhomyNotify.cancelUnread()
        IhomyNotify.remove([IhomyNotify.staleIdentifier])
        IhomyWidgetStore.reloadWidgets()
        call.resolve()
    }

    /// 前端提醒对账成功后上报时间(ms);后台任务用它判断「多久没校准过」。
    @objc func setReminderSyncAt(_ call: CAPPluginCall) {
        let ts = call.getDouble("ts") ?? 0
        guard ts > 0 else {
            call.reject("ts required")
            return
        }
        IhomyPrefs.reminderSyncAt = ts
        call.resolve()
    }

    @objc func setUnreadNotifyEnabled(_ call: CAPPluginCall) {
        IhomyPrefs.unreadNotifyEnabled = call.getBool("enabled") ?? true
        call.resolve()
    }

    @objc func getUnreadNotifyEnabled(_ call: CAPPluginCall) {
        call.resolve(["enabled": IhomyPrefs.unreadNotifyEnabled])
    }

    /// Android 是前台服务;iOS 只能是「排一次后台刷新 + 请求通知权限」。
    @objc func startLive(_ call: CAPPluginCall) {
        IhomyWidgetsPlugin.liveEnabled = true
        IhomyBackground.schedule()
        Task {
            if await IhomyNotify.ensureAuthorization() {
                await IhomyBackground.runBackgroundSync()
            }
            call.resolve()
        }
    }

    @objc func stopLive(_ call: CAPPluginCall) {
        IhomyWidgetsPlugin.liveEnabled = false
        IhomyNotify.cancelUnread()
        call.resolve()
    }

    @objc func isLiveRunning(_ call: CAPPluginCall) {
        let running = IhomyWidgetsPlugin.liveEnabled
            && !IhomyPrefs.token.isEmpty
            && IhomyPrefs.unreadNotifyEnabled
        call.resolve(["running": running])
    }

    @objc func getDailyWeather(_ call: CAPPluginCall) {
        call.resolve([
            "enabled": IhomyPrefs.dailyWeatherEnabled,
            "minute": IhomyPrefs.dailyWeatherMinute
        ])
    }

    @objc func setDailyWeatherEnabled(_ call: CAPPluginCall) {
        let enabled = call.getBool("enabled") ?? true
        IhomyPrefs.dailyWeatherEnabled = enabled
        if enabled {
            rescheduleDailyWeather()
        } else {
            IhomyNotify.cancelDailyWeather()
            IhomyNotify.remove([IhomyNotify.weatherPrefix + "preview"])
        }
        call.resolve()
    }

    @objc func setDailyWeatherMinute(_ call: CAPPluginCall) {
        guard let minute = call.getInt("minute"), minute >= 0, minute < 24 * 60 else {
            call.reject("minute out of range")
            return
        }
        IhomyPrefs.dailyWeatherMinute = minute
        rescheduleDailyWeather()
        call.resolve()
    }

    /// 设置页「发一条预览」:立刻抓天气并弹一条;权限/网络不满足直接告诉前端。
    @objc func previewDailyWeather(_ call: CAPPluginCall) {
        Task {
            let status = await IhomyNotify.authorizationStatus()
            guard status != .denied else {
                call.reject("permission")
                return
            }
            guard await IhomyNotify.ensureAuthorization() else {
                call.reject("permission")
                return
            }
            guard IhomyNetwork.shared.isConnected else {
                call.reject("network")
                return
            }
            guard let snapshot = try? await IhomyWeather.fetch(token: IhomyPrefs.token), let snapshot else {
                call.reject("network")
                return
            }
            IhomyNotify.previewWeather(title: IhomyWeather.title(snapshot), body: IhomyWeather.body(snapshot))
            call.resolve()
        }
    }

    @objc func refreshWidgets(_ call: CAPPluginCall) {
        Task {
            await IhomyWidgetStore.syncAll()
            IhomyWidgetStore.reloadWidgets()
            call.resolve()
        }
    }

    /// 直达本 App 的通知页;没有单独的「通知通道」层级,系统页即可。
    @objc func openNotificationSettings(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let target: String
            if #available(iOS 16.0, *) {
                target = UIApplication.openNotificationSettingsURLString
            } else {
                target = UIApplication.openSettingsURLString
            }
            guard let url = URL(string: target) else {
                call.reject("open_failed")
                return
            }
            UIApplication.shared.open(url, options: [:]) { ok in
                if ok {
                    call.resolve()
                } else {
                    call.reject("open_failed")
                }
            }
        }
    }

    /// 改时刻/开开关后按新时刻重排:重复触发器需要带一份内容,先抓一次天气再排。
    /// 抓不到就只保留旧的重复通知(iOS 的通知内容会在下次后台刷新时更新)。
    private func rescheduleDailyWeather() {
        guard IhomyPrefs.dailyWeatherEnabled else { return }
        Task {
            guard await IhomyNotify.ensureAuthorization() else { return }
            guard let snapshot = try? await IhomyWeather.fetch(token: IhomyPrefs.token), let snapshot else { return }
            IhomyNotify.scheduleDailyWeather(title: IhomyWeather.title(snapshot), body: IhomyWeather.body(snapshot))
        }
    }
}

// MARK: - 应用内更新

/// iOS 不允许侧载,自动更新只能走 App Store。
/// 保持 Android 同名插件的字段形状(`status`/`getAutoUpdate`/`downloadStatus`),
/// 但 `canInstall` 恒为 false、下载与安装一律返回 `not-supported`,前端据此不显示「立即更新」。
@objc(IhomyUpdaterPlugin)
class IhomyUpdaterPlugin: CAPPlugin, CAPBridgedPlugin {
    let identifier = "IhomyUpdaterPlugin"
    let jsName = "IhomyUpdater"
    let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "status", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openInstallSettings", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "download", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "downloadStatus", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "install", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "cleanup", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getAutoUpdate", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setAutoUpdate", returnType: CAPPluginReturnPromise)
    ]

    @objc func status(_ call: CAPPluginCall) {
        call.resolve(["canInstall": false])
    }

    @objc func openInstallSettings(_ call: CAPPluginCall) {
        call.reject("not-supported")
    }

    @objc func download(_ call: CAPPluginCall) {
        call.reject("not-supported")
    }

    @objc func downloadStatus(_ call: CAPPluginCall) {
        call.resolve([
            "status": "NONE",
            "file": "",
            "bytes": 0,
            "total": 0,
            "size": 0
        ])
    }

    @objc func install(_ call: CAPPluginCall) {
        call.reject("not-supported")
    }

    @objc func cleanup(_ call: CAPPluginCall) {
        call.resolve(["removed": 0])
    }

    @objc func getAutoUpdate(_ call: CAPPluginCall) {
        // Apple 规则下没有「闲时静默安装」,开关恒关,设置页按平台隐藏。
        call.resolve(["enabled": false, "deviceOwner": false])
    }

    @objc func setAutoUpdate(_ call: CAPPluginCall) {
        call.resolve()
    }
}

// MARK: - 画中画

/// 插件名 `IhomyPip`。Android 把整个 WebView 缩进小窗;iOS 只能让 `<video>`
/// 自身进入系统画中画(`webkitSetPresentationMode`),所以这里改走 JS 注入。
/// 前端接口保持不变:`setActive` 标记「正在播放」,离开 App 时自动进小窗。
@objc(IhomyPipPlugin)
class IhomyPipPlugin: CAPPlugin, CAPBridgedPlugin {
    let identifier = "IhomyPipPlugin"
    let jsName = "IhomyPip"
    let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "isSupported", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setActive", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "enter", returnType: CAPPluginReturnPromise)
    ]

    /// 是否有视频正在播放:离开 App 时据此决定要不要进小窗。
    private static var active = false

    private var resignObserver: NSObjectProtocol?

    override func load() {
        super.load()
        resignObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.willResignActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            guard IhomyPipPlugin.active else { return }
            self?.enterPictureInPicture(silent: true)
        }
    }

    deinit {
        if let resignObserver {
            NotificationCenter.default.removeObserver(resignObserver)
        }
    }

    @objc func isSupported(_ call: CAPPluginCall) {
        var supported = false
        if #available(iOS 14.0, *) {
            supported = AVPictureInPictureController.isPictureInPictureSupported()
        }
        call.resolve(["supported": supported])
    }

    @objc func setActive(_ call: CAPPluginCall) {
        IhomyPipPlugin.active = call.getBool("active") ?? false
        call.resolve()
    }

    @objc func enter(_ call: CAPPluginCall) {
        enterPictureInPicture(silent: false) { ok in
            if ok {
                call.resolve()
            } else {
                call.reject("pip_unavailable")
            }
        }
    }

    /// 在页面里找正在播放的 `<video>` 并切到系统画中画。
    /// `silent` 用于切后台的自动进入:失败静默,不打扰用户。
    private func enterPictureInPicture(silent: Bool, completion: ((Bool) -> Void)? = nil) {
        guard #available(iOS 14.0, *), AVPictureInPictureController.isPictureInPictureSupported(),
              let webView = bridge?.webView else {
            if !silent { completion?(false) }
            return
        }
        // 返回 'ok' / 'no-video' / 'paused' / 'unsupported',便于调用方给提示。
        let script = """
        (function () {
          var v = document.querySelector('video');
          if (!v) return 'no-video';
          if (!v.webkitSetPresentationMode) return 'unsupported';
          if (v.webkitPresentationMode === 'picture-in-picture') return 'ok';
          if (v.paused) return 'paused';
          v.webkitSetPresentationMode('picture-in-picture');
          return 'ok';
        })()
        """
        DispatchQueue.main.async {
            webView.evaluateJavaScript(script) { result, error in
                let ok = (result as? String) == "ok" && error == nil
                if !silent {
                    completion?(ok)
                }
            }
        }
    }
}

//
//  IhomyPluginsLite.swift
//  ihomy
//
//  轻量系统能力插件:生物识别、触觉、屏幕保护、通话权限、语音识别、相机直传。
//  与 Android `top.ihomy.app.biometric|haptics|screen|call|speech|camera` 同名同参;
//  iOS 无法等价实现的地方(防截屏、系统相机权限模型)按能力降级,接口形状保持不变。
//

import Foundation
import UIKit
import LocalAuthentication
import AVFoundation
import Speech

// MARK: - 指纹 / 人脸

/// 保险箱进页前的本机校验。iOS 用 LocalAuthentication,只认 Face ID / Touch ID,
/// 不回落设备密码(与 Android `BIOMETRIC_STRONG` 口径一致)。
@objc(IhomyBiometricPlugin)
class IhomyBiometricPlugin: CAPPlugin, CAPBridgedPlugin {
    let identifier = "IhomyBiometricPlugin"
    let jsName = "IhomyBiometric"
    let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "available", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "verify", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "cancel", returnType: CAPPluginReturnPromise)
    ]

    private var context: LAContext?
    private var pending: CAPPluginCall?

    @objc func available(_ call: CAPPluginCall) {
        let context = LAContext()
        let result = status(of: context)
        call.resolve([
            "available": result == "ok",
            "enrolled": result == "ok",
            "status": result
        ])
    }

    @objc func verify(_ call: CAPPluginCall) {
        if pending != nil {
            call.reject("busy")
            return
        }
        let context = LAContext()
        let state = status(of: context)
        guard state == "ok" else {
            call.reject(state)
            return
        }
        let title = call.getString("title") ?? ""
        let subtitle = call.getString("subtitle") ?? ""
        let cancel = call.getString("cancel") ?? "Cancel"
        context.localizedCancelTitle = cancel
        // 关掉「输入密码」兜底:本地校验只证明「本人在场」,密码仍由服务端校验。
        context.localizedFallbackTitle = ""

        pending = call
        self.context = context
        let reason = subtitle.isEmpty ? title : "\(title)\n\(subtitle)"
        context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics,
                               localizedReason: reason) { [weak self] success, error in
            DispatchQueue.main.async {
                guard let self else { return }
                let call = self.pending
                self.pending = nil
                self.context = nil
                guard let call else { return }
                if success {
                    call.resolve(["ok": true])
                } else {
                    call.reject(self.errorName(error))
                }
            }
        }
    }

    @objc func cancel(_ call: CAPPluginCall) {
        let active = context
        context = nil
        active?.invalidate()
        // invalidate 会自带一次 errorCancel 回调,这里先把 pending 摘掉,避免重复 reject。
        pending = nil
        call.resolve()
    }

    private func status(of context: LAContext) -> String {
        var error: NSError?
        let ok = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
        if ok { return "ok" }
        guard let code = (error as? LAError)?.code else { return "unavailable" }
        switch code {
        case .biometryNotEnrolled: return "no_enrollment"
        case .biometryNotAvailable: return "no_hardware"
        case .biometryLockout: return "lockout"
        default: return "unavailable"
        }
    }

    private func errorName(_ error: Error?) -> String {
        guard let code = (error as? LAError)?.code else { return "failed" }
        switch code {
        case .userCancel, .systemCancel, .appCancel: return "cancel"
        case .biometryLockout: return "lockout"
        case .biometryNotEnrolled: return "no_enrollment"
        case .biometryNotAvailable: return "no_hardware"
        case .passcodeNotSet: return "no_enrollment"
        default: return "failed"
        }
    }
}

// MARK: - 触觉反馈

/// 语义五类与 Android HapticsPlugin 一致;iOS 的强度由系统统一管理,不做自定义波形。
@objc(IhomyHapticsPlugin)
class IhomyHapticsPlugin: CAPPlugin, CAPBridgedPlugin {
    let identifier = "IhomyHapticsPlugin"
    let jsName = "IhomyHaptics"
    let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "play", returnType: CAPPluginReturnPromise)
    ]

    @objc func play(_ call: CAPPluginCall) {
        let kind = call.getString("kind") ?? "tap"
        DispatchQueue.main.async {
            switch kind {
            case "select":
                UISelectionFeedbackGenerator().selectionChanged()
            case "success":
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            case "warning":
                UINotificationFeedbackGenerator().notificationOccurred(.warning)
            case "error":
                UINotificationFeedbackGenerator().notificationOccurred(.error)
            default:
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
            call.resolve()
        }
    }
}

// MARK: - 屏幕保护

/// iOS 没有 FLAG_SECURE,无法真正阻止截图/录屏。
/// 实际可做的是:切后台时用遮罩盖住多任务缩略图,录屏(镜像)时显示遮罩。
/// 这是如实降级,不假装能防截屏。
enum IhomyScreenShield {
    private static var secure = false
    private static var installed = false
    private static weak var cover: UIView?

    static func install() {
        guard !installed else { return }
        installed = true
        let center = NotificationCenter.default
        center.addObserver(forName: UIApplication.willResignActiveNotification, object: nil, queue: .main) { _ in
            if secure { show() }
        }
        center.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { _ in
            if !UIScreen.main.isCaptured { hide() }
        }
        center.addObserver(forName: UIScreen.capturedDidChangeNotification, object: nil, queue: .main) { _ in
            if secure && UIScreen.main.isCaptured { show() } else if !secure { hide() }
        }
    }

    static func setSecure(_ on: Bool) {
        secure = on
        install()
        if !on { hide() }
    }

    static func setKeepOn(_ on: Bool) {
        DispatchQueue.main.async {
            UIApplication.shared.isIdleTimerDisabled = on
        }
    }

    private static func keyWindow() -> UIWindow? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: { $0.isKeyWindow })
    }

    private static func show() {
        guard let window = keyWindow(), cover == nil else { return }
        let view = UIView(frame: window.bounds)
        view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.backgroundColor = UIColor(red: 0.07, green: 0.09, blue: 0.13, alpha: 1)
        let label = UILabel()
        label.text = IhomyL10n.t("ihomy 已隐藏内容", "ihomy content hidden")
        label.textColor = UIColor.white.withAlphaComponent(0.72)
        label.font = .systemFont(ofSize: 14, weight: .medium)
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
        window.addSubview(view)
        cover = view
    }

    private static func hide() {
        cover?.removeFromSuperview()
        cover = nil
    }
}

@objc(IhomyScreenPlugin)
class IhomyScreenPlugin: CAPPlugin, CAPBridgedPlugin {
    let identifier = "IhomyScreenPlugin"
    let jsName = "IhomyScreen"
    let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "setSecure", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setKeepOn", returnType: CAPPluginReturnPromise)
    ]

    @objc func setSecure(_ call: CAPPluginCall) {
        IhomyScreenShield.setSecure(call.getBool("secure") ?? false)
        call.resolve()
    }

    @objc func setKeepOn(_ call: CAPPluginCall) {
        IhomyScreenShield.setKeepOn(call.getBool("keepOn") ?? false)
        call.resolve()
    }
}

// MARK: - 通话权限

/// 发起/接听前把麦克风(视频再加摄像头)要到手,避免 getUserMedia 在「正在接通」时才弹框。
@objc(IhomyCallPlugin)
class IhomyCallPlugin: CAPPlugin, CAPBridgedPlugin {
    let identifier = "IhomyCallPlugin"
    let jsName = "IhomyCall"
    let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "ensure", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openSettings", returnType: CAPPluginReturnPromise)
    ]

    @objc func ensure(_ call: CAPPluginCall) {
        let video = call.getBool("video") ?? true
        requestMicrophone { [weak self] mic in
            guard let self else { return }
            if !video {
                self.finish(call, microphone: mic, camera: true, video: false)
                return
            }
            self.requestCamera { cam in
                self.finish(call, microphone: mic, camera: cam, video: true)
            }
        }
    }

    private func finish(_ call: CAPPluginCall, microphone: Bool, camera: Bool, video: Bool) {
        let micDenied = !microphone
        let camDenied = video && !camera
        // iOS 一旦被拒就不会再弹框,只能去系统设置改;标成 blocked 让前端直接给「去设置」。
        let blocked = micDenied || camDenied
        call.resolve([
            "ok": microphone && (!video || camera),
            "microphone": microphone ? "granted" : "denied",
            "camera": video ? (camera ? "granted" : "denied") : "skipped",
            "blocked": blocked
        ])
    }

    private func requestMicrophone(_ done: @escaping (Bool) -> Void) {
        let session = AVAudioSession.sharedInstance()
        switch session.recordPermission {
        case .granted:
            done(true)
        case .denied:
            done(false)
        default:
            session.requestRecordPermission { granted in
                DispatchQueue.main.async { done(granted) }
            }
        }
    }

    private func requestCamera(_ done: @escaping (Bool) -> Void) {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            done(true)
        case .denied, .restricted:
            done(false)
        default:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async { done(granted) }
            }
        }
    }

    @objc func openSettings(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard let url = URL(string: UIApplication.openSettingsURLString),
                  UIApplication.shared.canOpenURL(url) else {
                call.reject("open_failed")
                return
            }
            UIApplication.shared.open(url, options: [:]) { ok in
                if ok { call.resolve() } else { call.reject("open_failed") }
            }
        }
    }
}

// MARK: - 语音识别

/// 说一句话 → 系统识别 → 文本。业务解析(金额/分类)仍在前端 `utils/voiceRecord.js`。
/// 引擎用 `SFSpeechRecognizer` + `AVAudioEngine`,默认不落盘、保存录音。
@objc(IhomySpeechPlugin)
class IhomySpeechPlugin: CAPPlugin, CAPBridgedPlugin {
    let identifier = "IhomySpeechPlugin"
    let jsName = "IhomySpeech"
    let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "available", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "start", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "cancel", returnType: CAPPluginReturnPromise)
    ]

    private var recognizer: SFSpeechRecognizer?
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var engine: AVAudioEngine?
    private var pending: CAPPluginCall?
    private var finished = false

    @objc func available(_ call: CAPPluginCall) {
        let locale = Locale.preferredLanguages.first.map { Locale(identifier: $0) } ?? Locale.current
        let recognizer = SFSpeechRecognizer(locale: locale) ?? SFSpeechRecognizer()
        let status = SFSpeechRecognizer.authorizationStatus()
        let ok = recognizer?.isAvailable == true && status != .restricted && status != .denied
        call.resolve(["available": ok])
    }

    @objc func start(_ call: CAPPluginCall) {
        if pending != nil {
            call.reject("busy")
            return
        }
        let locale = Locale.preferredLanguages.first.map { Locale(identifier: $0) } ?? Locale.current
        guard let recognizer = SFSpeechRecognizer(locale: locale) ?? SFSpeechRecognizer(), recognizer.isAvailable else {
            call.reject("no_service")
            return
        }
        self.recognizer = recognizer
        requestAuthorization { [weak self] allowed in
            guard let self else { return }
            guard allowed else {
                call.reject("permission")
                return
            }
            self.listen(call)
        }
    }

    @objc func cancel(_ call: CAPPluginCall) {
        finish(error: "cancel")
        call.resolve()
    }

    private func requestAuthorization(_ done: @escaping (Bool) -> Void) {
        let status = SFSpeechRecognizer.authorizationStatus()
        if status == .authorized {
            done(true)
            return
        }
        if status == .denied || status == .restricted {
            done(false)
            return
        }
        SFSpeechRecognizer.requestAuthorization { result in
            DispatchQueue.main.async { done(result == .authorized) }
        }
    }

    private func listen(_ call: CAPPluginCall) {
        pending = call
        finished = false
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            do {
                try self.startEngine()
            } catch {
                NSLog("[ihomy] speech start failed: \(error)")
                self.finish(error: "failed")
            }
        }
    }

    private func startEngine() throws {
        stopEngine()
        guard let recognizer else { throw NSError(domain: "ihomy.speech", code: 1) }
        let engine = AVAudioEngine()
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .measurement, options: .duckOthers)
        try session.setActive(true, options: .notifyOthersOnDeactivation)
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = false
        if #available(iOS 13.0, *) {
            request.requiresOnDeviceRecognition = false
        }
        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        guard format.sampleRate > 0 else { throw NSError(domain: "ihomy.speech", code: 2) }
        input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
            request.append(buffer)
        }
        engine.prepare()
        try engine.start()
        self.engine = engine
        self.request = request
        self.task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            guard let self else { return }
            if let result, result.isFinal {
                self.finish(text: result.bestTranscription.formattedString)
                return
            }
            if let error {
                self.finish(error: self.errorName(error))
            }
        }
    }

    private func stopEngine() {
        engine?.stop()
        if let input = engine?.inputNode {
            input.removeTap(onBus: 0)
        }
        engine = nil
        request?.endAudio()
        request = nil
        task?.cancel()
        task = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func finish(text: String) {
        guard !finished, let call = pending else { return }
        finished = true
        pending = nil
        stopEngine()
        call.resolve(["text": text])
    }

    private func finish(error: String) {
        guard !finished, let call = pending else {
            return
        }
        finished = true
        pending = nil
        stopEngine()
        call.reject(error)
    }

    private func errorName(_ error: Error) -> String {
        let code = (error as NSError).code
        switch code {
        case 203: return "no_match" // kAFAssistantErrorDomain 识别不出内容
        case 216: return "cancel"
        case 1101, 1700: return "network"
        case 201: return "permission"
        default: return "failed"
        }
    }

    deinit {
        stopEngine()
    }
}

// MARK: - 相机直传

/// 系统相机拍一张 → 写进 caches/camera → 前端读回上传。
/// iOS 只有 `UIImagePickerController` 这一条「系统相机」路径,不走文件选择器。
@objc(IhomyCameraPlugin)
class IhomyCameraPlugin: CAPPlugin, CAPBridgedPlugin {
    let identifier = "IhomyCameraPlugin"
    let jsName = "IhomyCamera"
    let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "available", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "capture", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "readFile", returnType: CAPPluginReturnPromise)
    ]

    private static let dirName = "camera"
    private static let maxBytes = 12 * 1024 * 1024
    private var pending: CAPPluginCall?
    private var pendingFile: URL?

    @objc func available(_ call: CAPPluginCall) {
        call.resolve(["available": UIImagePickerController.isSourceTypeAvailable(.camera)])
    }

    @objc func capture(_ call: CAPPluginCall) {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            call.reject("no_camera")
            return
        }
        if pending != nil {
            call.reject("busy")
            return
        }
        requestCamera { [weak self] allowed in
            guard let self else { return }
            guard allowed else {
                call.reject("denied")
                return
            }
            self.openCamera(call)
        }
    }

    private func requestCamera(_ done: @escaping (Bool) -> Void) {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            done(true)
        case .denied, .restricted:
            done(false)
        default:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async { done(granted) }
            }
        }
    }

    private func openCamera(_ call: CAPPluginCall) {
        guard let viewController = bridge?.viewController else {
            call.reject("no_activity")
            return
        }
        DispatchQueue.main.async {
            let picker = UIImagePickerController()
            picker.sourceType = .camera
            picker.delegate = self
            self.pending = call
            viewController.present(picker, animated: true)
        }
    }

    @objc func readFile(_ call: CAPPluginCall) {
        guard let path = call.getString("path"), !path.isEmpty else {
            call.reject("path required")
            return
        }
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                let dir = try self.cacheDir()
                let file = URL(fileURLWithPath: path)
                guard file.standardizedFileURL.path.hasPrefix(dir.standardizedFileURL.path + "/"),
                      FileManager.default.fileExists(atPath: file.path) else {
                    call.reject("文件已失效,请重新拍照")
                    return
                }
                let size = (try? FileManager.default.attributesOfItem(atPath: file.path)[.size] as? NSNumber)?.intValue ?? 0
                guard size > 0, size <= Self.maxBytes else {
                    call.reject("照片太大,请重拍")
                    return
                }
                let data = try Data(contentsOf: file)
                call.resolve([
                    "data": data.base64EncodedString(),
                    "name": file.lastPathComponent,
                    "size": data.count
                ])
            } catch {
                call.reject("读取照片失败:\(error.localizedDescription)")
            }
        }
    }

    private func cacheDir() throws -> URL {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let dir = base.appendingPathComponent(Self.dirName, isDirectory: true)
        if !FileManager.default.fileExists(atPath: dir.path) {
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        }
        return dir
    }

    private func save(_ image: UIImage) -> URL? {
        guard let dir = try? cacheDir() else { return nil }
        // 清掉上一张,避免缓存越攒越大
        if let old = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil) {
            for item in old { try? FileManager.default.removeItem(at: item) }
        }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMdd_HHmmss"
        let name = "IMG_\(formatter.string(from: Date())).jpg"
        let url = dir.appendingPathComponent(name)
        guard let data = image.jpegData(compressionQuality: 0.9) else { return nil }
        do {
            try data.write(to: url, options: .atomic)
            return url
        } catch {
            return nil
        }
    }
}

extension IhomyCameraPlugin: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
        let call = pending
        pending = nil
        pendingFile = nil
        call?.reject("cancel")
    }

    func imagePickerController(_ picker: UIImagePickerController,
                               didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        picker.dismiss(animated: true)
        let call = pending
        pending = nil
        guard let call else { return }
        guard let image = info[.originalImage] as? UIImage, let url = save(image) else {
            call.reject("failed")
            return
        }
        pendingFile = url
        let size = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? NSNumber)?.intValue ?? 0
        call.resolve([
            "name": url.lastPathComponent,
            "size": size,
            "path": url.path
        ])
    }
}

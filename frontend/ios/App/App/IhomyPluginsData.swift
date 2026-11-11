//
//  IhomyPluginsData.swift
//  ihomy
//
//  数据类插件:系统日历、相册自动备份、系统分享收件、诊断落盘。
//  与 Android `top.ihomy.app.calendar|photo|share|diagnostics` 的 JS 契约保持一致,
//  文件路径也统一放进 App Group 容器,主 App 与 Share 扩展共用。
//

import Foundation
import UIKit
import EventKit
import Photos
import Network
import CryptoKit
import UniformTypeIdentifiers
import Darwin

// MARK: - 系统日历

/// 单向导出到本机「ihomy」日历,给站内提醒做兜底。
/// 日期/时刻全部来自服务端 `/schedule/occurrences`,客户端不做历法推算。
@objc(IhomyCalendarPlugin)
class IhomyCalendarPlugin: CAPPlugin, CAPBridgedPlugin {
    let identifier = "IhomyCalendarPlugin"
    let jsName = "IhomyCalendar"
    let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "checkPermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestPermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "status", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "export", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clear", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "readEvents", returnType: CAPPluginReturnPromise)
    ]

    private static let calendarTitle = "ihomy"
    private static let eventDuration: TimeInterval = 30 * 60

    private let store = EKEventStore()
    private let queue = DispatchQueue(label: "ihomy.calendar")

    // MARK: 权限

    @objc func checkPermission(_ call: CAPPluginCall) {
        call.resolve(["state": permissionState()])
    }

    @objc func requestPermission(_ call: CAPPluginCall) {
        if permissionState() == "granted" {
            call.resolve(["state": "granted"])
            return
        }
        if #available(iOS 17.0, *) {
            store.requestFullAccessToEvents { [weak self] _, _ in
                DispatchQueue.main.async { self?.resolvePermission(call) }
            }
        } else {
            store.requestAccess(to: .event) { [weak self] _, _ in
                DispatchQueue.main.async { self?.resolvePermission(call) }
            }
        }
    }

    private func resolvePermission(_ call: CAPPluginCall) {
        call.resolve(["state": permissionState()])
    }

    private func permissionState() -> String {
        let status = EKEventStore.authorizationStatus(for: .event)
        if #available(iOS 17.0, *) {
            if status == .fullAccess { return "granted" }
            // 只写权限无法回读事件,但导出本身可用;为保持前端「能用」的判定,按已授权处理。
            if status == .writeOnly { return "granted" }
        }
        switch status {
        case .authorized: return "granted"
        case .denied, .restricted: return "denied"
        case .notDetermined: return "prompt"
        @unknown default: return "denied"
        }
    }

    // MARK: 查询

    @objc func status(_ call: CAPPluginCall) {
        queue.async {
            let granted = self.permissionState() == "granted"
            guard granted else {
                call.resolve(["granted": false, "calendarId": NSNull(), "count": 0])
                return
            }
            let calendar = self.findCalendar()
            var count = 0
            if let calendar {
                let predicate = self.store.predicateForEvents(withStart: Date.distantPast,
                                                              end: Date.distantFuture,
                                                              calendars: [calendar])
                count = self.store.events(matching: predicate).count
            }
            call.resolve([
                "granted": true,
                "calendarId": calendar?.calendarIdentifier ?? NSNull(),
                "count": count
            ])
        }
    }

    // MARK: 导出

    @objc func export(_ call: CAPPluginCall) {
        guard permissionState() == "granted" else {
            call.reject("PERMISSION_DENIED")
            return
        }
        let items = (call.getArray("items") as? [[String: Any]]) ?? []
        let displayName = call.getString("calendarName") ?? Self.calendarTitle
        queue.async {
            guard let calendar = self.ensureCalendar(displayName: displayName) else {
                call.reject("创建日历失败")
                return
            }
            let removed = self.removeAll(in: calendar)
            var inserted = 0
            var skipped = 0
            for raw in items {
                if self.writeEvent(raw, to: calendar) {
                    inserted += 1
                } else {
                    skipped += 1
                }
            }
            do {
                try self.store.commit()
            } catch {
                NSLog("[ihomy] calendar commit failed: \(error)")
            }
            call.resolve([
                "calendarId": calendar.calendarIdentifier,
                "inserted": inserted,
                "removed": removed,
                "skipped": skipped
            ])
        }
    }

    @objc func clear(_ call: CAPPluginCall) {
        guard permissionState() == "granted" else {
            call.reject("PERMISSION_DENIED")
            return
        }
        queue.async {
            guard let calendar = self.findCalendar() else {
                call.resolve(["removed": 0])
                return
            }
            let removed = self.removeAll(in: calendar)
            try? self.store.commit()
            call.resolve(["removed": removed])
        }
    }

    // MARK: 读取本机日历(只读合并展示)

    @objc func readEvents(_ call: CAPPluginCall) {
        guard let fromMs = call.getDouble("from"), let toMs = call.getDouble("to"), toMs > fromMs else {
            call.reject("from/to required")
            return
        }
        guard permissionState() == "granted" else {
            call.reject("PERMISSION_DENIED")
            return
        }
        queue.async {
            let from = Date(timeIntervalSince1970: fromMs / 1000)
            let to = Date(timeIntervalSince1970: toMs / 1000)
            let predicate = self.store.predicateForEvents(withStart: from, end: to, calendars: nil)
            let ownId = self.findCalendar()?.calendarIdentifier
            var out: [[String: Any]] = []
            for event in self.store.events(matching: predicate) {
                if out.count >= 300 { break }
                if let ownId, event.calendar.calendarIdentifier == ownId { continue }
                out.append([
                    "id": event.eventIdentifier ?? "",
                    "title": event.title ?? "",
                    "begin": event.startDate.timeIntervalSince1970 * 1000,
                    "end": event.endDate.timeIntervalSince1970 * 1000,
                    "allDay": event.isAllDay,
                    "calendarName": event.calendar.title
                ])
            }
            call.resolve(["events": out])
        }
    }

    // MARK: EventKit 读写

    private func findCalendar() -> EKCalendar? {
        store.calendars(for: .event).first { $0.title == Self.calendarTitle }
    }

    /// 本地日历账户不存在就建一个;iOS 不允许凭空建账户,只能挂到已有 source 下。
    private func ensureCalendar(displayName: String) -> EKCalendar? {
        if let existing = findCalendar() { return existing }
        guard let source = store.sources.first(where: { $0.sourceType == .local })
                ?? store.defaultCalendarForNewEvents?.source
                ?? store.sources.first else {
            return nil
        }
        let calendar = EKCalendar(for: .event, eventStore: store)
        calendar.title = Self.calendarTitle
        calendar.source = source
        calendar.cgColor = UIColor(red: 0.12, green: 0.23, blue: 0.37, alpha: 1).cgColor
        do {
            try store.saveCalendar(calendar, commit: false)
            return calendar
        } catch {
            NSLog("[ihomy] create calendar failed: \(error)")
            return nil
        }
    }

    private func removeAll(in calendar: EKCalendar) -> Int {
        let predicate = store.predicateForEvents(withStart: Date.distantPast,
                                                 end: Date.distantFuture,
                                                 calendars: [calendar])
        let events = store.events(matching: predicate)
        var removed = 0
        for event in events {
            do {
                try store.remove(event, span: .thisEvent, commit: false)
                removed += 1
            } catch {
                NSLog("[ihomy] remove event failed: \(error)")
            }
        }
        return removed
    }

    private func writeEvent(_ raw: [String: Any], to calendar: EKCalendar) -> Bool {
        guard let dateText = trim(raw["date"] as? String),
              let title = trim(raw["title"] as? String) else {
            return false
        }
        let time = trim(raw["time"] as? String)
        let zone = trim(raw["timeZone"] as? String).flatMap { TimeZone(identifier: $0) } ?? TimeZone.current
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = zone
        formatter.dateFormat = "yyyy-MM-dd"
        guard let day = formatter.date(from: dateText) else { return false }

        let event = EKEvent(eventStore: store)
        event.calendar = calendar
        event.title = title
        if let description = trim(raw["description"] as? String) { event.notes = description }
        if let link = trim(raw["link"] as? String) { event.url = URL(string: link) }

        if let time {
            let parts = time.split(separator: ":")
            guard parts.count == 2, let hour = Int(parts[0]), let minute = Int(parts[1]) else { return false }
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = zone
            var components = calendar.dateComponents([.year, .month, .day], from: day)
            components.hour = hour
            components.minute = minute
            components.timeZone = zone
            guard let start = calendar.date(from: components) else { return false }
            event.startDate = start
            event.endDate = start.addingTimeInterval(Self.eventDuration)
            event.isAllDay = false
            event.addAlarm(EKAlarm(relativeOffset: 0))
        } else {
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = zone
            let start = calendar.startOfDay(for: day)
            event.startDate = start
            event.endDate = start.addingTimeInterval(24 * 60 * 60)
            event.isAllDay = true
        }

        do {
            try store.save(event, span: .thisEvent, commit: false)
            return true
        } catch {
            NSLog("[ihomy] save event failed: \(error)")
            return false
        }
    }

    private func trim(_ value: String?) -> String? {
        let text = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return text.isEmpty || text == "null" ? nil : text
    }
}

// MARK: - 设备网络

/// NWPathMonitor 只在后台队列更新;这里用锁把「最近一次可用路径」缓存成同步快照,
/// 让 `status()` 能像 Android 的 ConnectivityManager 一样立即返回。
final class IhomyNetwork {
    static let shared = IhomyNetwork()

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "ihomy.network.monitor")
    private let lock = NSLock()
    private var current: NWPath?

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            self.lock.lock()
            self.current = path
            self.lock.unlock()
        }
        monitor.start(queue: queue)
    }

    var path: NWPath? {
        lock.lock()
        defer { lock.unlock() }
        return current
    }

    var isWifi: Bool { path?.usesInterfaceType(.wifi) == true }
    var isExpensive: Bool { path?.isExpensive ?? true }
    var isConnected: Bool { path?.status == .satisfied }
}

// MARK: - 相册备份

/// 只读系统相册,把照片复制进缓存并算 SHA-256,由前端逐张上传。
/// iOS 没有 `DATE_ADDED`,用 `creationDate`(缺失时退 `modificationDate`)当游标时间;
/// `mediaId` 是 `localIdentifier` 的 FNV-1a 32 位正整数哈希,并在共享缓存留 manifest 供 prepare 反查。
@objc(IhomyPhotoBackupPlugin)
class IhomyPhotoBackupPlugin: CAPPlugin, CAPBridgedPlugin {
    let identifier = "IhomyPhotoBackupPlugin"
    let jsName = "IhomyPhotoBackup"
    let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "status", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "checkPermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestPermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "count", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "list", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "prepare", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "drop", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "readBase64", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "cleanup", returnType: CAPPluginReturnPromise)
    ]

    private static let maxPhotoBytes: Int64 = 30 * 1024 * 1024
    private static let maxBase64Bytes = 12 * 1024 * 1024
    private static let cacheDirName = "photo-backup"
    private static let manifestName = "photo-manifest.json"
    private static let maxManifestEntries = 20000

    private let queue = DispatchQueue(label: "ihomy.photo.backup")

    // MARK: 设备状态

    @objc func status(_ call: CAPPluginCall) {
        queue.async {
            let battery = UIDevice.current.batteryState
            call.resolve([
                "permission": self.permissionState(),
                "charging": battery == .charging || battery == .full,
                "wifi": IhomyNetwork.shared.isWifi,
                "metered": IhomyNetwork.shared.isExpensive,
                "cacheBytes": self.cacheBytes()
            ])
        }
    }

    @objc func checkPermission(_ call: CAPPluginCall) {
        call.resolve(["state": permissionState()])
    }

    @objc func requestPermission(_ call: CAPPluginCall) {
        if permissionState() == "granted" {
            call.resolve(["state": "granted"])
            return
        }
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { _ in
            DispatchQueue.main.async {
                call.resolve(["state": self.permissionState()])
            }
        }
    }

    /// `.limited` 映射成 granted:iOS 允许读取用户已勾选的照片,备份流程可以继续。
    private func permissionState() -> String {
        switch PHPhotoLibrary.authorizationStatus(for: .readWrite) {
        case .authorized, .limited: return "granted"
        case .denied, .restricted: return "denied"
        case .notDetermined: return "prompt"
        @unknown default: return "denied"
        }
    }

    // MARK: 计数 / 分页

    @objc func count(_ call: CAPPluginCall) {
        guard permissionState() == "granted" else {
            call.resolve(["total": 0, "granted": false])
            return
        }
        let sinceMs = call.getDouble("sinceMs") ?? 0
        queue.async {
            let options = PHFetchOptions()
            options.predicate = NSPredicate(format: "mediaType == %d AND (creationDate >= %@ OR modificationDate >= %@)",
                                            PHAssetMediaType.image.rawValue,
                                            Date(timeIntervalSince1970: sinceMs / 1000),
                                            Date(timeIntervalSince1970: sinceMs / 1000))
            let result = PHAsset.fetchAssets(with: options)
            call.resolve(["total": result.count, "granted": true])
        }
    }

    @objc func list(_ call: CAPPluginCall) {
        guard permissionState() == "granted" else {
            call.reject("permission")
            return
        }
        let sinceMs = call.getDouble("sinceMs") ?? 0
        let afterDateAdded = call.getDouble("afterDateAdded") ?? 0
        let afterId = call.getInt("afterId") ?? 0
        var limit = call.getInt("limit") ?? 50
        if limit <= 0 || limit > 200 { limit = 50 }
        let pageSize = limit

        queue.async {
            let options = PHFetchOptions()
            options.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.image.rawValue)
            options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
            let result = PHAsset.fetchAssets(with: options)

            var manifest = self.loadManifest()
            var items: [[String: Any]] = []
            var hasMore = false
            var index = 0

            while index < result.count {
                let asset = result.object(at: index)
                index += 1
                let dateAdded = self.dateAdded(of: asset)
                if dateAdded < Int64(sinceMs) { continue }
                let mediaId = self.mediaId(for: asset)
                if afterDateAdded > 0 {
                    if dateAdded < Int64(afterDateAdded) { continue }
                    if dateAdded == Int64(afterDateAdded) && mediaId <= afterId { continue }
                }
                if items.count >= pageSize {
                    hasMore = true
                    break
                }
                manifest[String(mediaId)] = asset.localIdentifier
                items.append(self.item(asset, mediaId: mediaId, dateAdded: dateAdded))
            }

            self.saveManifest(manifest)
            call.resolve(["items": items, "hasMore": hasMore])
        }
    }

    // MARK: 准备上传

    @objc func prepare(_ call: CAPPluginCall) {
        guard permissionState() == "granted" else {
            call.reject("permission")
            return
        }
        guard let mediaId = call.getInt("mediaId"), mediaId > 0 else {
            call.reject("mediaId required")
            return
        }
        queue.async {
            guard let asset = self.resolveAsset(mediaId) else {
                call.reject("照片已不在相册里")
                return
            }
            let name = self.filename(of: asset)
            let mime = self.mime(of: name)
            let takenAt = self.dateAdded(of: asset)

            let options = PHImageRequestOptions()
            options.isNetworkAccessAllowed = true
            options.deliveryMode = .highQualityFormat
            options.version = .current
            options.isSynchronous = false

            PHImageManager.default().requestImageDataAndOrientation(for: asset, options: options) { data, _, _, info in
                let manager = FileManager.default
                if manager.isICloudPhotosError(info) {
                    // 网络不可用 / iCloud 未下载:按单张失败处理,前端会停下整轮并保留游标。
                    call.reject("照片还在 iCloud,请联网后重试")
                    return
                }
                guard let data, !data.isEmpty else {
                    call.reject("读取照片失败")
                    return
                }
                let size = Int64(data.count)
                if size > Self.maxPhotoBytes {
                    call.resolve([
                        "mediaId": mediaId,
                        "name": name,
                        "mime": mime,
                        "takenAt": takenAt,
                        "tooLarge": true,
                        "size": size
                    ])
                    return
                }
                guard let dir = self.cacheDir(),
                      let path = self.write(data, dir: dir, mediaId: mediaId, name: name) else {
                    call.reject("准备上传失败")
                    return
                }
                var digest = SHA256()
                digest.update(data: data)
                let checksum = digest.finalize().map { String(format: "%02x", $0) }.joined()
                call.resolve([
                    "mediaId": mediaId,
                    "name": name,
                    "mime": mime,
                    "takenAt": takenAt,
                    "tooLarge": false,
                    "size": size,
                    "checksum": checksum,
                    "path": path.path
                ])
            }
        }
    }

    // MARK: 缓存管理

    @objc func drop(_ call: CAPPluginCall) {
        guard let path = call.getString("path"), !path.isEmpty else {
            call.resolve()
            return
        }
        queue.async {
            guard let dir = self.cacheDir() else {
                call.resolve()
                return
            }
            let file = URL(fileURLWithPath: path)
            if file.standardizedFileURL.path.hasPrefix(dir.standardizedFileURL.path + "/") {
                try? FileManager.default.removeItem(at: file)
            }
            call.resolve()
        }
    }

    @objc func readBase64(_ call: CAPPluginCall) {
        guard let path = call.getString("path"), !path.isEmpty else {
            call.reject("path required")
            return
        }
        queue.async {
            guard let dir = self.cacheDir() else {
                call.reject("照片已失效,请重试")
                return
            }
            let file = URL(fileURLWithPath: path)
            guard file.standardizedFileURL.path.hasPrefix(dir.standardizedFileURL.path + "/"),
                  let attrs = try? FileManager.default.attributesOfItem(atPath: file.path),
                  let size = (attrs[.size] as? NSNumber)?.intValue,
                  size > 0, size <= Self.maxBase64Bytes,
                  let data = try? Data(contentsOf: file) else {
                call.reject("读取照片失败")
                return
            }
            call.resolve(["data": data.base64EncodedString(), "size": data.count])
        }
    }

    @objc func cleanup(_ call: CAPPluginCall) {
        queue.async {
            var removed = 0
            if let dir = self.cacheDir(),
               let files = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil) {
                for file in files where (try? FileManager.default.removeItem(at: file)) != nil {
                    removed += 1
                }
            }
            call.resolve(["removed": removed])
        }
    }

    private func cacheBytes() -> Int64 {
        guard let dir = cacheDir(),
              let files = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.fileSizeKey]) else {
            return 0
        }
        var total: Int64 = 0
        for file in files {
            total += Int64((try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)
        }
        return total
    }

    private func cacheDir() -> URL? {
        IhomyAppGroup.directory(named: Self.cacheDirName)
    }

    private func write(_ data: Data, dir: URL, mediaId: Int, name: String) -> URL? {
        let ext = (name as NSString).pathExtension
        let suffix = ext.isEmpty ? "jpg" : ext
        let url = dir.appendingPathComponent("\(mediaId)-\(Int(Date().timeIntervalSince1970 * 1000)).\(suffix)")
        do {
            try data.write(to: url, options: .atomic)
            return url
        } catch {
            NSLog("[ihomy] photo cache write failed: \(error)")
            return nil
        }
    }

    // MARK: 资产映射

    private func mediaId(for asset: PHAsset) -> Int {
        var hash: UInt32 = 0x811c9dc5
        for byte in asset.localIdentifier.utf8 {
            hash ^= UInt32(byte)
            hash = hash &* 0x01000193
        }
        return Int(hash & 0x7fffffff)
    }

    private func dateAdded(of asset: PHAsset) -> Int64 {
        let date = asset.creationDate ?? asset.modificationDate ?? Date()
        return Int64((date.timeIntervalSince1970 * 1000).rounded())
    }

    private func filename(of asset: PHAsset) -> String {
        if let resource = PHAssetResource.assetResources(for: asset).first {
            return resource.originalFilename
        }
        return "IMG_\(mediaId(for: asset)).jpg"
    }

    private func mime(of name: String) -> String {
        let ext = (name as NSString).pathExtension
        if !ext.isEmpty, let type = UTType(filenameExtension: ext), let mime = type.preferredMIMEType {
            return mime
        }
        return "image/jpeg"
    }

    private func item(_ asset: PHAsset, mediaId: Int, dateAdded: Int64) -> [String: Any] {
        let name = filename(of: asset)
        var size: Int64 = 0
        if let resource = PHAssetResource.assetResources(for: asset).first,
           let value = resource.value(forKey: "fileSize") as? NSNumber {
            size = value.int64Value
        }
        return [
            "mediaId": mediaId,
            "name": name,
            "mime": mime(of: name),
            "size": size,
            "dateAdded": dateAdded,
            "takenAt": dateAdded
        ]
    }

    private func resolveAsset(_ mediaId: Int) -> PHAsset? {
        var manifest = loadManifest()
        if let identifier = manifest[String(mediaId)] {
            let result = PHAsset.fetchAssets(withLocalIdentifiers: [identifier], options: nil)
            if let asset = result.firstObject { return asset }
        }
        // manifest 被系统清过 / App 换机:重扫一次把映射补回来。
        let options = PHFetchOptions()
        options.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.image.rawValue)
        let result = PHAsset.fetchAssets(with: options)
        for index in 0..<result.count {
            let asset = result.object(at: index)
            let id = mediaId(for: asset)
            manifest[String(id)] = asset.localIdentifier
            if id == mediaId {
                saveManifest(manifest)
                return asset
            }
        }
        saveManifest(manifest)
        return nil
    }

    private func manifestURL() -> URL? {
        IhomyAppGroup.directory(named: Self.cacheDirName)?.appendingPathComponent(Self.manifestName)
    }

    private func loadManifest() -> [String: String] {
        guard let url = manifestURL(),
              let data = try? Data(contentsOf: url),
              let dict = try? JSONSerialization.jsonObject(with: data) as? [String: String] else {
            return [:]
        }
        return dict
    }

    private func saveManifest(_ manifest: [String: String]) {
        guard let url = manifestURL() else { return }
        var trimmed = manifest
        if trimmed.count > Self.maxManifestEntries {
            // 超出上限只保留最后写入的一批(JSON 字典无序,足够 prepare 反查最近照片)
            trimmed = Dictionary(uniqueKeysWithValues: Array(trimmed).suffix(Self.maxManifestEntries))
        }
        guard let data = try? JSONSerialization.data(withJSONObject: trimmed) else { return }
        try? data.write(to: url, options: .atomic)
    }
}

private extension FileManager {
    /// PHImageManager 的失败信息——iCloud 未下载与「资产不存在」都归到这里。
    func isICloudPhotosError(_ info: [AnyHashable: Any]?) -> Bool {
        guard let info else { return false }
        if let error = info[PHImageErrorKey] as? NSError {
            return error.domain == "PHPhotosErrorDomain"
        }
        let cancelled = (info[PHImageCancelledKey] as? NSNumber)?.boolValue ?? false
        return cancelled
    }
}

// MARK: - 系统分享收件

/// 收件由 Share 扩展写进 App Group `share-in/pending.json`,插件只负责读取、
/// 惰性取文件与清理。图片/文档在扩展里已经落盘(security-scoped URL 在扩展退出后失效)。
@objc(IhomySharePlugin)
class IhomySharePlugin: CAPPlugin, CAPBridgedPlugin {
    let identifier = "IhomySharePlugin"
    let jsName = "IhomyShare"
    let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "getPending", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clearPending", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "prepare", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "readFile", returnType: CAPPluginReturnPromise)
    ]

    private static let maxReadBytes = 10 * 1024 * 1024
    private var cachedFor: Double = -1
    private var cached: [String: Any]?
    private var lastSeen: Double = 0

    override func load() {
        super.load()
        NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification,
                                              object: nil,
                                              queue: .main) { [weak self] _ in
            self?.notifyIfChanged()
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @objc func getPending(_ call: CAPPluginCall) {
        queueWork {
            guard let pending = IhomyShareStore.read() else {
                call.resolve(["has": false])
                return
            }
            let receivedAt = (pending["receivedAt"] as? NSNumber)?.doubleValue ?? 0
            if receivedAt == self.cachedFor, let cached = self.cached {
                call.resolve(cached)
                return
            }
            var payload = pending
            payload["has"] = true
            payload["skipped"] = (pending["skipped"] as? NSNumber)?.intValue ?? 0
            payload["skippedImages"] = (pending["skippedImages"] as? NSNumber)?.intValue ?? 0
            payload["skippedDocs"] = (pending["skippedDocs"] as? NSNumber)?.intValue ?? 0
            self.cachedFor = receivedAt
            self.cached = payload
            self.lastSeen = receivedAt
            call.resolve(payload)
        }
    }

    @objc func clearPending(_ call: CAPPluginCall) {
        IhomyShareStore.clear()
        cachedFor = -1
        cached = nil
        call.resolve()
    }

    @objc func prepare(_ call: CAPPluginCall) {
        guard let index = call.getInt("index") else {
            call.reject("index required")
            return
        }
        queueWork {
            guard let pending = IhomyShareStore.read(),
                  let files = pending["files"] as? [[String: Any]],
                  index >= 0, index < files.count else {
                call.reject("文件已失效,请重新分享")
                return
            }
            var file = files[index]
            guard let path = file["path"] as? String,
                  FileManager.default.fileExists(atPath: path) else {
                call.reject("文件已失效,请重新分享")
                return
            }
            let url = URL(fileURLWithPath: path)
            let size = ((try? FileManager.default.attributesOfItem(atPath: path)[.size]) as? NSNumber)?.intValue ?? 0
            call.resolve([
                "path": path,
                "size": size,
                "name": file["name"] as? String ?? url.lastPathComponent,
                "mime": file["mime"] as? String ?? "application/octet-stream"
            ])
            file["path"] = path
        }
    }

    @objc func readFile(_ call: CAPPluginCall) {
        guard let path = call.getString("path"), !path.isEmpty else {
            call.reject("path required")
            return
        }
        queueWork {
            guard let dir = IhomyShareStore.directory else {
                call.reject("文件已失效,请重新分享")
                return
            }
            let file = URL(fileURLWithPath: path)
            guard file.standardizedFileURL.path.hasPrefix(dir.standardizedFileURL.path + "/"),
                  let attrs = try? FileManager.default.attributesOfItem(atPath: file.path),
                  let size = (attrs[.size] as? NSNumber)?.intValue,
                  size > 0, size <= Self.maxReadBytes,
                  let data = try? Data(contentsOf: file) else {
                call.reject("读取分享文件失败")
                return
            }
            call.resolve([
                "data": data.base64EncodedString(),
                "name": file.lastPathComponent,
                "size": data.count
            ])
        }
    }

    private func queueWork(_ work: @escaping () -> Void) {
        DispatchQueue.global(qos: .userInitiated).async(execute: work)
    }

    /// App 已在前台时再次分享:扩展写完 pending.json 再把 App 拉到前台,
    /// 这里比对 receivedAt,变化就通知 JS(`initShareInbox` 监听 shareReceived)。
    private func notifyIfChanged() {
        DispatchQueue.global(qos: .userInitiated).async {
            guard let pending = IhomyShareStore.read() else { return }
            let receivedAt = (pending["receivedAt"] as? NSNumber)?.doubleValue ?? 0
            if receivedAt > 0, receivedAt != self.lastSeen {
                self.lastSeen = receivedAt
                self.cachedFor = -1
                self.cached = nil
                DispatchQueue.main.async {
                    self.notifyListeners("shareReceived", data: [:])
                }
            }
        }
    }
}

// MARK: - 诊断

/// 本地诊断落盘:前端错误 `front-errors.txt`(环形 200 条)+ 崩溃 `crash-<ts>.txt`(最多 5 份)。
/// 只给设置页手动导出用,不上传。
enum IhomyCrashStore {
    private static var installed = false
    /// 崩溃现场只能 `write()`:安装时装好一个预打开的文件描述符。
    private static var pendingFD: Int32 = -1
    private static var pendingURL: URL?
    private static let signalText = "ihomy crashed\n"

    static func install() {
        guard !installed else { return }
        installed = true
        migratePendingCrash()

        NSSetUncaughtExceptionHandler { exception in
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
            var text = "time: \(formatter.string(from: Date()))\n"
            text += "model: \(UIDevice.current.model)\n"
            text += "system: \(UIDevice.current.systemName) \(UIDevice.current.systemVersion)\n"
            text += "app: \(appVersion())\n"
            text += "thread: \(Thread.current.name ?? "?")\n"
            text += "---\n\(exception.name.rawValue): \(exception.reason ?? "")\n"
            text += exception.callStackSymbols.joined(separator: "\n")
            writeCrash(text)
        }

        openPendingFile()
        for sig in [SIGABRT, SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGTRAP] {
            signal(sig) { signal in
                if pendingFD >= 0 {
                    signalText.withCString { _ = write(pendingFD, $0, strlen($0)) }
                }
                signal(signal, SIG_DFL)
                raise(signal)
            }
        }
    }

    static func frontErrorCount() -> Int {
        readLines().count
    }

    static func appendFrontErrors(_ lines: [String]) -> Int {
        guard !lines.isEmpty else { return 0 }
        var all = readLines()
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
        let stamp = formatter.string(from: Date())
        for raw in lines {
            var text = raw.replacingOccurrences(of: "\r", with: " ").replacingOccurrences(of: "\n", with: " ")
            if text.count > 1000 { text = String(text.prefix(1000)) }
            all.append("\(stamp) \(text)")
        }
        while all.count > 200 { all.removeFirst() }
        writeText(all.joined(separator: "\n") + "\n", to: fileURL("front-errors.txt"), maxBytes: 512 * 1024)
        return lines.count
    }

    static func crashFiles() -> [[String: Any]] {
        guard let dir = directory(),
              let files = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.fileSizeKey, .contentModificationDateKey]) else {
            return []
        }
        return files
            .filter { $0.lastPathComponent.range(of: "^crash-\\d+\\.txt$", options: .regularExpression) != nil }
            .sorted { $0.lastPathComponent > $1.lastPathComponent }
            .map { file in
                let size = (try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
                let at = (try? file.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? Date()
                return ["name": file.lastPathComponent, "size": size, "at": at.timeIntervalSince1970 * 1000]
            }
    }

    static func read(_ name: String) -> String? {
        guard allowed(name), let url = fileURL(name),
              let data = try? Data(contentsOf: url) else { return nil }
        let clipped = data.prefix(512 * 1024)
        return String(data: clipped, encoding: .utf8) ?? ""
    }

    static func clear() -> Int {
        guard let dir = directory(),
              let files = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil) else {
            return 0
        }
        var removed = 0
        for file in files where (try? FileManager.default.removeItem(at: file)) != nil {
            removed += 1
        }
        return removed
    }

    // MARK: 落盘

    private static func directory() -> URL? {
        IhomyAppGroup.directory(named: "diagnostics")
    }

    private static func fileURL(_ name: String) -> URL? {
        directory()?.appendingPathComponent(name)
    }

    private static func allowed(_ name: String) -> Bool {
        if name == "front-errors.txt" { return true }
        return name.range(of: "^crash-\\d+\\.txt$", options: .regularExpression) != nil
    }

    private static func readLines() -> [String] {
        guard let url = fileURL("front-errors.txt"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return [] }
        return text.split(separator: "\n", omittingEmptySubsequences: false).map(String.init).filter { !$0.isEmpty }
    }

    private static func writeText(_ text: String, to url: URL?, maxBytes: Int) {
        guard let url else { return }
        var data = Data(text.utf8)
        if data.count > maxBytes {
            data = data.prefix(maxBytes)
        }
        try? data.write(to: url, options: .atomic)
    }

    private static func writeCrash(_ text: String) {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMddHHmmss"
        let name = "crash-\(Int(Date().timeIntervalSince1970 * 1000)).txt"
        writeText(text, to: fileURL(name), maxBytes: 64 * 1024)
        prune()
    }

    private static func prune() {
        let files = crashFiles().compactMap { $0["name"] as? String }
        for name in files.dropFirst(5) {
            if let url = fileURL(name) { try? FileManager.default.removeItem(at: url) }
        }
    }

    // MARK: 信号崩溃

    private static func openPendingFile() {
        guard let dir = directory() else { return }
        let url = dir.appendingPathComponent("crash-pending.txt")
        pendingURL = url
        pendingFD = open(url.path, O_CREAT | O_WRONLY | O_APPEND, 0o644)
    }

    /// 上次崩溃留下的信号记录:启动时改名为正式崩溃文件。
    private static func migratePendingCrash() {
        guard let dir = directory() else { return }
        let pending = dir.appendingPathComponent("crash-pending.txt")
        guard FileManager.default.fileExists(atPath: pending.path),
              let size = try? FileManager.default.attributesOfItem(atPath: pending.path)[.size] as? NSNumber,
              size.intValue > 0 else {
            try? FileManager.default.removeItem(at: pending)
            return
        }
        let name = "crash-\(Int(Date().timeIntervalSince1970 * 1000)).txt"
        try? FileManager.default.moveItem(at: pending, to: dir.appendingPathComponent(name))
        prune()
    }

    private static func appVersion() -> String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?"
        return "\(version) (\(build))"
    }
}

@objc(IhomyDiagnosticsPlugin)
class IhomyDiagnosticsPlugin: CAPPlugin, CAPBridgedPlugin {
    let identifier = "IhomyDiagnosticsPlugin"
    let jsName = "IhomyDiagnostics"
    let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "report", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "list", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "read", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clear", returnType: CAPPluginReturnPromise)
    ]

    @objc func report(_ call: CAPPluginCall) {
        let lines = (call.getArray("lines") as? [String]) ?? []
        DispatchQueue.global(qos: .utility).async {
            let stored = IhomyCrashStore.appendFrontErrors(lines)
            call.resolve(["stored": stored])
        }
    }

    @objc func list(_ call: CAPPluginCall) {
        DispatchQueue.global(qos: .utility).async {
            call.resolve([
                "crashes": IhomyCrashStore.crashFiles(),
                "errors": IhomyCrashStore.frontErrorCount()
            ])
        }
    }

    @objc func read(_ call: CAPPluginCall) {
        let name = call.getString("name") ?? ""
        DispatchQueue.global(qos: .utility).async {
            guard let text = IhomyCrashStore.read(name) else {
                call.reject("not-allowed")
                return
            }
            call.resolve(["name": name, "text": text])
        }
    }

    @objc func clear(_ call: CAPPluginCall) {
        DispatchQueue.global(qos: .utility).async {
            call.resolve(["removed": IhomyCrashStore.clear()])
        }
    }
}

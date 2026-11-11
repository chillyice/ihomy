//
//  ShareViewController.swift
//  IhomyShare
//
//  分享扩展:微信/相册/Safari/文件管理器 → 分享 → ihomy。
//  与 Android `ShareInboxPlugin` 落同一种 payload(`share-in/pending.json`):
//  文字/链接直接透传,文件在扩展里**立即**复制进 App Group(分享方给的临时 URL 在扩展退出后失效),
//  图片另生成 720px JPEG 缩略图;随后打开主 App 的 `ihomy://share` 深链,由 `/share` 页决定去向。
//

import UIKit
import UniformTypeIdentifiers
import ImageIO

final class ShareViewController: UIViewController {

    private static let maxImages = 9
    private static let maxImageBytes = 10 * 1024 * 1024
    private static let maxDocBytes = 500 * 1024 * 1024
    private static let maxTextChars = 20000
    private static let thumbSide = 720

    private let statusLabel = UILabel()
    private let receivedAt = Int(Date().timeIntervalSince1970 * 1000)

    private let lock = NSLock()
    private var files: [[String: Any]] = []
    private var textParts: [String] = []
    private var subject = ""
    private var firstMime = ""
    private var skippedImages = 0
    private var skippedDocs = 0
    private var fileIndex = 0

    private var finished = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        statusLabel.text = IhomyL10n.t("正在发送到 ihomy…", "Sending to ihomy…")
        statusLabel.font = .systemFont(ofSize: 15, weight: .medium)
        statusLabel.textColor = .secondaryLabel
        statusLabel.textAlignment = .center
        view.addSubview(statusLabel)
        NSLayoutConstraint.activate([
            statusLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            statusLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            statusLabel.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 24),
            statusLabel.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -24)
        ])
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        ingest()
    }

    // MARK: - 收内容

    private func ingest() {
        guard let items = extensionContext?.inputItems as? [NSExtensionItem], !items.isEmpty else {
            finish(empty: true)
            return
        }

        // 先清空上一份收件,再往里复制;清空与写入都在扩展里完成。
        IhomyShareStore.clear()

        let group = DispatchGroup()
        for item in items {
            if let title = item.attributedTitle?.string, !title.isEmpty {
                lock.lock()
                if subject.isEmpty { subject = title }
                lock.unlock()
            }
            if let content = item.attributedContentText?.string, !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                appendText(content)
            }
            for provider in item.attachments ?? [] {
                if provider.hasItemConformingToTypeIdentifier(UTType.image.identifier) {
                    let type = self.typeIdentifier(for: provider, conformingTo: .image)
                    loadFile(provider: provider, typeIdentifier: type, isImage: true, group: group)
                } else if provider.hasItemConformingToTypeIdentifier(UTType.movie.identifier) {
                    let type = self.typeIdentifier(for: provider, conformingTo: .movie)
                    loadFile(provider: provider, typeIdentifier: type, isImage: false, group: group)
                } else if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                    loadText(provider: provider, typeIdentifier: UTType.url.identifier, group: group)
                } else if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
                    loadText(provider: provider, typeIdentifier: UTType.plainText.identifier, group: group)
                } else if provider.hasItemConformingToTypeIdentifier(UTType.data.identifier) {
                    let type = self.typeIdentifier(for: provider, conformingTo: .data)
                    loadFile(provider: provider, typeIdentifier: type, isImage: false, group: group)
                }
            }
        }

        group.notify(queue: .main) { [weak self] in
            self?.persistAndOpen()
        }
    }

    private func appendText(_ raw: String) {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        lock.lock()
        if !textParts.contains(text) { textParts.append(text) }
        lock.unlock()
    }

    private func typeIdentifier(for provider: NSItemProvider, conformingTo type: UTType) -> String {
        for identifier in provider.registeredTypeIdentifiers {
            if let candidate = UTType(identifier), candidate.conforms(to: type) {
                return identifier
            }
        }
        return type.identifier
    }

    private func loadText(provider: NSItemProvider, typeIdentifier: String, group: DispatchGroup) {
        group.enter()
        provider.loadItem(forTypeIdentifier: typeIdentifier, options: nil) { [weak self] item, _ in
            defer { group.leave() }
            guard let self else { return }
            if let string = item as? String {
                self.appendText(string)
            } else if let url = item as? URL {
                self.appendText(url.absoluteString)
            } else if let data = item as? Data, let string = String(data: data, encoding: .utf8) {
                self.appendText(string)
            } else if let url = item as? NSURL, let string = url.absoluteString {
                self.appendText(string)
            }
        }
    }

    private func loadFile(provider: NSItemProvider, typeIdentifier: String, isImage: Bool, group: DispatchGroup) {
        if isImage {
            lock.lock()
            let overLimit = files.filter { ($0["kind"] as? String) == "image" }.count >= Self.maxImages
            lock.unlock()
            if overLimit {
                lock.lock(); skippedImages += 1; lock.unlock()
                return
            }
        }
        group.enter()
        provider.loadFileRepresentation(forTypeIdentifier: typeIdentifier) { [weak self] url, _ in
            defer { group.leave() }
            guard let self, let url else {
                self?.countSkipped(isImage: isImage)
                return
            }
            self.copyFile(from: url, provider: provider, typeIdentifier: typeIdentifier, isImage: isImage)
        }
    }

    /// 文件只在回调期间可读:立刻复制进 App Group,后续都读自己那份。
    private func copyFile(from source: URL, provider: NSItemProvider, typeIdentifier: String, isImage: Bool) {
        guard let dir = IhomyShareStore.directory else {
            countSkipped(isImage: isImage)
            return
        }
        let mime = UTType(typeIdentifier)?.preferredMIMEType
            ?? provider.registeredTypeIdentifiers.compactMap { UTType($0)?.preferredMIMEType }.first
            ?? "application/octet-stream"
        let name = displayName(for: source, provider: provider, mime: mime)
        let index = nextIndex()
        let dest = dir.appendingPathComponent("\(receivedAt)-\(index)-\(name)")

        do {
            try FileManager.default.copyItem(at: source, to: dest)
        } catch {
            // 个别 provider 只给 data 不给可复制文件:退回读取字节。
            guard let data = try? Data(contentsOf: source), !data.isEmpty else {
                countSkipped(isImage: isImage)
                return
            }
            do {
                try data.write(to: dest, options: .atomic)
            } catch {
                countSkipped(isImage: isImage)
                return
            }
        }

        let size = (try? FileManager.default.attributesOfItem(atPath: dest.path)[.size] as? NSNumber)?.intValue ?? 0
        let limit = isImage ? Self.maxImageBytes : Self.maxDocBytes
        guard size > 0, size <= limit else {
            try? FileManager.default.removeItem(at: dest)
            countSkipped(isImage: isImage)
            return
        }

        var file: [String: Any] = [
            "index": index,
            "kind": isImage ? "image" : "doc",
            "name": name,
            "mime": mime,
            "size": size,
            "path": dest.path
        ]
        if isImage, let thumb = thumbnailDataURL(dest) {
            file["thumb"] = thumb
        }
        lock.lock()
        files.append(file)
        if firstMime.isEmpty { firstMime = mime }
        lock.unlock()
    }

    private func nextIndex() -> Int {
        lock.lock()
        defer { lock.unlock() }
        let value = fileIndex
        fileIndex += 1
        return value
    }

    private func countSkipped(isImage: Bool) {
        lock.lock()
        if isImage { skippedImages += 1 } else { skippedDocs += 1 }
        lock.unlock()
    }

    /// 文件名参与路径拼接:去掉分隔符/控制字符,没有名字就按扩展名兜底。
    private func displayName(for source: URL, provider: NSItemProvider, mime: String) -> String {
        var name = provider.suggestedName ?? source.lastPathComponent
        if name.isEmpty || name == "tmp" || name.hasSuffix(".tmp") {
            let ext = UTType(tag: mime, tagClass: .mimeType, conformingTo: nil)?.preferredFilenameExtension ?? ""
            name = "share-\(nextIndexFallback())" + (ext.isEmpty ? "" : ".\(ext)")
        }
        let cleaned = name.replacingOccurrences(of: "[\\\\/:*?\"<>|\\r\\n\\t]",
                                                with: "_",
                                                options: .regularExpression)
        return cleaned.isEmpty ? "share" : cleaned
    }

    private func nextIndexFallback() -> Int {
        lock.lock()
        defer { lock.unlock() }
        return fileIndex
    }

    /// 720px JPEG data URL;CIImage/CGImageSource 直接降采样,不会把原图整张读进内存。
    private func thumbnailDataURL(_ url: URL) -> String? {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: Self.thumbSide
        ]
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            return nil
        }
        guard let data = UIImage(cgImage: cgImage).jpegData(compressionQuality: 0.7) else { return nil }
        return "data:image/jpeg;base64," + data.base64EncodedString()
    }

    // MARK: - 落盘并打开主 App

    private func persistAndOpen() {
        lock.lock()
        let collected = files
        let text = clip(textParts.joined(separator: "\n"))
        let subjectText = subject
        let mime = firstMime
        let images = skippedImages
        let docs = skippedDocs
        lock.unlock()

        guard !collected.isEmpty || !text.isEmpty else {
            finish(empty: true)
            return
        }

        let payload: [String: Any] = [
            "mode": "share",
            "text": text,
            "subject": subjectText,
            "mime": mime,
            "receivedAt": receivedAt,
            "files": collected,
            "skipped": images + docs,
            "skippedImages": images,
            "skippedDocs": docs
        ]
        guard IhomyShareStore.save(payload) else {
            statusLabel.text = IhomyL10n.t("保存分享内容失败", "Failed to save the shared content")
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { [weak self] in
                self?.finish(empty: true)
            }
            return
        }
        openHostApp()
    }

    private func clip(_ text: String) -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.count <= Self.maxTextChars { return trimmed }
        return String(trimmed.prefix(Self.maxTextChars))
    }

    /// 分享扩展不能直接 `UIApplication.shared.open`;沿响应链找 `openURL:` 是通用做法,
    /// 找不到时再退 `extensionContext.open`。
    private func openHostApp() {
        let target = URL(string: "ihomy://share")!
        var responder: UIResponder? = self
        let selector = NSSelectorFromString("openURL:")
        while let current = responder {
            if current.responds(to: selector) {
                current.perform(selector, with: target)
                finish(empty: false)
                return
            }
            responder = current.next
        }
        extensionContext?.open(target) { [weak self] _ in
            self?.finish(empty: false)
        }
    }

    private func finish(empty: Bool) {
        guard !finished else { return }
        finished = true
        if empty, statusLabel.superview != nil {
            statusLabel.text = IhomyL10n.t("没有可分享的内容", "Nothing to share")
        }
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }
}

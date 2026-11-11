//
//  IhomyShared.swift
//  ihomy
//
//  App 与 WidgetKit 扩展共用的纯逻辑:App Group 偏好、接口客户端、卡片数据、
//  本地通知与后台刷新。只依赖 Foundation / UserNotifications,
//  不碰 UIApplication —— 同一份源码会编进扩展 target(扩展 target 定义 IHOMY_EXTENSION)。
//

import Foundation
import UserNotifications

#if canImport(UIKit)
import UIKit
#endif

#if canImport(WidgetKit)
import WidgetKit
#endif

#if !IHOMY_EXTENSION
import BackgroundTasks
#endif

// MARK: - App Group

/// 主 App 与两个扩展共享的 App Group。键名与 Android `WidgetPrefs` 完全一致,
/// 换端时卡片/通知的偏好不需要迁移。
enum IhomyAppGroup {
    static let identifier = "group.top.ihomy.app"

    static var defaults: UserDefaults {
        UserDefaults(suiteName: identifier) ?? UserDefaults.standard
    }

    /// 共享容器:相册备份缓存、分享收件、诊断文件都放这里。
    /// 没有授权(或在模拟器上签名不完整)时回落到 App 自己的 caches,功能退化但不崩。
    static var containerURL: URL? {
        if let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier) {
            return url
        }
        return FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
    }

    static func directory(named name: String) -> URL? {
        guard let base = containerURL else { return nil }
        let dir = base.appendingPathComponent(name, isDirectory: true)
        if !FileManager.default.fileExists(atPath: dir.path) {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        }
        return dir
    }
}

// MARK: - 本地化

/// 原生扩展没有 vue-i18n,文案跟随系统语言;只有中英两套,和 Android strings / values-en 同口径。
enum IhomyL10n {
    static var isChinese: Bool {
        let preferred = Locale.preferredLanguages.first ?? "en"
        return preferred.hasPrefix("zh")
    }

    static func t(_ zh: String, _ en: String) -> String {
        isChinese ? zh : en
    }

    static let minute = t("分钟", "min")

    static func weekday(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: isChinese ? "zh_CN" : "en_US")
        formatter.dateFormat = "EEEE"
        return formatter.string(from: date)
    }

    static func monthDay(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: isChinese ? "zh_CN" : "en_US")
        formatter.dateFormat = isChinese ? "M月d日" : "MMM d"
        return formatter.string(from: date)
    }

    static func hourMinute(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: isChinese ? "zh_CN" : "en_US")
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }

    static func todayTitle(_ date: Date = Date()) -> String {
        if isChinese {
            return "今天 · \(monthDay(date)) \(weekday(date))"
        }
        return "Today · \(monthDay(date)) \(weekday(date))"
    }
}

// MARK: - 偏好

/// 键名是跨端契约(Android `WidgetPrefs`),改名会让老用户的卡片和提醒全部失效。
enum IhomyPrefs {
    static let tokenKey = "widget_token"
    static let cachePrefix = "cache_"
    static let todayInfoKey = "cache_today_info"
    static let updatedAtPrefix = "updated_at_"
    static let reminderSyncAtKey = "reminder_sync_at"
    static let lastNudgeDateKey = "last_nudge_date"
    static let lastUnreadNoticeKey = "last_unread_notice"
    static let lastUnreadChatKey = "last_unread_chat"
    static let unreadNotifyKey = "unread_notify_enabled"
    static let dailyWeatherKey = "daily_weather_enabled"
    static let dailyWeatherMinuteKey = "daily_weather_minute"
    static let lastWeatherDateKey = "last_weather_date"
    static let autoUpdateKey = "auto_update_enabled"
    static let autoUpdateNotifiedKey = "auto_update_notified"

    static let defaultDailyWeatherMinute = 7 * 60 + 30

    private static var defaults: UserDefaults { IhomyAppGroup.defaults }

    static var token: String {
        get { defaults.string(forKey: tokenKey) ?? "" }
        set { defaults.set(newValue, forKey: tokenKey) }
    }

    static func clearToken() {
        defaults.removeObject(forKey: tokenKey)
    }

    static func cache(_ widgetId: String) -> String {
        defaults.string(forKey: cachePrefix + widgetId) ?? ""
    }

    static func putCache(_ widgetId: String, _ json: String) {
        defaults.set(json, forKey: cachePrefix + widgetId)
        defaults.set(Date().timeIntervalSince1970 * 1000, forKey: updatedAtPrefix + widgetId)
    }

    static func updatedAt(_ widgetId: String) -> Double {
        defaults.double(forKey: updatedAtPrefix + widgetId)
    }

    static var todayInfo: String {
        get { defaults.string(forKey: todayInfoKey) ?? "" }
        set { defaults.set(newValue, forKey: todayInfoKey) }
    }

    static var reminderSyncAt: Double {
        get { defaults.double(forKey: reminderSyncAtKey) }
        set { defaults.set(newValue, forKey: reminderSyncAtKey) }
    }

    static var lastNudgeDate: String {
        get { defaults.string(forKey: lastNudgeDateKey) ?? "" }
        set { defaults.set(newValue, forKey: lastNudgeDateKey) }
    }

    static var lastUnreadNotice: Double {
        get { defaults.double(forKey: lastUnreadNoticeKey) }
        set { defaults.set(newValue, forKey: lastUnreadNoticeKey) }
    }

    static var lastUnreadChat: Double {
        get { defaults.double(forKey: lastUnreadChatKey) }
        set { defaults.set(newValue, forKey: lastUnreadChatKey) }
    }

    static var unreadNotifyEnabled: Bool {
        get {
            if defaults.object(forKey: unreadNotifyKey) == nil { return true }
            return defaults.bool(forKey: unreadNotifyKey)
        }
        set { defaults.set(newValue, forKey: unreadNotifyKey) }
    }

    static var dailyWeatherEnabled: Bool {
        get {
            if defaults.object(forKey: dailyWeatherKey) == nil { return true }
            return defaults.bool(forKey: dailyWeatherKey)
        }
        set { defaults.set(newValue, forKey: dailyWeatherKey) }
    }

    static var dailyWeatherMinute: Int {
        get {
            let value = defaults.object(forKey: dailyWeatherMinuteKey) == nil
                ? defaultDailyWeatherMinute
                : defaults.integer(forKey: dailyWeatherMinuteKey)
            return (value >= 0 && value < 24 * 60) ? value : defaultDailyWeatherMinute
        }
        set { defaults.set(newValue, forKey: dailyWeatherMinuteKey) }
    }

    static var lastWeatherDate: String {
        get { defaults.string(forKey: lastWeatherDateKey) ?? "" }
        set { defaults.set(newValue, forKey: lastWeatherDateKey) }
    }

    static var autoUpdateEnabled: Bool {
        get {
            if defaults.object(forKey: autoUpdateKey) == nil { return false }
            return defaults.bool(forKey: autoUpdateKey)
        }
        set { defaults.set(newValue, forKey: autoUpdateKey) }
    }

    static var autoUpdateNotified: String {
        get { defaults.string(forKey: autoUpdateNotifiedKey) ?? "" }
        set { defaults.set(newValue, forKey: autoUpdateNotifiedKey) }
    }
}

// MARK: - 日期工具

enum IhomyDate {
    static let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    static let monthFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM"
        return formatter
    }()

    static func string(_ date: Date) -> String {
        formatter.string(from: date)
    }

    static func date(_ text: String) -> Date? {
        formatter.date(from: text)
    }

    static func daysBetween(from: Date, to: Date) -> Int {
        let calendar = Calendar.current
        let a = calendar.startOfDay(for: from)
        let b = calendar.startOfDay(for: to)
        return calendar.dateComponents([.day], from: a, to: b).day ?? 0
    }

    /// "07:30" → 450;非法时间按 09:00 处理(与 reminders.js 的 minutesOf 同口径)
    static func minutes(_ time: String?) -> Int {
        guard let time, time.count == 5 else { return 9 * 60 }
        let parts = time.split(separator: ":")
        guard parts.count == 2, let hour = Int(parts[0]), let minute = Int(parts[1]) else { return 9 * 60 }
        return hour * 60 + minute
    }
}

// MARK: - 接口

struct IhomyAPIError: LocalizedError {
    let code: Int
    let message: String
    var errorDescription: String? { message }
}

/// 后台同步用的最小客户端:只认 `{code,message,data}`,Bearer 鉴权,
/// 与前端 `api/request.js` 的接口口径一致。
enum IhomyAPI {
    static let defaultBase = "https://ihomy.top/api"

    static var base: String {
        if let value = Bundle.main.object(forInfoDictionaryKey: "IHOMYApiBase") as? String, !value.isEmpty {
            return value
        }
        return defaultBase
    }

    static func get(_ path: String, token: String) async throws -> Any {
        try await request(path, method: "GET", token: token, body: nil)
    }

    static func post(_ path: String, token: String) async throws {
        _ = try await request(path, method: "POST", token: token, body: Data())
    }

    private static func request(_ path: String, method: String, token: String, body: Data?) async throws -> Any {
        guard let url = URL(string: base + path) else {
            throw IhomyAPIError(code: -1, message: "bad url")
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 20
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            request.httpBody = body.count > 0 ? body : Data("{}".utf8)
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw IhomyAPIError(code: -1, message: "no response")
        }
        guard (200..<300).contains(http.statusCode) else {
            throw IhomyAPIError(code: http.statusCode, message: "HTTP \(http.statusCode)")
        }
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw IhomyAPIError(code: -1, message: "bad json")
        }
        let code = (root["code"] as? NSNumber)?.intValue ?? -1
        guard code == 0 else {
            throw IhomyAPIError(code: code, message: (root["message"] as? String) ?? "bad code")
        }
        return root["data"] ?? NSNull()
    }
}

// MARK: - 数据模型

struct IhomyOccurrence {
    var key = ""
    var kind = ""
    var title = ""
    var date = ""
    var time: String?
    var days: Int?
    var done = false

    init() {}

    init?(json: [String: Any]) {
        key = json["key"] as? String ?? ""
        guard !key.isEmpty else { return nil }
        kind = json["kind"] as? String ?? ""
        title = json["title"] as? String ?? ""
        date = json["date"] as? String ?? ""
        if let value = json["time"] as? String, !value.isEmpty {
            time = value
        }
        if let value = json["days"] as? NSNumber {
            days = value.intValue
        }
        done = (json["done"] as? Bool) ?? ((json["done"] as? NSNumber)?.boolValue ?? false)
    }

    var jsonObject: [String: Any] {
        var out: [String: Any] = [
            "key": key,
            "kind": kind,
            "title": title,
            "date": date,
            "done": done
        ]
        if let time { out["time"] = time }
        if let days { out["days"] = days }
        return out
    }

    /// 卡片上的时刻:全天事项统一显示「全天」
    var timeText: String {
        guard let time, !time.isEmpty else { return IhomyL10n.t("全天", "All day") }
        return time
    }
}

struct IhomyTodayInfo {
    var date = ""
    var lunar = ""
    var mark = ""
    var weather = ""

    init() {}

    init(json: [String: Any]) {
        date = json["date"] as? String ?? ""
        lunar = json["lunar"] as? String ?? ""
        mark = json["mark"] as? String ?? ""
        weather = json["weather"] as? String ?? ""
    }

    var jsonObject: [String: Any] {
        ["date": date, "lunar": lunar, "mark": mark, "weather": weather]
    }
}

struct IhomyBookSummary {
    var month = ""
    var income: Double = 0
    var expense: Double = 0
    var balance: Double = 0
    var count = 0

    init() {}

    init(json: [String: Any]) {
        month = json["month"] as? String ?? ""
        income = (json["income"] as? NSNumber)?.doubleValue ?? 0
        expense = (json["expense"] as? NSNumber)?.doubleValue ?? 0
        balance = (json["balance"] as? NSNumber)?.doubleValue ?? 0
        count = (json["count"] as? NSNumber)?.intValue ?? 0
    }

    var jsonObject: [String: Any] {
        ["month": month, "income": income, "expense": expense, "balance": balance, "count": count]
    }

    var money: (String, String, String) {
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return (
            formatter.string(from: NSNumber(value: expense)) ?? "0.00",
            formatter.string(from: NSNumber(value: income)) ?? "0.00",
            formatter.string(from: NSNumber(value: balance)) ?? "0.00"
        )
    }
}

struct IhomyUnread {
    var notice = 0
    var chat = 0
    var total: Int { notice + chat }
}

// MARK: - 卡片数据

enum IhomyWidgetStore {
    private static let windowDays = 30

    static func fetchOccurrences(token: String) async throws -> [IhomyOccurrence] {
        let from = Date()
        let to = Calendar.current.date(byAdding: .day, value: windowDays - 1, to: from) ?? from
        let path = "/schedule/occurrences?from=\(IhomyDate.string(from))&to=\(IhomyDate.string(to))"
        guard let data = try await IhomyAPI.get(path, token: token) as? [String: Any],
              let items = data["items"] as? [[String: Any]] else {
            return []
        }
        return items.compactMap { IhomyOccurrence(json: $0) }
    }

    static func fetchTodayInfo(token: String) async throws -> IhomyTodayInfo {
        let today = Date()
        var info = IhomyTodayInfo()
        info.date = IhomyDate.string(today)
        if let data = try? await IhomyAPI.get("/public/calendar?month=\(IhomyDate.monthFormatter.string(from: today))", token: token) as? [String: Any],
           let days = data["days"] as? [[String: Any]] {
            for day in days where (day["date"] as? String) == info.date {
                let lunarMonth = day["lunarMonthText"] as? String ?? ""
                let lunarDay = day["lunarText"] as? String ?? ""
                info.lunar = lunarMonth + lunarDay
                let term = day["term"] as? String ?? ""
                let festivals = day["festivals"] as? [String] ?? []
                info.mark = !term.isEmpty ? term : (festivals.first ?? "")
                break
            }
        }
        if let data = try? await IhomyAPI.get("/public/weather", token: token) as? [String: Any] {
            if let temp = data["temp"], let text = data["text"] as? String {
                info.weather = "\(temp)° \(text)"
            }
        }
        return info
    }

    static func fetchBookSummary(token: String) async throws -> IhomyBookSummary {
        guard let data = try await IhomyAPI.get("/book/summary", token: token) as? [String: Any] else {
            return IhomyBookSummary()
        }
        return IhomyBookSummary(json: data)
    }

    static func fetchUnread(token: String) async throws -> IhomyUnread {
        var unread = IhomyUnread()
        if let value = try await IhomyAPI.get("/notification/unread-count", token: token) as? NSNumber {
            unread.notice = value.intValue
        } else if let value = try await IhomyAPI.get("/notification/unread-count", token: token) as? Int {
            unread.notice = value
        }
        if let data = try await IhomyAPI.get("/chat/unread", token: token) as? [String: Any],
           let value = data["unread"] as? NSNumber {
            unread.chat = value.intValue
        }
        return unread
    }

    static func toggleReminderDone(id: Int, date: String, token: String) async throws {
        try await IhomyAPI.post("/reminder/\(id)/toggle-done?date=\(date)", token: token)
    }

    // MARK: 缓存读写

    static func cache(items: [IhomyOccurrence], for widgetId: String) {
        let payload: [String: Any] = ["items": items.map { $0.jsonObject }]
        guard let data = try? JSONSerialization.data(withJSONObject: payload),
              let text = String(data: data, encoding: .utf8) else { return }
        IhomyPrefs.putCache(widgetId, text)
    }

    static func readCache(_ widgetId: String) -> [IhomyOccurrence] {
        guard let text = IhomyPrefs.cache(widgetId).data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: text) as? [String: Any],
              let items = root["items"] as? [[String: Any]] else {
            return []
        }
        return items.compactMap { IhomyOccurrence(json: $0) }
    }

    static func cacheTodayInfo(_ info: IhomyTodayInfo) {
        guard let data = try? JSONSerialization.data(withJSONObject: info.jsonObject),
              let text = String(data: data, encoding: .utf8) else { return }
        IhomyPrefs.todayInfo = text
    }

    static func readTodayInfo() -> IhomyTodayInfo {
        guard let text = IhomyPrefs.todayInfo.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: text) as? [String: Any] else {
            return IhomyTodayInfo()
        }
        return IhomyTodayInfo(json: root)
    }

    static func cacheBook(_ summary: IhomyBookSummary) {
        guard let data = try? JSONSerialization.data(withJSONObject: summary.jsonObject),
              let text = String(data: data, encoding: .utf8) else { return }
        IhomyPrefs.putCache("book", text)
    }

    static func readBook() -> IhomyBookSummary {
        guard let text = IhomyPrefs.cache("book").data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: text) as? [String: Any] else {
            return IhomyBookSummary()
        }
        return IhomyBookSummary(json: root)
    }

    /// App 在前台改过提醒/账目后由 JS 调 `refreshWidgets`,
    /// 拉一轮数据并让卡片立刻重绘(不指望下一个 30 分钟)。
    static func syncAll() async {
        let token = IhomyPrefs.token
        guard !token.isEmpty else { return }
        if let items = try? await fetchOccurrences(token: token) {
            cache(items: items, for: "today")
            cache(items: items, for: "reminder")
            cache(items: items, for: "countdown")
        }
        if let info = try? await fetchTodayInfo(token: token) {
            cacheTodayInfo(info)
        }
        if let book = try? await fetchBookSummary(token: token) {
            cacheBook(book)
        }
        reloadWidgets()
    }

    static func reloadWidgets() {
        #if canImport(WidgetKit)
        if #available(iOS 14.0, *) {
            WidgetCenter.shared.reloadAllTimelines()
        }
        #endif
    }
}

// MARK: - 天气

enum IhomyWeatherKind {
    case sun, partly, cloud, rain, thunder, snow, fog

    static func of(_ iconCode: String) -> IhomyWeatherKind {
        guard let code = Int(iconCode.trimmingCharacters(in: .whitespaces)) else { return .cloud }
        if code < 100 { return .cloud }
        if code < 200 {
            if code == 100 || code == 150 { return .sun }
            if code <= 103 || (code >= 151 && code <= 153) { return .partly }
            return .cloud
        }
        if code < 300 { return .fog }
        if code < 400 {
            if code == 302 || code == 303 { return .thunder }
            return .rain
        }
        if code < 500 { return .snow }
        if code < 600 { return .fog }
        return .cloud
    }
}

struct IhomyWeatherSnapshot {
    var city = ""
    var nowText = ""
    var dayText = ""
    var iconDay = ""
    var nowTemp: Int?
    var feelsLike: Int?
    var tempMax: Int?
    var tempMin: Int?
    var pop: Int?
    var windDir = ""
    var windScale = ""
    var aqiCategory = ""
    var aqi: Int?
    var uvIndex: Int?
    var warningType = ""
    var warningLevel = ""
}

enum IhomyWeatherAdvice: String {
    case warning, thunder, snow, rain, fog, airBad, hot, cold, bigDiff, uv, windy, nice

    static func of(_ snapshot: IhomyWeatherSnapshot) -> IhomyWeatherAdvice {
        if !snapshot.warningType.isEmpty { return .warning }
        switch IhomyWeatherKind.of(snapshot.iconDay) {
        case .thunder: return .thunder
        case .snow: return .snow
        case .rain: return .rain
        case .fog: return .fog
        default: break
        }
        if let pop = snapshot.pop, pop >= 50 { return .rain }
        if (snapshot.aqi ?? 0) >= 150 || isPolluted(snapshot.aqiCategory) { return .airBad }
        if let max = snapshot.tempMax, max >= 33 { return .hot }
        if let min = snapshot.tempMin, min <= 0 { return .cold }
        if let max = snapshot.tempMax, let min = snapshot.tempMin, max - min >= 10 { return .bigDiff }
        if let uv = snapshot.uvIndex, uv >= 7 { return .uv }
        if maxWind(snapshot.windScale) >= 5 { return .windy }
        return .nice
    }

    private static func isPolluted(_ category: String) -> Bool {
        guard !category.isEmpty else { return false }
        return category.contains("污染") || category.contains("严重")
            || category.contains("中度") || category.contains("重度")
    }

    private static func maxWind(_ scale: String) -> Int {
        var result = -1
        let parts = scale.components(separatedBy: CharacterSet.decimalDigits.inverted)
        for part in parts {
            if let value = Int(part) { result = max(result, value) }
        }
        return result
    }

    var text: String {
        switch self {
        case .warning: return IhomyL10n.t("有天气预警,出门多留意", "Weather alert — take care outside")
        case .thunder: return IhomyL10n.t("可能有雷雨,尽量别在户外久留", "Thunderstorms possible, stay indoors when you can")
        case .snow: return IhomyL10n.t("有雪,出门慢一点,注意路滑", "Snow today, take it slow and watch your step")
        case .rain: return IhomyL10n.t("可能有雨,出门带把伞", "Rain possible, take an umbrella")
        case .fog: return IhomyL10n.t("能见度不高,出门留意路况", "Low visibility, mind the roads")
        case .airBad: return IhomyL10n.t("空气不太好,外出戴口罩", "Air quality is poor, wear a mask outside")
        case .hot: return IhomyL10n.t("天气偏热,记得多喝水", "Hot day, keep drinking water")
        case .cold: return IhomyL10n.t("气温偏低,注意保暖", "Chilly, dress warm")
        case .bigDiff: return IhomyL10n.t("早晚温差大,出门带件外套", "Big day-night swing, bring a jacket")
        case .uv: return IhomyL10n.t("紫外线偏强,记得防晒", "Strong UV, remember sunscreen")
        case .windy: return IhomyL10n.t("风有点大,注意防风", "Quite windy, hold on to your hat")
        case .nice: return IhomyL10n.t("天气不错,适合出门走走", "Nice weather, good day for a walk")
        }
    }
}

enum IhomyWeather {
    /// 返回 nil = 服务器没配天气(不是错误,静默跳过)
    static func fetch(token: String) async throws -> IhomyWeatherSnapshot? {
        guard let data = try await IhomyAPI.get("/public/weather/detail", token: token) as? [String: Any] else {
            return nil
        }
        var snapshot = IhomyWeatherSnapshot()
        if let now = data["now"] as? [String: Any] {
            snapshot.city = now["city"] as? String ?? ""
            snapshot.nowText = now["text"] as? String ?? ""
            snapshot.iconDay = now["iconCode"] as? String ?? ""
            snapshot.nowTemp = intValue(now["temp"])
        }
        if let nowFull = data["nowFull"] as? [String: Any] {
            snapshot.feelsLike = intValue(nowFull["feelsLike"])
            if snapshot.nowText.isEmpty { snapshot.nowText = nowFull["text"] as? String ?? "" }
            if snapshot.iconDay.isEmpty { snapshot.iconDay = nowFull["icon"] as? String ?? "" }
            snapshot.windDir = nowFull["windDir"] as? String ?? ""
            snapshot.windScale = nowFull["windScale"] as? String ?? ""
        }
        if let days = data["daily"] as? [[String: Any]], let day = days.first {
            snapshot.dayText = day["textDay"] as? String ?? ""
            if let icon = day["iconDay"] as? String, !icon.isEmpty { snapshot.iconDay = icon }
            snapshot.tempMax = intValue(day["tempMax"])
            snapshot.tempMin = intValue(day["tempMin"])
            snapshot.pop = intValue(day["pop"])
            if let wind = day["windDirDay"] as? String, !wind.isEmpty { snapshot.windDir = wind }
            if let scale = day["windScaleDay"] as? String, !scale.isEmpty { snapshot.windScale = scale }
            snapshot.uvIndex = intValue(day["uvIndex"])
        }
        if let air = data["air"] as? [String: Any] {
            snapshot.aqiCategory = air["category"] as? String ?? ""
            snapshot.aqi = intValue(air["aqi"])
        }
        if let warnings = data["warning"] as? [[String: Any]], let warning = warnings.first {
            snapshot.warningType = warning["typeName"] as? String ?? ""
            snapshot.warningLevel = warning["level"] as? String ?? ""
        }
        return snapshot
    }

    private static func intValue(_ raw: Any?) -> Int? {
        if let number = raw as? NSNumber { return Int(number.doubleValue.rounded()) }
        if let text = raw as? String, let value = Double(text.trimmingCharacters(in: .whitespaces)) {
            return Int(value.rounded())
        }
        return nil
    }

    static func title(_ snapshot: IhomyWeatherSnapshot) -> String {
        let condition = !snapshot.dayText.isEmpty ? snapshot.dayText : snapshot.nowText
        if !condition.isEmpty, let max = snapshot.tempMax, let min = snapshot.tempMin {
            return "\(condition) · \(min)°~\(max)°"
        }
        if !condition.isEmpty, let now = snapshot.nowTemp {
            return "\(condition) · \(now)°"
        }
        return condition.isEmpty ? IhomyL10n.t("今日天气", "Today's weather") : condition
    }

    static func body(_ snapshot: IhomyWeatherSnapshot) -> String {
        var lines: [String] = [IhomyWeatherAdvice.of(snapshot).text]
        if let now = snapshot.nowTemp {
            lines.append(snapshot.feelsLike != nil
                ? IhomyL10n.t("现在 \(now)°,体感 \(snapshot.feelsLike!)°", "Now \(now)°, feels like \(snapshot.feelsLike!)°")
                : IhomyL10n.t("现在 \(now)°", "Now \(now)°"))
        }
        if let max = snapshot.tempMax, let min = snapshot.tempMin {
            lines.append(snapshot.pop != nil
                ? IhomyL10n.t("最高 \(max)° · 最低 \(min)° · 降水 \(snapshot.pop!)%", "High \(max)° · Low \(min)° · Rain \(snapshot.pop!)%")
                : IhomyL10n.t("最高 \(max)° · 最低 \(min)°", "High \(max)° · Low \(min)°"))
        }
        var extra: [String] = []
        if !snapshot.windDir.isEmpty, !snapshot.windScale.isEmpty {
            extra.append(IhomyL10n.t("\(snapshot.windDir) \(snapshot.windScale) 级", "Wind \(snapshot.windDir) \(snapshot.windScale)"))
        }
        if !snapshot.aqiCategory.isEmpty {
            extra.append(IhomyL10n.t("空气\(snapshot.aqiCategory)", "Air \(snapshot.aqiCategory)"))
        }
        if !extra.isEmpty { lines.append(extra.joined(separator: " · ")) }
        return lines.joined(separator: "\n")
    }
}

// MARK: - 通知

enum IhomyNotify {
    static let unreadIdentifier = "ihomy-unread"
    static let staleIdentifier = "ihomy-stale"
    static let weatherPrefix = "ihomy-weather-"
    static let weatherIdentifier = "ihomy-weather-daily"

    static func authorizationStatus() async -> UNAuthorizationStatus {
        await withCheckedContinuation { continuation in
            UNUserNotificationCenter.current().getNotificationSettings { settings in
                continuation.resume(returning: settings.authorizationStatus)
            }
        }
    }

    static func ensureAuthorization() async -> Bool {
        let status = await authorizationStatus()
        if status == .authorized || status == .provisional || status == .ephemeral { return true }
        if status == .denied { return false }
        return await withCheckedContinuation { continuation in
            UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
                continuation.resume(returning: granted)
            }
        }
    }

    static func add(_ request: UNNotificationRequest, replace: Bool = true) {
        let center = UNUserNotificationCenter.current()
        if replace {
            center.removePendingNotificationRequests(withIdentifiers: [request.identifier])
        }
        center.add(request) { error in
            if let error { NSLog("[ihomy] notify add failed: \(error)") }
        }
    }

    static func remove(_ identifiers: [String]) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: identifiers)
        center.removeDeliveredNotifications(withIdentifiers: identifiers)
    }

    private static func content(title: String, body: String, link: String?) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        if let link {
            content.userInfo = ["cap_extra": ["link": link]]
        }
        return content
    }

    static func postUnread(chat: Int, notice: Int) {
        guard chat + notice > 0 else {
            remove([unreadIdentifier])
            return
        }
        let content = content(
            title: IhomyL10n.t("家里有新消息", "New messages at home"),
            body: IhomyL10n.t("聊天 \(chat) 条 · 通知 \(notice) 条", "Chat \(chat) · Notices \(notice)"),
            link: "/notification"
        )
        content.categoryIdentifier = "IHOMY_UNREAD"
        add(UNNotificationRequest(identifier: unreadIdentifier, content: content, trigger: nil))
    }

    static func cancelUnread() {
        remove([unreadIdentifier])
    }

    static func postStale(count: Int) {
        let content = content(
            title: IhomyL10n.t("最近的日程等你确认", "Your schedule needs a check"),
            body: IhomyL10n.t("有 \(count) 条日程,点一下打开 ihomy 校准提醒", "\(count) upcoming item(s). Tap to open ihomy and recalibrate reminders."),
            link: "/plan?tab=today"
        )
        content.categoryIdentifier = "IHOMY_STALE"
        add(UNNotificationRequest(identifier: staleIdentifier, content: content, trigger: nil))
    }

    /// 每日天气用重复日历触发器:即使 App 长期没被系统唤醒,到点也会安静显示一条。
    /// 内容取最近一次后台同步抓到的文案,后台任务再跑一次时会覆盖成新的一天。
    static func scheduleDailyWeather(title: String, body: String) {
        remove([weatherIdentifier])
        guard IhomyPrefs.dailyWeatherEnabled else { return }
        let minute = IhomyPrefs.dailyWeatherMinute
        var components = DateComponents()
        components.hour = minute / 60
        components.minute = minute % 60
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
        let content = content(title: title, body: body, link: "/weather")
        content.categoryIdentifier = "IHOMY_WEATHER"
        content.interruptionLevel = .passive
        content.sound = nil
        add(UNNotificationRequest(identifier: weatherIdentifier, content: content, trigger: trigger), replace: false)
    }

    static func cancelDailyWeather() {
        remove([weatherIdentifier])
    }

    static func previewWeather(title: String, body: String) {
        let content = content(title: title, body: body, link: "/weather")
        content.categoryIdentifier = "IHOMY_WEATHER"
        content.interruptionLevel = .active
        add(UNNotificationRequest(identifier: weatherPrefix + "preview", content: content, trigger: nil))
    }
}

// MARK: - 后台刷新

#if !IHOMY_EXTENSION
enum IhomyBackground {
    static let refreshTaskIdentifier = "top.ihomy.app.refresh"

    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: refreshTaskIdentifier, using: nil) { task in
            guard let refresh = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            handle(refresh)
        }
    }

    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: refreshTaskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 30 * 60)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            NSLog("[ihomy] bg schedule failed: \(error)")
        }
    }

    private static func handle(_ task: BGAppRefreshTask) {
        // 先排下一次,避免任务被系统终止后整个链路断掉
        schedule()
        let work = Task {
            await runBackgroundSync()
            task.setTaskCompleted(success: true)
        }
        task.expirationHandler = {
            work.cancel()
        }
    }

    /// 后台一轮:卡片 + 未读 + 每日天气 + 日程校准提示。任何一步失败都继续下一步。
    static func runBackgroundSync() async {
        guard !IhomyPrefs.token.isEmpty else { return }
        await IhomyWidgetStore.syncAll()
        await syncUnread()
        await syncDailyWeather()
        await syncStaleCheck()
        IhomyWidgetStore.reloadWidgets()
    }

    private static func syncUnread() async {
        guard IhomyPrefs.unreadNotifyEnabled else { return }
        guard let unread = try? await IhomyWidgetStore.fetchUnread(token: IhomyPrefs.token) else { return }
        let allowed = await IhomyNotify.authorizationStatus() == .authorized
        if unread.total <= 0 {
            IhomyNotify.cancelUnread()
        } else if allowed, !quietHours(),
                  Double(unread.notice) > IhomyPrefs.lastUnreadNotice || Double(unread.chat) > IhomyPrefs.lastUnreadChat {
            IhomyNotify.postUnread(chat: unread.chat, notice: unread.notice)
        }
        IhomyPrefs.lastUnreadNotice = Double(unread.notice)
        IhomyPrefs.lastUnreadChat = Double(unread.chat)
    }

    private static func syncDailyWeather() async {
        guard IhomyPrefs.dailyWeatherEnabled else { return }
        let today = IhomyDate.string(Date())
        let now = Date()
        let calendar = Calendar.current
        let nowMinute = calendar.component(.hour, from: now) * 60 + calendar.component(.minute, from: now)
        let target = IhomyPrefs.dailyWeatherMinute
        let diff = abs(nowMinute - target)
        let distance = min(diff, 24 * 60 - diff)
        guard distance <= 180 else { return }
        if today == IhomyPrefs.lastWeatherDate {
            // 已经发过:仍刷新一次重复通知的文案,保证明天到点内容是新的
            if let snapshot = try? await IhomyWeather.fetch(token: IhomyPrefs.token), let snapshot {
                IhomyNotify.scheduleDailyWeather(title: IhomyWeather.title(snapshot), body: IhomyWeather.body(snapshot))
            }
            return
        }
        guard let snapshot = try? await IhomyWeather.fetch(token: IhomyPrefs.token), let snapshot else { return }
        let allowed = await IhomyNotify.ensureAuthorization()
        guard allowed else { return }
        IhomyNotify.scheduleDailyWeather(title: IhomyWeather.title(snapshot), body: IhomyWeather.body(snapshot))
        if distance <= 30 {
            // 到点这个窗口里直接发一条,重复触发器负责后面的日子
            IhomyNotify.previewWeather(title: IhomyWeather.title(snapshot), body: IhomyWeather.body(snapshot))
        }
        IhomyPrefs.lastWeatherDate = today
    }

    private static func syncStaleCheck() async {
        let now = Date().timeIntervalSince1970 * 1000
        let lastSync = IhomyPrefs.reminderSyncAt
        let staleWindow = 7.0 * 24 * 60 * 60 * 1000
        if lastSync > 0, now - lastSync <= staleWindow { return }
        let today = IhomyDate.string(Date())
        if today == IhomyPrefs.lastNudgeDate { return }
        guard let items = try? await IhomyWidgetStore.fetchOccurrences(token: IhomyPrefs.token) else { return }
        let limit = Calendar.current.date(byAdding: .day, value: 2, to: Date()) ?? Date()
        let count = items.filter { item in
            guard !item.done, let date = IhomyDate.date(item.date) else { return false }
            return date >= Calendar.current.startOfDay(for: Date()) && date <= limit
        }.count
        guard count > 0 else { return }
        guard await IhomyNotify.ensureAuthorization() else { return }
        IhomyNotify.postStale(count: count)
        IhomyPrefs.lastNudgeDate = today
    }

    private static func quietHours() -> Bool {
        let hour = Calendar.current.component(.hour, from: Date())
        return hour >= 23 || hour < 6
    }
}
#endif

// MARK: - 分享收件(扩展写入,主 App 读取)

enum IhomyShareStore {
    static var directory: URL? {
        IhomyAppGroup.directory(named: "share-in")
    }

    static var pendingFile: URL? {
        directory?.appendingPathComponent("pending.json")
    }

    static func save(_ payload: [String: Any]) -> Bool {
        guard let file = pendingFile,
              let data = try? JSONSerialization.data(withJSONObject: payload) else { return false }
        do {
            try data.write(to: file, options: .atomic)
            return true
        } catch {
            NSLog("[ihomy] share payload write failed: \(error)")
            return false
        }
    }

    static func read() -> [String: Any]? {
        guard let file = pendingFile, FileManager.default.fileExists(atPath: file.path),
              let data = try? Data(contentsOf: file),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        return root
    }

    static func clear() {
        guard let directory else { return }
        try? FileManager.default.removeItem(at: directory)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }
}

//
//  IhomyWidgets.swift
//  IhomyWidgets
//
//  四张桌面卡片:今日概览 / 今日提醒 / 纪念日倒计时 / 本月记账。
//  与 Android 的 WidgetProvider 一一对应:数据只读 App Group 缓存,
//  令牌、缓存键、更新时刻全部沿用 `IhomyPrefs`(同名同键)。
//  卡片自己不拉网络,刷新由主 App 的后台任务 + `WidgetCenter.reloadAllTimelines()` 驱动。
//

import SwiftUI
import WidgetKit
import AppIntents

// MARK: - 数据整理

private func ihomySorted(_ items: [IhomyOccurrence]) -> [IhomyOccurrence] {
    items.sorted { lhs, rhs in
        if lhs.date != rhs.date { return lhs.date < rhs.date }
        let lt = lhs.time ?? "99:99"
        let rt = rhs.time ?? "99:99"
        if lt != rt { return lt < rt }
        return lhs.key < rhs.key
    }
}

private func ihomyOnDate(_ items: [IhomyOccurrence], _ date: Date) -> [IhomyOccurrence] {
    let text = IhomyDate.string(date)
    return ihomySorted(items.filter { $0.date == text && !$0.done })
}

private func ihomyDaysLeft(_ item: IhomyOccurrence) -> Int {
    if let days = item.days { return max(days, 0) }
    guard let date = IhomyDate.date(item.date) else { return 0 }
    return max(IhomyDate.daysBetween(from: Date(), to: date), 0)
}

// MARK: - 今日概览

struct IhomyTodayEntry: TimelineEntry {
    let date: Date
    let loggedIn: Bool
    let items: [IhomyOccurrence]
    let info: IhomyTodayInfo
    let updatedAt: Double
}

struct IhomyTodayProvider: TimelineProvider {
    func placeholder(in context: Context) -> IhomyTodayEntry {
        sample()
    }

    func getSnapshot(in context: Context, completion: @escaping (IhomyTodayEntry) -> Void) {
        completion(load())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<IhomyTodayEntry>) -> Void) {
        // 缓存由 App 侧刷新;这里每 30 分钟只要一次新时间线,顺便让「今天」跨天重绘。
        let entry = load()
        let next = Date().addingTimeInterval(30 * 60)
        completion(Timeline(entries: [entry], policy: .after(next)))
    }

    private func load() -> IhomyTodayEntry {
        IhomyTodayEntry(
            date: Date(),
            loggedIn: !IhomyPrefs.token.isEmpty,
            items: IhomyWidgetStore.readCache("today"),
            info: IhomyWidgetStore.readTodayInfo(),
            updatedAt: IhomyPrefs.updatedAt("today")
        )
    }

    private func sample() -> IhomyTodayEntry {
        var first = IhomyOccurrence()
        first.key = "REMINDER:1:2026-01-01"
        first.kind = "REMINDER"
        first.title = IhomyL10n.t("接孩子放学", "Pick up the kids")
        first.date = IhomyDate.string(Date())
        first.time = "16:30"
        var second = IhomyOccurrence()
        second.key = "PLAN:2:2026-01-01"
        second.kind = "PLAN"
        second.title = IhomyL10n.t("周末一起做饭", "Cook together this weekend")
        second.date = IhomyDate.string(Date())
        second.time = "18:00"
        var info = IhomyTodayInfo()
        info.date = IhomyDate.string(Date())
        info.lunar = IhomyL10n.t("十月廿二", "10-22")
        info.weather = "12° 晴"
        return IhomyTodayEntry(date: Date(), loggedIn: true, items: [first, second], info: info,
                               updatedAt: Date().timeIntervalSince1970 * 1000)
    }
}

struct IhomyTodayView: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.widgetFamily) private var family
    let entry: IhomyTodayEntry

    private var maxRows: Int { family == .systemLarge ? 6 : 3 }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(IhomyL10n.todayTitle(entry.date))
                .font(.system(size: 15, weight: .semibold, design: .rounded))
                .foregroundStyle(IhomyWidgetStyle.primary(scheme))
                .lineLimit(1)

            let subtitle = ihomyTodaySubtitle(entry.info)
            if !subtitle.isEmpty {
                Text(subtitle)
                    .font(.system(size: 10.5, weight: .medium, design: .rounded))
                    .foregroundStyle(IhomyWidgetStyle.secondary(scheme))
                    .lineLimit(1)
            }

            Spacer(minLength: 4)

            if !entry.loggedIn {
                IhomyWidgetEmpty(text: ihomySignInText())
            } else if entry.items.isEmpty {
                IhomyWidgetEmpty(text: ihomyLoadingText())
            } else {
                let today = ihomyOnDate(entry.items, entry.date)
                let rows = today.isEmpty ? Array(ihomySorted(entry.items).prefix(maxRows))
                                         : Array(today.prefix(maxRows))
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, item in
                        IhomyOccurrenceRow(item: item)
                    }
                }
            }

            Spacer(minLength: 0)

            IhomyWidgetFooter(text: ihomyUpdatedText(entry.updatedAt))
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .ihomyWidgetBackground(scheme)
        .widgetURL(URL(string: "ihomy://plan"))
    }
}

struct IhomyTodayWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "IhomyToday", provider: IhomyTodayProvider()) { entry in
            IhomyTodayView(entry: entry)
        }
        .configurationDisplayName(IhomyL10n.t("今日概览", "Today"))
        .description(IhomyL10n.t("农历、天气与最近的日程。", "Lunar date, weather and upcoming plans."))
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

// MARK: - 今日提醒(桌面勾选)

@available(iOS 17.0, *)
struct IhomyToggleReminderIntent: AppIntent {
    static var title: LocalizedStringResource = "完成提醒"
    static var description = IntentDescription("在家今天的提醒")

    @Parameter(title: "Key")
    var key: String

    init() {}

    init(key: String) {
        self.key = key
    }

    func perform() async throws -> some IntentResult {
        // key 形如 REMINDER:123:2026-11-11,与 Android WidgetActionReceiver 同一口径。
        let parts = key.split(separator: ":")
        if parts.count >= 3, let id = Int(parts[1]) {
            try? await IhomyWidgetStore.toggleReminderDone(
                id: id,
                date: String(parts[2]),
                token: IhomyPrefs.token
            )
            await IhomyWidgetStore.syncAll()
        }
        return .result()
    }
}

struct IhomyReminderEntry: TimelineEntry {
    let date: Date
    let loggedIn: Bool
    let items: [IhomyOccurrence]
    let updatedAt: Double
}

struct IhomyReminderProvider: TimelineProvider {
    func placeholder(in context: Context) -> IhomyReminderEntry {
        IhomyReminderEntry(date: Date(), loggedIn: true,
                            items: IhomyTodayProvider().placeholder(in: context).items,
                            updatedAt: Date().timeIntervalSince1970 * 1000)
    }

    func getSnapshot(in context: Context, completion: @escaping (IhomyReminderEntry) -> Void) {
        completion(load())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<IhomyReminderEntry>) -> Void) {
        completion(Timeline(entries: [load()], policy: .after(Date().addingTimeInterval(30 * 60))))
    }

    private func load() -> IhomyReminderEntry {
        IhomyReminderEntry(
            date: Date(),
            loggedIn: !IhomyPrefs.token.isEmpty,
            items: IhomyWidgetStore.readCache("reminder"),
            updatedAt: IhomyPrefs.updatedAt("reminder")
        )
    }
}

struct IhomyReminderView: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.widgetFamily) private var family
    let entry: IhomyReminderEntry

    private var maxRows: Int { family == .systemLarge ? 6 : 4 }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(IhomyL10n.t("今日提醒 · \(IhomyL10n.monthDay(entry.date))",
                             "Reminders · \(IhomyL10n.monthDay(entry.date))"))
                .font(.system(size: 15, weight: .semibold, design: .rounded))
                .foregroundStyle(IhomyWidgetStyle.primary(scheme))
                .lineLimit(1)

            Spacer(minLength: 4)

            if !entry.loggedIn {
                IhomyWidgetEmpty(text: ihomySignInText())
            } else if entry.items.isEmpty {
                IhomyWidgetEmpty(text: ihomyLoadingText())
            } else {
                let today = ihomyOnDate(entry.items, entry.date)
                    .filter { $0.kind == "REMINDER" || $0.kind == "TASK" }
                if today.isEmpty {
                    IhomyWidgetEmpty(text: IhomyL10n.t("今天没有提醒", "No reminders today"))
                } else {
                    VStack(alignment: .leading, spacing: 2) {
                        ForEach(Array(today.prefix(maxRows).enumerated()), id: \.offset) { _, item in
                            HStack(spacing: 6) {
                                IhomyOccurrenceRow(item: item)
                                if #available(iOSApplicationExtension 17.0, *) {
                                    Button(intent: IhomyToggleReminderIntent(key: item.key)) {
                                        Image(systemName: "circle")
                                            .font(.system(size: 12, weight: .regular))
                                            .foregroundStyle(IhomyWidgetStyle.secondary(scheme).opacity(0.7))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(minLength: 0)

            IhomyWidgetFooter(text: ihomyUpdatedText(entry.updatedAt))
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .ihomyWidgetBackground(scheme)
        .widgetURL(URL(string: "ihomy://plan?date=\(IhomyDate.string(entry.date))"))
    }
}

struct IhomyReminderWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "IhomyReminder", provider: IhomyReminderProvider()) { entry in
            IhomyReminderView(entry: entry)
        }
        .configurationDisplayName(IhomyL10n.t("今日提醒", "Reminders"))
        .description(IhomyL10n.t("今天的提醒与待办,可直接勾选。", "Today's reminders and tasks, checkable from the home screen."))
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

// MARK: - 纪念日倒计时

struct IhomyCountdownEntry: TimelineEntry {
    let date: Date
    let loggedIn: Bool
    let next: IhomyOccurrence?
    let days: Int
    let updatedAt: Double
}

struct IhomyCountdownProvider: TimelineProvider {
    func placeholder(in context: Context) -> IhomyCountdownEntry {
        var item = IhomyOccurrence()
        item.key = "ANNIVERSARY:1:2026-12-24"
        item.kind = "ANNIVERSARY"
        item.title = IhomyL10n.t("结婚纪念日", "Anniversary")
        item.date = IhomyDate.string(Calendar.current.date(byAdding: .day, value: 43, to: Date()) ?? Date())
        item.days = 43
        return IhomyCountdownEntry(date: Date(), loggedIn: true, next: item, days: 43, updatedAt: 0)
    }

    func getSnapshot(in context: Context, completion: @escaping (IhomyCountdownEntry) -> Void) {
        completion(load())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<IhomyCountdownEntry>) -> Void) {
        completion(Timeline(entries: [load()], policy: .after(Date().addingTimeInterval(30 * 60))))
    }

    private func load() -> IhomyCountdownEntry {
        let items = IhomyWidgetStore.readCache("countdown").filter { $0.kind == "ANNIVERSARY" }
        let next = items.min { lhs, rhs in
            if lhs.date != rhs.date { return lhs.date < rhs.date }
            return lhs.key < rhs.key
        }
        return IhomyCountdownEntry(
            date: Date(),
            loggedIn: !IhomyPrefs.token.isEmpty,
            next: next,
            days: next.map { ihomyDaysLeft($0) } ?? 0,
            updatedAt: IhomyPrefs.updatedAt("countdown")
        )
    }
}

struct IhomyCountdownView: View {
    @Environment(\.colorScheme) private var scheme
    let entry: IhomyCountdownEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            if !entry.loggedIn {
                IhomyWidgetEmpty(text: ihomySignInText())
            } else if entry.next == nil {
                IhomyWidgetEmpty(text: ihomyLoadingText())
            } else {
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text("\(entry.days)")
                        .font(.system(size: 40, weight: .bold, design: .rounded))
                        .foregroundStyle(IhomyWidgetStyle.accentColor(scheme))
                        .minimumScaleFactor(0.6)
                        .lineLimit(1)
                    Text(IhomyL10n.t("天", "d"))
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundStyle(IhomyWidgetStyle.secondary(scheme))
                    Spacer(minLength: 0)
                }
                Text(entry.next?.title ?? "")
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundStyle(IhomyWidgetStyle.primary(scheme))
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
            if let next = entry.next {
                IhomyWidgetFooter(text: "\(IhomyL10n.monthDay(IhomyDate.date(next.date) ?? entry.date)) · \(ihomyUpdatedText(entry.updatedAt))")
            } else {
                IhomyWidgetFooter(text: ihomyUpdatedText(entry.updatedAt))
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .ihomyWidgetBackground(scheme)
        .widgetURL(URL(string: "ihomy://anniversary"))
    }
}

struct IhomyCountdownWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "IhomyCountdown", provider: IhomyCountdownProvider()) { entry in
            IhomyCountdownView(entry: entry)
        }
        .configurationDisplayName(IhomyL10n.t("纪念日倒计时", "Countdown"))
        .description(IhomyL10n.t("离下一个纪念日还有几天。", "Days until the next anniversary."))
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// MARK: - 记账本月

struct IhomyBookEntry: TimelineEntry {
    let date: Date
    let loggedIn: Bool
    let hasCache: Bool
    let summary: IhomyBookSummary
    let updatedAt: Double
}

struct IhomyBookProvider: TimelineProvider {
    func placeholder(in context: Context) -> IhomyBookEntry {
        var summary = IhomyBookSummary()
        summary.month = IhomyDate.monthFormatter.string(from: Date())
        summary.expense = 3280
        summary.income = 12000
        summary.balance = 8720
        summary.count = 24
        return IhomyBookEntry(date: Date(), loggedIn: true, hasCache: true, summary: summary,
                              updatedAt: Date().timeIntervalSince1970 * 1000)
    }

    func getSnapshot(in context: Context, completion: @escaping (IhomyBookEntry) -> Void) {
        completion(load())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<IhomyBookEntry>) -> Void) {
        completion(Timeline(entries: [load()], policy: .after(Date().addingTimeInterval(30 * 60))))
    }

    private func load() -> IhomyBookEntry {
        IhomyBookEntry(
            date: Date(),
            loggedIn: !IhomyPrefs.token.isEmpty,
            hasCache: !IhomyPrefs.cache("book").isEmpty,
            summary: IhomyWidgetStore.readBook(),
            updatedAt: IhomyPrefs.updatedAt("book")
        )
    }
}

struct IhomyBookView: View {
    @Environment(\.colorScheme) private var scheme
    let entry: IhomyBookEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline) {
                Text(IhomyL10n.t("本月收支", "This month"))
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundStyle(IhomyWidgetStyle.primary(scheme))
                Spacer(minLength: 0)
                Image(systemName: "yensign.circle")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(IhomyWidgetStyle.accentColor(scheme))
            }

            if !entry.loggedIn {
                IhomyWidgetEmpty(text: ihomySignInText())
            } else if !entry.hasCache {
                IhomyWidgetEmpty(text: ihomyLoadingText())
            } else {
                Text(entry.summary.count == 0
                     ? IhomyL10n.t("本月还没有记录", "Nothing recorded yet")
                     : IhomyL10n.t("共 \(entry.summary.count) 笔", "\(entry.summary.count) entries"))
                    .font(.system(size: 10.5, weight: .medium, design: .rounded))
                    .foregroundStyle(IhomyWidgetStyle.secondary(scheme))
                    .lineLimit(1)

                Spacer(minLength: 2)

                let money = entry.summary.money
                HStack(spacing: 0) {
                    amount(IhomyL10n.t("支出", "Spent"), money.0, IhomyWidgetStyle.expense)
                    amount(IhomyL10n.t("收入", "Income"), money.1, IhomyWidgetStyle.income)
                    amount(IhomyL10n.t("结余", "Balance"), money.2, IhomyWidgetStyle.primary(scheme))
                }
            }

            Spacer(minLength: 0)

            IhomyWidgetFooter(text: footer)
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .ihomyWidgetBackground(scheme)
        .widgetURL(URL(string: "ihomy://book"))
    }

    private func amount(_ label: String, _ value: String, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(label)
                .font(.system(size: 10, weight: .medium, design: .rounded))
                .foregroundStyle(IhomyWidgetStyle.secondary(scheme))
            Text(value)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundStyle(color)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var footer: String {
        let updated = ihomyUpdatedText(entry.updatedAt)
        let month = entry.summary.month
        guard month.count >= 7, let value = Int(month.suffix(2)), value > 0 else { return updated }
        let head = IhomyL10n.t("\(value)月", "\(IhomyL10n.monthDay(entry.date))")
        return updated.isEmpty ? head : "\(head) · \(updated)"
    }
}

struct IhomyBookWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "IhomyBook", provider: IhomyBookProvider()) { entry in
            IhomyBookView(entry: entry)
        }
        .configurationDisplayName(IhomyL10n.t("记账本月", "Monthly budget"))
        .description(IhomyL10n.t("本月支出、收入与结余。", "This month's spending, income and balance."))
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

// MARK: - 卡片包

@main
struct IhomyWidgetsBundle: WidgetBundle {
    @WidgetBundleBuilder
    var body: some Widget {
        IhomyTodayWidget()
        IhomyReminderWidget()
        IhomyCountdownWidget()
        IhomyBookWidget()
    }
}

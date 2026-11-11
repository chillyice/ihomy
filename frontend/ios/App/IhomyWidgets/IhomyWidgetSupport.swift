//
//  IhomyWidgetSupport.swift
//  IhomyWidgets
//
//  四张卡片的公共外观与三态。配色跟 App 的暖纸主题同一套,
//  深色模式按系统外观切换;数据全部来自 App Group 缓存(见 IhomyShared.swift)。
//

import SwiftUI
import WidgetKit

enum IhomyWidgetStyle {
    static let paper = Color(red: 0.984, green: 0.953, blue: 0.894)
    static let paperDeep = Color(red: 0.941, green: 0.886, blue: 0.800)
    static let ink = Color(red: 0.227, green: 0.180, blue: 0.133)
    static let inkSoft = Color(red: 0.478, green: 0.420, blue: 0.353)
    static let accent = Color(red: 0.659, green: 0.282, blue: 0.227)
    static let income = Color(red: 0.290, green: 0.459, blue: 0.220)
    static let expense = Color(red: 0.710, green: 0.263, blue: 0.231)

    static let nightPaper = Color(red: 0.204, green: 0.145, blue: 0.110)
    static let nightPaperDeep = Color(red: 0.125, green: 0.086, blue: 0.063)
    static let nightInk = Color(red: 0.937, green: 0.878, blue: 0.784)
    static let nightInkSoft = Color(red: 0.753, green: 0.663, blue: 0.549)
    static let nightAccent = Color(red: 0.882, green: 0.604, blue: 0.549)

    static func primary(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? nightInk : ink
    }

    static func secondary(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? nightInkSoft : inkSoft
    }

    static func accentColor(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? nightAccent : accent
    }

    static func gradient(_ scheme: ColorScheme) -> LinearGradient {
        let colors = scheme == .dark
            ? [nightPaper, nightPaperDeep]
            : [paper, paperDeep]
        return LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
    }
}

extension View {
    /// iOS 17 起 WidgetKit 要求背景走 containerBackground,否则卡片在桌面是透明/被裁;
    /// iOS 15/16 退回铺一层渐变。
    @ViewBuilder
    func ihomyWidgetBackground(_ scheme: ColorScheme) -> some View {
        let gradient = IhomyWidgetStyle.gradient(scheme)
        if #available(iOSApplicationExtension 17.0, *) {
            containerBackground(for: .widget) { gradient }
        } else {
            ZStack {
                gradient
                self
            }
        }
    }
}

// MARK: - 三态与页脚

/// 未登录 / 无数据时统一用一句说明,点击仍进站内对应页。
struct IhomyWidgetEmpty: View {
    @Environment(\.colorScheme) private var scheme
    let text: String

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "house")
                .font(.system(size: 12, weight: .semibold))
            Text(text)
                .font(.system(size: 12, weight: .medium, design: .rounded))
        }
        .foregroundStyle(IhomyWidgetStyle.secondary(scheme))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }
}

/// 「21:30 更新」;没同步过就不占位置。
func ihomyUpdatedText(_ at: Double) -> String {
    guard at > 0 else { return "" }
    let date = Date(timeIntervalSince1970: at / 1000)
    return IhomyL10n.t("\(IhomyL10n.hourMinute(date)) 更新",
                       "Updated \(IhomyL10n.hourMinute(date))")
}

func ihomySignInText() -> String {
    IhomyL10n.t("打开 ihomy 登录后查看", "Open ihomy to sign in")
}

func ihomyLoadingText() -> String {
    IhomyL10n.t("正在加载…", "Loading…")
}

/// 卡片统一页脚:左对齐的小字,右侧可放状态点。
struct IhomyWidgetFooter: View {
    @Environment(\.colorScheme) private var scheme
    let text: String

    var body: some View {
        HStack(spacing: 4) {
            Text(text)
                .font(.system(size: 10, weight: .medium, design: .rounded))
                .foregroundStyle(IhomyWidgetStyle.secondary(scheme).opacity(0.8))
            Spacer(minLength: 0)
        }
        .frame(height: 12)
    }
}

/// 一行日程:时刻固定宽度,标题超长截断(和 Android RemoteViews 的显示口径一致)。
struct IhomyOccurrenceRow: View {
    @Environment(\.colorScheme) private var scheme
    let item: IhomyOccurrence
    var showCheck = false

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(item.timeText)
                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                .foregroundStyle(IhomyWidgetStyle.accentColor(scheme))
                .frame(width: 42, alignment: .leading)
                .lineLimit(1)
            Text(item.title)
                .font(.system(size: 12, weight: .medium, design: .rounded))
                .foregroundStyle(IhomyWidgetStyle.primary(scheme))
                .lineLimit(1)
            Spacer(minLength: 0)
            if showCheck {
                Image(systemName: "circle")
                    .font(.system(size: 9, weight: .regular))
                    .foregroundStyle(IhomyWidgetStyle.secondary(scheme).opacity(0.7))
            }
        }
        .frame(height: 17)
    }
}

/// 今日卡副标题:「农历十月廿二 · 立冬 · 12° 晴」;缓存不是今天(跨天未同步)就隐藏。
func ihomyTodaySubtitle(_ info: IhomyTodayInfo) -> String {
    let today = IhomyDate.string(Date())
    guard info.date == today else { return "" }
    var parts: [String] = []
    if !info.lunar.isEmpty {
        parts.append(IhomyL10n.t("农历\(info.lunar)", "Lunar \(info.lunar)"))
    }
    if !info.mark.isEmpty {
        parts.append(info.mark)
    }
    if !info.weather.isEmpty {
        parts.append(info.weather)
    }
    return parts.joined(separator: " · ")
}

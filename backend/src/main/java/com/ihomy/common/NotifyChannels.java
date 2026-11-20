package com.ihomy.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 提醒方式(family_reminder.notify_channels):逗号分隔的 EMAIL / APP。
 * 空(NULL 或空串)= 仅应用内提醒,是历史数据与「都不勾选」的默认口径;
 * 非空时严格按选择执行——例如只选 EMAIL 就不发站内通知、也不注册原生闹钟。
 * 身份判定唯一入口,新增通道只在这里加常量与解析分支。
 */
public final class NotifyChannels {

    public static final String EMAIL = "EMAIL";
    public static final String APP = "APP";

    private NotifyChannels() {
    }

    /** 落库:去重、只留合法值、固定 EMAIL,APP 顺序;空列表返回空串(等同未配置) */
    public static String normalize(List<String> channels) {
        if (channels == null) return null;
        Set<String> picked = new LinkedHashSet<>();
        for (String raw : channels) {
            String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (EMAIL.equals(value) || APP.equals(value)) picked.add(value);
        }
        List<String> ordered = new ArrayList<>(2);
        if (picked.contains(EMAIL)) ordered.add(EMAIL);
        if (picked.contains(APP)) ordered.add(APP);
        return String.join(",", ordered);
    }

    /** 读取:脏值/大小写都能解析,空 = 空列表 */
    public static List<String> parse(String stored) {
        List<String> result = new ArrayList<>(2);
        if (stored == null || stored.isBlank()) return result;
        for (String part : stored.split(",")) {
            String value = part.trim().toUpperCase(Locale.ROOT);
            if ((EMAIL.equals(value) || APP.equals(value)) && !result.contains(value)) result.add(value);
        }
        return result;
    }

    public static boolean has(String stored, String channel) {
        return parse(stored).contains(channel);
    }

    /** 应用内提醒是否开启:未配置(空)按默认开启,显式只选 EMAIL 时关闭 */
    public static boolean appEnabled(String stored) {
        return stored == null || stored.isBlank() || has(stored, APP);
    }

    public static boolean emailEnabled(String stored) {
        return has(stored, EMAIL);
    }
}

package com.ihomy.common;

import java.time.LocalDateTime;

/**
 * 健康数据保留水位(纯计算,便于单测)。
 * POINT 样本(单点/小时聚合)保留 180 天——它最占空间且价值随时间快速下降;
 * 日聚合、睡眠会话与睡眠分期永久保留(年度对比、长期趋势都靠它们)。
 * 查看审计是轻量表,保留 90 天只为纠纷时定位谁看过什么。
 * retentionDays < 1 视为关闭该类清理,返回 null(与天气日志清理同口径)。
 */
public final class HealthRetention {

    private HealthRetention() {
    }

    /** 给定「现在」与保留天数,返回早于该时刻的数据应被清理 */
    public static LocalDateTime cutoff(LocalDateTime now, int retentionDays) {
        return retentionDays < 1 ? null : now.minusDays(retentionDays);
    }
}

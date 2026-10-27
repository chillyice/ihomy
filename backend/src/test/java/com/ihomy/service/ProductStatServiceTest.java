package com.ihomy.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 产品统计的区间与下载文件判定(纯逻辑,不碰数据库)。
 * 分桶在「全部」区间按跨度自适应:≤62 天按天,超过按月。
 */
class ProductStatServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27); // 周二

    @Test
    void rangeNormalizationFallsBackToAll() {
        assertThat(ProductStatService.normalizeRange(null)).isEqualTo("all");
        assertThat(ProductStatService.normalizeRange("")).isEqualTo("all");
        assertThat(ProductStatService.normalizeRange("quarter")).isEqualTo("all");
        assertThat(ProductStatService.normalizeRange("day")).isEqualTo("day");
        assertThat(ProductStatService.normalizeRange("year")).isEqualTo("year");
    }

    @Test
    void rangeStartFollowsCalendarBoundaries() {
        assertThat(ProductStatService.startOf("day", TODAY)).isEqualTo(TODAY);
        assertThat(ProductStatService.startOf("week", TODAY)).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(ProductStatService.startOf("month", TODAY)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(ProductStatService.startOf("year", TODAY)).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(ProductStatService.startOf("all", TODAY)).isNull();
    }

    @Test
    void onlyInstallerFileNamesCount() {
        assertThat(ProductStatService.isInstaller("kada.exe")).isTrue();
        assertThat(ProductStatService.isInstaller("bit_pouch_0.3.0_amd64.AppImage")).isTrue();
        assertThat(ProductStatService.isInstaller("kada_1.2.0_universal.dmg")).isTrue();
        // 更新清单与签名不当下载
        assertThat(ProductStatService.isInstaller("latest.json")).isFalse();
        assertThat(ProductStatService.isInstaller("kada.exe.sig")).isFalse();
        // 目录穿越与子路径一律拒绝
        assertThat(ProductStatService.isInstaller("../uploads/kada.exe")).isFalse();
        assertThat(ProductStatService.isInstaller("updates/Kada_1.2.0_x64-portable.exe")).isFalse();
        assertThat(ProductStatService.isInstaller(null)).isFalse();
    }
}

package com.ihomy.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 睡眠区间聚合口径:相邻分段合并成会话、间隔超 30 分钟断场、重叠不重复计时、
 * 碎片(<3 分钟)丢弃、睡眠日跨零点归前一天、白天开始的会话记小睡。
 */
class HealthSleepAggregatorTest {

    private static HealthSleepAggregator.Interval it(String from, String to, String stage) {
        return new HealthSleepAggregator.Interval(LocalDateTime.parse(from), LocalDateTime.parse(to), stage);
    }

    @Test
    void threeStagesMergeIntoOneFullSession() {
        List<HealthSleepAggregator.Session> sessions = HealthSleepAggregator.aggregate(List.of(
                it("2026-11-15T23:30:00", "2026-11-16T00:30:00", "DEEP"),
                it("2026-11-16T00:30:00", "2026-11-16T05:00:00", "LIGHT"),
                it("2026-11-16T05:00:00", "2026-11-16T06:30:00", "REM"),
                it("2026-11-16T06:30:00", "2026-11-16T07:00:00", "AWAKE")));

        assertThat(sessions).hasSize(1);
        HealthSleepAggregator.Session s = sessions.get(0);
        assertThat(s.totalMin()).isEqualTo(450);
        assertThat(s.deepMin()).isEqualTo(60);
        assertThat(s.lightMin()).isEqualTo(270);
        assertThat(s.remMin()).isEqualTo(90);
        assertThat(s.awakeMin()).isEqualTo(30);
        assertThat(s.stageLevel()).isEqualTo("FULL");
        // 23:30 入睡跨零点,归前一天
        assertThat(s.sleepDate()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(s.kind()).isEqualTo("NIGHT");
        assertThat(s.stages()).hasSize(4);
    }

    @Test
    void gapOverThirtyMinutesSplitsSessions() {
        List<HealthSleepAggregator.Session> sessions = HealthSleepAggregator.aggregate(List.of(
                it("2026-11-15T23:00:00", "2026-11-16T02:00:00", "LIGHT"),
                it("2026-11-16T03:00:00", "2026-11-16T06:00:00", "LIGHT")));

        assertThat(sessions).hasSize(2);
        assertThat(sessions.get(0).totalMin()).isEqualTo(180);
        assertThat(sessions.get(1).totalMin()).isEqualTo(180);
    }

    @Test
    void smallGapStaysInOneSession() {
        List<HealthSleepAggregator.Session> sessions = HealthSleepAggregator.aggregate(List.of(
                it("2026-11-15T23:00:00", "2026-11-16T02:00:00", "LIGHT"),
                it("2026-11-16T02:20:00", "2026-11-16T06:00:00", "LIGHT")));

        assertThat(sessions).hasSize(1);
        // 会话跨度含 20 分钟空档(空档没有分期区间,只进在床总时长)
        assertThat(sessions.get(0).totalMin()).isEqualTo(420);
        assertThat(sessions.get(0).lightMin()).isEqualTo(400);
    }

    @Test
    void overlappingIntervalsAreCountedOnce() {
        List<HealthSleepAggregator.Session> sessions = HealthSleepAggregator.aggregate(List.of(
                it("2026-11-15T23:00:00", "2026-11-16T01:00:00", "LIGHT"),
                it("2026-11-16T00:00:00", "2026-11-16T02:00:00", "DEEP")));

        assertThat(sessions).hasSize(1);
        HealthSleepAggregator.Session s = sessions.get(0);
        assertThat(s.totalMin()).isEqualTo(180);
        // 重叠的 00:00–01:00 先到先得,归 LIGHT
        assertThat(s.lightMin()).isEqualTo(120);
        assertThat(s.deepMin()).isEqualTo(60);
    }

    @Test
    void fragmentsShorterThanThreeMinutesAreDropped() {
        List<HealthSleepAggregator.Session> sessions = HealthSleepAggregator.aggregate(List.of(
                it("2026-11-16T14:00:00", "2026-11-16T14:02:00", "UNKNOWN")));

        assertThat(sessions).isEmpty();
    }

    @Test
    void daytimeSessionIsNapOnTheSameDate() {
        List<HealthSleepAggregator.Session> sessions = HealthSleepAggregator.aggregate(List.of(
                it("2026-11-16T13:00:00", "2026-11-16T14:20:00", "LIGHT")));

        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).kind()).isEqualTo("NAP");
        assertThat(sessions.get(0).sleepDate()).isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void onlyAwakeAndInBedIsSummaryWithoutStageMinutes() {
        List<HealthSleepAggregator.Session> sessions = HealthSleepAggregator.aggregate(List.of(
                it("2026-11-16T01:00:00", "2026-11-16T07:00:00", "INBED"),
                it("2026-11-16T01:30:00", "2026-11-16T02:00:00", "AWAKE")));

        assertThat(sessions).hasSize(1);
        HealthSleepAggregator.Session s = sessions.get(0);
        assertThat(s.stageLevel()).isEqualTo("SUMMARY");
        assertThat(s.deepMin()).isNull();
        assertThat(s.lightMin()).isNull();
        assertThat(s.remMin()).isNull();
        assertThat(s.awakeMin()).isEqualTo(30);
    }

    @Test
    void invalidIntervalsAreIgnored() {
        assertThat(HealthSleepAggregator.aggregate(List.of(
                it("2026-11-16T05:00:00", "2026-11-16T05:00:00", "LIGHT")))).isEmpty();
        assertThat(HealthSleepAggregator.aggregate(List.of())).isEmpty();
        assertThat(HealthSleepAggregator.aggregate(null)).isEmpty();
    }
}

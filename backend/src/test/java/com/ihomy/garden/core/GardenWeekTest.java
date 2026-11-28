package com.ihomy.garden.core;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 周窗口纯函数单测(玩法稿 §15 周挑战):周一为界、ISO 周键、轮换确定性。
 * 固定日期,不依赖真实时钟。
 */
class GardenWeekTest {

    private static final LocalDate SUN_1129 = LocalDate.of(2026, 11, 29);   // 周日,ISO 2026-W48
    private static final LocalDate MON_1130 = LocalDate.of(2026, 11, 30);   // 周一,ISO 2026-W49

    @Test
    void 周一周日边界() {
        assertEquals(LocalDate.of(2026, 11, 23), GardenWeek.start(SUN_1129));
        assertEquals(LocalDate.of(2026, 11, 29), GardenWeek.end(SUN_1129));
        // 周一当天仍算本周(previousOrSame,不是 previous)
        assertEquals(MON_1130, GardenWeek.start(MON_1130));
        assertEquals(LocalDate.of(2026, 12, 6), GardenWeek.end(MON_1130));
        assertEquals(DayOfWeek.MONDAY, GardenWeek.start(MON_1130).getDayOfWeek());
    }

    @Test
    void ISO周键与同一周内恒定() {
        assertEquals("2026-W48", GardenWeek.key(SUN_1129));
        assertEquals("2026-W49", GardenWeek.key(MON_1130));
        // 周一→周日整周同键(领取凭证按周去重的前提)
        for (int i = 0; i < 7; i++) {
            assertEquals("2026-W49", GardenWeek.key(MON_1130.plusDays(i)));
        }
        // 跨年周按 ISO 周年:2027-01-01(周五)属 2026 年第 53 周
        assertEquals("2026-W53", GardenWeek.key(LocalDate.of(2027, 1, 1)));
    }

    @Test
    void 周序号连续递增() {
        assertEquals(GardenWeek.index(MON_1130) + 1, GardenWeek.index(MON_1130.plusDays(7)));
        // 周一→周日同序号(11-29 是上一周的周日,与 11-30 不同周)
        assertEquals(GardenWeek.index(MON_1130), GardenWeek.index(MON_1130.plusDays(6)));
        // 1970-01-05(周一)为序号 0,便于锚定
        assertEquals(0, GardenWeek.index(LocalDate.of(1970, 1, 5)));
    }

    @Test
    void 轮换确定性与环移() {
        List<String> pool = List.of("A", "B", "C", "D", "E", "F");
        assertEquals(List.of("A", "B", "C"), GardenWeek.pick(pool, LocalDate.of(1970, 1, 5), 3));
        assertEquals(List.of("B", "C", "D"), GardenWeek.pick(pool, LocalDate.of(1970, 1, 12), 3));
        // 同一周任意一天结果相同(周一取或周日取一致)
        assertEquals(GardenWeek.pick(pool, MON_1130, 3), GardenWeek.pick(pool, MON_1130.plusDays(6), 3));
        // 环形:6 条池轮 6 周回到原点
        assertEquals(GardenWeek.pick(pool, LocalDate.of(1970, 1, 5), 3),
                GardenWeek.pick(pool, LocalDate.of(1970, 1, 5).plusWeeks(6), 3));
        // 池小于取数时全给,且不重复
        List<String> two = GardenWeek.pick(List.of("E", "F"), MON_1130, 3);
        assertEquals(2, two.size());
        assertTrue(two.containsAll(List.of("E", "F")));
        assertTrue(GardenWeek.pick(List.of(), MON_1130, 3).isEmpty());
    }
}

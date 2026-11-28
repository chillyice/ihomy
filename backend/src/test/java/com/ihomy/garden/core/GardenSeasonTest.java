package com.ihomy.garden.core;

import com.ihomy.garden.GardenConst;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 季节纯函数(FR-20)与归属判据(PLOT_OWNERSHIP)。
 */
class GardenSeasonTest {

    @Test
    void monthMapsToSeason() {
        assertEquals(GardenSeason.SPRING, GardenSeason.seasonOf(LocalDate.of(2026, 3, 1)));
        assertEquals(GardenSeason.SPRING, GardenSeason.seasonOf(LocalDate.of(2026, 5, 31)));
        assertEquals(GardenSeason.SUMMER, GardenSeason.seasonOf(LocalDate.of(2026, 6, 1)));
        assertEquals(GardenSeason.SUMMER, GardenSeason.seasonOf(LocalDate.of(2026, 8, 31)));
        assertEquals(GardenSeason.AUTUMN, GardenSeason.seasonOf(LocalDate.of(2026, 9, 1)));
        assertEquals(GardenSeason.AUTUMN, GardenSeason.seasonOf(LocalDate.of(2026, 11, 28)));
        assertEquals(GardenSeason.WINTER, GardenSeason.seasonOf(LocalDate.of(2026, 12, 1)));
        assertEquals(GardenSeason.WINTER, GardenSeason.seasonOf(LocalDate.of(2026, 2, 28)));
    }

    @Test
    void allSeasonCropsAreAlwaysInSeason() {
        List<String> all = List.of("SPRING", "SUMMER", "AUTUMN", "WINTER");
        assertTrue(GardenSeason.inSeason(all, GardenSeason.WINTER));
        assertTrue(GardenSeason.inSeason(List.of("SPRING", "SUMMER"), GardenSeason.SUMMER));
        assertFalse(GardenSeason.inSeason(List.of("SPRING", "SUMMER"), GardenSeason.WINTER));
    }

    @Test
    void ownershipFollowsVenueLevelRule() {
        // 共享地块(空 owner_uid)任何成员可操作;个人地块只有本人
        assertTrue(GardenConst.mayOperate("", "01JMEMBERAAAAAAAAAAAAAAAAAA"));
        assertTrue(GardenConst.mayOperate("01JMEMBERAAAAAAAAAAAAAAAAAA", "01JMEMBERAAAAAAAAAAAAAAAAAA"));
        assertFalse(GardenConst.mayOperate("01JMEMBERBBBBBBBBBBBBBBBBBB", "01JMEMBERAAAAAAAAAAAAAAAAAA"));
        assertTrue(GardenConst.isPersonalVenue("WINDOWSILL"));
        assertTrue(GardenConst.isSharedVenue("FARM"));
        assertFalse(GardenConst.isPersonalVenue("BACKYARD"));
    }
}

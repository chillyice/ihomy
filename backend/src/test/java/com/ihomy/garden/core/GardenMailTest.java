package com.ihomy.garden.core;

import com.ihomy.garden.content.CropDef;
import com.ihomy.garden.content.GardenContent;
import com.ihomy.garden.content.MailDef;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 每日信封纯函数:首盆随季、确定性选种(同一世界同一天一致)、避开前一天种子、教学脚本与季节短笺。
 */
class GardenMailTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 28);
    private final GardenContent content = new GardenContent();
    private final List<CropDef> crops = content.unlockedCrops();
    private final MailDef mail = content.mail();

    @Test
    void firstPotFollowsSeason() {
        assertEquals("CATGRASS", GardenMail.firstPotCropCode(crops, GardenSeason.SPRING));
        assertEquals("CATGRASS", GardenMail.firstPotCropCode(crops, GardenSeason.AUTUMN));
        assertEquals("MINT", GardenMail.firstPotCropCode(crops, GardenSeason.SUMMER));
        assertEquals("SNAKE_PLANT", GardenMail.firstPotCropCode(crops, GardenSeason.WINTER));
    }

    @Test
    void deterministicSeedPerWorldAndDate() {
        String a = GardenMail.dailySeedCode(crops, DAY, "01JTESTWORLD0000000000AAAAAA");
        String b = GardenMail.dailySeedCode(crops, DAY, "01JTESTWORLD0000000000AAAAAA");
        assertEquals(a, b);
        String other = GardenMail.dailySeedCode(crops, DAY, "01JTESTWORLD0000000000BBBBBB");
        // 不同世界同一天可能不同(允许),但同一世界必须一致——这里只断言两者都合法
        assertTrue(crops.stream().anyMatch(c -> c.code().equals(other)));
    }

    @Test
    void dailySeedDiffersFromPreviousDayWhenPossible() {
        String today = GardenMail.dailySeedCode(crops, DAY, "W1");
        String yesterday = GardenMail.dailySeedCode(crops, DAY.minusDays(1), "W1");
        // 冬季当季池 4 种(全年种),避开逻辑应保证相邻两天不同
        assertNotEquals(yesterday, today);
    }

    @Test
    void firstLetterCarriesInitialGift() {
        GardenMail.Letter letter = GardenMail.deriveFirst("W1", DAY, crops, mail);
        assertEquals("FIRST", letter.type());
        assertEquals(30, letter.coins());
        assertEquals("CATGRASS", letter.seedCropCode()); // 11 月 = 秋 → 首盆猫草(春/秋)
        assertTrue(letter.text().contains("30"));
    }

    @Test
    void firstPotIsSnakePlantInWinter() {
        // 12 月 = 冬 → 虎皮兰(§5.8 首盆随季)
        GardenMail.Letter letter = GardenMail.deriveFirst("W1", LocalDate.of(2026, 12, 20), crops, mail);
        assertEquals("SNAKE_PLANT", letter.seedCropCode());
    }

    @Test
    void teachingScriptCoversDay2To7() {
        LocalDate cycleStart = LocalDate.of(2026, 11, 1);
        for (int day = 2; day <= 7; day++) {
            GardenMail.Letter letter = GardenMail.deriveForDate("W1", cycleStart.plusDays(day - 1),
                    cycleStart, crops, mail);
            assertEquals("TEACHING", letter.type(), "day " + day);
            assertTrue(letter.text() != null && !letter.text().isEmpty());
            assertEquals(0, letter.coins());
            assertTrue(letter.seedCropCode() != null);
        }
    }

    @Test
    void day4MentionsSeason() {
        LocalDate cycleStart = LocalDate.of(2026, 11, 1);
        GardenMail.Letter letter = GardenMail.deriveForDate("W1", cycleStart.plusDays(3), cycleStart, crops, mail);
        // 11 月 4 日 = 秋
        assertEquals("现在是秋。这几种最适合,那几种要等", letter.text());
    }

    @Test
    void afterDay7FallsBackToSeasonalNote() {
        LocalDate cycleStart = LocalDate.of(2026, 1, 1);
        GardenMail.Letter letter = GardenMail.deriveForDate("W1", LocalDate.of(2026, 12, 28),
                cycleStart, crops, mail);
        assertEquals("DAILY", letter.type());
        assertTrue(mail.seasonal().get(GardenSeason.WINTER).contains(letter.text()));
    }

    @Test
    void hashIsStableLong() {
        assertEquals(GardenMail.hash("W1", DAY, "WINTER"), GardenMail.hash("W1", DAY, "WINTER"));
        assertTrue(GardenMail.hash("W1", DAY, "WINTER") >= 0);
    }
}

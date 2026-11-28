package com.ihomy.garden.core;

import com.ihomy.garden.content.CropDef;
import com.ihomy.garden.content.MailDef;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * 每日信封纯函数(FR-27 / 玩法稿 §18.7):同一世界同一天,谁先拆、换设备、重放都拿到同一封信与同一种子。
 * hash(worldId + 日期 + 季节) 决定种子与文案,不用随机(NFR-02);日期以服务端为准。
 */
public final class GardenMail {

    /** 信的推导结果(未含奖励落库;奖励由服务层执行) */
    public record Letter(String date, String type, String text, String seedCropCode, int coins, String signature) {
    }

    private GardenMail() {
    }

    /** 首封(世界第一次拆信):当季首盆种 + 30 园币(FR-27「首封含初始礼包」) */
    public static Letter deriveFirst(String worldId, LocalDate date, List<CropDef> unlockedCrops, MailDef mail) {
        String cropCode = firstPotCropCode(unlockedCrops, GardenSeason.seasonOf(date));
        return new Letter(date.toString(), "FIRST", mail.firstLetter().text(), cropCode,
                30, mail.firstLetter().signature());
    }

    /** 按日期推导当天的信:第 2–7 天教学信(按 cycle_start_at 起算),之后取季节短笺 */
    public static Letter deriveForDate(String worldId, LocalDate date, LocalDate cycleStart,
                                       List<CropDef> unlockedCrops, MailDef mail) {
        String season = GardenSeason.seasonOf(date);
        long day = cycleStart == null ? 0 : ChronoUnit.DAYS.between(cycleStart, date) + 1;
        String text;
        String type;
        if (day >= 2 && day <= 7) {
            type = "TEACHING";
            text = scriptText(mail, (int) day, season, worldId, date);
        } else {
            type = "DAILY";
            text = seasonalText(mail, season, worldId, date);
        }
        String cropCode = dailySeedCode(unlockedCrops, date, worldId);
        return new Letter(date.toString(), type, text, cropCode, 0, mail.signature());
    }

    /** 当季首盆种(§5.8:春/秋 猫草、夏 薄荷、冬 虎皮兰;配置里标 first_pot) */
    public static String firstPotCropCode(List<CropDef> crops, String season) {
        CropDef pick = crops.stream().filter(c -> c.firstPot() && GardenSeason.inSeason(c.seasons(), season))
                .findFirst().orElse(null);
        if (pick == null) {
            pick = crops.stream().filter(CropDef::firstPot).findFirst().orElse(null);
        }
        if (pick == null) {
            pick = crops.get(0);
        }
        return pick.code();
    }

    /** 每日种子:当季池 → 全年池 → 全部;hash 选取并避开前一天的种子 */
    public static String dailySeedCode(List<CropDef> crops, LocalDate date, String worldId) {
        List<CropDef> pool = crops.stream()
                .filter(c -> GardenSeason.inSeason(c.seasons(), GardenSeason.seasonOf(date))).toList();
        if (pool.isEmpty()) {
            pool = crops.stream().filter(c -> GardenSeason.isAllSeason(c.seasons())).toList();
        }
        if (pool.isEmpty()) {
            pool = crops;
        }
        int idx = (int) (hash(worldId, date, GardenSeason.seasonOf(date)) % pool.size());
        // 「另一种当季」(§18.7 第 2 天):与前一天相同则顺延一位(前一天只用 hash,不再递归避让)
        LocalDate prev = date.minusDays(1);
        List<CropDef> prevPool = crops.stream()
                .filter(c -> GardenSeason.inSeason(c.seasons(), GardenSeason.seasonOf(prev))).toList();
        if (!prevPool.isEmpty()) {
            int prevIdx = (int) (hash(worldId, prev, GardenSeason.seasonOf(prev)) % prevPool.size());
            if (pool.size() > 1 && prevPool.get(prevIdx).code().equals(pool.get(idx).code())) {
                idx = (idx + 1) % pool.size();
            }
        }
        return pool.get(idx).code();
    }

    /** 教学信文案(第 4 天含季节名占位符) */
    private static String scriptText(MailDef mail, int day, String season, String worldId, LocalDate date) {
        String text = mail.script().stream()
                .filter(l -> l.day() == day)
                .findFirst()
                .map(MailDef.ScriptLine::text)
                .orElseGet(() -> seasonalText(mail, season, worldId, date));
        return text.replace("{season}", mail.seasonNames().getOrDefault(season, season));
    }

    /** 季节短笺(同一天确定性选取) */
    private static String seasonalText(MailDef mail, String season, String worldId, LocalDate date) {
        List<String> lines = mail.seasonal().get(season);
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        int idx = (int) (hash(worldId, date, season) % lines.size());
        return lines.get(idx);
    }

    /** 确定性哈希:SHA-256 前 8 字节取正 long(禁 Math.random 的替代) */
    public static long hash(String worldId, LocalDate date, String season) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            String input = "garden-mail|" + worldId + "|" + date + "|" + season.toUpperCase(Locale.ROOT);
            byte[] bytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (bytes[i] & 0xFFL);
            }
            return v & Long.MAX_VALUE;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}

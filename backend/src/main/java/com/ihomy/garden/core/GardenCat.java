package com.ihomy.garden.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

/**
 * 猫(FR-36 情绪层,玩法稿 §13.1):它不做事,它是「家里有人」的证据。
 * 位置 = f(节气, 时段, 已解锁场域) 的纯函数——零存储、确定性、可重放;天气槽位预留(接入后进参)。
 * 硬规定:只睡空盆与干土,不碰已种地块——候选集由调用方从空盆里出;无候选走 STROLL。
 * 无新增端点、无新增 op、无新增表;动画与路径只在客户端,不进渲染契约。
 */
public final class GardenCat {

    private GardenCat() {
    }

    /** 猫的位置视图:plotId 非空 = 睡在某只空盆上;null = 在场域里散步(area 生效) */
    public record Position(Long plotId, String venue, Integer slotNo, String posture, String area) {
    }

    /** 空盆候选(调用方从全景地块里筛 cropCode 为空的出) */
    public record Candidate(Long plotId, String venue, Integer slotNo, boolean outdoor) {
    }

    /** 偏好室内的节气事件窗(炎热找阴凉、寒冷回屋里) */
    private static final List<String> INDOOR_PREF_EVENTS =
            List.of("LONG_SUMMER", "MIDSUMMER", "WINTER_ENTRY", "DORMANCY", "COLD_WAVE");

    public static Position position(String worldId, List<Candidate> candidates, List<String> areas,
                                    LocalDate today, int hour, String eventCode, boolean weatherIndoor) {
        boolean preferIndoor = weatherIndoor
                || (eventCode != null && INDOOR_PREF_EVENTS.contains(eventCode));
        List<Candidate> pool = candidates.stream()
                .filter(c -> preferIndoor != c.outdoor())
                .toList();
        if (pool.isEmpty()) {
            pool = candidates;
        }
        if (pool.isEmpty()) {
            String area = areas == null || areas.isEmpty() ? "WINDOWSILL"
                    : areas.get(pick(worldId, today, hour) % areas.size());
            return new Position(null, area, null, "STROLL", area);
        }
        Candidate chosen = pool.get(pick(worldId, today, hour) % pool.size());
        String posture = (hour >= 23 || hour < 5) ? "SLEEP"
                : (chosen.outdoor() ? "SUNBATHE" : "LOUNGE");
        return new Position(chosen.plotId(), chosen.venue(), chosen.slotNo(), posture, chosen.venue());
    }

    /** 确定性散列:同一 (worldId, 日期, 时段桶) 永远同一只盆 */
    private static int pick(String worldId, LocalDate today, int hour) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String seed = worldId + "|" + today + "|" + (hour / 2);
            byte[] hex = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            return Math.abs(HexFormat.of().formatHex(hex).hashCode());
        } catch (Exception e) {
            return 0;
        }
    }
}

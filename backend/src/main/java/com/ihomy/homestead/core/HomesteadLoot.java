package com.ihomy.homestead.core;

import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.ActionDef;
import com.ihomy.homestead.content.AffixDef;
import com.ihomy.homestead.content.EquipmentDef;
import com.ihomy.homestead.content.HomesteadContent;
import com.ihomy.homestead.content.RarityDef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 装备掉落与宝箱的确定性掷点(纯函数,唯一口径)。
 * <p>
 * 同一 (userId, 动作/区域/商店 hash, 序号) 无论重算多少次结果完全一致:
 * 品质按品质下限起算、用 {@link HomesteadConst#QUALITY_ROLL_BANDS} 逐档上跳;
 * 词缀条数取品质定义,从词缀池里不放回抽,数值落在该品质的 min_bp~max_bp。
 * 打造成的实例固定 COMMON、无词缀,不走这里。
 */
public final class HomesteadLoot {

    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private HomesteadLoot() {
    }

    /** 一次掉落的实例数据(未落库):蓝本编码 + 品质 + 词缀紧凑串 */
    public record Instance(String code, String quality, String affixes) {
    }

    /**
     * 掷一件装备实例。
     *
     * @param seed         玩家维度种子(通常 userId)
     * @param salt         来源维度(区域/商店编码等,转成 hash)
     * @param seq          本次来源内第几次掉落(0 起)
     * @param qualityFloor 品质下限(空 = 最低品质)
     */
    public static Instance roll(HomesteadContent content, String code, long seed, String salt, long seq,
                                String qualityFloor) {
        EquipmentDef gear = content == null ? null : content.gear(code);
        if (gear == null) {
            return null;
        }
        long base = mix(seed, salt, seq);
        String quality = qualityOf(content, qualityFloor, base);
        List<HomesteadBonus.Affix> affixes = affixes(content, quality, base);
        return new Instance(code, quality, formatAffixes(affixes));
    }

    /** 品质掷点:从下限档起,按固定概率表上跳(不会越过品质表最后一档) */
    public static String qualityOf(HomesteadContent content, String floor, long roll) {
        List<RarityDef> rarities = content == null ? List.of() : content.rarities();
        if (rarities.isEmpty()) {
            return HomesteadConst.QUALITY_COMMON;
        }
        int index = Math.max(0, Math.min(rarities.size() - 1, content.rarityIndex(floor)));
        int draw = (int) Long.remainderUnsigned(roll >>> 7, 100);
        int[] bands = HomesteadConst.QUALITY_ROLL_BANDS;
        for (int step = 0; step < bands.length; step++) {
            if (draw < bands[step]) {
                index = Math.min(rarities.size() - 1, index + step);
                break;
            }
        }
        return rarities.get(index).code();
    }

    /** 按品质抽词缀(条数与数值区间都由品质表定义) */
    public static List<HomesteadBonus.Affix> affixes(HomesteadContent content, String quality, long base) {
        RarityDef rarity = content == null ? null : content.rarity(quality);
        List<AffixDef> pool = content == null ? List.of() : content.affixPool();
        if (rarity == null || rarity.affixes() <= 0 || pool.isEmpty()) {
            return List.of();
        }
        List<HomesteadBonus.Affix> out = new ArrayList<>();
        Set<String> taken = new HashSet<>();
        int want = Math.min(rarity.affixes(), pool.size());
        int min = Math.max(1, rarity.minBp());
        int max = Math.max(min, rarity.maxBp());
        for (int i = 0; i < want * 4 && out.size() < want; i++) {
            long draw = mix64(base ^ (i + 1) * GOLDEN);
            AffixDef def = pool.get((int) Long.remainderUnsigned(draw >>> 11, pool.size()));
            if (!taken.add(def.kind() + ":" + def.target())) {
                continue;
            }
            int span = max - min + 1;
            int bp = min + (int) Long.remainderUnsigned(draw >>> 29, span);
            out.add(new HomesteadBonus.Affix(def.kind(), def.target(), bp));
        }
        return List.copyOf(out);
    }

    /** 词缀落库格式:SPEED:ALL:120,XP:FARM:80(空串 = 无词缀) */
    public static String formatAffixes(List<HomesteadBonus.Affix> affixes) {
        if (affixes == null || affixes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (HomesteadBonus.Affix affix : affixes) {
            if (affix == null || affix.kind() == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(affix.kind()).append(':').append(affix.target()).append(':').append(affix.bp());
        }
        return sb.toString();
    }

    /** 解析落库的词缀串(坏数据跳过,不抛异常) */
    public static List<HomesteadBonus.Affix> parseAffixes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<HomesteadBonus.Affix> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String[] fields = part.split(":");
            if (fields.length != 3) {
                continue;
            }
            try {
                int bp = Integer.parseInt(fields[2].trim());
                if (bp > 0) {
                    out.add(new HomesteadBonus.Affix(fields[0].trim(), fields[1].trim(), bp));
                }
            } catch (NumberFormatException ignored) {
                // 坏数据跳过
            }
        }
        return List.copyOf(out);
    }

    /** 分解产出:蓝本 dismantle × 品质系数(向下取整,至少 1) */
    public static List<ActionDef.ItemAmount> dismantle(HomesteadContent content, String gearCode, String quality) {
        EquipmentDef gear = content == null ? null : content.gear(gearCode);
        if (gear == null || gear.dismantle().isEmpty()) {
            return List.of();
        }
        int qualityBp = HomesteadBonus.qualityBp(content, quality);
        List<ActionDef.ItemAmount> out = new ArrayList<>();
        Map<String, Long> merged = new LinkedHashMap<>();
        for (ActionDef.ItemAmount amount : gear.dismantle()) {
            long qty = Math.max(1L, (long) amount.qty() * qualityBp / HomesteadConst.BP_BASE);
            merged.merge(amount.item(), qty, Long::sum);
        }
        merged.forEach((item, qty) -> out.add(new ActionDef.ItemAmount(item, qty)));
        return List.copyOf(out);
    }

    /** 来源种子(供 service 与单测共用):玩家 + 来源编码 + 序号 */
    public static long base(long seed, String salt, long seq) {
        return mix(seed, salt, seq);
    }

    private static long mix(long seed, String salt, long seq) {
        int hash = 0x811C9DC5;
        if (salt != null) {
            for (byte b : salt.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
                hash ^= (b & 0xFF);
                hash *= 0x01000193;
            }
        }
        return mix64(seed ^ hash ^ (seq + 1) * GOLDEN);
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}

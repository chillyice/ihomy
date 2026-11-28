package com.ihomy.homestead.core;

import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.ActionDef;
import com.ihomy.homestead.content.BuildingDef;
import com.ihomy.homestead.content.EffectKind;
import com.ihomy.homestead.content.EquipmentDef;
import com.ihomy.homestead.content.GearSetDef;
import com.ihomy.homestead.content.HomesteadContent;
import com.ihomy.homestead.content.RarityDef;
import com.ihomy.homestead.content.SkillDef;
import com.ihomy.homestead.content.ToolDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小院加成聚合(纯函数,唯一口径):工具 + 已穿装备(品质·强化·词缀) + 套装 + 场地 + 生效中的料理 buff
 * 全部折成同一组整数加成,引擎按这组值结算,前端只展示服务端给的结果。
 * <p>
 * 工具只作用于自己的动作;装备/场地/词缀的 target 可以是 GATHER / PROCESS / ALL,也可以直接是技能码
 * (如 FARM、FISHING),按当前动作的技能码命中;buff 由引擎按剩余时间逐次叠加(这里不含 buff)。
 * <p>
 * 数值口径:装备蓝本值 × 品质倍率(基点) × 每强化一级 +50%(整数向下取整);
 * 词缀是掷出来的绝对值(不再乘品质与强化);套装按穿齐件数折进加成,同样不吃强化。
 */
public final class HomesteadBonus {

    private static final long HOUR_MS = 3600_000L;

    private HomesteadBonus() {
    }

    /**
     * 一组加成(全部整数)。
     * speed/xp/yield/rare/efficiency 是基点;sellBonusBp 是卖价基点;extraCapHours 是离线多补小时。
     */
    public record Bonus(int speedBp,
                        int xpBp,
                        int yieldBp,
                        int rareBp,
                        int efficiencyBp,
                        long sellBonusBp,
                        int extraCapHours) {

        /** 离线补算上限:基础 24 小时 + 鞋/仓房加成 */
        public long capMs() {
            return HomesteadConst.OFFLINE_CAP_MS + Math.max(0, extraCapHours) * HOUR_MS;
        }

        /** 逐项封顶,防止内容叠出离谱数值 */
        public Bonus capped() {
            return new Bonus(
                    clamp(speedBp, HomesteadConst.MAX_SPEED_BONUS_BP),
                    clamp(xpBp, HomesteadConst.MAX_XP_BONUS_BP),
                    clamp(yieldBp, HomesteadConst.MAX_CHANCE_BP),
                    clamp(rareBp, HomesteadConst.MAX_CHANCE_BP),
                    clamp(efficiencyBp, HomesteadConst.MAX_CHANCE_BP),
                    Math.max(0, sellBonusBp),
                    Math.max(0, extraCapHours));
        }

        /** 卖货折价:行情算完的铜板再乘卖价加成,向下取整 */
        public long sellCoins(long baseCoins) {
            if (baseCoins <= 0) {
                return 0L;
            }
            long bp = HomesteadConst.BP_BASE + Math.max(0, sellBonusBp);
            return baseCoins * bp / HomesteadConst.BP_BASE;
        }

        public Bonus plus(Bonus other) {
            if (other == null) {
                return this;
            }
            return new Bonus(speedBp + other.speedBp, xpBp + other.xpBp,
                    yieldBp + other.yieldBp, rareBp + other.rareBp,
                    efficiencyBp + other.efficiencyBp,
                    sellBonusBp + other.sellBonusBp,
                    extraCapHours + other.extraCapHours);
        }

        private static int clamp(int value, int max) {
            return Math.max(0, Math.min(max, value));
        }
    }

    /** 无任何加成 */
    public static final Bonus NONE = new Bonus(0, 0, 0, 0, 0, 0L, 0);

    /** 掉落实例上的一条词缀(绝对值,不吃品质与强化) */
    public record Affix(String kind, String target, int bp) {
        public Affix {
            target = target == null || target.isBlank() ? EffectKind.TARGET_ALL : target;
        }
    }

    /**
     * 已穿的一件装备(蓝本编码 + 品质 + 强化等级 0–5 + 词缀)。
     * 品质用编码,倍率从内容表取(未知按 100%);打造成的实例固定 COMMON、无词缀。
     */
    public record WornGear(String code, String quality, int enhance, List<Affix> affixes) {

        public WornGear {
            affixes = affixes == null ? List.of() : List.copyOf(affixes);
        }

        public static WornGear plain(String code, int enhance) {
            return new WornGear(code, HomesteadConst.QUALITY_COMMON, enhance, List.of());
        }
    }

    /** 一条生效中的料理 buff(kind/target/bp/到期时刻) */
    public record BuffSpan(String kind, String target, int bp, long expireAt) {
    }

    /**
     * 折叠当前动作的全部静态加成(工具 + 装备 + 套装 + 场地),不含 buff。
     *
     * @param action          当前动作(工具按动作绑定)
     * @param toolTier        该动作工具的阶数(0 = 徒手)
     * @param worn            当前穿戴的装备(最多 12 件)
     * @param buildingLevels  场地编码 → 等级(未建的不传)
     */
    public static Bonus resolve(HomesteadContent content,
                                ActionDef action,
                                int toolTier,
                                List<WornGear> worn,
                                Map<String, Integer> buildingLevels) {
        if (content == null) {
            return NONE;
        }
        // action 为空 = 空闲(卖货/看离线上限):只折全局效果(OFFLINE_CAP / SELL_BONUS),不认工具
        SkillDef skill = action == null ? null : content.skill(action.skill());
        String skillCode = skill == null ? null : skill.code();
        String kind = skill == null ? EffectKind.TARGET_ALL : skill.kind();
        int speed = 0;
        int xp = 0;
        int yield = 0;
        int rare = 0;
        int efficiency = 0;
        long sell = 0L;
        int capHours = 0;

        // 1) 工具:只作用于自己的动作
        ToolDef tool = action == null ? null : content.toolOfAction(action.code());
        if (tool != null && toolTier > 0) {
            for (ToolDef.ToolEffect effect : tool.effects()) {
                int value = Math.max(0, effect.bp()) * toolTier;
                switch (effect.kind()) {
                    case EffectKind.SPEED -> speed += value;
                    case EffectKind.XP -> xp += value;
                    case EffectKind.YIELD -> yield += value;
                    case EffectKind.RARE -> rare += value;
                    case EffectKind.EFFICIENCY -> efficiency += value;
                    default -> {
                    }
                }
            }
        }

        // 2) 装备:蓝本值 × 品质倍率 × (10 + 5×强化)/10;词缀直接相加
        if (worn != null) {
            for (WornGear wornGear : worn) {
                if (wornGear == null) {
                    continue;
                }
                EquipmentDef gear = content.gear(wornGear.code());
                if (gear == null) {
                    continue;
                }
                int enhance = enhanceOf(wornGear);
                int qualityBp = qualityBp(content, wornGear.quality());
                for (EquipmentDef.EffectDef effect : gear.effects()) {
                    if (!matches(effect.target(), skillCode, kind) && !isGlobal(effect.kind())) {
                        continue;
                    }
                    int value = scaled(effect.bp(), qualityBp, enhance);
                    int hours = scaled(effect.hours(), qualityBp, enhance);
                    switch (effect.kind()) {
                        case EffectKind.SPEED -> speed += value;
                        case EffectKind.XP -> xp += value;
                        case EffectKind.YIELD -> yield += value;
                        case EffectKind.RARE -> rare += value;
                        case EffectKind.EFFICIENCY -> efficiency += value;
                        case EffectKind.OFFLINE_CAP -> capHours += hours;
                        case EffectKind.SELL_BONUS -> sell += value;
                        default -> {
                        }
                    }
                }
                for (Affix affix : wornGear.affixes()) {
                    if (affix == null || (!matches(affix.target(), skillCode, kind) && !isGlobal(affix.kind()))) {
                        continue;
                    }
                    int value = Math.max(0, affix.bp());
                    switch (affix.kind()) {
                        case EffectKind.SPEED -> speed += value;
                        case EffectKind.XP -> xp += value;
                        case EffectKind.YIELD -> yield += value;
                        case EffectKind.RARE -> rare += value;
                        case EffectKind.EFFICIENCY -> efficiency += value;
                        case EffectKind.SELL_BONUS -> sell += value;
                        default -> {
                        }
                    }
                }
            }
        }

        // 3) 套装:按穿齐件数折进,吃多档
        for (GearSetDef.SetStep step : setSteps(content, worn)) {
            for (EquipmentDef.EffectDef effect : step.effects()) {
                if (!matches(effect.target(), skillCode, kind) && !isGlobal(effect.kind())) {
                    continue;
                }
                int value = effect.bp();
                int hours = effect.hours();
                switch (effect.kind()) {
                    case EffectKind.SPEED -> speed += value;
                    case EffectKind.XP -> xp += value;
                    case EffectKind.YIELD -> yield += value;
                    case EffectKind.RARE -> rare += value;
                    case EffectKind.EFFICIENCY -> efficiency += value;
                    case EffectKind.OFFLINE_CAP -> capHours += hours;
                    case EffectKind.SELL_BONUS -> sell += value;
                    default -> {
                    }
                }
            }
        }

        // 4) 场地:按等级线性累加
        if (buildingLevels != null) {
            for (Map.Entry<String, Integer> entry : buildingLevels.entrySet()) {
                BuildingDef building = content.building(entry.getKey());
                if (building == null || entry.getValue() == null || entry.getValue() <= 0) {
                    continue;
                }
                int level = Math.min(entry.getValue(), building.maxLevel());
                for (BuildingDef.EffectDef effect : building.effects()) {
                    if (!matches(effect.target(), skillCode, kind) && !isGlobal(effect.kind())) {
                        continue;
                    }
                    int value = effect.bp() * level;
                    int hours = effect.hours() * level;
                    switch (effect.kind()) {
                        case EffectKind.SPEED -> speed += value;
                        case EffectKind.XP -> xp += value;
                        case EffectKind.YIELD -> yield += value;
                        case EffectKind.RARE -> rare += value;
                        case EffectKind.EFFICIENCY -> efficiency += value;
                        case EffectKind.OFFLINE_CAP -> capHours += Math.max(0, hours);
                        case EffectKind.SELL_BONUS -> sell += value;
                        default -> {
                        }
                    }
                }
            }
        }
        return new Bonus(speed, xp, yield, rare, efficiency, sell, capHours).capped();
    }

    /** 当前穿戴已触发(件数够)的套装档位,按内容表顺序 */
    public static List<GearSetDef.SetStep> setSteps(HomesteadContent content, List<WornGear> worn) {
        Map<String, Integer> counts = setCounts(content, worn);
        if (counts.isEmpty()) {
            return List.of();
        }
        List<GearSetDef.SetStep> active = new ArrayList<>();
        for (GearSetDef set : content.gearSets()) {
            int pieces = counts.getOrDefault(set.code(), 0);
            if (pieces <= 0) {
                continue;
            }
            for (GearSetDef.SetStep step : set.steps()) {
                if (pieces >= step.pieces()) {
                    active.add(step);
                }
            }
        }
        return active;
    }

    /** 穿戴的各套装件数(仅含有穿戴的套装,按内容表顺序) */
    public static Map<String, Integer> setCounts(HomesteadContent content, List<WornGear> worn) {
        if (content == null || worn == null || worn.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (WornGear wornGear : worn) {
            EquipmentDef gear = wornGear == null ? null : content.gear(wornGear.code());
            if (gear == null || gear.set() == null) {
                continue;
            }
            counts.merge(gear.set(), 1, Integer::sum);
        }
        return counts;
    }

    /** 穿戴总战力:每件蓝本战力 × 品质倍率 × (10 + 5×强化)/10,至少 1;探险判定用 */
    public static int powerOf(HomesteadContent content, List<WornGear> worn) {
        if (content == null || worn == null || worn.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (WornGear wornGear : worn) {
            EquipmentDef gear = wornGear == null ? null : content.gear(wornGear.code());
            if (gear == null) {
                continue;
            }
            int value = scaled(gear.power(), qualityBp(content, wornGear.quality()), enhanceOf(wornGear));
            total += Math.max(1, value);
        }
        return total;
    }

    /**
     * 把某时刻仍生效的 buff 折进静态加成。
     * 同维直接相加(target ALL 与对应类别都算),最终统一封顶。
     */
    public static Bonus withBuffs(Bonus base, String skillKind, List<BuffSpan> buffs, long at) {
        Bonus out = base == null ? NONE : base;
        if (buffs == null || buffs.isEmpty()) {
            return out.capped();
        }
        int speed = 0;
        int xp = 0;
        int yield = 0;
        int rare = 0;
        for (BuffSpan buff : buffs) {
            if (buff == null || buff.expireAt() <= at) {
                continue;
            }
            if (!matches(buff.target(), null, skillKind)) {
                continue;
            }
            switch (buff.kind()) {
                case EffectKind.SPEED -> speed += Math.max(0, buff.bp());
                case EffectKind.XP -> xp += Math.max(0, buff.bp());
                case EffectKind.YIELD -> yield += Math.max(0, buff.bp());
                case EffectKind.RARE -> rare += Math.max(0, buff.bp());
                default -> {
                }
            }
        }
        if (speed == 0 && xp == 0 && yield == 0 && rare == 0) {
            return out.capped();
        }
        return out.plus(new Bonus(speed, xp, yield, rare, 0, 0L, 0)).capped();
    }

    /** 品质倍率(未知/空按 100%) */
    public static int qualityBp(HomesteadContent content, String quality) {
        RarityDef rarity = content == null || quality == null ? null : content.rarity(quality);
        return rarity == null ? HomesteadConst.BP_BASE : Math.max(1, rarity.multBp());
    }

    private static int enhanceOf(WornGear wornGear) {
        return Math.max(0, Math.min(wornGear.enhance(), HomesteadConst.MAX_GEAR_TIER));
    }

    /** 蓝本值 → 品质 × 强化后的值(整数向下取整) */
    private static int scaled(int base, int qualityBp, int enhance) {
        if (base <= 0) {
            return 0;
        }
        long byQuality = (long) base * qualityBp / HomesteadConst.BP_BASE;
        return (int) (byQuality * (10 + 5L * enhance) / 10);
    }

    /**
     * target 是否命中当前动作:ALL 恒命中;GATHER / PROCESS 比技能类别;其余按技能码比。
     * 传入 skillCode 为 null(如 buff 匹配)时只认类别。
     */
    private static boolean matches(String target, String skillCode, String skillKind) {
        if (target == null || EffectKind.TARGET_ALL.equals(target)) {
            return true;
        }
        if (EffectKind.TARGET_GATHER.equals(target) || EffectKind.TARGET_PROCESS.equals(target)) {
            return target.equals(skillKind);
        }
        return target.equals(skillCode);
    }

    /** 离线上限与卖价不区分技能类别 */
    private static boolean isGlobal(String kind) {
        return EffectKind.OFFLINE_CAP.equals(kind) || EffectKind.SELL_BONUS.equals(kind);
    }
}

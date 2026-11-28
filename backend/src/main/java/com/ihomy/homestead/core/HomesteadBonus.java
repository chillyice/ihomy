package com.ihomy.homestead.core;

import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.ActionDef;
import com.ihomy.homestead.content.BuildingDef;
import com.ihomy.homestead.content.EffectKind;
import com.ihomy.homestead.content.EquipmentDef;
import com.ihomy.homestead.content.HomesteadContent;
import com.ihomy.homestead.content.SkillDef;
import com.ihomy.homestead.content.ToolDef;

import java.util.List;
import java.util.Map;

/**
 * 小院加成聚合(纯函数,唯一口径):工具 + 已穿装备(含强化) + 场地 + 生效中的料理 buff
 * 全部折成同一组整数加成,引擎按这组值结算,前端只展示服务端给的结果。
 * <p>
 * 工具只作用于自己的动作;装备/场地按 target(GATHER / PROCESS / ALL)匹配技能类别;
 * buff 由引擎按剩余时间逐次叠加(这里不含 buff)。
 * 装备效果每强化一级 +50%(整数向下取整);场地效果按等级线性累加;工具效果按阶数线性累加。
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

    /** 已穿的一件装备(编码 + 强化等级 0–5) */
    public record WornGear(String code, int enhance) {
    }

    /** 一条生效中的料理 buff(kind/target/bp/到期时刻) */
    public record BuffSpan(String kind, String target, int bp, long expireAt) {
    }

    /**
     * 折叠当前动作的全部静态加成(工具 + 装备 + 场地),不含 buff。
     *
     * @param action          当前动作(工具按动作绑定)
     * @param toolTier        该动作工具的阶数(0 = 徒手)
     * @param worn            当前穿戴的装备(最多 5 件)
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

        // 2) 装备:每强化一级 +50%
        if (worn != null) {
            for (WornGear wornGear : worn) {
                if (wornGear == null) {
                    continue;
                }
                EquipmentDef gear = content.gear(wornGear.code());
                if (gear == null) {
                    continue;
                }
                int enhance = Math.max(0, Math.min(wornGear.enhance(), HomesteadConst.MAX_GEAR_TIER));
                for (EquipmentDef.EffectDef effect : gear.effects()) {
                    if (!matches(effect.target(), kind) && !isGlobal(effect.kind())) {
                        continue;
                    }
                    int value = effect.bp() * (10 + 5 * enhance) / 10;
                    int hours = effect.hours() * (10 + 5 * enhance) / 10;
                    switch (effect.kind()) {
                        case EffectKind.SPEED -> speed += value;
                        case EffectKind.XP -> xp += value;
                        case EffectKind.YIELD -> yield += value;
                        case EffectKind.RARE -> rare += value;
                        case EffectKind.EFFICIENCY -> efficiency += value;
                        case EffectKind.OFFLINE_CAP -> capHours += Math.max(0, hours);
                        case EffectKind.SELL_BONUS -> sell += Math.max(0, value);
                        default -> {
                        }
                    }
                }
            }
        }

        // 3) 场地:按等级线性累加
        if (buildingLevels != null) {
            for (Map.Entry<String, Integer> entry : buildingLevels.entrySet()) {
                BuildingDef building = content.building(entry.getKey());
                if (building == null || entry.getValue() == null || entry.getValue() <= 0) {
                    continue;
                }
                int level = Math.min(entry.getValue(), building.maxLevel());
                for (BuildingDef.EffectDef effect : building.effects()) {
                    if (!matches(effect.target(), kind) && !isGlobal(effect.kind())) {
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
                        case EffectKind.SELL_BONUS -> sell += Math.max(0, value);
                        default -> {
                        }
                    }
                }
            }
        }
        return new Bonus(speed, xp, yield, rare, efficiency, sell, capHours).capped();
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
            if (!matches(buff.target(), skillKind)) {
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

    /** target 是否命中当前技能类别(ALL 恒命中) */
    private static boolean matches(String target, String skillKind) {
        if (target == null || EffectKind.TARGET_ALL.equals(target)) {
            return true;
        }
        return target.equals(skillKind);
    }

    /** 离线上限与卖价不区分技能类别 */
    private static boolean isGlobal(String kind) {
        return EffectKind.OFFLINE_CAP.equals(kind) || EffectKind.SELL_BONUS.equals(kind);
    }
}

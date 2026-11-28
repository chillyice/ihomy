package com.ihomy.homestead.core;

import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.EquipmentDef;
import com.ihomy.homestead.content.HomesteadContent;
import com.ihomy.homestead.content.SkillDef;

import java.util.Map;

/**
 * 穿戴加成(纯函数,唯一口径):把五件衣裳的阶数折成整数加成,引擎与卖货都从这里取。
 * <p>
 * 与工具的区别:工具绑一个技能、只加快那个技能;穿戴按类别给(采集 / 加工 / 全部)、
 * 还能放宽离线上限、抬一点卖价。全部整数运算,不取时钟、不用浮点、不用随机。
 */
public final class HomesteadGear {

    private static final long HOUR_MS = 3600_000L;

    private HomesteadGear() {
    }

    /**
     * 穿戴总加成(基点与小时)。
     *
     * @param gatherBp     采集类技能每阶合计(来自 HAT)
     * @param processBp    加工类技能合计(来自 APRON)
     * @param allBp        全部技能合计(来自 SLEEVE)
     * @param sellBonusBp  卖价加成基点(来自 CHARM,200 = +2%)
     * @param extraCapHours 离线上限多补小时(来自 BOOTS,4 阶 5 = +20 小时)
     */
    public record Bonuses(int gatherBp, int processBp, int allBp, long sellBonusBp, int extraCapHours) {

        /** 离线补算上限:基础 24 小时 + 鞋子加成 */
        public long capMs() {
            return HomesteadConst.OFFLINE_CAP_MS + Math.max(0, extraCapHours) * HOUR_MS;
        }

        /** 某个技能类别的加速基点(ALL 与对应类别相加) */
        public int speedBpFor(String skillKind) {
            int bp = Math.max(0, allBp);
            if (SkillDef.KIND_GATHER.equals(skillKind)) {
                bp += Math.max(0, gatherBp);
            } else if (SkillDef.KIND_PROCESS.equals(skillKind)) {
                bp += Math.max(0, processBp);
            }
            return bp;
        }

        /**
         * 卖货折价:在赶集行情算完的铜板上再乘卖价加成,向下取整(整数口径)。
         * 与行情不叠加利率、只在此处乘一次,便于对账。
         */
        public long sellCoins(long baseCoins) {
            if (baseCoins <= 0) {
                return 0L;
            }
            long bp = HomesteadConst.BP_BASE + Math.max(0, sellBonusBp);
            return baseCoins * bp / HomesteadConst.BP_BASE;
        }
    }

    /** 无穿戴(未建档/内容缺失时的兜底) */
    public static final Bonuses NONE = new Bonuses(0, 0, 0, 0L, 0);

    /**
     * 按内容表与「部位 → 阶数」折叠出总加成。
     * 阶数按内容表的 steps 数量截断(内容缩水时老档不至于越界),未知部位忽略。
     */
    public static Bonuses of(HomesteadContent content, Map<String, Integer> tierBySlot) {
        if (content == null || tierBySlot == null || tierBySlot.isEmpty()) {
            return NONE;
        }
        int gatherBp = 0;
        int processBp = 0;
        int allBp = 0;
        long sellBp = 0L;
        int capHours = 0;
        for (EquipmentDef slot : content.equipment()) {
            Integer raw = tierBySlot.get(slot.code());
            int tier = raw == null ? 0 : Math.max(0, Math.min(raw, slot.steps().size()));
            if (tier <= 0 || slot.effect() == null) {
                continue;
            }
            EquipmentDef.EffectDef effect = slot.effect();
            switch (effect.kind()) {
                case EquipmentDef.EffectDef.KIND_SPEED -> {
                    int gain = Math.max(0, effect.bp()) * tier;
                    if (EquipmentDef.EffectDef.TARGET_GATHER.equals(effect.target())) {
                        gatherBp += gain;
                    } else if (EquipmentDef.EffectDef.TARGET_PROCESS.equals(effect.target())) {
                        processBp += gain;
                    } else {
                        allBp += gain;
                    }
                }
                case EquipmentDef.EffectDef.KIND_OFFLINE_CAP -> capHours += Math.max(0, effect.hours()) * tier;
                case EquipmentDef.EffectDef.KIND_SELL_BONUS -> sellBp += (long) Math.max(0, effect.bp()) * tier;
                default -> {
                    // 内容校验会拦未知 kind,这里静默跳过(新增效果类型尚未上线时老代码也能跑)
                }
            }
        }
        return new Bonuses(gatherBp, processBp, allBp, sellBp, capHours);
    }
}

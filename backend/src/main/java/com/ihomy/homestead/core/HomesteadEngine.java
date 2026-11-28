package com.ihomy.homestead.core;

import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.ActionDef;
import com.ihomy.homestead.content.HomesteadContent;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小院慢生活的结算引擎(纯函数,唯一数值口径)。
 * <p>
 * 约定:整数毫秒 / 基点倍率,不取时钟(now 由调用方给),不用浮点,不用随机数生成器。
 * <p>
 * 「概率」全部是确定性掷点:每次完成用 {@code mix64(seed ^ actionHash ^ (seq+1)*GOLDEN)}
 * 切三段比特分别判省料 / 多收 / 稀有,同一玩家同一动作同一次数永远得到同一结果,
 * 因此离线补算、重连补算与单测都能精确复现(刷新页面不会重掷)。
 * <p>
 * 一次结算只推进「当前一个动作」:挂机时长先按加成上限封顶(默认 24h,仓房/鞋可放宽),
 * 之后逐次完成——每次按当时等级与生效中的料理 buff 重算单次耗时/加成,材料耗尽停在 MATERIALS。
 */
public final class HomesteadEngine {

    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private HomesteadEngine() {
    }

    /** 结算输入(不可变) */
    public record SettleInput(
            ActionDef action,
            int level,
            long xp,
            long actionSeq,
            long progressMs,
            long lastSettleAt,
            long now,
            Map<String, Long> materials,
            HomesteadBonus.Bonus bonus,
            List<HomesteadBonus.BuffSpan> buffs,
            long capMs,
            long seed) {

        public SettleInput {
            materials = materials == null ? Map.of() : Map.copyOf(materials);
            bonus = bonus == null ? HomesteadBonus.NONE : bonus;
            buffs = buffs == null ? List.of() : List.copyOf(buffs);
            capMs = capMs > 0 ? capMs : HomesteadConst.OFFLINE_CAP_MS;
        }

        /** 口袋写法:上限取静态加成的 capMs,不做掷点扰动(seed=0) */
        public SettleInput(ActionDef action, int level, long xp, long actionSeq, long progressMs,
                           long lastSettleAt, long now, Map<String, Long> materials,
                           HomesteadBonus.Bonus bonus) {
            this(action, level, xp, actionSeq, progressMs, lastSettleAt, now, materials,
                    bonus, List.of(), bonus == null ? HomesteadConst.OFFLINE_CAP_MS : bonus.capMs(), 0L);
        }

        /** 无任何加成的写法(单测与徒手采集) */
        public SettleInput(ActionDef action, int level, long xp, long actionSeq, long progressMs,
                           long lastSettleAt, long now, Map<String, Long> materials) {
            this(action, level, xp, actionSeq, progressMs, lastSettleAt, now, materials,
                    HomesteadBonus.NONE);
        }
    }

    /** 结算结果 */
    public record SettleResult(
            long completions,
            long xpGained,
            int levelUps,
            int levelAfter,
            long xpAfter,
            long actionSeqAfter,
            Map<String, Long> consumed,
            Map<String, Long> gained,
            long elapsedMs,
            boolean capped,
            long progressMs,
            String stopReason) {

        /** 是否停下来了(材料耗尽) */
        public boolean stopped() {
            return stopReason != null;
        }
    }

    /** 当前速度倍率(基点):10000 + (等级-1)×100 + 各项加成 */
    public static long speedBp(int level, int bonusBp) {
        return HomesteadConst.SPEED_BASE_BP
                + (long) Math.max(0, level - 1) * HomesteadConst.LEVEL_STEP_BP
                + Math.max(0, bonusBp);
    }

    /** 单次动作耗时:ceil(baseMs × 10000 / speedBp),至少 1ms */
    public static long msPerItem(long baseMs, int level, int bonusBp) {
        long speedBp = speedBp(level, bonusBp);
        long ms = (baseMs * HomesteadConst.SPEED_BASE_BP + speedBp - 1) / speedBp;
        return Math.max(1L, ms);
    }

    /**
     * 结算。
     *
     * @param in      输入(当前动作/等级/经验/动作次数/余量/上次结算时刻/现在/库存/加成/buff/上限/掷点种子)
     * @param content 内容表(取经验曲线与满级)
     */
    public static SettleResult settle(SettleInput in, HomesteadContent content) {
        ActionDef action = in.action();
        int maxLevel = content.maxLevel();
        int level = Math.min(Math.max(in.level(), 1), maxLevel);
        long xp = Math.max(0, in.xp());
        // 经验已够但存档没升级:先补齐
        while (level < maxLevel) {
            int need = content.xpForNextLevel(level);
            if (need <= 0 || xp < need) {
                break;
            }
            xp -= need;
            level++;
        }

        long elapsed = in.now() - in.lastSettleAt();
        boolean capped = false;
        if (elapsed < 0) {
            elapsed = 0; // 时钟回拨:不补算,也不回退进度
        } else if (elapsed > in.capMs()) {
            elapsed = in.capMs();
            capped = true;
        }
        long totalMs = Math.max(0, in.progressMs()) + elapsed;

        Map<String, Long> stock = new HashMap<>();
        in.materials().forEach((k, v) -> {
            if (v != null && v > 0) {
                stock.put(k, v);
            }
        });
        Map<String, Long> consumed = new LinkedHashMap<>();
        Map<String, Long> gained = new LinkedHashMap<>();

        String skillKind = skillKindOf(content, action.skill());
        int actionHash = actionHash(action.code());
        long completions = 0;
        long xpGained = 0;
        int levelUps = 0;
        long seq = Math.max(0, in.actionSeq());
        long remaining = totalMs;
        // 当前这段剩余时间对应的墙上时刻:用于判断 buff 是否已到期(每完成一次次向前推进)
        long cursor = in.now() - totalMs;
        String stopReason = null;

        int guard = 0;
        while (remaining > 0 && guard++ < HomesteadConst.MAX_COMPLETIONS_PER_SETTLE) {
            HomesteadBonus.Bonus eff = HomesteadBonus.withBuffs(in.bonus(), skillKind, in.buffs(), cursor);
            long perItem = msPerItem(action.baseMs(), level, eff.speedBp());
            if (remaining < perItem) {
                break;
            }
            if (action.consumesMaterials() && !hasMaterials(action, stock)) {
                stopReason = HomesteadConst.STOP_MATERIALS;
                remaining = 0;
                break;
            }

            long roll = roll(in.seed(), actionHash, seq);
            boolean savedMaterials = action.consumesMaterials()
                    && rollBp(roll, 29) < eff.efficiencyBp();
            if (!savedMaterials) {
                for (ActionDef.ItemAmount input : action.inputs()) {
                    stock.merge(input.item(), -input.qty(), Long::sum);
                    consumed.merge(input.item(), input.qty(), Long::sum);
                }
            }

            List<ActionDef.ItemAmount> yields = action.yields();
            boolean bonusYield = !yields.isEmpty() && rollBp(roll, 13) < eff.yieldBp();
            for (int i = 0; i < yields.size(); i++) {
                ActionDef.ItemAmount output = yields.get(i);
                long qty = output.qty() + (bonusYield && i == 0 ? 1 : 0);
                gained.merge(output.item(), qty, Long::sum);
            }

            ActionDef.RareDrop rare = action.rare();
            if (rare != null && rare.chanceBp() > 0) {
                int chance = Math.min(HomesteadConst.MAX_CHANCE_BP, rare.chanceBp() + eff.rareBp());
                if (rollBp(roll, 0) < chance) {
                    gained.merge(rare.item(), rare.qty(), Long::sum);
                }
            }

            seq++;
            completions++;
            long xpGain = xpFor(action.xp(), eff.xpBp());
            xpGained += xpGain;
            xp += xpGain;
            int before = level;
            while (level < maxLevel) {
                int need = content.xpForNextLevel(level);
                if (need <= 0 || xp < need) {
                    break;
                }
                xp -= need;
                level++;
            }
            levelUps += level - before;

            remaining -= perItem;
            cursor += perItem;
        }

        return new SettleResult(
                completions,
                xpGained,
                levelUps,
                level,
                xp,
                seq,
                Map.copyOf(consumed),
                Map.copyOf(gained),
                elapsed,
                capped,
                stopReason != null ? 0 : remaining,
                stopReason);
    }

    /** 每次完成的经验(含经验加成,至少 1) */
    private static long xpFor(long baseXp, int xpBp) {
        if (baseXp <= 0) {
            return 0;
        }
        long gain = baseXp * (HomesteadConst.BP_BASE + (long) Math.max(0, xpBp)) / HomesteadConst.BP_BASE;
        return Math.max(1L, gain);
    }

    /** 库存是否够做一次 */
    private static boolean hasMaterials(ActionDef action, Map<String, Long> stock) {
        for (ActionDef.ItemAmount input : action.inputs()) {
            if (stock.getOrDefault(input.item(), 0L) < input.qty()) {
                return false;
            }
        }
        return true;
    }

    /** 一次完成的确定性掷点(引擎与单测共用同一口径) */
    static long roll(long seed, int actionHash, long seq) {
        return mix64(seed ^ actionHash ^ (seq + 1) * GOLDEN);
    }

    /** 从 64 位掷点里切出一段当 0–9999 的基点概率 */
    private static int rollBp(long roll, int shift) {
        return (int) Long.remainderUnsigned(roll >>> shift, HomesteadConst.BP_BASE);
    }

    /** splitmix64 收尾:雪崩好、无状态、跨 JVM 一致 */
    static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** 动作编码的 FNV-1a 32 位哈希(跨进程稳定,不用 String.hashCode) */
    static int actionHash(String code) {
        int hash = 0x811C9DC5;
        if (code != null) {
            for (byte b : code.getBytes(StandardCharsets.UTF_8)) {
                hash ^= (b & 0xFF);
                hash *= 0x01000193;
            }
        }
        return hash == 0 ? 1 : hash;
    }

    /** 动作所属技能的类别(有加成按 GATHER/PROCESS 分类,查不到按 ALL) */
    private static String skillKindOf(HomesteadContent content, String skillCode) {
        var skill = content == null ? null : content.skill(skillCode);
        return skill == null || skill.kind() == null ? "ALL" : skill.kind();
    }
}

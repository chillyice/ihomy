package com.ihomy.homestead.core;

import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.ActionDef;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小院慢生活的结算引擎(纯函数,唯一数值口径)。
 * <p>
 * 约定:整数毫秒 / 基点倍率,不取时钟(now 由调用方给),不用浮点,不用随机数
 * (稀有产出按动作累计次数 seq % every 确定性发放),因此离线补算与单测都可精确复现。
 * <p>
 * 一次结算只推进「当前一个动作」:挂机时长先按档位上限封顶(默认 24h,穿鞋后放宽),
 * 再按「升到下一级所需次数」分段,每段用该段起点的等级/工具/穿戴重算单次耗时(升级会变快),
 * 材料耗尽即停在 MATERIALS。
 */
public final class HomesteadEngine {

    private HomesteadEngine() {
    }

    /** 结算输入(不可变) */
    public record SettleInput(
            ActionDef action,
            int level,
            long xp,
            int toolTier,
            long actionSeq,
            long progressMs,
            long lastSettleAt,
            long now,
            Map<String, Long> materials,
            int speedBonusBp,
            long capMs) {

        public SettleInput {
            materials = materials == null ? Map.of() : Map.copyOf(materials);
            speedBonusBp = Math.max(0, speedBonusBp);
            capMs = capMs > 0 ? capMs : HomesteadConst.OFFLINE_CAP_MS;
        }

        /** 未穿戴时的写法(离线上限取全局默认) */
        public SettleInput(ActionDef action, int level, long xp, int toolTier, long actionSeq,
                           long progressMs, long lastSettleAt, long now, Map<String, Long> materials) {
            this(action, level, xp, toolTier, actionSeq, progressMs, lastSettleAt, now, materials,
                    0, HomesteadConst.OFFLINE_CAP_MS);
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

    /** 当前速度倍率(基点):10000 + (等级-1)×100 + 工具阶×800 */
    public static long speedBp(int level, int toolTier) {
        return speedBp(level, toolTier, 0);
    }

    /** 含穿戴加成的速度倍率:在上面基础上加穿戴给的基点(采集/加工/全部已由 HomesteadGear 折叠) */
    public static long speedBp(int level, int toolTier, int speedBonusBp) {
        return HomesteadConst.SPEED_BASE_BP
                + (long) Math.max(0, level - 1) * HomesteadConst.LEVEL_STEP_BP
                + (long) Math.max(0, toolTier) * HomesteadConst.TOOL_STEP_BP
                + Math.max(0, speedBonusBp);
    }

    /** 单次动作耗时:ceil(baseMs × 10000 / speedBp),至少 1ms */
    public static long msPerItem(long baseMs, int level, int toolTier) {
        return msPerItem(baseMs, level, toolTier, 0);
    }

    /** 含穿戴加成的单次动作耗时(与 speedBp 同口径) */
    public static long msPerItem(long baseMs, int level, int toolTier, int speedBonusBp) {
        long speedBp = speedBp(level, toolTier, speedBonusBp);
        long ms = (baseMs * HomesteadConst.SPEED_BASE_BP + speedBp - 1) / speedBp;
        return Math.max(1L, ms);
    }

    /**
     * 结算。
     *
     * @param in 输入(当前动作/等级/经验/工具阶/动作次数/余量/上次结算时刻/现在/库存)
     * @param content 内容表(取经验曲线与满级)
     */
    public static SettleResult settle(SettleInput in, com.ihomy.homestead.content.HomesteadContent content) {
        ActionDef action = in.action();
        int maxLevel = content.maxLevel();
        int level = Math.min(Math.max(in.level(), 1), maxLevel);
        long xp = Math.max(0, in.xp());
        // 经验已够但存档没升级:先补齐(升级不改经验口径,只是把等级推上去)
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

        long completions = 0;
        long xpGained = 0;
        int levelUps = 0;
        long seq = Math.max(0, in.actionSeq());
        String stopReason = null;

        int guard = 0;
        while (guard++ < HomesteadConst.MAX_SETTLE_SEGMENTS) {
            long perItem = msPerItem(action.baseMs(), level, in.toolTier(), in.speedBonusBp());
            long byTime = totalMs / perItem;
            if (byTime <= 0) {
                break;
            }
            long byLevel = itemsToNextLevel(level, xp, content.xpForNextLevel(level), action.xp(), maxLevel);
            long byMaterials = action.consumesMaterials() ? affordable(action, stock) : Long.MAX_VALUE;
            long n = Math.min(byTime, Math.min(byLevel, byMaterials));
            if (n <= 0) {
                if (action.consumesMaterials() && byMaterials <= 0) {
                    stopReason = HomesteadConst.STOP_MATERIALS;
                }
                break;
            }

            // 材料
            for (ActionDef.ItemAmount input : action.inputs()) {
                long use = input.qty() * n;
                stock.merge(input.item(), -use, Long::sum);
                consume(consumed, input.item(), use);
            }
            // 产出(含稀有)
            for (ActionDef.ItemAmount output : action.yields()) {
                gain(gained, output.item(), output.qty() * n);
            }
            ActionDef.RareDrop rare = action.rare();
            if (rare != null) {
                long before = seq / rare.every();
                long after = (seq + n) / rare.every();
                long drops = after - before;
                if (drops > 0) {
                    gain(gained, rare.item(), drops * rare.qty());
                }
            }
            seq += n;
            completions += n;
            xpGained += action.xp() * n;

            // 经验推进(可能连升多级)
            xp += action.xp() * n;
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

            totalMs -= n * perItem;
            if (action.consumesMaterials() && affordable(action, stock) <= 0) {
                stopReason = HomesteadConst.STOP_MATERIALS;
                break;
            }
            if (level == before) {
                break; // 没升级:剩余时间不够下一次(byTime 已取整)
            }
            // 升级了:等级变了,单次耗时变短,继续用剩余时间推进
        }

        if (stopReason != null) {
            totalMs = 0;
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
                totalMs,
                stopReason);
    }

    /** 距离下一级还差多少次动作(满级返回 Long.MAX_VALUE) */
    private static long itemsToNextLevel(int level, long xp, int need, long xpPerAction, int maxLevel) {
        if (level >= maxLevel || need <= 0) {
            return Long.MAX_VALUE;
        }
        long gap = Math.max(0, need - xp);
        if (xpPerAction <= 0) {
            return Long.MAX_VALUE;
        }
        return (gap + xpPerAction - 1) / xpPerAction;
    }

    /** 现有库存还能做多少次(取最紧的一种材料) */
    private static long affordable(ActionDef action, Map<String, Long> stock) {
        long min = Long.MAX_VALUE;
        for (ActionDef.ItemAmount input : action.inputs()) {
            long have = Math.max(0, stock.getOrDefault(input.item(), 0L));
            min = Math.min(min, have / input.qty());
        }
        return min == Long.MAX_VALUE ? 0 : min;
    }

    private static void consume(Map<String, Long> consumed, String item, long qty) {
        consumed.merge(item, qty, Long::sum);
    }

    private static void gain(Map<String, Long> gained, String item, long qty) {
        if (qty > 0) {
            gained.merge(item, qty, Long::sum);
        }
    }
}

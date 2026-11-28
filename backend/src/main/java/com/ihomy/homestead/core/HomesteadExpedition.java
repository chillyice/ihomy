package com.ihomy.homestead.core;

import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.ExpeditionDef;
import com.ihomy.homestead.content.HomesteadContent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 探险事件判定(纯函数,唯一口径)。
 * <p>
 * 一趟 = 区域内若干事件逐个判定:分数 = 事件技能等级 × 权重 + 穿戴总战力,再叠 ±10% 的确定性抖动
 * (同一趟同一事件永远同一个抖动,便于复算与展示)。分数 ≥ 难度即通过:
 * 按事件掉落表的权重抽一条(物品或装备);没通过只拿保底积分。
 * 积分在商店换宝箱或直接换物品;装备掉落的品质按区域下限起算。
 */
public final class HomesteadExpedition {

    private HomesteadExpedition() {
    }

    /** 单个事件的结果(报告里按顺序展示) */
    public record Step(int index,
                       String kind,
                       String skill,
                       int difficulty,
                       int score,
                       boolean passed,
                       int points,
                       String item,
                       long qty,
                       HomesteadLoot.Instance gear) {
    }

    /** 一趟探险的报告(落库为紧凑 JSON,前端渲染成「探险手记」) */
    public record Report(String region,
                         String regionName,
                         String regionNameEn,
                         int power,
                         List<Step> steps,
                         int points,
                         Map<String, Long> items,
                         List<HomesteadLoot.Instance> gear) {

        public Report {
            steps = steps == null ? List.of() : List.copyOf(steps);
            items = items == null ? Map.of() : Map.copyOf(items);
            gear = gear == null ? List.of() : List.copyOf(gear);
        }

        public int passedCount() {
            int passed = 0;
            for (Step step : steps) {
                if (step.passed()) {
                    passed++;
                }
            }
            return passed;
        }
    }

    /**
     * 结算一趟探险。
     *
     * @param seed        玩家维度种子(通常 userId)
     * @param tripId      探险记录 id(同一趟重算也一致)
     * @param skillLevels 技能码 → 等级(缺省 0)
     * @param worn        出发时穿戴的装备(战力口径与加成一致)
     */
    public static Report resolve(HomesteadContent content,
                                 ExpeditionDef.Region region,
                                 long seed,
                                 long tripId,
                                 Map<String, Integer> skillLevels,
                                 List<HomesteadBonus.WornGear> worn) {
        int power = HomesteadBonus.powerOf(content, worn);
        String salt = region.code() + "#" + tripId;
        Map<String, Long> items = new LinkedHashMap<>();
        List<HomesteadLoot.Instance> gear = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        int totalPoints = 0;

        List<ExpeditionDef.Event> events = region.events();
        for (int i = 0; i < events.size(); i++) {
            ExpeditionDef.Event event = events.get(i);
            int level = skillLevels == null ? 0 : skillLevels.getOrDefault(event.skill(), 0);
            int base = level * HomesteadConst.EXPEDITION_LEVEL_WEIGHT + power;
            long jitterRoll = HomesteadLoot.base(seed, salt + ":roll", i);
            int jitterBp = (int) Long.remainderUnsigned(jitterRoll >>> 13,
                    2L * HomesteadConst.EXPEDITION_JITTER_BP + 1) - HomesteadConst.EXPEDITION_JITTER_BP;
            int score = (int) ((long) base * (HomesteadConst.BP_BASE + jitterBp) / HomesteadConst.BP_BASE);
            boolean passed = score >= event.difficulty();

            int points = 0;
            String item = null;
            long qty = 0;
            HomesteadLoot.Instance dropped = null;
            if (passed) {
                points = event.points();
                ExpeditionDef.Loot loot = pick(event.loot(), HomesteadLoot.base(seed, salt + ":loot", i));
                if (loot != null) {
                    if (loot.gear()) {
                        String floor = loot.qualityFloor() != null && !loot.qualityFloor().isBlank()
                                ? loot.qualityFloor() : region.gearQualityFloor();
                        String code = pickGear(region.gearPool(), HomesteadLoot.base(seed, salt + ":gear", i));
                        dropped = HomesteadLoot.roll(content, code, seed, salt + ":inst", gear.size(), floor);
                        if (dropped != null) {
                            gear.add(dropped);
                        }
                    } else {
                        item = loot.item();
                        qty = loot.qty();
                        items.merge(item, qty, Long::sum);
                    }
                }
            } else {
                points = event.failPoints();
            }
            totalPoints += points;
            steps.add(new Step(i, event.kind(), event.skill(), event.difficulty(), score, passed,
                    points, item, qty, dropped));
        }
        return new Report(region.code(), region.name(), region.nameEn(), power, steps, totalPoints,
                items, gear);
    }

    /** 权重抽一条(权重和越大越容易中;空表返回 null);宝箱开箱与事件掉落共用这一口径 */
    public static ExpeditionDef.Loot pick(List<ExpeditionDef.Loot> pool, long roll) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        int total = 0;
        for (ExpeditionDef.Loot loot : pool) {
            total += Math.max(0, loot.weight());
        }
        if (total <= 0) {
            return pool.get(0);
        }
        int draw = (int) Long.remainderUnsigned(roll >>> 17, total);
        int cursor = 0;
        for (ExpeditionDef.Loot loot : pool) {
            cursor += Math.max(0, loot.weight());
            if (draw < cursor) {
                return loot;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** 区域装备池等概率抽一件 */
    private static String pickGear(List<String> pool, long roll) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        return pool.get((int) Long.remainderUnsigned(roll >>> 23, pool.size()));
    }
}

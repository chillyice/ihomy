package com.ihomy.homestead.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 探险内容(content/homestead/expeditions.json):区域(含事件与掉落)+ 积分商店。
 * <p>
 * 一趟探险 = 区域内若干事件逐个判定:分数 = 事件技能等级 × 权重 + 装备战力,再叠确定性抖动,
 * 达标即通过,按事件掉落表拿随机物品/装备,或拿积分(积分在商店换宝箱或直接换物品)。
 */
public final class ExpeditionDef {

    private ExpeditionDef() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Region(
            @JsonProperty("code") String code,
            @JsonProperty("name") String name,
            @JsonProperty("name_en") String nameEn,
            @JsonProperty("skill") String skill,
            @JsonProperty("require_level") int requireLevel,
            @JsonProperty("power_floor") int powerFloor,
            @JsonProperty("fee_coins") long feeCoins,
            @JsonProperty("supplies") List<ActionDef.ItemAmount> supplies,
            @JsonProperty("minutes") int minutes,
            @JsonProperty("gear_quality_floor") String gearQualityFloor,
            @JsonProperty("gear_pool") List<String> gearPool,
            @JsonProperty("events") List<Event> events) {

        public Region {
            supplies = supplies == null ? List.of() : List.copyOf(supplies);
            gearPool = gearPool == null ? List.of() : List.copyOf(gearPool);
            events = events == null ? List.of() : List.copyOf(events);
        }
    }

    /** 一个探险事件:kind 只影响文案(FIGHT/SCOUT/TRAP),判定口径一致 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
            @JsonProperty("kind") String kind,
            @JsonProperty("skill") String skill,
            @JsonProperty("difficulty") int difficulty,
            @JsonProperty("points") int points,
            @JsonProperty("fail_points") int failPoints,
            @JsonProperty("loot") List<Loot> loot) {

        public Event {
            loot = loot == null ? List.of() : List.copyOf(loot);
        }
    }

    /**
     * 一条掉落:item+qty 为物品;gear=true 表示从区域 gear_pool 里掷一件装备实例,
     * quality_floor 缺省取区域的 gear_quality_floor。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Loot(
            @JsonProperty("item") String item,
            @JsonProperty("qty") Integer qty,
            @JsonProperty("weight") int weight,
            @JsonProperty("gear") Boolean gear,
            @JsonProperty("quality_floor") String qualityFloor) {

        public Loot {
            qty = qty == null || qty < 1 ? 1 : qty;
            gear = gear != null && gear;
        }
    }

    /** 积分商店:CHEST = 宝箱(材料掉落 + 指定次数装备掉落);ITEM = 直接换物品(单价更贵) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Shop(
            @JsonProperty("code") String code,
            @JsonProperty("kind") String kind,
            @JsonProperty("name") String name,
            @JsonProperty("name_en") String nameEn,
            @JsonProperty("desc") String desc,
            @JsonProperty("desc_en") String descEn,
            @JsonProperty("points") int points,
            @JsonProperty("item") String item,
            @JsonProperty("qty") Integer qty,
            @JsonProperty("materials") Integer materials,
            @JsonProperty("gear_rolls") Integer gearRolls,
            @JsonProperty("gear_quality_floor") String gearQualityFloor,
            @JsonProperty("pool") List<Loot> pool) {

        public Shop {
            qty = qty == null || qty < 1 ? 1 : qty;
            // ITEM 档不写 materials / gear_rolls:Jackson 3 不接受缺字段进基本类型,缺省按 0
            materials = materials == null || materials < 0 ? 0 : materials;
            gearRolls = gearRolls == null || gearRolls < 0 ? 0 : gearRolls;
            pool = pool == null ? List.of() : List.copyOf(pool);
        }

        public boolean chest() {
            return "CHEST".equals(kind);
        }
    }
}

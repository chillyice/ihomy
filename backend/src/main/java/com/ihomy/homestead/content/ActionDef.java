package com.ihomy.homestead.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 动作条目(content/homestead/actions.json)。
 * inputs 为空即采集(可无限挂);加工动作材料耗尽会自动停。
 * rare 是确定性概率掉落:每次完成用 mix64(seed ^ 动作 ^ 次数) 掷点,命中给 rare.qty;
 * chanceBp 是基础掉率(基点,320 = 3.2%),实际掉率再加工具/装备/建筑/buff 的稀有加成。
 * require_building 不为空时,还要对应场地达到 require_building_level 级才能开工。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActionDef(
        @JsonProperty("code") String code,
        @JsonProperty("skill") String skill,
        @JsonProperty("name") String name,
        @JsonProperty("name_en") String nameEn,
        @JsonProperty("level") int level,
        @JsonProperty("seconds") int seconds,
        @JsonProperty("xp") long xp,
        @JsonProperty("inputs") List<ItemAmount> inputs,
        @JsonProperty("yields") List<ItemAmount> yields,
        @JsonProperty("rare") RareDrop rare,
        @JsonProperty("require_building") String requireBuilding,
        @JsonProperty("require_building_level") Integer requireBuildingLevel) {

    public ActionDef {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        yields = yields == null ? List.of() : List.copyOf(yields);
        requireBuildingLevel = requireBuildingLevel == null ? 0 : requireBuildingLevel;
    }

    /** 是否加工类(需要材料) */
    public boolean consumesMaterials() {
        return !inputs.isEmpty();
    }

    /** 一次动作的基础毫秒数 */
    public long baseMs() {
        return seconds * 1000L;
    }

    /** 是否被场地锁着(需要对应建筑到指定等级) */
    public boolean needsBuilding() {
        return requireBuilding != null && !requireBuilding.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemAmount(
            @JsonProperty("item") String item,
            @JsonProperty("qty") long qty) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RareDrop(
            @JsonProperty("item") String item,
            @JsonProperty("qty") long qty,
            @JsonProperty("chanceBp") Integer chanceBp) {

        public RareDrop {
            chanceBp = chanceBp == null ? 0 : chanceBp;
        }
    }
}

package com.ihomy.homestead.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 动作条目(content/homestead/actions.json)。
 * inputs 为空即采集(可无限挂);加工动作材料耗尽会自动停。
 * rare 是确定性稀有掉落:动作累计次数 seq 每满 every 次给一次。
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
        @JsonProperty("rare") RareDrop rare) {

    public ActionDef {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        yields = yields == null ? List.of() : List.copyOf(yields);
    }

    /** 是否加工类(需要材料) */
    public boolean consumesMaterials() {
        return !inputs.isEmpty();
    }

    /** 一次动作的基础毫秒数 */
    public long baseMs() {
        return seconds * 1000L;
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
            @JsonProperty("every") long every) {
    }
}

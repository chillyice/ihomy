package com.ihomy.homestead.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 工具条目(content/homestead/tools.json)。
 * 一个动作一件工具(66 件):steps[0] = 从无到有(制造),steps[1..4] = 1→5 阶强化。
 * 工具只作用于自己的动作;effects 按阶数线性叠加(kind=SPEED/XP/YIELD/RARE/EFFICIENCY,单位基点)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolDef(
        @JsonProperty("code") String code,
        @JsonProperty("action") String action,
        @JsonProperty("skill") String skill,
        @JsonProperty("name") String name,
        @JsonProperty("name_en") String nameEn,
        @JsonProperty("desc") String desc,
        @JsonProperty("desc_en") String descEn,
        @JsonProperty("require_level") int requireLevel,
        @JsonProperty("effects") List<ToolEffect> effects,
        @JsonProperty("steps") List<ToolStep> steps) {

    public ToolDef {
        effects = effects == null ? List.of() : List.copyOf(effects);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** 升到下一阶的花费(tier 为当前阶,0 = 还没制造) */
    public ToolStep nextStep(int tier) {
        return tier >= 0 && tier < steps.size() ? steps.get(tier) : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolStep(
            @JsonProperty("coins") long coins,
            @JsonProperty("materials") List<ActionDef.ItemAmount> materials) {

        public ToolStep {
            materials = materials == null ? List.of() : List.copyOf(materials);
        }
    }

    /** 工具效果(每阶数值,只作用于工具自己的动作) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolEffect(
            @JsonProperty("kind") String kind,
            @JsonProperty("bp") int bp) {
    }
}

package com.ihomy.homestead.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 工具条目(content/homestead/tools.json)。
 * 每技能一件;steps[i] = 从 i 阶升到 i+1 阶的花费,0 阶为徒手,最高等阶 = steps.size()。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolDef(
        @JsonProperty("code") String code,
        @JsonProperty("skill") String skill,
        @JsonProperty("name") String name,
        @JsonProperty("name_en") String nameEn,
        @JsonProperty("desc") String desc,
        @JsonProperty("desc_en") String descEn,
        @JsonProperty("steps") List<ToolStep> steps) {

    public ToolDef {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** 升到下一阶的花费(tier 为当前阶,0 起) */
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
}

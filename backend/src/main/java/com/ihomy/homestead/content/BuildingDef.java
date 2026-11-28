package com.ihomy.homestead.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 场地条目(content/homestead/buildings.json)。
 * 11 座技能工坊 + 仓房/晒场/井台,共 14 栋;每栋 0→10 级,effects 按级线性累加。
 * 工坊的 unlock_action 指向本技能第 6 个动作(门槛 70 级 + 本级 5 级)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BuildingDef(
        @JsonProperty("code") String code,
        @JsonProperty("skill") String skill,
        @JsonProperty("unlock_action") String unlockAction,
        @JsonProperty("name") String name,
        @JsonProperty("name_en") String nameEn,
        @JsonProperty("icon") String icon,
        @JsonProperty("desc") String desc,
        @JsonProperty("desc_en") String descEn,
        @JsonProperty("max_level") int maxLevel,
        @JsonProperty("effects") List<EffectDef> effects,
        @JsonProperty("steps") List<BuildingStep> steps) {

    public BuildingDef {
        effects = effects == null ? List.of() : List.copyOf(effects);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** 升到下一级的花费(level 为当前等级,0 = 还没建) */
    public BuildingStep nextStep(int level) {
        return level >= 0 && level < steps.size() ? steps.get(level) : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BuildingStep(
            @JsonProperty("coins") long coins,
            @JsonProperty("materials") List<ActionDef.ItemAmount> materials) {

        public BuildingStep {
            materials = materials == null ? List.of() : List.copyOf(materials);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EffectDef(
            @JsonProperty("kind") String kind,
            @JsonProperty("target") String target,
            @JsonProperty("bp") Integer bp,
            @JsonProperty("hours") Integer hours) {

        public EffectDef {
            bp = bp == null ? 0 : bp;
            hours = hours == null ? 0 : hours;
            target = target == null || target.isBlank() ? EffectKind.TARGET_ALL : target;
        }
    }
}

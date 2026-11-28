package com.ihomy.homestead.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 装备条目(content/homestead/equipment.json)。
 * 5 个部位(HEAD/BODY/HANDS/FEET/CHARM)各 3 件,共 15 件;一件装备要先制造、再强化 0→5。
 * <p>
 * craft = 制造花费;steps[i] = 强化 i→i+1 级的花费。
 * effects 是强化 0 级时的基础值,每强化一级 +50%(整数向下取整)。
 * kind:SPEED / XP / YIELD / RARE / EFFICIENCY / OFFLINE_CAP / SELL_BONUS;
 * target:GATHER / PROCESS / ALL(只有 SPEED/XP/YIELD/RARE/EFFICIENCY 用得上,离线上限与卖价按全部算)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EquipmentDef(
        @JsonProperty("code") String code,
        @JsonProperty("slot") String slot,
        @JsonProperty("skill") String skill,
        @JsonProperty("require_level") int requireLevel,
        @JsonProperty("name") String name,
        @JsonProperty("name_en") String nameEn,
        @JsonProperty("desc") String desc,
        @JsonProperty("desc_en") String descEn,
        @JsonProperty("effects") List<EffectDef> effects,
        @JsonProperty("craft") EquipmentStep craft,
        @JsonProperty("steps") List<EquipmentStep> steps) {

    public EquipmentDef {
        effects = effects == null ? List.of() : List.copyOf(effects);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** 强化到下一级的花费(level 为当前强化等级,0 起) */
    public EquipmentStep nextStep(int level) {
        return level >= 0 && level < steps.size() ? steps.get(level) : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EquipmentStep(
            @JsonProperty("coins") long coins,
            @JsonProperty("materials") List<ActionDef.ItemAmount> materials) {

        public EquipmentStep {
            materials = materials == null ? List.of() : List.copyOf(materials);
        }
    }

    /**
     * 效果定义(bp / hours 按类型只写一个,缺省归一成 0)。
     */
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

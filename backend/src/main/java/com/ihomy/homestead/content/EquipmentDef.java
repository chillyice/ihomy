package com.ihomy.homestead.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 穿戴条目(content/homestead/equipment.json)。
 * 五个部位各一件;steps[i] = 从 i 阶升到 i+1 阶的花费,0 阶 = 还没穿上。
 * <p>
 * effect 只放整数口径:
 * SPEED(按基点给 GATHER/PROCESS/ALL 某类技能加速)、
 * OFFLINE_CAP(每阶多补几小时离线)、
 * SELL_BONUS(每阶卖价高几个基点)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EquipmentDef(
        @JsonProperty("code") String code,
        @JsonProperty("name") String name,
        @JsonProperty("name_en") String nameEn,
        @JsonProperty("desc") String desc,
        @JsonProperty("desc_en") String descEn,
        @JsonProperty("effect") EffectDef effect,
        @JsonProperty("steps") List<EquipmentStep> steps) {

    public EquipmentDef {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** 升到下一阶的花费(tier 为当前阶,0 起) */
    public EquipmentStep nextStep(int tier) {
        return tier >= 0 && tier < steps.size() ? steps.get(tier) : null;
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
     * 效果定义(每阶数值;target 只给 SPEED 用)。
     * bp / hours 用包装类型:JSON 里按效果类型只写其中一个,缺省的那项归一成 0。
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
        }

        public static final String KIND_SPEED = "SPEED";
        public static final String KIND_OFFLINE_CAP = "OFFLINE_CAP";
        public static final String KIND_SELL_BONUS = "SELL_BONUS";

        public static final String TARGET_GATHER = "GATHER";
        public static final String TARGET_PROCESS = "PROCESS";
        public static final String TARGET_ALL = "ALL";
    }
}

package com.ihomy.homestead.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 装备蓝本(content/homestead/equipment.json 的 slots)。
 * 11 个槽位家族各 3 件打造档 + 1 件掉落专属,共 44 件;打造出来的叫「实例」,同一蓝本可以有多件。
 * <p>
 * source = CRAFT(可打造)/ DROP(只能从探险、宝箱掉落);CRAFT 必须有 craft,DROP 的 craft 为 null。
 * power = 战力(探险事件判定用),实例的实际战力再乘品质倍率与强化系数。
 * set = 套装编码(body/sets 里定义);dismantle = 分解返还的材料(按品质放大)。
 * <p>
 * effects 是强化 0 级、品质普通时的基础值:品质倍率 × 每强化一级 +50%(整数向下取整);词缀不吃强化。
 * kind:SPEED / XP / YIELD / RARE / EFFICIENCY / OFFLINE_CAP / SELL_BONUS;
 * target:GATHER / PROCESS / ALL / 技能码(定向到具体工作,如 FARM、FISHING)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EquipmentDef(
        @JsonProperty("code") String code,
        @JsonProperty("slot") String slot,
        @JsonProperty("skill") String skill,
        @JsonProperty("require_level") int requireLevel,
        @JsonProperty("source") String source,
        @JsonProperty("power") int power,
        @JsonProperty("set") String set,
        @JsonProperty("name") String name,
        @JsonProperty("name_en") String nameEn,
        @JsonProperty("desc") String desc,
        @JsonProperty("desc_en") String descEn,
        @JsonProperty("effects") List<EffectDef> effects,
        @JsonProperty("craft") EquipmentStep craft,
        @JsonProperty("dismantle") List<ActionDef.ItemAmount> dismantle,
        @JsonProperty("steps") List<EquipmentStep> steps) {

    public EquipmentDef {
        effects = effects == null ? List.of() : List.copyOf(effects);
        dismantle = dismantle == null ? List.of() : List.copyOf(dismantle);
        steps = steps == null ? List.of() : List.copyOf(steps);
        source = source == null || source.isBlank() ? "CRAFT" : source;
    }

    /** 强化到下一级的花费(level 为当前强化等级,0 起) */
    public EquipmentStep nextStep(int level) {
        return level >= 0 && level < steps.size() ? steps.get(level) : null;
    }

    public boolean craftable() {
        return "CRAFT".equals(source);
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

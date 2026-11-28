package com.ihomy.homestead.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 套装系列(content/homestead/equipment.json 的 sets)。
 * steps 按穿戴件数从少到多排列,穿够 pieces 件就把该档 effects 全部折进加成(可以叠多档)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GearSetDef(
        @JsonProperty("code") String code,
        @JsonProperty("name") String name,
        @JsonProperty("name_en") String nameEn,
        @JsonProperty("steps") List<SetStep> steps) {

    public GearSetDef {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SetStep(
            @JsonProperty("pieces") int pieces,
            @JsonProperty("effects") List<EquipmentDef.EffectDef> effects) {

        public SetStep {
            effects = effects == null ? List.of() : List.copyOf(effects);
        }
    }
}

package com.ihomy.homestead.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 掉落词缀池(content/homestead/equipment.json 的 affixes)。
 * 掉落装备按品质条数从池里确定性抽(同一件实例不会抽到重复词缀),具体数值走品质的 min_bp~max_bp。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AffixDef(
        @JsonProperty("kind") String kind,
        @JsonProperty("target") String target) {

    public AffixDef {
        target = target == null || target.isBlank() ? EffectKind.TARGET_ALL : target;
    }
}

package com.ihomy.homestead.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 品质档(content/homestead/equipment.json 的 rarities)。
 * multBp = 蓝本基础效果与战力的倍率(10000 = 100%);affixes = 掉落时随机词缀条数;
 * minBp / maxBp = 单条词缀的取值范围(整数基点,确定性掷点取区间内)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RarityDef(
        @JsonProperty("code") String code,
        @JsonProperty("name") String name,
        @JsonProperty("name_en") String nameEn,
        @JsonProperty("mult_bp") int multBp,
        @JsonProperty("affixes") int affixes,
        @JsonProperty("min_bp") int minBp,
        @JsonProperty("max_bp") int maxBp) {
}

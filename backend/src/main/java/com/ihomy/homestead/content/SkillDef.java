package com.ihomy.homestead.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 技能条目(content/homestead/skills.json)。
 * kind:GATHER(采集,动作不需要材料)/ PROCESS(加工,动作消耗材料)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SkillDef(
        @JsonProperty("code") String code,
        @JsonProperty("name") String name,
        @JsonProperty("name_en") String nameEn,
        @JsonProperty("kind") String kind,
        @JsonProperty("icon") String icon,
        @JsonProperty("desc") String desc,
        @JsonProperty("desc_en") String descEn) {

    public static final String KIND_GATHER = "GATHER";
    public static final String KIND_PROCESS = "PROCESS";
}

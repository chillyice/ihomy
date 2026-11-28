package com.ihomy.garden.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 技能树配置(content/skills.json,唯一真相源为 garden 仓 content/,构建期拷贝;玩法稿 §4.2)。
 * 层价按 tier 统一表(1/2/3/4/5 层每级 1/2/4/6/9 点,GardenConst.SKILL_TIER_COST);
 * live=false = 效果未落地,不可分配(§4.2.2「没有可用玩法时隐藏」);pending 注明等哪个系统。
 */
public final class SkillDefs {

    private SkillDefs() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillFile(@JsonProperty("trees") List<SkillTreeDef> trees) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillTreeDef(
            @JsonProperty("code") String code,
            @JsonProperty("name") String name,
            @JsonProperty("root") String root,
            @JsonProperty("nodes") List<SkillNodeDef> nodes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillNodeDef(
            @JsonProperty("code") String code,
            @JsonProperty("branch") String branch,
            @JsonProperty("tier") int tier,
            @JsonProperty("prereq") List<String> prereq,
            @JsonProperty("levels") int levels,
            @JsonProperty("release") String release,
            @JsonProperty("live") boolean live,
            @JsonProperty("venue") String venue,
            @JsonProperty("effectText") String effectText,
            @JsonProperty("pending") String pending) {

        /** P1 分期 */
        public boolean isP1() {
            return "P1".equals(release);
        }
    }
}

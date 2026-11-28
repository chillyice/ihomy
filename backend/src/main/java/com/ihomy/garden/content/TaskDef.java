package com.ihomy.garden.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 任务配置条目(content/tasks.json,唯一真相源为 garden 仓 content/,构建期拷贝;玩法稿 §3.2 / FR-35)。
 * 条件只许 7 类计数(HARVEST_N/SELL_COINS/HOLD_KINDS/FACILITY_N/CODEX_STAGE/CARE_N/PLANT_N),
 * window: null=累计 / STAGE=本阶解锁日起 / TERM=当前节气交接日起;状态类条件(HOLD_KINDS 等)window 无效。
 * titlesBySeason = 主线链与年终链的四季变体(按当季取文案);节气支线一条一节气、标题本身就是当季文案,不做变体。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskDef(
        @JsonProperty("code") String code,
        @JsonProperty("layer") String layer,
        @JsonProperty("stage") Integer stage,
        @JsonProperty("term") String term,
        @JsonProperty("order") Integer order,
        @JsonProperty("title") String title,
        @JsonProperty("condition") Condition condition,
        @JsonProperty("reward") Reward reward,
        @JsonProperty("card") String card,
        @JsonProperty("auto") String auto,
        @JsonProperty("titlesBySeason") Map<String, String> titlesBySeason,
        @JsonProperty("needsSeasonVariants") boolean needsSeasonVariants) {

    public enum Layer {
        STARTER, MAIN, MID, TERM_SIDE, SIDE, FINAL,
        /** 周挑战:从池里按周轮换取 3 条,window=WEEK,领取凭证带 ISO 周键(玩法稿 §15) */
        WEEKLY;

        public static Layer of(String code) {
            for (Layer l : values()) {
                if (l.name().equals(code)) {
                    return l;
                }
            }
            throw new IllegalArgumentException("未知任务层 " + code);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Condition(
            @JsonProperty("type") String type,
            @JsonProperty("value") int value,
            @JsonProperty("window") String window,
            @JsonProperty("stage") Integer stage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reward(
            @JsonProperty("coins") Integer coins,
            @JsonProperty("seed") String seed) {
    }

    /** auto 非空 = 随对应动作自动完成(FINAL_5 立架随 STAGE_ADVANCE_10),不参与领取与闸门 */
    public boolean isAuto() {
        return auto != null && !auto.isBlank();
    }

    /** 当季标题:有变体且该季文案非空则取之,否则回退中性 title(完整性由 GardenContent 启动校验兜底) */
    public String titleFor(String season) {
        if (titlesBySeason != null && season != null) {
            String t = titlesBySeason.get(season);
            if (t != null && !t.isBlank()) {
                return t;
            }
        }
        return title;
    }
}

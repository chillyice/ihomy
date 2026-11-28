package com.ihomy.garden.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 节气配置条目(content/solar_terms.json,唯一真相源为 garden 仓 content/,构建期拷贝;玩法稿 §14.4)。
 * date 为常年区间代表日(MM-dd);event 是节气事件系统(P2)的输入,本轮只随表下发、不生效。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SolarTermDef(
        @JsonProperty("code") String code,
        @JsonProperty("name") String name,
        @JsonProperty("nameEn") String nameEn,
        @JsonProperty("date") String date,
        @JsonProperty("event") String event) {

    /** 交接点所在月(1–12;小寒/大寒在 1 月,跨年由 GardenSolarTerms 取模处理) */
    public int month() {
        return Integer.parseInt(date.substring(0, 2));
    }

    /** 交接点所在日 */
    public int day() {
        return Integer.parseInt(date.substring(3, 5));
    }
}

package com.ihomy.garden.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 作物配置条目(content/crops.json,唯一真相源为 garden 仓 content/,构建期拷贝)。
 * 字段名与 JSON snake_case 一一对应;倍率以基点(int)参与运算,核心禁浮点(NFR-02)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CropDef(
        @JsonProperty("code") String code,
        @JsonProperty("name") String name,
        @JsonProperty("use") String use,
        @JsonProperty("form") String form,
        @JsonProperty("unlock_stage") int unlockStage,
        @JsonProperty("duration_hours") int durationHours,
        @JsonProperty("yield_min") int yieldMin,
        @JsonProperty("yield_max") int yieldMax,
        @JsonProperty("seed_price") int seedPrice,
        @JsonProperty("fruit_price") int fruitPrice,
        @JsonProperty("seasons") List<String> seasons,
        @JsonProperty("season_mult") double seasonMult,
        @JsonProperty("off_season_mult") double offSeasonMult,
        @JsonProperty("soil_preference") List<String> soilPreference,
        @JsonProperty("soil_compatible") List<String> soilCompatible,
        @JsonProperty("hardiness") String hardiness,
        @JsonProperty("temp_min_c") Integer tempMinC,
        @JsonProperty("temp_max_c") Integer tempMaxC,
        @JsonProperty("first_pot") boolean firstPot) {

    /** 本命季成长倍率(基点) */
    public int seasonMultBp() {
        return (int) Math.round(seasonMult * 100);
    }

    /** 逆季成长倍率(基点) */
    public int offSeasonMultBp() {
        return (int) Math.round(offSeasonMult * 100);
    }

    /**
     * 耐寒下限(℃):低于它算低温胁迫(玩法稿 §8.1)。未配置时按 hardiness 兜底——
     * NORMAL 5 / TOLERANT 2 / EXTREME 0,保证新作物漏配也不因 0 默认值把整片地判成受冻。
     */
    public int effectiveTempMinC() {
        if (tempMinC != null) {
            return tempMinC;
        }
        return switch (hardiness == null ? "NORMAL" : hardiness) {
            case "EXTREME" -> 0;
            case "TOLERANT" -> 2;
            default -> 5;
        };
    }

    /** 耐热上限(℃):高于它算高温胁迫;未配置时 NORMAL 30 / TOLERANT 33 / EXTREME 35 */
    public int effectiveTempMaxC() {
        if (tempMaxC != null) {
            return tempMaxC;
        }
        return switch (hardiness == null ? "NORMAL" : hardiness) {
            case "EXTREME" -> 35;
            case "TOLERANT" -> 33;
            default -> 30;
        };
    }
}

package com.ihomy.garden.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 场域配置(content/venues.json):土壤类型、室内标记与网格(每格带解锁阶段)。
 * 归属(PLOT_OWNERSHIP)是工程常量,不进 content(架构分册 §19-B)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VenueDef(
        @JsonProperty("code") String code,
        @JsonProperty("name") String name,
        @JsonProperty("soil_type") String soilType,
        @JsonProperty("indoor") boolean indoor,
        @JsonProperty("slots") List<SlotDef> slots) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SlotDef(
            @JsonProperty("slot_no") int slotNo,
            @JsonProperty("unlock_stage") int unlockStage) {
    }
}

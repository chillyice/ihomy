package com.ihomy.homestead.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 物品条目(content/homestead/items.json)。
 * category:MATERIAL(采集原料)/ PRODUCT(加工成品)/ RARE(稀有特产)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemDef(
        @JsonProperty("code") String code,
        @JsonProperty("name") String name,
        @JsonProperty("name_en") String nameEn,
        @JsonProperty("category") String category,
        @JsonProperty("sell_price") long sellPrice) {

    public static final String CAT_MATERIAL = "MATERIAL";
    public static final String CAT_PRODUCT = "PRODUCT";
    public static final String CAT_RARE = "RARE";
}

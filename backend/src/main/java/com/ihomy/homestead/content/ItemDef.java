package com.ihomy.homestead.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 物品条目(content/homestead/items.json)。
 * category:MATERIAL(采集原料)/ PRODUCT(加工成品)/ RARE(稀有特产)。
 * buff 只挂在部分成品上:使用后给一段时间加成(kind=SPEED/XP/YIELD/RARE,target=GATHER/PROCESS/ALL)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemDef(
        @JsonProperty("code") String code,
        @JsonProperty("name") String name,
        @JsonProperty("name_en") String nameEn,
        @JsonProperty("category") String category,
        @JsonProperty("sell_price") long sellPrice,
        @JsonProperty("buff") BuffDef buff) {

    public static final String CAT_MATERIAL = "MATERIAL";
    public static final String CAT_PRODUCT = "PRODUCT";
    public static final String CAT_RARE = "RARE";

    /** 是否是可以吃/喝的料理 */
    public boolean consumable() {
        return buff != null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BuffDef(
            @JsonProperty("kind") String kind,
            @JsonProperty("target") String target,
            @JsonProperty("bp") int bp,
            @JsonProperty("minutes") int minutes) {

        public static final String KIND_SPEED = "SPEED";
        public static final String KIND_XP = "XP";
        public static final String KIND_YIELD = "YIELD";
        public static final String KIND_RARE = "RARE";
    }
}

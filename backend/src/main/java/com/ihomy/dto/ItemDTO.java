package com.ihomy.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物品参数(family_item):四级粒度第4级。aliases 逗号分隔,3期 AI 匹配用。
 */
@Data
public class ItemDTO {
    private Long furnitureId;
    private Long roomId;
    private String name;
    private String aliases;
    private String position;
    private String imageUrl;
    private String type;
    private java.math.BigDecimal quantity;
    private String unit;
    private String note;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime storedAt;
    private Integer shelfLife;
    private String shelfLifeUnit;
    private java.math.BigDecimal relX;
    private java.math.BigDecimal relY;
}
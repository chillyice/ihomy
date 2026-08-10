package com.ihomy.dto;

import lombok.Data;

/**
 * 物品参数(family_item):四级粒度第4级。aliases 逗号分隔,3期 AI 匹配用。
 */
@Data
public class ItemDTO {
    private Long furnitureId;
    private String name;
    private String aliases;
    private String position;
    private String note;
}
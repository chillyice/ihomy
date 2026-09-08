package com.ihomy.dto;

import lombok.Data;

/**
 * 物品定位 AI 语义请求(V9.40):自然语言找物/放物。
 */
@Data
public class ItemAiDTO {
    /** 找物:自然语言描述(如 我的充电器在哪) */
    private String query;
    /** 放物:自然语言描述(如 把剪刀放在厨房抽屉里) */
    private String text;
}

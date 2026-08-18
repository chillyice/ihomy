package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物品实体(family_item):物品定位四级粒度第4级(位置为文本)。
 * aliases 存逗号分隔别名,3期 AI 拆解名称+别名后按此匹配。
 */
@Data
@TableName("family_item")
public class Item {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long furnitureId;
    private String name;
    private String aliases;
    private String position;
    private String imageUrl;
    private String type;
    private java.math.BigDecimal quantity;
    private String unit;
    private String note;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

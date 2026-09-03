package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家具实体(family_furniture):挂在房间下,物品定位四级粒度第3级。
 */
@Data
@TableName("family_furniture")
public class Furniture {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long roomId;
    private String name;
    private String type;
    private java.math.BigDecimal x;
    private java.math.BigDecimal y;
    private java.math.BigDecimal w;
    private java.math.BigDecimal h;
    private String note;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

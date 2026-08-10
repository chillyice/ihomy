package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分商品实体(family_points_product):家长上架的家庭虚拟物品。
 */
@Data
@TableName("family_points_product")
public class PointsProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String name;
    private String icon;
    private Integer points;
    private Integer stock;
    private Integer perLimit;
    private Integer enabled;
    private Long createdBy;
    private LocalDateTime createdAt;
}
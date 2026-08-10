package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 兑换订单实体(family_points_order):记录兑换快照,家长可核销确认使用。
 */
@Data
@TableName("family_points_order")
public class PointsOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long familyId;
    private Long productId;
    private String productName;
    private Integer pointsSpent;
    private String status;
    private LocalDateTime createdAt;
}
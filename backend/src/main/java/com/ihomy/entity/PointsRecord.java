package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水实体(family_points_record):每笔变动一行,balance 为变动后余额。
 */
@Data
@TableName("family_points_record")
public class PointsRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long familyId;
    private String changeType;
    /** 变动积分:change 为 MySQL 保留字,反引号转义 */
    @TableField("`change`")
    private Integer change;
    private Integer balance;
    private String remark;
    private LocalDateTime createdAt;
}
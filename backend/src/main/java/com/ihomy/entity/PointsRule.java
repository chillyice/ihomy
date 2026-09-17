package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分获取规则实体(family_points_rule):每家庭每功能一行,家长配置开关与分值。
 * 未配置的功能读取时回退 PointsRuleConst 默认值,不落库。
 */
@Data
@TableName("family_points_rule")
public class PointsRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    /** 功能 code(checkin/blog/diary/photo/video/task/game_petlink/plant_*,见 PointsRuleConst) */
    private String featureCode;
    /** 是否允许该功能获取积分:0 关闭 1 开启 */
    private Integer enabled;
    /** 每次/每单位获取积分(任务此项不生效) */
    private Integer points;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

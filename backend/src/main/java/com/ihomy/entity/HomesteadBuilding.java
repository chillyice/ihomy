package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小院慢生活场地(game_homestead_building):一人一栋一行,level 0 起(0 = 未建)。
 * 14 栋 0→10 级,效果按级线性累加;工坊 Lv5 解锁该技能的门槛 70 动作。
 */
@Data
@TableName("game_homestead_building")
public class HomesteadBuilding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String buildingCode;
    private Integer level;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

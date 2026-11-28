package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小院慢生活穿戴档(game_homestead_equipment):一人一部位一行,0 阶 = 未穿。
 * 效果与升阶花费在 content/homestead/equipment.json(HAT 采集加速 / APRON 加工加速 /
 * SLEEVE 全部加速 / BOOTS 离线上限 / CHARM 卖价加成),折叠口径见 HomesteadGear。
 */
@Data
@TableName("game_homestead_equipment")
public class HomesteadEquipment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String slotCode;
    private Integer tier;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

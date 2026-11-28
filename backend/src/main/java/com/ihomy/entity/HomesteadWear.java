package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小院慢生活穿戴(game_homestead_wear):一人 12 格一行,gear_id 为空即该格空着。
 * 蓝本按槽位家族挂(RING 家族两格都能穿),同一件实例只能穿一格;穿之前要等级达标。
 */
@Data
@TableName("game_homestead_wear")
public class HomesteadWear {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String slotCode;
    private Long gearId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

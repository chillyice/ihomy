package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小院慢生活穿戴(game_homestead_wear):一人一部位一行,gear_code 为空即该部位空着。
 * 5 个部位(HEAD/BODY/HANDS/FEET/CHARM)各只能穿一件,穿之前要已制造且等级达标。
 */
@Data
@TableName("game_homestead_wear")
public class HomesteadWear {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String slotCode;
    private String gearCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

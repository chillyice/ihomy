package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小院慢生活装备(game_homestead_gear):一人一件一行,只记「已制造」的装备。
 * 基础效果在 content/homestead/equipment.json;enhance 0–5,每级效果 +50%(折叠见 HomesteadBonus)。
 */
@Data
@TableName("game_homestead_gear")
public class HomesteadGear {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String gearCode;
    private Integer enhance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

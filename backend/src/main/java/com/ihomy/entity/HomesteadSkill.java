package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小院慢生活技能档(game_homestead_skill):一人一技能一行,建号时一次补齐全部技能。
 * xp 是当前等级内经验;满级后不再增长(引擎结算时会截断)。
 */
@Data
@TableName("game_homestead_skill")
public class HomesteadSkill {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String skillCode;
    private Integer level;
    private Long xp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

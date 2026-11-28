package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小院慢生活探险(game_homestead_expedition):一人可有多行历史,同一时刻只允许一行 ACTIVE。
 * 出发时扣费扣供给并记 ends_at;到点由 HomesteadService.advance() 惰性结算(在 FOR UPDATE 事务内),
 * 结算结果写 report(紧凑 JSON = 探险手记)并把 status 置 DONE,发奖在同一个事务里完成。
 */
@Data
@TableName("game_homestead_expedition")
public class HomesteadExpedition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String regionCode;
    /** ACTIVE 在途 / DONE 已结算 */
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endsAt;
    /** 探险手记(结算后写入;在途为空) */
    private String report;
    private LocalDateTime createdAt;
}

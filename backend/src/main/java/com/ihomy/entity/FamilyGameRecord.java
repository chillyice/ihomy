package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 游戏完成记录(family_game_record):成就指标的唯一游戏口径。
 * 单人 reward / 一起玩结算 / 家庭对战终局各落一行,只记来源与胜负,不存分数。
 */
@Data
@TableName("family_game_record")
public class FamilyGameRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long familyId;
    private String gameCode;
    /** SOLO / ROOM / MATCH */
    private String source;
    /** FINISH / WIN / LOSE */
    private String result;
    private LocalDateTime createdAt;
}

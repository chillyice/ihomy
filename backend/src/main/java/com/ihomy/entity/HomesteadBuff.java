package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小院慢生活料理 buff(game_homestead_buff):吃掉一份料理就插一行,到期后由服务端清理。
 * 同一时刻最多 3 条(超出丢掉最早到期的),效果按 target(GATHER/PROCESS/ALL)匹配技能类别。
 */
@Data
@TableName("game_homestead_buff")
public class HomesteadBuff {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 来源物品编码(展示用) */
    private String itemCode;
    private String kind;
    private String target;
    private Integer bp;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
}

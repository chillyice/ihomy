package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭游戏对战实体(family_game_match):同种子赛(ASYNC_SEED)与实时对战(REALTIME)。
 * 结算幂等由应用层保证;胜者按双方已报成绩在第二人 finish 时判定。
 */
@Data
@TableName("family_game_match")
public class GameMatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String gameCode;
    private String mode;
    private Long seed;
    private Long hostId;
    private Long guestId;
    private String status;
    private Long hostScore;
    private Long guestScore;
    private Long winnerId;
    private String resultJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}

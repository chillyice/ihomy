package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小院慢生活玩家档(game_homestead_player):一人一行,服务端权威。
 * action_code 为空即空闲;action_seq 是当前动作的累计次数(稀有掉落计数,换动作归零);
 * last_settle_at 是离线补算锚点,每次结算后推进到当前时刻(时钟回拨时也前移,避免反复白算)。
 */
@Data
@TableName("game_homestead_player")
public class HomesteadPlayer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long familyId;
    private Long coins;
    private String actionCode;
    private Long actionSeq;
    private Long actionProgressMs;
    private LocalDateTime lastSettleAt;
    private Long totalActions;
    private String contentVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

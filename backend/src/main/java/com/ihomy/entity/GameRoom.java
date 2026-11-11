package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭游戏房间(family_game_room):一次「一起玩」的房间。
 * 玩法 mode 为 PK(对抗)/COOP(合作),成员与准备状态在 family_game_room_member。
 * 一次可容纳 2~8 人;房间进行中的实时状态由房主经 WS game-signal 广播,服务端不落盘。
 */
@Data
@TableName("family_game_room")
public class GameRoom {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String gameCode;
    private String mode;
    private Long seed;
    private Long hostId;
    private Integer maxPlayers;
    private String status;
    private Long winnerId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}

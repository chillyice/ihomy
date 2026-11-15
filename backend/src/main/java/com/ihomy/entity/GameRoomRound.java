package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 房间对局成绩(family_game_room_round):「一起玩」房间内每一局每个参战成员的成绩快照,
 * 跨「再来一局」累计,构成房间内排行榜;房间与成员行删除后本表保留流水。
 */
@Data
@TableName("family_game_room_round")
public class GameRoomRound {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private Long familyId;
    private String gameCode;
    /** 本房间第几局(从 1 递增) */
    private Integer roundNo;
    private Long userId;
    /** 成员名快照(排行榜展示兜底,优先取当前昵称) */
    private String seatName;
    private Integer score;
    /** 1 = 本局胜者(COOP 无胜者恒 0) */
    private Integer isWinner;
    private LocalDateTime createdAt;
}

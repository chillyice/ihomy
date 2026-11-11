package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 游戏房间成员:准备状态、成绩与完成时间。
 * 成绩列 NULL=未上报;finishedAt 只在首次上报时写入(幂等,重复上报不覆盖)。
 */
@Data
@TableName("family_game_room_member")
public class GameRoomMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private Long familyId;
    private Long userId;
    private String role;
    private Integer ready;
    private Long score;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}

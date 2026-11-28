package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小院慢生活换装预设(game_homestead_loadout):一人最多 5 套,slot_index 1/2 建号即有,3–5 铜板解锁。
 * payload 是穿戴快照(HEAD:12,RING_A:15),空串即空预设;应用时按快照逐格换装,快照里已不存在的实例跳过。
 */
@Data
@TableName("game_homestead_loadout")
public class HomesteadLoadout {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 预设序号(1–5) */
    private Integer slotIndex;
    private String name;
    /** 穿戴快照(槽位:实例 id,逗号分隔;空串 = 空预设) */
    private String payload;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

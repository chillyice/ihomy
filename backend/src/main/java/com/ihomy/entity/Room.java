package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 房间实体(family_room):物品定位五级粒度第3级,挂在房子下(floor 为房子内楼层),
 * 2期户型图以 room.id 挂载坐标。
 */
@Data
@TableName("family_room")
public class Room {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long houseId;
    private String name;
    private Integer floor;
    private Integer sortOrder;
    private String note;
    private String geometry;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

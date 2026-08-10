package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 房子实体(family_house):物品定位五级粒度第2级。
 * 部分家庭不止一套房产,房子下再分楼层(房间.floor)与房间。
 */
@Data
@TableName("family_house")
public class House {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String name;
    private String address;
    private Integer sortOrder;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
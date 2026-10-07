package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭智能设备实体(sys_iot_device):从 Home Assistant 实体状态同步,family_id 隔离。
 * room 为自由文本(中控页手工分组),lastSampleAt 用于历史采样节流(变更或满 10 分钟才落一条)。
 */
@Data
@TableName("sys_iot_device")
public class IotDevice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String entityId;
    private String name;
    private String domain;
    private String deviceClass;
    private String unit;
    private String room;
    private String state;
    private Integer enabled;
    private LocalDateTime lastSeenAt;
    private LocalDateTime lastSampleAt;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}

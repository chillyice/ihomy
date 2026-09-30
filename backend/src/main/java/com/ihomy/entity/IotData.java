package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能设备历史数据实体(sys_iot_data):按 (device_id, created_at) 查历史曲线,每晚清理旧数据。
 */
@Data
@TableName("sys_iot_data")
public class IotData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private String value;
    private LocalDateTime createdAt;
}

package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 存储设备实体(sys_storage_device):家庭级独立配置,
 * device_type SYSTEM系统/NAS/REMOTE远程磁盘/MOUNT挂载,root_path 为服务器可访问根目录。
 */
@Data
@TableName("sys_storage_device")
public class StorageDevice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String name;
    private String deviceType;
    private String rootPath;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
}
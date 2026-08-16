package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统参数实体(sys_parameter):name/value 键值对。
 * 当前用途:存 AES 加密盐值(aes-salt),用于解密外挂配置文件中的 ENC(...) 密文。
 */
@Data
@TableName("sys_parameter")
public class SysParameter {
    @TableId(type = IdType.INPUT)
    private String name;
    private String value;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

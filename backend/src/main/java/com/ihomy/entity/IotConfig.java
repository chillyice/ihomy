package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能家居接入配置实体(sys_iot_config):每家庭一条,存 Home Assistant 站点地址与长期访问令牌。
 * ihomy 只调 HA 的 REST API 读状态/控制设备;token 存 ENC 密文,出接口只回 hasToken。
 */
@Data
@TableName("sys_iot_config")
public class IotConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String baseUrl;
    private String token;
    private Integer enabled;
    private LocalDateTime lastSyncAt;
    private String lastError;
    private Long createdBy;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}

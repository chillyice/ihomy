package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 和风天气凭证实体(sys_weather_credential):多环境凭证账本,同时仅一条 status=1 启用。
 * 私钥 PEM 用于 JWT 签名,公钥 PEM 仅作对照(验证签名用)。
 */
@Data
@TableName("sys_weather_credential")
public class WeatherCredential {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String env;
    private String name;
    private String apiHost;
    private String projectId;
    private String keyId;
    private String publicKey;
    private String privateKey;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

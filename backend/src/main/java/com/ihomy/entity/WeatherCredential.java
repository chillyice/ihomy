package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 天气 API 凭证实体(sys_weather_credential):多环境 + 多天气源(provider)账本,同时仅一条 status=1 启用。
 * 和风(QWEATHER)用 apiHost/projectId/keyId/publicKey/privateKey(私钥 PEM 用于 JWT 签名);
 * 其他天气源(OPENWEATHER/AMAP)凭证走 configJson(整体 ENC 加密 JSON,如 {"apiKey":"..."})。
 */
@Data
@TableName("sys_weather_credential")
public class WeatherCredential {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String env;
    private String name;
    private String provider;
    private String apiHost;
    private String projectId;
    private String keyId;
    private String publicKey;
    private String privateKey;
    private String configJson;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

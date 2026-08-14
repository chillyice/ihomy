package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 和风天气 API 调用日志实体(sys_weather_log):每次 callApi 记录一条。
 * 天气数据公开可存;quota 接口响应可能含账号信息不存。
 */
@Data
@TableName("sys_weather_log")
public class WeatherLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String apiType;
    private String locationId;
    private String status;
    private Integer costMs;
    private String response;
    private String errorMsg;
    private LocalDateTime createdAt;
}

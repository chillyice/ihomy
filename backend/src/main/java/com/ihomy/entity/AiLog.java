package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭 AI 调用日志实体(report_ai):每次 AI 调用记录一条。
 * feature_code 区分功能(ITEM_FIND/ITEM_PUT/CHAT/IMAGE/WEATHER_IMAGE/ASR);family_id 家庭隔离;
 * status=SUCCESS/FAIL,不记录请求/响应内容(可能含用户隐私)。
 */
@Data
@TableName("report_ai")
public class AiLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String featureCode;
    private String model;
    private String provider;
    private String status;
    private Integer costMs;
    private String errorMsg;
    private LocalDateTime createdAt;
}

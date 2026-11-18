package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 家庭成员睡眠会话(family_health_sleep):服务端从上报的分期区间聚合出的派生数据,一晚一来源一行。
 * 重算即整窗物理替换,故表上不设 deleted 逻辑删列(物理删一律走 Mapper XML)。
 */
@Data
@TableName("family_health_sleep")
public class FamilyHealthSleep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long userId;
    private LocalDate sleepDate;
    private String kind;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer totalMin;
    private Integer deepMin;
    private Integer lightMin;
    private Integer remMin;
    private Integer awakeMin;
    private String stageLevel;
    private String source;
    private String sourceApp;
    private String provider;
    private LocalDateTime computedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

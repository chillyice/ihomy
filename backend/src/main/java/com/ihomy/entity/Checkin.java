package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 签到记录(family_checkin):每日一次,user_id+checkin_date 唯一约束。
 */
@Data
@TableName("family_checkin")
public class Checkin {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long familyId;
    private LocalDate checkinDate;
    private Integer points;
    private Integer streak;
    private LocalDateTime createdAt;
}
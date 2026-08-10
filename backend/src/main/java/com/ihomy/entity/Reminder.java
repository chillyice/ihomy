package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 提醒事项实体(family_reminder):到点全家站内通知,支持一次性/每日/每周/每月重复。
 */
@Data
@TableName("family_reminder")
public class Reminder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String title;
    private String content;
    private LocalDate remindDate;
    private LocalTime remindTime;
    private String repeatType;
    private Integer done;
    private Long createdBy;
    private LocalDateTime createdAt;
}
package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 家庭纪念日实体(family_anniversary):calendar solar/lunar(闰月 isLeap),每年重复。
 * 关联成员为多对多(V10.11 起),见 family_anniversary_member;无关联=家庭级纪念日。
 * 提前提醒:remindDaysBefore>0 时到点由 AnniversaryReminderService 通知全家,remindTime 空=08:00。
 */
@Data
@TableName("family_anniversary")
public class Anniversary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String calendar;
    private Integer month;
    private Integer day;
    private Integer isLeap;
    private Long familyId;
    private String recurring;
    /** 提前几天提醒(0/NULL=不提醒);关闭提醒时要能写回 NULL,故 updateStrategy=ALWAYS */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer remindDaysBefore;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalTime remindTime;
    private Long createdBy;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
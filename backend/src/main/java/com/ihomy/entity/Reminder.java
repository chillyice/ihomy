package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 提醒事项实体(family_reminder):到点全家站内通知,支持一次性/每日/每周/每月/每年重复。
 * 每年重复可选择阳历或农历(闰月),农历字段只在 calendar=lunar 时有值;
 * 循环提醒的完成态按日记录在 family_reminder_check,done 列只对一次性生效。
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
    /** 历法 solar/lunar,仅每年重复时有意义 */
    private String calendar;
    /** 农历月(1-12);从农历切回阳历要能写回 NULL,故 updateStrategy=ALWAYS */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer lunarMonth;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer lunarDay;
    /** 农历是否闰月:0 否 / 1 是 */
    private Integer isLeap;
    private Integer done;
    /** 关联计划ID:非空=由家庭计划托管,提醒侧只允许勾完成 */
    private Long planId;
    private Long createdBy;
    private LocalDateTime createdAt;
}

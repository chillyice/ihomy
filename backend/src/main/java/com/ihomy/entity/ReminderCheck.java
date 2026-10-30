package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 循环提醒按日完成记录(family_reminder_check):一行 = 某条提醒在某天已完成。
 * 一次性提醒仍用 family_reminder.done;循环提醒取消完成即删掉当天这行。
 */
@Data
@TableName("family_reminder_check")
public class ReminderCheck {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reminderId;
    private Long familyId;
    private LocalDate checkDate;
    private Long doneBy;
    private LocalDateTime createdAt;
}

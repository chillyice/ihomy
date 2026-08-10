package com.ihomy.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 提醒事项表单:repeatType 0一次性 1每日 2每周 3每月。
 */
@Data
public class ReminderDTO {
    private String title;
    private String content;
    private LocalDate remindDate;
    private LocalTime remindTime;
    private Integer repeatType;
}
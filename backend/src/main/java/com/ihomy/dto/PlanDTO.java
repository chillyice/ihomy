package com.ihomy.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 家庭计划表单:创建/编辑计划主体。
 * remindTime 是 {@code HH:mm} 或 {@code HH:mm:ss} 字符串:null=不修改,空串=清除计划提醒,其余=设置提醒时刻。
 * 用字符串而非 LocalTime,是为了区分「字段未传(不改)」与「传空串(清除)」。
 */
@Data
public class PlanDTO {
    private String title;
    private String description;
    private LocalDate targetDate;
    private String status;
    private String remindTime;
}

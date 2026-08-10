package com.ihomy.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 计划子任务表单:新增或编辑(勾选完成/改指派/截止)。
 */
@Data
public class PlanTaskDTO {
    private String title;
    private Long assigneeId;
    private LocalDate dueDate;
    private Integer done;
}
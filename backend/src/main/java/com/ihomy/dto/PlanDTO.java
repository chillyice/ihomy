package com.ihomy.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 家庭计划表单:创建/编辑计划主体。
 */
@Data
public class PlanDTO {
    private String title;
    private String description;
    private LocalDate targetDate;
    private Integer status;
}
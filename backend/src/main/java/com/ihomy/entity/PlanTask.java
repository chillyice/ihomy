package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 计划子任务实体(family_plan_task):挂靠家庭计划,全部完成则计划自动完成。
 */
@Data
@TableName("family_plan_task")
public class PlanTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private String title;
    private Long assigneeId;
    private LocalDate dueDate;
    private Integer done;
    private LocalDateTime createdAt;
}
package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 家庭计划实体(family_plan):中长期目标,进度由子任务完成度决定。
 */
@Data
@TableName("family_plan")
public class FamilyPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String title;
    private String description;
    private LocalDate targetDate;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
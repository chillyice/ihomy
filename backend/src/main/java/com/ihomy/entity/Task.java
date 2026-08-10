package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 悬赏任务实体(family_task):成员发布,他人领取完成,发布者确认后发放奖励。
 * 状态:OPEN 待领取 / IN_PROGRESS 进行中 / REVIEW 待确认 / DONE 已完成 / CANCELLED 已取消。
 */
@Data
@TableName("family_task")
public class Task {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String title;
    private String description;
    private String rewardType;
    private Integer rewardPoints;
    private String rewardItem;
    private String status;
    private Long createdBy;
    private Long assigneeId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
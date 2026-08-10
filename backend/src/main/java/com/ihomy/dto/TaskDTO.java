package com.ihomy.dto;

import lombok.Data;

/**
 * 悬赏任务表单:rewardType 0无奖励/1积分(rewardPoints)/2自定义物品(rewardItem)。
 */
@Data
public class TaskDTO {
    private String title;
    private String description;
    private Integer rewardType;
    private Integer rewardPoints;
    private String rewardItem;
}
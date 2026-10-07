package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 悬赏任务表单:rewardType 0无奖励/1积分(rewardPoints)/2自定义物品(rewardItem)。
 */
@Data
public class TaskDTO {
    @NotBlank(message = "请填写任务标题")
    @Size(max = 100, message = "标题最多 100 字")
    private String title;
    @Size(max = 500, message = "任务说明最多 500 字")
    private String description;
    private String rewardType;
    private Integer rewardPoints;
    @Size(max = 100, message = "奖励物品最多 100 字")
    private String rewardItem;
}

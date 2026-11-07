package com.ihomy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 对战成绩上报:本局得分(2048=分数;贪吃蛇=食物数)。
 */
@Data
public class GameMatchFinishDTO {
    @Min(value = 0, message = "成绩不能为负")
    @Max(value = 9999999, message = "成绩不合法")
    private Long score;
}

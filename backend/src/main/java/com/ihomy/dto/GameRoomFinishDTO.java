package com.ihomy.dto;

import lombok.Data;

/** 房间成绩上报:score 为分数字段;合作模式结束时房主也用它收尾 */
@Data
public class GameRoomFinishDTO {
    private Long score;
}

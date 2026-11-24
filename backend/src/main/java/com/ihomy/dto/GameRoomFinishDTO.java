package com.ihomy.dto;

import lombok.Data;

/** 房间成绩上报:score 为分数字段;合作模式结束时房主也用它收尾 */
@Data
public class GameRoomFinishDTO {
    private Long score;
    /** 八球由房主上报实际胜者，轮次标识按字符串传输以保留完整精度。 */
    private Long winnerId;
    private String roundToken;
}

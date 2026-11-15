package com.ihomy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 房间让位请求:把自己的参战席位让给指定的观战成员(对局进行中)。
 */
@Data
public class GameRoomYieldDTO {
    @NotNull(message = "要让位的对象不能为空")
    private Long toUserId;
}

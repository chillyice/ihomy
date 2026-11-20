package com.ihomy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 房主请成员离开房间:只能由房主发起,不能指向自己。
 */
@Data
public class GameRoomKickDTO {
    @NotNull(message = "要请走的人不能为空")
    private Long targetUserId;
}

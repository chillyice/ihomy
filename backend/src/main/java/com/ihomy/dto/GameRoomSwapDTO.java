package com.ihomy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 房间换位申请:观战者向指定的参战玩家发起,对方同意后两人互换席位。
 */
@Data
public class GameRoomSwapDTO {
    @NotNull(message = "要交换的对象不能为空")
    private Long targetUserId;
}

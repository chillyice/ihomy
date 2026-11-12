package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 房间切换游戏:房主在游戏室内换玩的游戏(仅等待中),换完按新游戏席位随机分配玩家/观战。
 */
@Data
public class GameRoomGameDTO {
    @NotBlank(message = "游戏不能为空")
    @Size(max = 32, message = "游戏标识过长")
    private String gameCode;
}

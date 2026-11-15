package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 房间切换游戏/玩法(仅等待中):
 * 换游戏——按进房顺序重排席位并重掷 seed;只传 mode(同游戏)——仅换玩法,不动席位与 seed。
 */
@Data
public class GameRoomGameDTO {
    @NotBlank(message = "游戏不能为空")
    @Size(max = 32, message = "游戏标识过长")
    private String gameCode;

    @Size(max = 16, message = "玩法标识过长")
    private String mode;
}

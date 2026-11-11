package com.ihomy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建游戏房间:游戏 code + 玩法(PK/COOP)+ 人数上限。
 */
@Data
public class GameRoomCreateDTO {
    @Size(max = 32, message = "游戏标识过长")
    private String gameCode;

    @Size(max = 10, message = "玩法不正确")
    private String mode;

    @Min(value = 2, message = "至少 2 人才能一起玩")
    @Max(value = 8, message = "最多 8 人一起玩")
    private Integer maxPlayers;
}

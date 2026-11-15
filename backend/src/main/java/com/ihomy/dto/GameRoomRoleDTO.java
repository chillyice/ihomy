package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 房间席位直切:PLAYER 参战 / SPECTATOR 观战(取值合法性在 service 校验)。
 */
@Data
public class GameRoomRoleDTO {
    @NotBlank(message = "要切换的席位不能为空")
    @Size(max = 16, message = "席位标识不正确")
    private String role;
}

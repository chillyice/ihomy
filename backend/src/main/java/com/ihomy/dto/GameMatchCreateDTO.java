package com.ihomy.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建家庭对战请求:游戏 code 与赛制。
 */
@Data
public class GameMatchCreateDTO {
    @Size(max = 32, message = "游戏标识过长")
    private String gameCode;

    @Size(max = 20, message = "对战方式不正确")
    private String mode;
}

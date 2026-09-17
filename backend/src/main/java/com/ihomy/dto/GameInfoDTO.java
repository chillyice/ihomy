package com.ihomy.dto;

import lombok.Data;

/** 小游戏编辑请求:改名 / 改描述 */
@Data
public class GameInfoDTO {
    private String name;
    private String description;
}

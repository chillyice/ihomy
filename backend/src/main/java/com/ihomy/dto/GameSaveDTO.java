package com.ihomy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 游戏存档文件:游戏目录 saves/ 下的一个文件 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameSaveDTO {

    private String name;

    private long size;

    /** 最后修改时间(epoch 毫秒) */
    private long updatedAt;
}

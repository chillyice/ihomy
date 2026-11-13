package com.ihomy.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 小游戏编辑请求:改名 / 改描述 / 改分类 / 改外链(仅 LINK 类型生效) */
@Data
public class GameInfoDTO {
    @Size(max = 100, message = "游戏名不能超过 100 字")
    private String name;

    @Size(max = 500, message = "描述不能超过 500 字")
    private String description;

    @Size(max = 20, message = "游戏分类无效")
    private String category;

    @Size(max = 50, message = "游戏系列不能超过 50 字")
    private String series;

    @Size(max = 500, message = "网址不能超过 500 字")
    private String linkUrl;

    /** 可见范围(PRIVATE/CIRCLE/FAMILY/PUBLIC);null=不修改 */
    private String visibility;
}

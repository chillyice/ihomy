package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 添加外部网页游戏请求:网址经 GameLinkUrl 校验后入库(LINK 类型) */
@Data
public class GameLinkDTO {
    @NotBlank
    @Size(max = 100, message = "游戏名不能超过 100 字")
    private String name;

    @NotBlank
    @Size(max = 500, message = "网址不能超过 500 字")
    private String url;

    @Size(max = 500, message = "描述不能超过 500 字")
    private String description;

    @Size(max = 20, message = "游戏分类无效")
    private String category;

    @Size(max = 50, message = "游戏系列不能超过 50 字")
    private String series;

    /** 可见范围(PRIVATE/CIRCLE/FAMILY/PUBLIC);空=FAMILY */
    private String visibility;
}

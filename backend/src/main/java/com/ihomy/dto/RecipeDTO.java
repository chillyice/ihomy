package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 菜谱表单:基础字段 + 三个 JSON 字段(素材/设备/步骤)。
 * JSON 字段前端传 JSON 字符串,后端原样存库。
 */
@Data
public class RecipeDTO {
    @NotBlank(message = "请填写菜名")
    @Size(max = 100, message = "菜名最多 100 字")
    private String name;
    @Size(max = 500, message = "封面图地址过长")
    private String coverImage;
    @Size(max = 20, message = "菜系取值不正确")
    private String cuisine;
    @Size(max = 20, message = "类别取值不正确")
    private String category;
    @Size(max = 20, message = "风味取值不正确")
    private String flavor;
    @Size(max = 500, message = "简介最多 500 字")
    private String description;
    private String ingredients;
    private String equipment;
    private String steps;
}

package com.ihomy.dto;

import lombok.Data;

/**
 * 菜谱表单:基础字段 + 三个 JSON 字段(素材/设备/步骤)。
 * JSON 字段前端传 JSON 字符串,后端原样存库。
 */
@Data
public class RecipeDTO {
    private String name;
    private String coverImage;
    private String cuisine;
    private String category;
    private String flavor;
    private String description;
    private String ingredients;
    private String equipment;
    private String steps;
}

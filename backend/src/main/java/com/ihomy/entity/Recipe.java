package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭菜谱实体(family_recipe):
 * ingredients/equipment/steps 均以 JSON 字符串存储,前端解析为对象数组。
 * 结构约定见 schema.sql 表注释。
 */
@Data
@TableName("family_recipe")
public class Recipe {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String name;
    private String coverImage;
    private String cuisine;
    private String category;
    private String flavor;
    private String description;
    private String ingredients;
    private String equipment;
    private String steps;
    private Long authorId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}

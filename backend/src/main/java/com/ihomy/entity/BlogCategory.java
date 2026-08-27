package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 博客分类实体(content_blog_category):家庭级分类,子分类用 name 含父前缀(如 技术/前端)。
 */
@Data
@TableName("content_blog_category")
public class BlogCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long familyId;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}

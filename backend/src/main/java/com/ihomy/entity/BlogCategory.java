package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 博客分类实体(content_blog_category):家庭级分类树,parent_id 自引用(NULL=顶级)。
 */
@Data
@TableName("content_blog_category")
public class BlogCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long parentId;
    private Long familyId;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}

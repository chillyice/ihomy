package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 博客实体(content_blog):status DRAFT/PUBLISHED/HIDDEN,visibility PRIVATE/FAMILY/PUBLIC。
 */
@Data
@TableName("content_blog")
public class Blog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private String coverImage;
    private String tags;
    private Long authorId;
    private Long familyId;
    private String status;
    private String visibility;
    private Integer viewCount;
    private Integer likeCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}

package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 电子图书实体(content_book):file_format EPUB/PDF/TXT/MOBI,status DRAFT/PUBLISHED,visibility PRIVATE/FAMILY/PUBLIC。
 */
@Data
@TableName("content_book")
public class ContentBook {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String author;
    private String description;
    private String coverUrl;
    private String fileUrl;
    private String fileFormat;
    private Long fileSize;
    private String category;
    private String tags;
    private String status;
    private String visibility;
    private Long uploaderId;
    private Long familyId;
    private Integer viewCount;
    private Integer likeCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}

package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 电子书书签实体(content_book_bookmark):cfi 为 EPUB 定位符(精确到章节内字符,前端据此跳回原处),
 * label 为用户可读的书签备注;仅本人可见,不参与家庭共享。
 */
@Data
@TableName("content_book_bookmark")
public class BookBookmark {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bookId;
    private Long userId;
    private Long familyId;
    private String cfi;
    private String label;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}

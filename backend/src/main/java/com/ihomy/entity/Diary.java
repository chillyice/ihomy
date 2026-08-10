package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生活日志实体(content_diary):多图以 JSON 字符串存 images 字段。
 */
@Data
@TableName("content_diary")
public class Diary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String content;
    private String mood;
    private String weather;
    private String images;
    private Integer likeCount;
    private Long authorId;
    private Long familyId;
    private String visibility;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}

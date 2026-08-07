package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 点赞实体(content_like):content_type+content_id+user_id 唯一(DB UNIQUE 防重复点赞)。
 */
@Data
@TableName("content_like")
public class ContentLike {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String contentType;
    private Long contentId;
    private Long userId;
    private Long familyId;
    private LocalDateTime createdAt;
}

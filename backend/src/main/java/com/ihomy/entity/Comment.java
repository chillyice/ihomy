package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论实体(content_comment):content_type+content_id 定位目标内容,parentId 形成回复树。
 */
@Data
@TableName("content_comment")
public class Comment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String contentType;
    private Long contentId;
    private Long parentId;
    private Long replyToUserId;
    private String content;
    private Long authorId;
    private Long familyId;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}

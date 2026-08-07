package com.ihomy.dto;

import lombok.Data;

/**
 * 评论表单:contentType/blog|diary|photo + contentId 定位目标,parentId 回复树。
 */
@Data
public class CommentDTO {
    private String contentType;
    private Long contentId;
    private Long parentId;
    private Long replyToUserId;
    private String content;
}

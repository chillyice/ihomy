package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知实体(family_notification):type 如 comment/reply/system,sourceId 指向关联业务记录。
 */
@Data
@TableName("family_notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long receiverId;
    private String type;
    private String content;
    private Long sourceId;
    private String contentType;
    private Long contentId;
    private Integer isRead;
    private LocalDateTime createdAt;
}

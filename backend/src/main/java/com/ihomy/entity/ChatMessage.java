package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息实体(family_chat_message):家庭房间内的一条消息。
 * msg_type 见 common/ChatMsgType;payload 为结构化 JSON(≤4KB,TEXT 恒为 NULL)。
 */
@Data
@TableName("family_chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long senderId;
    private String content;
    private String msgType;
    private String payload;
    private LocalDateTime createdAt;
}
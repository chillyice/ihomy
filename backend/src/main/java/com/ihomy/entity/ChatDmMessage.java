package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私聊消息(sys_chat_dm_message):成员与成员之间的私信(同家庭或亲友圈家庭之间)。
 * msg_type 见 common/ChatMsgType;payload 为结构化 JSON(≤4KB,TEXT 恒为 NULL);
 * read_at 为收件人已读时间,NULL 即未读。
 */
@Data
@TableName("sys_chat_dm_message")
public class ChatDmMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long senderId;
    private Long recipientId;
    private String content;
    private String msgType;
    private String payload;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}

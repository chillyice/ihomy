package com.ihomy.dto;

import lombok.Data;

/**
 * 聊天已读请求:msgId 为要标记已读的消息 ID(缺省=该家庭最新消息)。
 */
@Data
public class ChatReadDTO {
    private Long msgId;
}
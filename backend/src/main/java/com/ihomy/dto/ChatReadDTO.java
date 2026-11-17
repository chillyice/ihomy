package com.ihomy.dto;

import lombok.Data;

/**
 * 聊天已读请求:
 * 家庭聊天,msgId 为要标记已读的消息 ID(缺省=该家庭最新消息),familyId 指定亲友圈家庭房间(缺省=本家);
 * 私聊,peerId 为对方用户 id(把对方发来的未读全部置已读),二者只传其一。
 */
@Data
public class ChatReadDTO {
    private Long msgId;
    private Long familyId;
    private Long peerId;
}

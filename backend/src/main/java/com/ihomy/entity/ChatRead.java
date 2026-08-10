package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天已读游标实体(family_chat_read):记录某用户在某家庭最后已读的消息 ID,
 * 未读数 = 该家庭最大消息 ID - lastReadMsgId(比 Redis 持久可靠)。
 */
@Data
@TableName("family_chat_read")
public class ChatRead {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long familyId;
    private Long lastReadMsgId;
    private LocalDateTime updatedAt;
}
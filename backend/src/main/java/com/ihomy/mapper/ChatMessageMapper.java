package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息映射。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
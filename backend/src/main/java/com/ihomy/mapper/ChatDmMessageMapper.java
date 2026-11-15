package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.ChatDmMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 私聊消息映射。
 */
@Mapper
public interface ChatDmMessageMapper extends BaseMapper<ChatDmMessage> {
}

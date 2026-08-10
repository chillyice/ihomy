package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.ChatRead;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天已读游标映射。
 */
@Mapper
public interface ChatReadMapper extends BaseMapper<ChatRead> {
}
package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.GameRoomMember;
import org.apache.ibatis.annotations.Mapper;

/** 游戏房间成员 mapper:全部走 MP 条件构造(见 GameRoomService) */
@Mapper
public interface GameRoomMemberMapper extends BaseMapper<GameRoomMember> {
}

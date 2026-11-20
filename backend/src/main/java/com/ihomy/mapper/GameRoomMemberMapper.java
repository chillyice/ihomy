package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.GameRoomMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 游戏房间成员 mapper:条件构造走 MP,进出房间的物理删在 XML */
@Mapper
public interface GameRoomMemberMapper extends BaseMapper<GameRoomMember> {

    /** 物理删除成员行(退出/踢出):逻辑删会撞 uk_room_user_deleted,见 GameRoomMemberMapper.xml */
    int hardDelete(@Param("id") Long id);
}

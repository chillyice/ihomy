package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.GameMatch;
import org.apache.ibatis.annotations.Mapper;

/** 家庭游戏对战 mapper:自定义 SQL 无,全部走 MP 条件构造(见 GameMatchService) */
@Mapper
public interface GameMatchMapper extends BaseMapper<GameMatch> {
}
